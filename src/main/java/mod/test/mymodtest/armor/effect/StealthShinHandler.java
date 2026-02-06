package mod.test.mymodtest.armor.effect;

import mod.test.mymodtest.armor.ArmorConfig;
import mod.test.mymodtest.armor.item.ArmorItems;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 潜行之胫 - 摔落垫层（充能型坠落减伤）
 *
 * 机制：
 * - 触发源：摔落伤害结算前判定
 * - 充能：每 45s（900 ticks）获得 1 层，最多 2 层
 * - 消耗：仅当本次摔落伤害 >= 3.0 HP 才消耗 1 层
 * - 减伤：消耗时本次摔落伤害 -80%（×0.20）
 * - 限制：不足门槛不消耗；满层后不再继续充能；45s 到点发一次提示
 */
public class StealthShinHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("MoonTrace");

    /** 玩家上次检查 tick */
    private static final Map<UUID, Long> lastCheckTick = new ConcurrentHashMap<>();

    /** 玩家当前充能层数 */
    private static final Map<UUID, Integer> chargeCount = new ConcurrentHashMap<>();

    /** 玩家下次充能完成 tick */
    private static final Map<UUID, Long> nextChargeTick = new ConcurrentHashMap<>();

    /**
     * 每 tick 调用（在 ServerTickEvent 中）
     * 处理充能逻辑
     */
    public static void tick(ServerWorld world, long nowTick) {
        for (var player : world.getPlayers()) {
            if (!(player instanceof ServerPlayerEntity serverPlayer)) continue;

            UUID playerId = serverPlayer.getUuid();

            // 检查是否穿戴该护腿
            boolean wearing = isWearing(serverPlayer);
            if (!wearing) {
                // 穿脱护腿清理：立即清理所有状态
                if (lastCheckTick.containsKey(playerId) || chargeCount.containsKey(playerId) || nextChargeTick.containsKey(playerId)) {
                    lastCheckTick.remove(playerId);
                    chargeCount.remove(playerId);
                    nextChargeTick.remove(playerId);
                    if (ArmorConfig.DEBUG) {
                        LOGGER.info("[MoonTrace|Armor|STATE] action=state_change result=OK state=stealth_shin_charge charges=0 reason=not_wearing ctx{{p={}}}",
                                serverPlayer.getName().getString());
                    }
                }
                continue;
            }

            // 检查检查间隔
            Long lastTick = lastCheckTick.get(playerId);
            if (lastTick != null && nowTick - lastTick < ArmorConfig.STEALTH_SHIN_CHECK_INTERVAL) {
                continue;
            }
            lastCheckTick.put(playerId, nowTick);

            // 获取当前层数
            int charges = chargeCount.getOrDefault(playerId, 0);

            // 如果已满层，不充能
            if (charges >= ArmorConfig.STEALTH_SHIN_MAX_CHARGES) {
                continue;
            }

            // 初始化下次充能时间
            if (!nextChargeTick.containsKey(playerId)) {
                nextChargeTick.put(playerId, nowTick + ArmorConfig.STEALTH_SHIN_CHARGE_INTERVAL);
            }

            // 检查是否到达充能时间
            Long nextTick = nextChargeTick.get(playerId);
            if (nowTick >= nextTick) {
                // 增加层数
                int newCharges = Math.min(charges + 1, ArmorConfig.STEALTH_SHIN_MAX_CHARGES);
                chargeCount.put(playerId, newCharges);

                // 设置下次充能时间
                if (newCharges < ArmorConfig.STEALTH_SHIN_MAX_CHARGES) {
                    nextChargeTick.put(playerId, nowTick + ArmorConfig.STEALTH_SHIN_CHARGE_INTERVAL);
                } else {
                    nextChargeTick.remove(playerId);
                }

                // 提示玩家
                serverPlayer.sendMessage(Text.literal("[Stealth Shin] Charge ready! (" + newCharges + "/" + ArmorConfig.STEALTH_SHIN_MAX_CHARGES + ")"), true);
                serverPlayer.getWorld().playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                        SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), SoundCategory.PLAYERS, 0.5f, 1.5f);

                LOGGER.info("[MoonTrace|Armor|STATE] action=state_change result=OK state=stealth_shin_charge charges={} ctx{{p={}}}",
                        newCharges, serverPlayer.getName().getString());
            }
        }
    }

    /**
     * 处理摔落伤害
     * 在伤害事件中调用
     *
     * @param player      受伤玩家
     * @param source      伤害来源
     * @param amount      原始伤害值
     * @param nowTick     当前 server tick
     * @return 修改后的伤害值
     */
    public static float onFallDamage(ServerPlayerEntity player, DamageSource source, float amount, long nowTick) {
        // 检查是否穿戴该护腿
        if (!isWearing(player)) {
            return amount;
        }

        // 检查是否为摔落伤害
        if (!isFallDamage(source)) {
            return amount;
        }

        UUID playerId = player.getUuid();

        // 检查是否有充能层数
        int charges = chargeCount.getOrDefault(playerId, 0);
        if (charges <= 0) {
            if (ArmorConfig.DEBUG) {
                LOGGER.info("[MoonTrace|Armor|TRIGGER] action=check result=BLOCKED reason=no_charges ctx{{p={}}}",
                        player.getName().getString());
            }
            return amount;
        }

        // 检查伤害是否达到门槛
        if (amount < ArmorConfig.STEALTH_SHIN_MIN_FALL_DAMAGE) {
            if (ArmorConfig.DEBUG) {
                LOGGER.info("[MoonTrace|Armor|TRIGGER] action=check result=BLOCKED reason=fall_damage_below_threshold damage={} threshold={} ctx{{p={}}}",
                        String.format("%.1f", amount), ArmorConfig.STEALTH_SHIN_MIN_FALL_DAMAGE,
                        player.getName().getString());
            }
            return amount;
        }

        // 消耗充能层
        int newCharges = charges - 1;
        chargeCount.put(playerId, newCharges);

        // 如果消耗后层数小于上限，开始下次充能
        if (!nextChargeTick.containsKey(playerId)) {
            nextChargeTick.put(playerId, nowTick + ArmorConfig.STEALTH_SHIN_CHARGE_INTERVAL);
        }

        // 应用减伤
        float reduction = ArmorConfig.STEALTH_SHIN_FALL_REDUCTION;
        float finalDamage = amount * (1.0f - reduction);

        // 日志
        LOGGER.info("[MoonTrace|Armor|TRIGGER] action=trigger result=OK effect={} ctx{{p={}}}",
                ArmorConfig.STEALTH_SHIN_EFFECT_ID, player.getName().getString());
        LOGGER.info("[MoonTrace|Armor|APPLY] action=damage_modify result=OK effect=stealth_shin_fall_pad final{{amount={}}} src{{original={}}} charges_left={} ctx{{p={}}}",
                String.format("%.1f", finalDamage), String.format("%.1f", amount), newCharges,
                player.getName().getString());

        // 播放音效
        player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BLOCK_WOOL_FALL, SoundCategory.PLAYERS, 1.0f, 1.0f);

        return finalDamage;
    }

    /**
     * 判断伤害是否为摔落伤害
     */
    private static boolean isFallDamage(DamageSource source) {
        return source.isOf(net.minecraft.entity.damage.DamageTypes.FALL);
    }

    private static boolean isWearing(ServerPlayerEntity player) {
        return player.getEquippedStack(EquipmentSlot.LEGS).isOf(ArmorItems.STEALTH_SHIN_LEGGINGS);
    }

    /**
     * 获取玩家当前层数
     */
    public static int getCharges(ServerPlayerEntity player) {
        return chargeCount.getOrDefault(player.getUuid(), 0);
    }

    /**
     * 玩家下线时清理状态
     */
    public static void onPlayerLogout(ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        lastCheckTick.remove(playerId);
        chargeCount.remove(playerId);
        nextChargeTick.remove(playerId);
    }

    /**
     * 玩家死亡/重生时清理状态
     */
    public static void onPlayerRespawn(ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        lastCheckTick.remove(playerId);
        chargeCount.remove(playerId);
        nextChargeTick.remove(playerId);
    }
}
