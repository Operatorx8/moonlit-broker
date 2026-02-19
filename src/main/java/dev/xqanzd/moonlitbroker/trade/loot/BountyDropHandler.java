package dev.xqanzd.moonlitbroker.trade.loot;

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

import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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

    private static final float BASE_DROP_CHANCE = 0.005f; // 0.5%
    private static final float LOOTING_BONUS_PER_LEVEL = 0.001f; // +0.1% / level
    private static final float MAX_DROP_CHANCE = 0.02f; // 2%
    // cooldown 值从 TradeConfig.BOUNTY_DROP_COOLDOWN_TICKS 读取
    private static volatile boolean bountyTagEmptyWarned = false;

    // ===== Rate-limited actionbar hints for blocked gates =====
    private static final long BOUNTY_HINT_COOLDOWN_TICKS = 600L; // 30 seconds
    private static final Map<UUID, Long> lastHintTickByPlayer = new ConcurrentHashMap<>();

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
            sendRateLimitedHint(player, world);
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
        int required = rollRequired(entity);

        // Tier 判定：rare > elite > common
        String bountyTier;
        if (entity.getType().isIn(ModEntityTypeTags.BOUNTY_RARE_TARGETS)) {
            bountyTier = BountyContractItem.TIER_RARE;
        } else if (isElite) {
            bountyTier = BountyContractItem.TIER_ELITE;
        } else {
            bountyTier = BountyContractItem.TIER_COMMON;
        }

        if (TradeConfig.TRADE_DEBUG) {
            DensityTier densityTier = isElite ? getEliteDensityTier(entity.getType()) : null;
            LOGGER.debug("[Bounty] CONTRACT_GEN target={} elite={} tier={} densityTier={} required={}",
                    mobId, isElite, bountyTier, densityTier, required);
        }

        // 生成契约（含 Tier + Schema）
        ItemStack contract = new ItemStack(ModItems.BOUNTY_CONTRACT, 1);
        BountyContractItem.initializeWithTier(contract, mobId.toString(), required, bountyTier);
        net.minecraft.entity.ItemEntity itemEntity = entity.dropStack(contract);

        // P0: 掉落反馈 - 发光 + actionbar + 音效（仅 dropStack 成功时）
        if (itemEntity != null) {
            // 记录掉落 tick，启动冷却窗口
            MerchantUnlockState.recordContractDrop(world, player.getUuid());
            itemEntity.setGlowing(true);

            // actionbar: 目标名 + 初始进度
            net.minecraft.text.Text targetName = resolveTargetName(mobId.toString());
            player.sendMessage(
                    net.minecraft.text.Text.translatable(
                            "actionbar.xqanzd_moonlit_broker.bounty.contract_drop",
                            targetName, required
                    ).formatted(net.minecraft.util.Formatting.LIGHT_PURPLE),
                    true);

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
                "[MoonTrade] action=BOUNTY_CONTRACT_DROP result=DROP mob={} required={} tier={} roll={} chance={} elite={} looting={} side=S player={} dim={}",
                mobId, required, bountyTier, roll, chance, isElite, lootingLevel, player.getName().getString(),
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

    /**
     * 向未登记玩家发送 rate-limited actionbar 提示（30 秒冷却）。
     */
    private static void sendRateLimitedHint(ServerPlayerEntity player, ServerWorld world) {
        long now = world.getTime();
        Long last = lastHintTickByPlayer.get(player.getUuid());
        if (last != null && now - last < BOUNTY_HINT_COOLDOWN_TICKS) return;
        lastHintTickByPlayer.put(player.getUuid(), now);
        player.sendMessage(
                net.minecraft.text.Text.translatable(
                        "actionbar.xqanzd_moonlit_broker.bounty.hint_not_registered"
                ).formatted(net.minecraft.util.Formatting.GRAY),
                true);
    }
}
