package dev.xqanzd.moonlitbroker.trade;

/**
 * 交易页面类型枚举
 * BASE — 基础页（通用 + 变体特色交易）
 * NORMAL — 进阶页（Sigil 解封链 + 隐藏太刀，需达到交易次数）
 * HIDDEN — 隐藏页（仅太刀交易，需解封后切换）
 */
public enum TradePage {
    BASE,
    NORMAL,
    HIDDEN;

    public static TradePage fromOrdinal(int ordinal) {
        if (ordinal >= 0 && ordinal < values().length) {
            return values()[ordinal];
        }
        return BASE;
    }
}
