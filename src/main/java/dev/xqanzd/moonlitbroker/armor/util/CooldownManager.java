package dev.xqanzd.moonlitbroker.armor.util;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 统一冷却管理器
 * 用于管理所有盔甲效果的冷却时间
 *
 * Key 格式: <playerUUID>:<effectId>
 * Value: 冷却结束的 tick
 */
public final class CooldownManager {
    private CooldownManager() {}

    /** 冷却存储: key -> 冷却结束 tick */
    private static final Map<String, Long> cooldowns = new ConcurrentHashMap<>();

    /**
     * 生成冷却 key
     */
    private static String makeKey(UUID playerId, String effectId) {
        return playerId.toString() + ":" + effectId;
    }

    /**
     * 检查冷却是否就绪
     * @param playerId 玩家 UUID
     * @param effectId 效果 ID
     * @param currentTick 当前游戏 tick
     * @return true 如果冷却已结束或不存在
     */
    public static boolean isReady(UUID playerId, String effectId, long currentTick) {
        String key = makeKey(playerId, effectId);
        Long expireTick = cooldowns.get(key);
        return expireTick == null || currentTick >= expireTick;
    }

    /**
     * 获取剩余冷却 ticks
     * @return 剩余 ticks，如果已就绪则返回 0
     */
    public static long getRemainingTicks(UUID playerId, String effectId, long currentTick) {
        String key = makeKey(playerId, effectId);
        Long expireTick = cooldowns.get(key);
        if (expireTick == null) return 0;
        return Math.max(0, expireTick - currentTick);
    }

    /**
     * 设置冷却
     * @param playerId 玩家 UUID
     * @param effectId 效果 ID
     * @param currentTick 当前游戏 tick
     * @param durationTicks 冷却持续时间
     */
    public static void setCooldown(UUID playerId, String effectId, long currentTick, long durationTicks) {
        String key = makeKey(playerId, effectId);
        cooldowns.put(key, currentTick + durationTicks);
    }

    /**
     * 清除指定效果的冷却（调试用）
     */
    public static void clearCooldown(UUID playerId, String effectId) {
        String key = makeKey(playerId, effectId);
        cooldowns.remove(key);
    }

    /**
     * 清除玩家所有冷却（玩家下线/死亡时）
     */
    public static void clearAllCooldowns(UUID playerId) {
        String prefix = playerId.toString() + ":";
        cooldowns.keySet().removeIf(key -> key.startsWith(prefix));
    }

    /**
     * 清理过期的冷却记录（定期调用以节省内存）
     */
    public static void cleanupExpired(long currentTick) {
        cooldowns.entrySet().removeIf(entry -> entry.getValue() < currentTick);
    }
}
