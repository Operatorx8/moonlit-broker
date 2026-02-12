package dev.xqanzd.moonlitbroker.katana.effect;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 窃念之黯状态管理器
 *
 * 管理 ReadWrite 标记和倒因噬果冷却
 */
public class OblivionManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("Oblivion");

    // ReadWrite 标记
    private static final Map<Integer, ReadWriteState> readWriteMarks = new ConcurrentHashMap<>();

    // 目标的 ReadWrite 冷却（防止同一目标被频繁标记）
    private static final Map<Integer, Long> targetCooldowns = new ConcurrentHashMap<>();

    // 玩家的倒因噬果冷却
    private static final Map<UUID, Long> causalityCooldowns = new ConcurrentHashMap<>();

    public record ReadWriteState(int expireAtTick, UUID sourcePlayer) {}

    // ========== ReadWrite 相关 ==========

    /**
     * 检查目标是否可以被施加 ReadWrite（冷却检查）
     */
    public static boolean canApplyReadWrite(LivingEntity target, long currentTick) {
        Long cooldownUntil = targetCooldowns.get(target.getId());
        if (cooldownUntil != null && currentTick < cooldownUntil) {
            if (OblivionConfig.DEBUG) {
                LOGGER.info("[Oblivion] ReadWrite on cooldown for {}, {} ticks remaining",
                    target.getName().getString(), cooldownUntil - currentTick);
            }
            return false;
        }
        return true;
    }

    /**
     * 施加 ReadWrite 标记
     */
    public static void applyReadWrite(LivingEntity target, PlayerEntity source,
                                       int durationTicks, int cooldownTicks, long currentTick) {
        int expireAt = (int) currentTick + durationTicks;
        readWriteMarks.put(target.getId(), new ReadWriteState(expireAt, source.getUuid()));

        // 设置该目标的冷却
        targetCooldowns.put(target.getId(), currentTick + cooldownTicks);

        if (OblivionConfig.DEBUG) {
            LOGGER.info("[Oblivion] Applied ReadWrite to {} for {} ticks, cooldown {} ticks",
                target.getName().getString(), durationTicks, cooldownTicks);
        }
    }

    /**
     * 检查目标是否有 ReadWrite 标记
     */
    public static boolean hasReadWrite(LivingEntity target, long currentTick) {
        ReadWriteState state = readWriteMarks.get(target.getId());
        if (state == null) return false;

        if (state.expireAtTick() < currentTick) {
            readWriteMarks.remove(target.getId());
            return false;
        }
        return true;
    }

    // ========== 倒因噬果 相关 ==========

    /**
     * 检查玩家的倒因噬果是否在冷却中
     */
    public static boolean isCausalityOnCooldown(PlayerEntity player, long currentTick) {
        Long cooldownUntil = causalityCooldowns.get(player.getUuid());
        if (cooldownUntil != null && currentTick < cooldownUntil) {
            if (OblivionConfig.DEBUG) {
                LOGGER.info("[Oblivion] Causality on cooldown for {}, {} ticks remaining",
                    player.getName().getString(), cooldownUntil - currentTick);
            }
            return true;
        }
        return false;
    }

    /**
     * 设置倒因噬果冷却
     */
    public static void setCausalityCooldown(PlayerEntity player, int cooldownTicks, long currentTick) {
        causalityCooldowns.put(player.getUuid(), currentTick + cooldownTicks);

        if (OblivionConfig.DEBUG) {
            LOGGER.info("[Oblivion] Causality cooldown set for {}: {} ticks",
                player.getName().getString(), cooldownTicks);
        }
    }

    // ========== 清理 ==========

    public static void tickCleanup(long currentTick) {
        // 清理过期的 ReadWrite 标记
        readWriteMarks.entrySet().removeIf(e -> e.getValue().expireAtTick() < currentTick);

        // 清理过期的目标冷却
        targetCooldowns.entrySet().removeIf(e -> e.getValue() < currentTick);

        // 清理过期的倒因噬果冷却
        causalityCooldowns.entrySet().removeIf(e -> e.getValue() < currentTick);
    }
}
