package mod.test.mymodtest.armor.item;

import java.util.Map;

/**
 * Default dye color mapping for all custom armor items.
 */
public final class ArmorColorPalette {

    public static final int TWILIGHT_BLUE = 0x3E6FB0;
    public static final int EMBER_RED = 0xB64A3A;
    public static final int VERDANT_GREEN = 0x4A8F58;
    public static final int ROYAL_PURPLE = 0x6B5AA9;
    public static final int AMBER_GOLD = 0xB78A2E;
    public static final int ASH_SLATE = 0x4A5568;

    private static final Map<String, Integer> ITEM_COLOR = Map.ofEntries(
            Map.entry("sentinel_helmet", TWILIGHT_BLUE),
            Map.entry("silent_oath_helmet", ASH_SLATE),
            Map.entry("exile_mask_helmet", EMBER_RED),
            Map.entry("relic_circlet_helmet", AMBER_GOLD),
            Map.entry("retracer_ornament_helmet", ROYAL_PURPLE),

            Map.entry("old_market_chestplate", AMBER_GOLD),
            Map.entry("blood_pact_chestplate", EMBER_RED),
            Map.entry("ghost_god_chestplate", ROYAL_PURPLE),
            Map.entry("windbreaker_chestplate", VERDANT_GREEN),
            Map.entry("void_devourer_chestplate", TWILIGHT_BLUE),

            Map.entry("smuggler_shin_leggings", ASH_SLATE),
            Map.entry("smuggler_pouch_leggings", VERDANT_GREEN),
            Map.entry("graze_guard_leggings", TWILIGHT_BLUE),
            Map.entry("stealth_shin_leggings", ASH_SLATE),
            Map.entry("clear_ledger_leggings", VERDANT_GREEN),

            Map.entry("untraceable_treads_boots", ASH_SLATE),
            Map.entry("boundary_walker_boots", TWILIGHT_BLUE),
            Map.entry("ghost_step_boots", ROYAL_PURPLE),
            Map.entry("marching_boots", AMBER_GOLD),
            Map.entry("gossamer_boots", VERDANT_GREEN),

            Map.entry("scavenger_goggles", AMBER_GOLD),
            Map.entry("cast_iron_sallet", ASH_SLATE),
            Map.entry("sanctified_hood", ROYAL_PURPLE),

            Map.entry("reactive_bug_plate", TWILIGHT_BLUE),
            Map.entry("patchwork_coat", EMBER_RED),
            Map.entry("ritual_robe", ROYAL_PURPLE),

            Map.entry("wrapped_leggings", EMBER_RED),
            Map.entry("reinforced_greaves", ASH_SLATE),
            Map.entry("cargo_pants", VERDANT_GREEN),

            Map.entry("penitent_boots", EMBER_RED),
            Map.entry("standard_iron_boots", ASH_SLATE),
            Map.entry("cushion_hiking_boots", VERDANT_GREEN)
    );

    private static final Map<String, String> ITEM_GROUP = Map.ofEntries(
            Map.entry("sentinel_helmet", "twilight_blue"),
            Map.entry("silent_oath_helmet", "ash_slate"),
            Map.entry("exile_mask_helmet", "ember_red"),
            Map.entry("relic_circlet_helmet", "amber_gold"),
            Map.entry("retracer_ornament_helmet", "royal_purple"),

            Map.entry("old_market_chestplate", "amber_gold"),
            Map.entry("blood_pact_chestplate", "ember_red"),
            Map.entry("ghost_god_chestplate", "royal_purple"),
            Map.entry("windbreaker_chestplate", "verdant_green"),
            Map.entry("void_devourer_chestplate", "twilight_blue"),

            Map.entry("smuggler_shin_leggings", "ash_slate"),
            Map.entry("smuggler_pouch_leggings", "verdant_green"),
            Map.entry("graze_guard_leggings", "twilight_blue"),
            Map.entry("stealth_shin_leggings", "ash_slate"),
            Map.entry("clear_ledger_leggings", "verdant_green"),

            Map.entry("untraceable_treads_boots", "ash_slate"),
            Map.entry("boundary_walker_boots", "twilight_blue"),
            Map.entry("ghost_step_boots", "royal_purple"),
            Map.entry("marching_boots", "amber_gold"),
            Map.entry("gossamer_boots", "verdant_green"),

            Map.entry("scavenger_goggles", "amber_gold"),
            Map.entry("cast_iron_sallet", "ash_slate"),
            Map.entry("sanctified_hood", "royal_purple"),

            Map.entry("reactive_bug_plate", "twilight_blue"),
            Map.entry("patchwork_coat", "ember_red"),
            Map.entry("ritual_robe", "royal_purple"),

            Map.entry("wrapped_leggings", "ember_red"),
            Map.entry("reinforced_greaves", "ash_slate"),
            Map.entry("cargo_pants", "verdant_green"),

            Map.entry("penitent_boots", "ember_red"),
            Map.entry("standard_iron_boots", "ash_slate"),
            Map.entry("cushion_hiking_boots", "verdant_green")
    );

    private ArmorColorPalette() {}

    public static int colorFor(String itemId) {
        return ITEM_COLOR.getOrDefault(itemId, ASH_SLATE);
    }

    public static String groupFor(String itemId) {
        return ITEM_GROUP.getOrDefault(itemId, "ash_slate");
    }
}
