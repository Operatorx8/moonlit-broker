package mod.test.mymodtest.registry;

import mod.test.mymodtest.trade.item.MerchantMarkItem;
import mod.test.mymodtest.trade.item.TradeScrollItem;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Language;

import java.util.List;
import java.util.UUID;

public final class ModTooltips {
    private static final String MOD_ID = "mymodtest";
    private static final int MAX_INDEXED_TOOLTIP_LINES = 12;

    private static boolean initialized;

    private ModTooltips() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        ItemTooltipCallback.EVENT.register((stack, context, type, tooltip) -> appendTooltip(stack, tooltip));
    }

    private static void appendTooltip(ItemStack stack, List<Text> tooltip) {
        Identifier id = Registries.ITEM.getId(stack.getItem());
        if (!MOD_ID.equals(id.getNamespace())) {
            return;
        }

        String path = id.getPath();

        // Dynamic tooltip content must run before static key-based lookup.
        if ("trade_scroll".equals(path)) {
            addTradeScrollTooltip(path, stack, tooltip);
            return;
        }
        if ("merchant_mark".equals(path)) {
            addMerchantMarkTooltip(path, stack, tooltip);
            return;
        }

        // Unified multi-line lore path: tooltip.<modid>.<item_path>.0..n
        if (addIndexedTooltip(path, tooltip)) {
            return;
        }

        boolean handledByLegacySwitch = switch (path) {
            case "untraceable_treads_boots" -> {
                addBoot1Tooltip(path, tooltip);
                yield true;
            }
            case "boundary_walker_boots" -> {
                addBoot2Tooltip(path, tooltip);
                yield true;
            }
            case "ghost_step_boots" -> {
                addBoot3Tooltip(path, tooltip);
                yield true;
            }
            case "marching_boots" -> {
                addBoot4Tooltip(path, tooltip);
                yield true;
            }
            case "gossamer_boots" -> {
                addBoot5Tooltip(path, tooltip);
                yield true;
            }
            case "moon_glow_katana" -> {
                addMoonGlowTooltip(path, tooltip);
                yield true;
            }
            case "regret_blade" -> {
                addRegretTooltip(path, tooltip);
                yield true;
            }
            case "eclipse_blade" -> {
                addEclipseTooltip(path, tooltip);
                yield true;
            }
            case "oblivion_edge" -> {
                addOblivionTooltip(path, tooltip);
                yield true;
            }
            case "nmap_katana" -> {
                addNmapTooltip(path, tooltip);
                yield true;
            }
            case "acer" -> {
                addTransitionalWeaponTooltip(path, tooltip, "[Keen Edge]", "Critical hits deal 1.7x damage.");
                yield true;
            }
            case "velox" -> {
                addTransitionalWeaponTooltip(path, tooltip, "[Swift Blade]", "Fast speed, lower damage.");
                yield true;
            }
            case "fatalis" -> {
                addTransitionalWeaponTooltip(path, tooltip, "[Heavy Strike]", "High damage, standard speed.");
                yield true;
            }
            case "guide_scroll" -> {
                addGuideScrollTooltip(path, tooltip);
                yield true;
            }
            default -> false;
        };

        if (!handledByLegacySwitch) {
            addLegacyLoreAndEffect(path, tooltip);
        }
    }

    private static boolean addIndexedTooltip(String path, List<Text> tooltip) {
        boolean added = false;

        for (int i = 0; i < MAX_INDEXED_TOOLTIP_LINES; i++) {
            String lineKey = indexedKey(path, i);
            if (!hasTranslation(lineKey)) {
                continue;
            }

            if (!added) {
                tooltip.add(Text.empty());
                added = true;
            }

            Formatting style = i == 0 ? Formatting.GOLD : Formatting.GRAY;
            tooltip.add(Text.translatable(lineKey).formatted(style));
        }

        return added;
    }

    private static void addBoot1Tooltip(String path, List<Text> tooltip) {
        tooltip.add(Text.empty());
        addLine(tooltip, key(path, "subtitle"), null, "[Vanish]", Formatting.GOLD);
        addLine(tooltip, key(path, "line1"), null,
                "12s no hit on LivingEntity and 12s no damage from LivingEntity -> Invisibility 3s.",
                Formatting.GRAY);
        addLine(tooltip, key(path, "line2"), null,
                "Trigger checks every 20 ticks.",
                Formatting.GRAY);
        addLine(tooltip, key(path, "params"), null, "CD: 45s.", Formatting.DARK_AQUA);
    }

    private static void addBoot2Tooltip(String path, List<Text> tooltip) {
        tooltip.add(Text.empty());
        addLine(tooltip, key(path, "subtitle"), null, "[Boundary Leap]", Formatting.GOLD);
        addLine(tooltip, key(path, "line1"), null,
                "Overworld + sky visible + (rain|thunder|snow|night) -> Jump Boost I.",
                Formatting.GRAY);
        addLine(tooltip, key(path, "line2"), null,
                "Scans every 20 ticks; leaving condition ends on next scan.",
                Formatting.GRAY);
        addLine(tooltip, key(path, "params"), null, "Refresh window: 25 ticks.", Formatting.DARK_AQUA);
    }

    private static void addBoot3Tooltip(String path, List<Text> tooltip) {
        tooltip.add(Text.empty());
        addLine(tooltip, key(path, "subtitle"), null, "[Phantom]", Formatting.GOLD);
        addLine(tooltip, key(path, "line1"), null,
                "Out of combat: no entity push. After active attack: disabled for 8s.",
                Formatting.GRAY);
        addLine(tooltip, key(path, "line2"), null,
                "If >=2 LivingEntity hits arrive on different ticks in a 20t window, burst on for 1s.",
                Formatting.GRAY);
        addLine(tooltip, key(path, "params"), null, "Burst CD: 15s.", Formatting.DARK_AQUA);
    }

    private static void addBoot4Tooltip(String path, List<Text> tooltip) {
        tooltip.add(Text.empty());
        addLine(tooltip, key(path, "subtitle"), null, "[March]", Formatting.GOLD);
        addLine(tooltip, key(path, "line1"), null,
                "8s no hit + 4s no hurt -> Speed II.",
                Formatting.GRAY);
        addLine(tooltip, key(path, "line2"), null,
                "Checked every 20 ticks; max 15s; hitting LivingEntity exits immediately.",
                Formatting.GRAY);
        addLine(tooltip, key(path, "params"), null, "After exit CD: 12s.", Formatting.DARK_AQUA);
    }

    private static void addBoot5Tooltip(String path, List<Text> tooltip) {
        tooltip.add(Text.empty());
        addLine(tooltip, key(path, "subtitle"), null, "[Web Walk]", Formatting.GOLD);
        addLine(tooltip, key(path, "line1"), null,
                "Only when cobweb slowMovement is applied: slowdown reduced by 70%.",
                Formatting.GRAY);
        addLine(tooltip, key(path, "line2"), null,
                "If Slowness is level II+, downgrade to level I.",
                Formatting.GRAY);
        addLine(tooltip, key(path, "params"), null, "Scope: cobweb interaction only.", Formatting.DARK_AQUA);
    }

    private static void addMoonGlowTooltip(String path, List<Text> tooltip) {
        tooltip.add(Text.empty());
        addLine(tooltip, key(path, "subtitle"), "item.mymodtest.moon_glow_katana.effect_name", "[Moon Trace]", Formatting.BLUE);
        addLine(tooltip, key(path, "line1"), "item.mymodtest.moon_glow_katana.effect_desc1", "Night-sky hits may mark enemies.", Formatting.GRAY);
        addLine(tooltip, key(path, "line2"), "item.mymodtest.moon_glow_katana.effect_desc2", "Next hit deals bonus damage.", Formatting.GRAY);
    }

    private static void addRegretTooltip(String path, List<Text> tooltip) {
        tooltip.add(Text.empty());
        addLine(tooltip, key(path, "subtitle"), "item.mymodtest.regret_blade.effect_name", "[Life Cut]", Formatting.DARK_RED);
        addLine(tooltip, key(path, "line1"), "item.mymodtest.regret_blade.effect_desc1", "Cuts 30% current HP.", Formatting.GRAY);
        addLine(tooltip, key(path, "line2"), "item.mymodtest.regret_blade.effect_desc2", "Armor penetration applies.", Formatting.GOLD);
        addLine(tooltip, key(path, "params"), "item.mymodtest.regret_blade.effect_desc3", "Cannot deliver final blow.", Formatting.GRAY);
    }

    private static void addEclipseTooltip(String path, List<Text> tooltip) {
        tooltip.add(Text.empty());
        addLine(tooltip, key(path, "subtitle"), "item.mymodtest.eclipse_blade.effect_name", "[Lunar Eclipse]", Formatting.DARK_PURPLE);
        addLine(tooltip, key(path, "line1"), "item.mymodtest.eclipse_blade.effect_penetration", "Armor penetration scales on marked targets.", Formatting.RED);
        addLine(tooltip, key(path, "line2"), "item.mymodtest.eclipse_blade.effect_desc1", "Can apply eclipse mark.", Formatting.GRAY);
        addLine(tooltip, key(path, "params"), "item.mymodtest.eclipse_blade.effect_desc2", "Marked targets receive random curses.", Formatting.GRAY);
    }

    private static void addOblivionTooltip(String path, List<Text> tooltip) {
        tooltip.add(Text.empty());
        addLine(tooltip, key(path, "subtitle"), "item.mymodtest.oblivion_edge.effect_name", "[ReadWrite]", Formatting.DARK_PURPLE, Formatting.BOLD);
        addLine(tooltip, key(path, "line1"), "item.mymodtest.oblivion_edge.effect_readwrite", "Can mark targets.", Formatting.GRAY);
        addLine(tooltip, key(path, "line2"), "item.mymodtest.oblivion_edge.effect_causality", "Low HP can trigger causality reversal.", Formatting.RED);
        addLine(tooltip, key(path, "params"), "item.mymodtest.oblivion_edge.effect_penetration", "Bonus penetration on marked targets.", Formatting.GOLD);
    }

    private static void addNmapTooltip(String path, List<Text> tooltip) {
        tooltip.add(Text.empty());
        addLine(tooltip, key(path, "subtitle"), null, "nmap -sV -O -A --script=vuln", Formatting.DARK_GRAY, Formatting.ITALIC);
        addLine(tooltip, key(path, "line1"), "item.mymodtest.nmap_katana.effect_name", "[Nmap Protocol]", Formatting.GOLD, Formatting.BOLD);
        addLine(tooltip, key(path, "line2"), "item.mymodtest.nmap_katana.module_discovery", "Host discovery module.", Formatting.AQUA);
        addLine(tooltip, key(path, "params"), "item.mymodtest.nmap_katana.module_enum", "Port enum module.", Formatting.GREEN);
        addLine(tooltip, null, "item.mymodtest.nmap_katana.module_vuln", "Vulnerability module.", Formatting.RED);
        addLine(tooltip, null, "item.mymodtest.nmap_katana.module_firewall", "Firewall module.", Formatting.LIGHT_PURPLE);
    }

    private static void addTransitionalWeaponTooltip(String path, List<Text> tooltip, String fallbackSubtitle, String fallbackLine) {
        tooltip.add(Text.empty());
        addLine(tooltip, key(path, "subtitle"), "item.mymodtest." + path + ".effect", fallbackSubtitle, Formatting.GOLD);
        addLine(tooltip, key(path, "line1"), "item.mymodtest." + path + ".desc", fallbackLine, Formatting.GRAY);
    }

    private static void addGuideScrollTooltip(String path, List<Text> tooltip) {
        tooltip.add(Text.empty());
        addLine(tooltip, key(path, "subtitle"), null, "Merchant Guide", Formatting.GOLD);
        addLine(tooltip, key(path, "line1"), null, "Trade with the merchant to gain reputation.", Formatting.GRAY);
        addLine(tooltip, key(path, "line2"), null, "At 15 reputation, hidden trade becomes available.", Formatting.GRAY);
        addLine(tooltip, key(path, "params"), null, "Needs Merchant Mark and Sealed Ledger.", Formatting.GRAY);
    }

    private static void addTradeScrollTooltip(String path, ItemStack stack, List<Text> tooltip) {
        tooltip.add(Text.empty());
        String grade = TradeScrollItem.getGrade(stack);
        int uses = TradeScrollItem.getUses(stack);

        addLine(tooltip, key(path, "subtitle"), null, "Trade Scroll", Formatting.GOLD);
        tooltip.add(Text.literal("Grade: " + grade)
                .formatted("SEALED".equals(grade) ? Formatting.LIGHT_PURPLE : Formatting.GRAY));
        tooltip.add(Text.literal("Uses: " + uses)
                .formatted(uses > 0 ? Formatting.GREEN : Formatting.RED));
    }

    private static void addMerchantMarkTooltip(String path, ItemStack stack, List<Text> tooltip) {
        tooltip.add(Text.empty());
        addLine(tooltip, key(path, "subtitle"), null, "Merchant Mark", Formatting.GOLD);

        UUID owner = MerchantMarkItem.getOwnerUUID(stack);
        if (owner != null) {
            tooltip.add(Text.literal("Bound").formatted(Formatting.GREEN));
            tooltip.add(Text.literal("UUID: " + owner.toString().substring(0, 8) + "...").formatted(Formatting.DARK_GRAY));
        } else {
            tooltip.add(Text.literal("Unbound - right-click merchant to bind").formatted(Formatting.YELLOW));
        }
    }

    private static boolean addLegacyLoreAndEffect(String path, List<Text> tooltip) {
        String effectKey = "item." + MOD_ID + "." + path + ".effect";
        String descKey = "item." + MOD_ID + "." + path + ".desc";
        String loreKey = "item." + MOD_ID + "." + path + ".lore";

        boolean hasEffect = hasTranslation(effectKey);
        boolean hasDesc = hasTranslation(descKey);
        boolean hasLore = hasTranslation(loreKey);

        if (!hasEffect && !hasDesc && !hasLore) {
            return false;
        }

        tooltip.add(Text.empty());
        if (hasEffect) {
            tooltip.add(Text.translatable(effectKey).formatted(Formatting.GOLD));
        }
        if (hasDesc) {
            tooltip.add(Text.translatable(descKey).formatted(Formatting.GRAY));
        }
        if (hasLore) {
            tooltip.add(Text.translatable(loreKey).formatted(Formatting.DARK_GRAY, Formatting.ITALIC));
        }
        return true;
    }

    private static String key(String path, String suffix) {
        return "tooltip." + MOD_ID + "." + path + "." + suffix;
    }

    private static String indexedKey(String path, int index) {
        return "tooltip." + MOD_ID + "." + path + "." + index;
    }

    private static void addLine(List<Text> tooltip, String key, String legacyKey, String fallback, Formatting... formatting) {
        MutableText line = null;

        if (key != null && hasTranslation(key)) {
            line = Text.translatable(key);
        } else if (legacyKey != null && hasTranslation(legacyKey)) {
            line = Text.translatable(legacyKey);
        } else if (fallback != null && !fallback.isBlank()) {
            line = Text.literal(fallback);
        }

        if (line != null) {
            tooltip.add(line.copy().formatted(formatting));
        }
    }

    private static boolean hasTranslation(String key) {
        return Language.getInstance().hasTranslation(key);
    }
}
