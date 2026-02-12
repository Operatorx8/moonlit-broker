package dev.xqanzd.moonlitbroker.trade;

/**
 * 交易页面操作类型
 */
public enum TradeAction {
    /** 打开普通页 */
    OPEN_NORMAL,
    /** 切换到隐藏页 */
    SWITCH_SECRET,
    /** 刷新当前页 */
    REFRESH,
    /** 上一页 */
    PREV_PAGE,
    /** 下一页 */
    NEXT_PAGE,
    /** 提交悬赏 */
    SUBMIT_BOUNTY;
    
    /**
     * Convert ordinal to TradeAction.
     * @return the action, or null if ordinal is out of range (CRITICAL: do NOT default to a state-changing action)
     */
    public static TradeAction fromOrdinal(int ordinal) {
        if (ordinal >= 0 && ordinal < values().length) {
            return values()[ordinal];
        }
        return null; // Invalid ordinal - caller must reject
    }
}
