package dev.xqanzd.moonlitbroker.trade.loot;

import dev.xqanzd.moonlitbroker.armor.util.CooldownManager;
import dev.xqanzd.moonlitbroker.registry.ModEntityTypeTags;
import dev.xqanzd.moonlitbroker.registry.ModItems;
import dev.xqanzd.moonlitbroker.trade.TradeConfig;
import dev.xqanzd.moonlitbroker.trade.item.BountyContractItem;
import dev.xqanzd.moonlitbroker.world.MerchantUnlockState;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.Angerable;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import net.minecraft.world.World;

/**
 * Bounty v2: 怪物击杀 → 掉落悬赏契约
 * Tag 驱动目标池 + 低概率掉落，需持有 MerchantMark，背包中无契约时才掉落
 *
 * --- 测试步骤 ---
 * 1) /give @p xqanzd_moonlit_broker:merchant_mark   (或首次右键商人自动获得)
 * 2) 击杀敌对生物，0.5% 概率掉落悬赏契约（查看日志 action=BOUNTY_CONTRACT_DROP）
 *    或临时命令: /give @p xqanzd_moonlit_broker:bounty_contract
 *    然后手动初始化: 使用 /bountycontract give <player> zombie 5
 * 3) 杀对应目标至进度满（日志 action=BOUNTY_PROGRESS）
 * 4) 右键神秘商人提交契约（日志 action=BOUNTY_SUBMIT_ACCEPT）
 * 5) 获得 Trade Scroll (Grade=NORMAL, Uses=3)
 * 6) 用 Scroll 刷新 NORMAL 页，确认:
 *    - 日志中 offersHash 变化（对比 OPEN_UI 前后）
 *    - Scroll NBT Uses 减 1
 *    - 日志出现 action=REFRESH / NORMAL_BUILD
 */
public class BountyDropHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(BountyDropHandler.class);
    private static final Random RANDOM = new Random();
    private static boolean REGISTERED = false;

    private static final float BASE_DROP_CHANCE = 0.008f; // 0.8%
    private static final float LOOTING_BONUS_PER_LEVEL = 0.001f; // +0.1% / level
    private static final float MAX_DROP_CHANCE = 0.02f; // 2%
    // cooldown 值从 TradeConfig.BOUNTY_DROP_COOLDOWN_TICKS 读取
    private static volatile boolean bountyTagEmptyWarned = false;
    private static volatile boolean pickWeightWarned = false;
    private static volatile boolean commonPoolEmptyWarned = false;

    /** Dimension hint cooldown (2400 ticks = 2 minutes) per player per dimension type */
    private static final long DIM_HINT_CD_TICKS = 2400L;
    private static final String COOLDOWN_DIM_HINT_NETHER = "bounty_dim_hint_nether";
    private static final String COOLDOWN_DIM_HINT_END = "bounty_dim_hint_end";
    private static final String COOLDOWN_DIM_HINT_OCEAN = "bounty_dim_hint_ocean";

    // ===== Elite density tiers (caps required for rare elites) =====
    enum DensityTier {
        DENSE(15), MEDIUM(12), RARE(10);
        final int capMax;
        DensityTier(int capMax) { this.capMax = capMax; }
    }

    private static final Set<EntityType<?>> ELITE_RARE = Set.of(
            EntityType.EVOKER, EntityType.PIGLIN_BRUTE);
    private static final Set<EntityType<?>> ELITE_MEDIUM = Set.of(
            EntityType.WITHER_SKELETON);
    // DENSE = all other elites (pillager, vindicator, witch, etc.)

    /** Nether-only mobs: re-roll when player is in the overworld */
    private static final Set<EntityType<?>> NETHER_ONLY = Set.of(
            EntityType.BLAZE, EntityType.GHAST, EntityType.MAGMA_CUBE,
            EntityType.WITHER_SKELETON, EntityType.PIGLIN_BRUTE,
            EntityType.PIGLIN, EntityType.ZOMBIFIED_PIGLIN);

    /** End-dimension mobs: soft dimension hint */
    private static final Set<EntityType<?>> END_MOBS = Set.of(EntityType.SHULKER);
    /** Ocean structure mobs: soft dimension hint */
    private static final Set<EntityType<?>> OCEAN_MOBS = Set.of(EntityType.ELDER_GUARDIAN);

    static DensityTier getEliteDensityTier(EntityType<?> type) {
        if (ELITE_RARE.contains(type)) return DensityTier.RARE;
        if (ELITE_MEDIUM.contains(type)) return DensityTier.MEDIUM;
        return DensityTier.DENSE;
    }

    // ===== Triangular distribution sampling =====
    static int sampleTriangularInt(Random r, int min, int mode, int max) {
        double u = r.nextDouble();
        double c = (mode - min) / (double) (max - min);
        double x;
        if (u < c) {
            x = min + Math.sqrt(u * (max - min) * (mode - min));
        } else {
            x = max - Math.sqrt((1 - u) * (max - min) * (max - mode));
        }
        return Math.max(min, Math.min(max, (int) Math.round(x)));
    }

    public static void register() {
        if (REGISTERED) {
            return;
        }
        REGISTERED = true;
        ServerLivingEntityEvents.AFTER_DEATH.register(BountyDropHandler::onMobDeath);
        LOGGER.info("[MoonTrade] action=BOUNTY_DROP_HANDLER_REGISTER side=S");
    }

    private static void onMobDeath(LivingEntity entity, DamageSource source) {
        // 仅玩家击杀
        if (!(source.getAttacker() instanceof ServerPlayerEntity player)) return;
        if (!(entity.getWorld() instanceof ServerWorld world)) return;
        Identifier mobId = Registries.ENTITY_TYPE.getId(entity.getType());

        // 改为 tag 驱动，不再写死目标白名单。
        if (!entity.getType().isIn(ModEntityTypeTags.BOUNTY_TARGETS)) {
            // 护栏 B: release 下只 warn 一次
            if (!bountyTagEmptyWarned) {
                bountyTagEmptyWarned = true;
                LOGGER.warn("[MoonTrade] action=BOUNTY_TAG_MISS mob={} — 该实体不在 bounty_targets tag 中。" +
                        "若所有怪物都不掉契约，请检查 data/{}/tags/entity_type/ 目录",
                        mobId, ModItems.MOD_ID);
            }
            return;
        }

        // (4) neutral gate：中立目标只有"正在对该玩家仇恨/激怒"时才允许掉落
        if (entity.getType().isIn(ModEntityTypeTags.BOUNTY_NEUTRAL_TARGETS) && !isAngeredAtPlayer(entity, player)) {
            if (TradeConfig.TRADE_DEBUG) {
                LOGGER.info(
                        "[MoonTrade] action=BOUNTY_CONTRACT_DROP_CHECK result=SKIP_NEUTRAL_NOT_ANGRY mob={} player={} dim={}",
                        mobId, player.getName().getString(), world.getRegistryKey().getValue());
            }
            return;
        }

        // (5) creative gate：避免创造模式测试刷物品污染生态
        if (player.isCreative()) {
            if (TradeConfig.TRADE_DEBUG) {
                LOGGER.info("[MoonTrade] action=BOUNTY_CONTRACT_DROP_CHECK result=SKIP_CREATIVE player={}", player.getName().getString());
            }
            return;
        }

        // (6) MarkBound gate：玩家需曾与商人交互过
        boolean eligible = MerchantUnlockState.isBountyEligible(world, player.getUuid());
        if (!eligible) {
            if (TradeConfig.TRADE_DEBUG) {
                LOGGER.info("[MoonTrade] action=BOUNTY_GATE result=NOT_ELIGIBLE mob={} player={} dim={}",
                        mobId, player.getName().getString(), world.getRegistryKey().getValue());
            }
            maybeSendUnregisteredHint(world, player);
            return;
        }

        // (7) "背包已有契约" gate（maxCount=1 精神）
        boolean hasContract = playerHasBountyContract(player);
        if (hasContract) {
            if (TradeConfig.TRADE_DEBUG) {
                LOGGER.info("[MoonTrade] action=BOUNTY_GATE result=HAS_CONTRACT mob={} player={} dim={}",
                        mobId, player.getName().getString(), world.getRegistryKey().getValue());
            }
            return;
        }

        // (8) 并发掉落冷却：同 tick 多怪死亡只掉 1 张
        if (MerchantUnlockState.isDropCooldownActive(world, player.getUuid(), TradeConfig.BOUNTY_DROP_COOLDOWN_TICKS)) {
            if (TradeConfig.TRADE_DEBUG) {
                LOGGER.info("[MoonTrade] action=BOUNTY_CONTRACT_DROP_CHECK result=SKIP_COOLDOWN player={}", player.getName().getString());
            }
            return;
        }

        // 概率判定
        int lootingLevel = getLootingLevel(world, player);
        float baseChance = Math.min(MAX_DROP_CHANCE, BASE_DROP_CHANCE + lootingLevel * LOOTING_BONUS_PER_LEVEL);
        // Elite 倍率：提高概率，不改阀门
        boolean isElite = entity.getType().isIn(ModEntityTypeTags.BOUNTY_ELITE_TARGETS);
        float chance = (TradeConfig.ENABLE_ELITE_DROP_BONUS && isElite)
                ? Math.min(1.0f, baseChance * TradeConfig.BOUNTY_ELITE_CHANCE_MULTIPLIER)
                : baseChance;
        // 仅在 dev + debug + FORCE_BOUNTY_DROP 三重门同时为 true 时强制 chance=1.0f
        if (TradeConfig.MASTER_DEBUG && TradeConfig.TRADE_DEBUG && TradeConfig.FORCE_BOUNTY_DROP) {
            chance = 1.0f;
        }
        float roll = RANDOM.nextFloat();
        if (roll >= chance) {
            if (TradeConfig.TRADE_DEBUG) {
                LOGGER.info(
                        "[MoonTrade] action=BOUNTY_CONTRACT_DROP_CHECK result=MISS mob={} roll={} chance={} elite={} looting={} player={} dim={}",
                        mobId, roll, chance, isElite, lootingLevel, player.getName().getString(),
                        world.getRegistryKey().getValue());
            }
            return;
        }
        // Pick target from tag pools (may differ from killed mob)
        Identifier chosen = pickBountyTarget(world, player, mobId);

        // Determine tier from tag membership of chosen target
        EntityType<?> chosenType = Registries.ENTITY_TYPE.get(chosen);
        boolean chosenIsRare = chosenType.isIn(ModEntityTypeTags.BOUNTY_RARE_TARGETS);
        boolean chosenIsElite = chosenType.isIn(ModEntityTypeTags.BOUNTY_ELITE_TARGETS);
        String bountyTier = chosenIsRare ? BountyContractItem.TIER_RARE
                : chosenIsElite ? BountyContractItem.TIER_ELITE
                : BountyContractItem.TIER_COMMON;
        int required = rollRequiredForTier(bountyTier, chosenType);

        if (TradeConfig.TRADE_DEBUG) {
            DensityTier densityTier = chosenIsElite ? getEliteDensityTier(chosenType) : null;
            boolean sameAsKilled = chosen.equals(mobId);
            LOGGER.debug("[Bounty] CONTRACT_PICK killedMobId={} chosenTargetId={} tier={} required={} playerDim={} sameAsKilled={} densityTier={}",
                    mobId, chosen, bountyTier, required, world.getRegistryKey().getValue(), sameAsKilled, densityTier);
        }

        // 生成契约（含 Tier + Schema）
        ItemStack contract = new ItemStack(ModItems.BOUNTY_CONTRACT, 1);
        BountyContractItem.initializeWithTier(contract, chosen.toString(), required, bountyTier);
        net.minecraft.entity.ItemEntity itemEntity = entity.dropStack(contract);

        // P0: 掉落反馈 - 发光 + actionbar + 音效（仅 dropStack 成功时）
        if (itemEntity != null) {
            // 记录掉落 tick，启动冷却窗口
            MerchantUnlockState.recordContractDrop(world, player.getUuid());
            itemEntity.setGlowing(true);

            // actionbar: tier-specific message with target + required
            net.minecraft.text.Text targetName = resolveTargetName(chosen.toString());
            String dropKey;
            net.minecraft.util.Formatting dropColor;
            switch (bountyTier) {
                case BountyContractItem.TIER_RARE:
                    dropKey = "actionbar.xqanzd_moonlit_broker.bounty.contract_drop.rare";
                    dropColor = net.minecraft.util.Formatting.AQUA;
                    break;
                case BountyContractItem.TIER_ELITE:
                    dropKey = "actionbar.xqanzd_moonlit_broker.bounty.contract_drop.elite";
                    dropColor = net.minecraft.util.Formatting.GOLD;
                    break;
                default:
                    dropKey = "actionbar.xqanzd_moonlit_broker.bounty.contract_drop";
                    dropColor = net.minecraft.util.Formatting.LIGHT_PURPLE;
                    break;
            }
            player.sendMessage(
                    net.minecraft.text.Text.translatable(dropKey, targetName, required)
                            .formatted(dropColor),
                    true);

            // Dimension hint: rate-limited soft chat message when target is in another dimension
            long hintTick = world.getTime();
            if (END_MOBS.contains(chosenType)) {
                if (CooldownManager.isReady(player.getUuid(), COOLDOWN_DIM_HINT_END, hintTick)) {
                    CooldownManager.setCooldown(player.getUuid(), COOLDOWN_DIM_HINT_END, hintTick, DIM_HINT_CD_TICKS);
                    player.sendMessage(
                            net.minecraft.text.Text.translatable("msg.xqanzd_moonlit_broker.bounty.dim_hint.end")
                                    .formatted(net.minecraft.util.Formatting.GRAY),
                            false);
                }
            } else if (OCEAN_MOBS.contains(chosenType)) {
                if (CooldownManager.isReady(player.getUuid(), COOLDOWN_DIM_HINT_OCEAN, hintTick)) {
                    CooldownManager.setCooldown(player.getUuid(), COOLDOWN_DIM_HINT_OCEAN, hintTick, DIM_HINT_CD_TICKS);
                    player.sendMessage(
                            net.minecraft.text.Text.translatable("msg.xqanzd_moonlit_broker.bounty.dim_hint.ocean")
                                    .formatted(net.minecraft.util.Formatting.GRAY),
                            false);
                }
            } else if (NETHER_ONLY.contains(chosenType) && world.getRegistryKey() != World.NETHER) {
                if (CooldownManager.isReady(player.getUuid(), COOLDOWN_DIM_HINT_NETHER, hintTick)) {
                    CooldownManager.setCooldown(player.getUuid(), COOLDOWN_DIM_HINT_NETHER, hintTick, DIM_HINT_CD_TICKS);
                    player.sendMessage(
                            net.minecraft.text.Text.translatable("msg.xqanzd_moonlit_broker.bounty.dim_hint.nether")
                                    .formatted(net.minecraft.util.Formatting.GRAY),
                            false);
                }
            }

            // 音效: 经验球拾取音（明显但不刺耳）
            player.getWorld().playSound(null,
                    player.getX(), player.getY(), player.getZ(),
                    net.minecraft.sound.SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP,
                    net.minecraft.sound.SoundCategory.PLAYERS,
                    1.0f, 0.8f);
        } else {
            LOGGER.warn("[MoonTrade] action=BOUNTY_CONTRACT_DROP result=DROP_STACK_NULL mob={} player={}",
                    mobId, player.getName().getString());
        }

        LOGGER.info(
                "[MoonTrade] action=BOUNTY_CONTRACT_DROP result=DROP mob={} chosen={} required={} tier={} roll={} chance={} elite={} looting={} side=S player={} dim={}",
                mobId, chosen, required, bountyTier, roll, chance, isElite, lootingLevel, player.getName().getString(),
                world.getRegistryKey().getValue());
    }

    /**
     * 将 entity ID 字符串解析为可读名称
     */
    private static net.minecraft.text.Text resolveTargetName(String target) {
        net.minecraft.util.Identifier targetId = net.minecraft.util.Identifier.tryParse(target);
        if (targetId != null) {
            net.minecraft.entity.EntityType<?> entityType = Registries.ENTITY_TYPE.get(targetId);
            if (entityType != null) {
                return entityType.getName();
            }
        }
        return net.minecraft.text.Text.literal(target);
    }

    private static boolean playerHasBountyContract(ServerPlayerEntity player) {
        for (ItemStack stack : player.getInventory().main) {
            if (stack.isOf(ModItems.BOUNTY_CONTRACT)) return true;
        }
        for (ItemStack stack : player.getInventory().offHand) {
            if (stack.isOf(ModItems.BOUNTY_CONTRACT)) return true;
        }
        return false;
    }

    private static int rollRequired(LivingEntity entity) {
        boolean isElite = entity.getType().isIn(ModEntityTypeTags.BOUNTY_ELITE_TARGETS);
        int required;
        if (isElite) {
            // Elite: triangular [5..15] mode 10, then cap by density tier
            required = sampleTriangularInt(RANDOM, 5, 10, 15);
            DensityTier tier = getEliteDensityTier(entity.getType());
            required = Math.max(5, Math.min(required, tier.capMax));
        } else {
            // Normal: triangular [10..25] mode 17
            required = sampleTriangularInt(RANDOM, 10, 17, 25);
        }
        return required;
    }

    /**
     * Pick a bounty target from tag pools by tier weight.
     * Falls back to killedMobId if pools are empty.
     * Re-rolls nether-only targets when the player is in the overworld.
     */
    private static Identifier pickBountyTarget(ServerWorld world, ServerPlayerEntity player, Identifier killedMobId) {
        List<Identifier> allList = Registries.ENTITY_TYPE.stream()
                .filter(t -> t.isIn(ModEntityTypeTags.BOUNTY_TARGETS))
                .map(Registries.ENTITY_TYPE::getId)
                .collect(Collectors.toCollection(ArrayList::new));
        if (allList.isEmpty()) return killedMobId;

        Set<Identifier> eliteSet = Registries.ENTITY_TYPE.stream()
                .filter(t -> t.isIn(ModEntityTypeTags.BOUNTY_ELITE_TARGETS))
                .map(Registries.ENTITY_TYPE::getId)
                .collect(Collectors.toSet());
        Set<Identifier> rareSet = Registries.ENTITY_TYPE.stream()
                .filter(t -> t.isIn(ModEntityTypeTags.BOUNTY_RARE_TARGETS))
                .map(Registries.ENTITY_TYPE::getId)
                .collect(Collectors.toSet());

        List<Identifier> commonList = allList.stream()
                .filter(id -> !eliteSet.contains(id) && !rareSet.contains(id))
                .collect(Collectors.toCollection(ArrayList::new));
        List<Identifier> eliteList = new ArrayList<>(eliteSet);
        List<Identifier> rareList = new ArrayList<>(rareSet);

        // Guardrail: clamp weights if misconfigured
        float rareW = TradeConfig.BOUNTY_PICK_RARE_WEIGHT;
        float eliteW = TradeConfig.BOUNTY_PICK_ELITE_WEIGHT;
        if (rareW + eliteW > 1.0f) {
            if (!pickWeightWarned) {
                pickWeightWarned = true;
                LOGGER.warn("[MoonTrade] action=BOUNTY_PICK_WEIGHT_CLAMP rareWeight={} eliteWeight={} sum={} — clamped to 1.0",
                        rareW, eliteW, rareW + eliteW);
            }
            float scale = 1.0f / (rareW + eliteW);
            rareW *= scale;
            eliteW *= scale;
        }

        // Guardrail: warn once if common pool is empty (all entities are elite or rare)
        if (commonList.isEmpty() && !commonPoolEmptyWarned) {
            commonPoolEmptyWarned = true;
            LOGGER.warn("[MoonTrade] action=BOUNTY_PICK_COMMON_POOL_EMPTY — all bounty_targets are elite/rare, falling back to allList");
        }

        // Tier roll
        float r = RANDOM.nextFloat();
        List<Identifier> pool;
        String rolledFrom;
        if (r < rareW && !rareList.isEmpty()) {
            pool = rareList;
            rolledFrom = "rare";
        } else if (r < rareW + eliteW && !eliteList.isEmpty()) {
            pool = eliteList;
            rolledFrom = "elite";
        } else if (!commonList.isEmpty()) {
            pool = commonList;
            rolledFrom = "common";
        } else {
            pool = allList;
            rolledFrom = "fallback_all";
        }

        Identifier chosen = pool.get(RANDOM.nextInt(pool.size()));

        // Dimension re-roll: if chosen target belongs to another dimension and player is in overworld,
        // re-roll once from the same pool (excluding cross-dimension mobs), fall back to commonList
        if (world.getRegistryKey() == World.OVERWORLD) {
            EntityType<?> chosenType = Registries.ENTITY_TYPE.get(chosen);
            if (NETHER_ONLY.contains(chosenType) || END_MOBS.contains(chosenType) || OCEAN_MOBS.contains(chosenType)) {
                // Try same pool minus cross-dimension mobs first
                List<Identifier> filtered = pool.stream()
                        .filter(id -> {
                            EntityType<?> t = Registries.ENTITY_TYPE.get(id);
                            return !NETHER_ONLY.contains(t) && !END_MOBS.contains(t) && !OCEAN_MOBS.contains(t);
                        })
                        .collect(Collectors.toList());
                List<Identifier> rerollPool = !filtered.isEmpty() ? filtered
                        : !commonList.isEmpty() ? commonList
                        : null;
                if (rerollPool != null) {
                    Identifier rerolled = rerollPool.get(RANDOM.nextInt(rerollPool.size()));
                    if (TradeConfig.TRADE_DEBUG) {
                        LOGGER.debug("[Bounty] DIM_REROLL original={} rerolled={} reason={}",
                                chosen, rerolled,
                                NETHER_ONLY.contains(chosenType) ? "nether" :
                                END_MOBS.contains(chosenType) ? "end" : "ocean");
                    }
                    chosen = rerolled;
                    rolledFrom = rolledFrom + "_rerolled";
                }
                // if no reroll pool available, keep original — dim_hint will warn
            }
        }

        if (TradeConfig.TRADE_DEBUG) {
            LOGGER.debug("[Bounty] PICK_TARGET chosen={} rolledFrom={} killedMob={} poolSize={} playerDim={} allSize={} commonSize={} eliteSize={} rareSize={}",
                    chosen, rolledFrom, killedMobId, pool.size(),
                    world.getRegistryKey().getValue(),
                    allList.size(), commonList.size(), eliteList.size(), rareList.size());
        }

        return chosen;
    }

    /**
     * Roll required kill count based on tier and chosen entity type.
     */
    private static int rollRequiredForTier(String tier, EntityType<?> chosenType) {
        switch (tier) {
            case BountyContractItem.TIER_RARE:
                // Rare: triangular [8..18] mode 12 — lower than common, feels rewarding
                return sampleTriangularInt(RANDOM, 8, 12, 18);
            case BountyContractItem.TIER_ELITE:
                // Elite: triangular [5..15] mode 10, then cap by density tier
                int required = sampleTriangularInt(RANDOM, 5, 10, 15);
                DensityTier densityTier = getEliteDensityTier(chosenType);
                return Math.max(5, Math.min(required, densityTier.capMax));
            default:
                // Common: triangular [10..25] mode 17
                return sampleTriangularInt(RANDOM, 10, 17, 25);
        }
    }

    private static int getLootingLevel(ServerWorld world, ServerPlayerEntity player) {
        Registry<Enchantment> enchantmentRegistry = world.getRegistryManager().get(RegistryKeys.ENCHANTMENT);
        if (enchantmentRegistry == null) {
            return 0;
        }
        Optional<? extends net.minecraft.registry.entry.RegistryEntry.Reference<Enchantment>> looting = enchantmentRegistry
                .getEntry(Enchantments.LOOTING);
        return looting.map(entry -> EnchantmentHelper.getEquipmentLevel(entry, player)).orElse(0);
    }

    private static boolean isAngeredAtPlayer(LivingEntity entity, ServerPlayerEntity player) {
        if (entity instanceof MobEntity mobEntity) {
            LivingEntity target = mobEntity.getTarget();
            if (target != null && target.getUuid().equals(player.getUuid())) {
                return true;
            }
        }
        if (entity instanceof Angerable angerable) {
            return player.getUuid().equals(angerable.getAngryAt());
        }
        return false;
    }

    private static void maybeSendUnregisteredHint(ServerWorld world, ServerPlayerEntity player) {
        MerchantUnlockState state = MerchantUnlockState.getServerState(world);
        MerchantUnlockState.Progress progress = state.getOrCreateProgress(player.getUuid());

        long now = MerchantUnlockState.getOverworldTick(world);
        long last = progress.getLastUnregisteredHintTick();
        if (last != 0L && now - last < TradeConfig.BOUNTY_UNREGISTERED_HINT_COOLDOWN_TICKS) {
            return;
        }

        progress.setLastUnregisteredHintTick(now);
        state.markDirty();

        net.minecraft.text.Text msg = net.minecraft.text.Text.translatable(
                "msg.xqanzd_moonlit_broker.bounty.hint_register"
        ).formatted(net.minecraft.util.Formatting.GRAY);
        player.sendMessage(msg, TradeConfig.BOUNTY_UNREGISTERED_HINT_USE_ACTIONBAR);
    }
}
