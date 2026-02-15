package dev.xqanzd.moonlitbroker.trade.loot;

import dev.xqanzd.moonlitbroker.registry.ModEntityTypeTags;
import dev.xqanzd.moonlitbroker.registry.ModItems;
import dev.xqanzd.moonlitbroker.trade.TradeConfig;
import dev.xqanzd.moonlitbroker.trade.item.BountyContractItem;
import dev.xqanzd.moonlitbroker.trade.item.MerchantMarkItem;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
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

import java.util.Optional;
import java.util.Random;

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

    private static final float BASE_DROP_CHANCE = 0.005f; // 0.5%
    private static final float LOOTING_BONUS_PER_LEVEL = 0.001f; // +0.1% / level
    private static final float MAX_DROP_CHANCE = 0.02f; // 2%
    private static volatile boolean bountyTagEmptyWarned = false;

    public static void register() {
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

        // neutral 目标只有“正在对该玩家仇恨/激怒”时才允许掉落
        if (entity.getType().isIn(ModEntityTypeTags.SILVERNOTE_NEUTRAL_DROPPERS) && !isAngeredAtPlayer(entity, player)) {
            if (TradeConfig.TRADE_DEBUG) {
                LOGGER.info(
                        "[MoonTrade] action=BOUNTY_CONTRACT_DROP_CHECK result=SKIP_NEUTRAL_NOT_ANGRY mob={} player={} dim={}",
                        mobId, player.getName().getString(), world.getRegistryKey().getValue());
            }
            return;
        }

        // Gate: 需持有 MerchantMark
        if (!MerchantMarkItem.playerHasValidMark(player)) return;

        // 背包中已有契约则不掉落（maxCount=1 精神）
        if (playerHasBountyContract(player)) return;

        // 概率判定
        int lootingLevel = getLootingLevel(world, player);
        float baseChance = Math.min(MAX_DROP_CHANCE, BASE_DROP_CHANCE + lootingLevel * LOOTING_BONUS_PER_LEVEL);
        // Elite 倍率：提高概率，不改阀门
        boolean isElite = entity.getType().isIn(ModEntityTypeTags.BOUNTY_ELITE_TARGETS);
        float chance = (TradeConfig.ENABLE_ELITE_DROP_BONUS && isElite)
                ? Math.min(1.0f, baseChance * TradeConfig.BOUNTY_ELITE_CHANCE_MULTIPLIER)
                : baseChance;
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

        // 生成契约
        ItemStack contract = new ItemStack(ModItems.BOUNTY_CONTRACT, 1);
        BountyContractItem.initialize(contract, mobId.toString(), required);
        net.minecraft.entity.ItemEntity itemEntity = entity.dropStack(contract);

        // P0: 掉落反馈 - 发光 + actionbar + 音效（仅 dropStack 成功时）
        if (itemEntity != null) {
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
                "[MoonTrade] action=BOUNTY_CONTRACT_DROP result=DROP mob={} required={} roll={} chance={} elite={} looting={} side=S player={} dim={}",
                mobId, required, roll, chance, isElite, lootingLevel, player.getName().getString(),
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
        // 高血量目标给更低 required，避免过长追猎链。
        double maxHealth = entity.getAttributeValue(EntityAttributes.GENERIC_MAX_HEALTH);
        if (maxHealth >= 30.0) {
            return 2 + RANDOM.nextInt(3); // 2-4
        }
        return 3 + RANDOM.nextInt(4); // 3-6
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
}
