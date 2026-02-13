package dev.xqanzd.moonlitbroker.trade;

/**
 * 商人变体枚举（5 种主题方向）
 */
public enum MerchantVariant {
    EXPLORATION, // 探索向
    DEFENSE,     // 防御向
    OFFENSE,     // 进攻向
    PROFIT,      // 收益向
    MOBILITY;    // 机动向

    private static final MerchantVariant[] VALUES = values();

    /**
     * 根据种子随机选一个变体
     */
    public static MerchantVariant fromSeed(long seed) {
        int idx = (int) (Math.abs(seed) % VALUES.length);
        return VALUES[idx];
    }
}
