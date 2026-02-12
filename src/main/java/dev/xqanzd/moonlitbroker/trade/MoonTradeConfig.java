package dev.xqanzd.moonlitbroker.trade;

/**
 * MoonTrade debug/runtime toggles for merchant trade/spawn behaviors.
 */
public final class MoonTradeConfig {
    private MoonTradeConfig() {}

    /**
     * Debug spawn-egg randomization:
     * - true in debug/dev by default
     * - false in release by default
     * - can be disabled in debug via -Dmm.debugEggRandomize=false
     */
    public static final boolean DEBUG_EGG_RANDOMIZE =
        TradeConfig.MASTER_DEBUG && !"false".equalsIgnoreCase(System.getProperty("mm.debugEggRandomize", "true"));
}

