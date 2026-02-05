package mod.test.mymodtest.armor.effect;

import mod.test.mymodtest.armor.ArmorConfig;
import mod.test.mymodtest.armor.item.ArmorItems;
import mod.test.mymodtest.armor.util.CooldownManager;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * 旧市护甲 - 经验"分流增益"（交易/击杀双通道）
 *
 * 机制：
 * - 交易经验：50% 概率 ×1.5，CD 60s，仅首次完成该交易槽
 * - 击杀经验：25% 概率 ×2，CD 30s
 * - 两条增益各自独立冷却
 */
public class OldMarketHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("MoonTrace");
    private static final Random RANDOM = new Random();

    /** 记录已触发的交易槽 key: playerUUID:merchantUUID:tradeIndex */
    private static final Set<String> triggeredTrades = new HashSet<>();

    /**
     * 交易完成时调用（由 TradeOutputSlotMixin 触发）
     *
     * @param player      完成交易的玩家
     * @param merchantId  商人 UUID
     * @param tradeIndex  交易槽索引
     * @param baseXp      原始交易经验
     * @param currentTick 当前 tick
     * @return 额外给予的经验值（0 表示未触发）
     */
    public static int onTradeComplete(ServerPlayerEntity player, UUID merchantId, int tradeIndex, int baseXp, long currentTick) {
        // 检查是否穿戴该胸甲
        if (!isWearing(player)) {
            return 0;
        }

        // 生成交易 key
        String tradeKey = player.getUuid() + ":" + merchantId + ":" + tradeIndex;

        // 检查是否首次触发该交易槽
        if (triggeredTrades.contains(tradeKey)) {
            if (ArmorConfig.DEBUG) {
                LOGGER.info("[MoonTrace|Armor|TRIGGER] action=check result=BLOCKED reason=trade_already_triggered tradeKey={} ctx{{p={}}}",
                        tradeKey, player.getName().getString());
            }
            return 0;
        }

        // 检查冷却
        if (!CooldownManager.isReady(player.getUuid(), ArmorConfig.OLD_MARKET_TRADE_XP_EFFECT_ID, currentTick)) {
            if (ArmorConfig.DEBUG) {
                long cdLeft = CooldownManager.getRemainingTicks(player.getUuid(), ArmorConfig.OLD_MARKET_TRADE_XP_EFFECT_ID, currentTick);
                LOGGER.info("[MoonTrace|Armor|TRIGGER] action=check result=BLOCKED reason=cd_hit cd_total={} cd_left={} ctx{{p={}}}",
                        ArmorConfig.OLD_MARKET_TRADE_XP_COOLDOWN, cdLeft, player.getName().getString());
            }
            return 0;
        }

        // RNG 检查
        float roll = RANDOM.nextFloat();
        if (roll >= ArmorConfig.OLD_MARKET_TRADE_XP_CHANCE) {
            if (ArmorConfig.DEBUG) {
                LOGGER.info("[MoonTrace|Armor|TRIGGER] action=check result=BLOCKED reason=rng_fail rng{{roll={} need={} hit=NO}} ctx{{p={}}}",
                        String.format("%.2f", roll), ArmorConfig.OLD_MARKET_TRADE_XP_CHANCE, player.getName().getString());
            }
            return 0;
        }

        // 触发！
        triggeredTrades.add(tradeKey);
        CooldownManager.setCooldown(player.getUuid(), ArmorConfig.OLD_MARKET_TRADE_XP_EFFECT_ID, currentTick, ArmorConfig.OLD_MARKET_TRADE_XP_COOLDOWN);

        // 计算额外经验
        int bonusXp = (int) Math.floor(baseXp * (ArmorConfig.OLD_MARKET_TRADE_XP_MULTIPLIER - 1.0f));
        int totalXp = baseXp + bonusXp;

        // 日志
        LOGGER.info("[MoonTrace|Armor|TRIGGER] action=trigger result=OK effect={} rng{{roll={} need={} hit=YES}} ctx{{p={} merchant={} trade_idx={}}}",
                ArmorConfig.OLD_MARKET_TRADE_XP_EFFECT_ID, String.format("%.2f", roll), ArmorConfig.OLD_MARKET_TRADE_XP_CHANCE,
                player.getName().getString(), merchantId.toString().substring(0, 8), tradeIndex);
        LOGGER.info("[MoonTrace|Armor|APPLY] action=apply result=OK effect=xp_multiplier final{{base={} bonus={} total={}}} ctx{{p={}}}",
                baseXp, bonusXp, totalXp, player.getName().getString());

        return bonusXp;
    }

    /**
     * 怪物掉落经验时调用（由 LivingEntityDropXpMixin 触发）
     *
     * @param player      击杀者
     * @param baseXp      原始掉落经验
     * @param currentTick 当前 tick
     * @return 额外给予的经验值（0 表示未触发）
     */
    public static int onKillXp(ServerPlayerEntity player, int baseXp, long currentTick) {
        // 检查是否穿戴该胸甲
        if (!isWearing(player)) {
            return 0;
        }

        // 检查冷却
        if (!CooldownManager.isReady(player.getUuid(), ArmorConfig.OLD_MARKET_KILL_XP_EFFECT_ID, currentTick)) {
            if (ArmorConfig.DEBUG) {
                long cdLeft = CooldownManager.getRemainingTicks(player.getUuid(), ArmorConfig.OLD_MARKET_KILL_XP_EFFECT_ID, currentTick);
                LOGGER.info("[MoonTrace|Armor|TRIGGER] action=check result=BLOCKED reason=cd_hit cd_total={} cd_left={} ctx{{p={}}}",
                        ArmorConfig.OLD_MARKET_KILL_XP_COOLDOWN, cdLeft, player.getName().getString());
            }
            return 0;
        }

        // RNG 检查
        float roll = RANDOM.nextFloat();
        if (roll >= ArmorConfig.OLD_MARKET_KILL_XP_CHANCE) {
            if (ArmorConfig.DEBUG) {
                LOGGER.info("[MoonTrace|Armor|TRIGGER] action=check result=BLOCKED reason=rng_fail rng{{roll={} need={} hit=NO}} ctx{{p={}}}",
                        String.format("%.2f", roll), ArmorConfig.OLD_MARKET_KILL_XP_CHANCE, player.getName().getString());
            }
            return 0;
        }

        // 触发！
        CooldownManager.setCooldown(player.getUuid(), ArmorConfig.OLD_MARKET_KILL_XP_EFFECT_ID, currentTick, ArmorConfig.OLD_MARKET_KILL_XP_COOLDOWN);

        // 计算额外经验（×2 = 额外 100%）
        int bonusXp = (int) Math.floor(baseXp * (ArmorConfig.OLD_MARKET_KILL_XP_MULTIPLIER - 1.0f));
        int totalXp = baseXp + bonusXp;

        // 日志
        LOGGER.info("[MoonTrace|Armor|TRIGGER] action=trigger result=OK effect={} rng{{roll={} need={} hit=YES}} ctx{{p={}}}",
                ArmorConfig.OLD_MARKET_KILL_XP_EFFECT_ID, String.format("%.2f", roll), ArmorConfig.OLD_MARKET_KILL_XP_CHANCE,
                player.getName().getString());
        LOGGER.info("[MoonTrace|Armor|APPLY] action=apply result=OK effect=xp_multiplier final{{base={} bonus={} total={}}} ctx{{p={}}}",
                baseXp, bonusXp, totalXp, player.getName().getString());

        return bonusXp;
    }

    /**
     * 生成额外经验球给玩家
     */
    public static void spawnBonusXp(ServerWorld world, ServerPlayerEntity player, int bonusXp) {
        if (bonusXp > 0) {
            ExperienceOrbEntity.spawn(world, player.getPos(), bonusXp);
        }
    }

    private static boolean isWearing(ServerPlayerEntity player) {
        return player.getEquippedStack(EquipmentSlot.CHEST).isOf(ArmorItems.OLD_MARKET_CHESTPLATE);
    }

    /**
     * 清理交易记录（可选：玩家下线时）
     */
    public static void clearPlayerTrades(UUID playerId) {
        String prefix = playerId.toString() + ":";
        triggeredTrades.removeIf(key -> key.startsWith(prefix));
    }
}
