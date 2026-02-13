package dev.xqanzd.moonlitbroker.trade;

import java.util.*;

/**
 * 每个变体的锚点 + 随机 B 候选池配置
 * <p>
 * A 锚点 = Page1 固定过渡装（带特效）
 * B 锚点 = Page3 固定招牌装
 * B 随机池 = Page3 额外抽 1 件
 */
public final class VariantAnchorConfig {

    private VariantAnchorConfig() {}

    // ==================== A 锚点（Page1 过渡特效装） ====================
    private static final Map<MerchantVariant, String> A_ANCHORS = new EnumMap<>(MerchantVariant.class);

    static {
        A_ANCHORS.put(MerchantVariant.EXPLORATION, "cargo_pants");
        A_ANCHORS.put(MerchantVariant.DEFENSE,     "cushion_hiking_boots");
        A_ANCHORS.put(MerchantVariant.OFFENSE,     "reactive_bug_plate");
        A_ANCHORS.put(MerchantVariant.PROFIT,      "sanctified_hood");
        A_ANCHORS.put(MerchantVariant.MOBILITY,    "cargo_pants");
    }

    // ==================== B 锚点（Page3 招牌装，尽量非 EPIC） ====================
    private static final Map<MerchantVariant, String> B_ANCHORS = new EnumMap<>(MerchantVariant.class);

    static {
        B_ANCHORS.put(MerchantVariant.EXPLORATION, "sentinel_helmet");
        B_ANCHORS.put(MerchantVariant.DEFENSE,     "silent_oath_helmet");
        B_ANCHORS.put(MerchantVariant.OFFENSE,     "blood_pact_chestplate");
        B_ANCHORS.put(MerchantVariant.PROFIT,      "old_market_chestplate");
        B_ANCHORS.put(MerchantVariant.MOBILITY,    "marching_boots");
    }

    // ==================== B 随机池（按主题） ====================
    private static final Map<MerchantVariant, List<String>> B_RANDOM_POOLS = new EnumMap<>(MerchantVariant.class);

    /** EPIC 物品集合，用于 EPIC cap 检查 */
    public static final Set<String> EPIC_ITEMS = Set.of(
        "ghost_god_chestplate",
        "graze_guard_leggings",
        "untraceable_treads_boots"
    );

    static {
        B_RANDOM_POOLS.put(MerchantVariant.EXPLORATION, List.of(
            "relic_circlet_helmet",
            "boundary_walker_boots",
            "windbreaker_chestplate"
            // sentinel_helmet 是锚点，不进随机池
        ));

        B_RANDOM_POOLS.put(MerchantVariant.DEFENSE, List.of(
            "ghost_god_chestplate",    // EPIC
            "graze_guard_leggings",    // EPIC
            "ghost_step_boots",
            "retracer_ornament_helmet"
            // silent_oath_helmet 是锚点，不进随机池
        ));

        B_RANDOM_POOLS.put(MerchantVariant.OFFENSE, List.of(
            "exile_mask_helmet",
            "void_devourer_chestplate",
            "clear_ledger_leggings",
            "marching_boots"
            // blood_pact_chestplate 是锚点，不进随机池
        ));

        B_RANDOM_POOLS.put(MerchantVariant.PROFIT, List.of(
            "smuggler_shin_leggings",
            "smuggler_pouch_leggings"
            // old_market_chestplate 是锚点，不进随机池
        ));

        B_RANDOM_POOLS.put(MerchantVariant.MOBILITY, List.of(
            "untraceable_treads_boots", // EPIC
            "stealth_shin_leggings",
            "gossamer_boots"
            // marching_boots 是锚点，不进随机池
        ));
    }

    public static String getAAnchor(MerchantVariant variant) {
        return A_ANCHORS.get(variant);
    }

    public static String getBAnchor(MerchantVariant variant) {
        return B_ANCHORS.get(variant);
    }

    public static List<String> getBRandomPool(MerchantVariant variant) {
        return B_RANDOM_POOLS.getOrDefault(variant, Collections.emptyList());
    }

    public static boolean isEpic(String itemId) {
        return EPIC_ITEMS.contains(itemId);
    }
}
