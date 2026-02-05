package mod.test.mymodtest.trade;

/**
 * 交易页面类型枚举
 */
public enum TradePage {
    NORMAL,
    SECRET;
    
    public static TradePage fromOrdinal(int ordinal) {
        if (ordinal >= 0 && ordinal < values().length) {
            return values()[ordinal];
        }
        return NORMAL;
    }
}
