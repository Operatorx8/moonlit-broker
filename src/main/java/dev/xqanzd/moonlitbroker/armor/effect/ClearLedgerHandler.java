package dev.xqanzd.moonlitbroker.armor.effect;

import dev.xqanzd.moonlitbroker.armor.ArmorConfig;
import dev.xqanzd.moonlitbroker.armor.item.ArmorItems;
import dev.xqanzd.moonlitbroker.armor.util.CooldownManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 清账步态 - 击杀步态加速
 *
 * 机制：
 * - 触发源：玩家击杀 LivingEntity 成功事件
 * - 初次触发：给予 Speed I 3s（60 ticks）
 * - 冷却：16s（320 ticks）
 * - CD 内击杀：不刷新成 3s，仅在当前剩余时间基础上 +1s（20 ticks）
 * - 持续上限：最多 6s（120 ticks）
 * - 限制：PVP 不触发；只统计可归属击杀
 */
public class ClearLedgerHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("MoonTrace");

    /**
     * 记录每个玩家的清账步态速度效果到期 tick
     * 用于判断当前 Speed 是否来自本效果
     */
    private static final Map<UUID, Long> speedExpiresTick = new ConcurrentHashMap<>();

    /**
     * 每 tick 调用（在 ServerTickEvent 中）
     * 处理过期清理和穿脱护腿清理
     */
    public static void tick(ServerWorld world, long nowTick) {
        // 清理过期的 speedExpiresTick 条目
        Iterator<Map.Entry<UUID, Long>> it = speedExpiresTick.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> entry = it.next();
            if (nowTick >= entry.getValue()) {
                it.remove();
            }
        }

        // 穿脱护腿清理：未穿戴时移除状态
        for (var player : world.getPlayers()) {
            if (!(player instanceof ServerPlayerEntity serverPlayer)) continue;

            UUID playerId = serverPlayer.getUuid();
            if (!isWearing(serverPlayer) && speedExpiresTick.containsKey(playerId)) {
                speedExpiresTick.remove(playerId);
                if (ArmorConfig.DEBUG) {
                    LOGGER.info("[MoonTrace|Armor|STATE] action=state_change result=OK state=clear_ledger_removed reason=not_wearing ctx{{p={}}}",
                            serverPlayer.getName().getString());
                }
            }
        }
    }

    /**
     * 玩家击杀 LivingEntity 时调用
     *
     * @param player      击杀者
     * @param killed      被击杀的实体
     * @param nowTick     当前 server tick
     */
    public static void onKill(ServerPlayerEntity player, Entity killed, long nowTick) {
        // 检查是否穿戴该护腿
        if (!isWearing(player)) {
            return;
        }

        // 检查是否为 LivingEntity
        if (!(killed instanceof LivingEntity)) {
            return;
        }

        // PVP 排除
        if (killed instanceof ServerPlayerEntity) {
            if (ArmorConfig.DEBUG) {
                LOGGER.info("[MoonTrace|Armor|TRIGGER] action=check result=BLOCKED reason=pvp_excluded ctx{{p={} t={}}}",
                        player.getName().getString(), killed.getName().getString());
            }
            return;
        }

        UUID playerId = player.getUuid();

        // 检查冷却状态
        boolean cdReady = CooldownManager.isReady(playerId, ArmorConfig.CLEAR_LEDGER_EFFECT_ID, nowTick);

        if (cdReady) {
            // CD 已就绪：首次触发，给予初始持续时间
            int duration = ArmorConfig.CLEAR_LEDGER_INITIAL_DURATION;
            applySpeed(player, duration);

            // 记录效果到期 tick
            speedExpiresTick.put(playerId, nowTick + duration);

            // 进入冷却
            CooldownManager.setCooldown(playerId, ArmorConfig.CLEAR_LEDGER_EFFECT_ID, nowTick, ArmorConfig.CLEAR_LEDGER_COOLDOWN);

            LOGGER.info("[MoonTrace|Armor|TRIGGER] action=trigger result=OK effect={} first_proc=true ctx{{p={} t={}}}",
                    ArmorConfig.CLEAR_LEDGER_EFFECT_ID, player.getName().getString(),
                    killed.getType().getName().getString());
            LOGGER.info("[MoonTrace|Armor|APPLY] action=apply result=OK effect=speed final{{dur={} amp={}}} ctx{{p={}}}",
                    duration, ArmorConfig.CLEAR_LEDGER_SPEED_AMPLIFIER, player.getName().getString());
        } else {
            // CD 内击杀：检查当前是否仍有我们的速度效果
            if (isOurSpeedActive(playerId, nowTick)) {
                // 计算剩余时间
                long expiresTick = speedExpiresTick.get(playerId);
                int currentRemaining = (int) (expiresTick - nowTick);

                if (currentRemaining > 0) {
                    // 延长时间，但不超过上限
                    int newDuration = Math.min(currentRemaining + ArmorConfig.CLEAR_LEDGER_EXTEND_DURATION,
                            ArmorConfig.CLEAR_LEDGER_MAX_DURATION);

                    // 应用延长后的速度
                    applySpeed(player, newDuration);

                    // 更新到期 tick
                    speedExpiresTick.put(playerId, nowTick + newDuration);

                    LOGGER.info("[MoonTrace|Armor|TRIGGER] action=trigger result=OK effect={} first_proc=false ctx{{p={} t={}}}",
                            ArmorConfig.CLEAR_LEDGER_EFFECT_ID, player.getName().getString(),
                            killed.getType().getName().getString());
                    LOGGER.info("[MoonTrace|Armor|APPLY] action=apply result=OK effect=speed_extend add={} cap={} final{{dur={}}} ctx{{p={}}}",
                            ArmorConfig.CLEAR_LEDGER_EXTEND_DURATION, ArmorConfig.CLEAR_LEDGER_MAX_DURATION,
                            newDuration, player.getName().getString());
                }
            } else {
                // 没有当前速度效果（可能过期了），且在 CD 内，不触发
                if (ArmorConfig.DEBUG) {
                    long cdLeft = CooldownManager.getRemainingTicks(playerId, ArmorConfig.CLEAR_LEDGER_EFFECT_ID, nowTick);
                    LOGGER.info("[MoonTrace|Armor|TRIGGER] action=check result=BLOCKED reason=cd_hit cd_total={} cd_left={} ctx{{p={}}}",
                            ArmorConfig.CLEAR_LEDGER_COOLDOWN, cdLeft, player.getName().getString());
                }
            }
        }
    }

    /**
     * 应用速度效果
     * 注意：不主动 removeStatusEffect，让效果自然过期或被新效果覆盖
     */
    private static void applySpeed(ServerPlayerEntity player, int duration) {
        // 直接添加新的速度效果（会覆盖同类型效果）
        StatusEffectInstance speedEffect = new StatusEffectInstance(
                StatusEffects.SPEED,
                duration,
                ArmorConfig.CLEAR_LEDGER_SPEED_AMPLIFIER,
                false,  // ambient
                true,   // showParticles
                true    // showIcon
        );
        player.addStatusEffect(speedEffect);
    }

    /**
     * 判断我们的速度效果是否仍活跃
     * 使用 expiresTick 来判断，而非依赖 amplifier/ambient
     */
    private static boolean isOurSpeedActive(UUID playerId, long nowTick) {
        Long expiresTick = speedExpiresTick.get(playerId);
        if (expiresTick == null) {
            return false;
        }
        return nowTick < expiresTick;
    }

    private static boolean isWearing(ServerPlayerEntity player) {
        return player.getEquippedStack(EquipmentSlot.LEGS).isOf(ArmorItems.CLEAR_LEDGER_LEGGINGS);
    }

    /**
     * 玩家下线时清理状态
     */
    public static void onPlayerLogout(ServerPlayerEntity player) {
        speedExpiresTick.remove(player.getUuid());
    }

    /**
     * 玩家死亡/重生时清理状态
     */
    public static void onPlayerRespawn(ServerPlayerEntity player) {
        speedExpiresTick.remove(player.getUuid());
    }
}
