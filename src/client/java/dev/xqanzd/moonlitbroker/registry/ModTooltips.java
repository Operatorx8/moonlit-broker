package dev.xqanzd.moonlitbroker.registry;

import dev.xqanzd.moonlitbroker.trade.item.MerchantMarkItem;
import dev.xqanzd.moonlitbroker.trade.item.TradeScrollItem;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Language;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ModTooltips {
    private static final String MOD_ID = "xqanzd_moonlit_broker";
    private static final boolean DEBUG_TOOLTIP = false;
    private static final boolean LEGACY_TOOLTIP_FALLBACK_ENABLED = false;

    private static final Map<String, String> FALLBACK_TAGLINES = createFallbackTaglines();
    private static final Map<String, List<String>> FALLBACK_TOOLTIPS = createFallbackTooltips();

    private static boolean initialized;

    private ModTooltips() {}

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

        if ("trade_scroll".equals(path)) {
            addTradeScrollTooltip(stack, tooltip);
            return;
        }
        if ("merchant_mark".equals(path)) {
            addMerchantMarkTooltip(stack, tooltip);
            return;
        }

        boolean taglineAdded = TooltipHelper.appendTagline(
                MOD_ID,
                path,
                tooltip,
                FALLBACK_TAGLINES.get(path)
        );

        boolean appended = TooltipHelper.appendLore(
                MOD_ID,
                path,
                tooltip,
                FALLBACK_TOOLTIPS.get(path)
        );

        if (!appended && LEGACY_TOOLTIP_FALLBACK_ENABLED) {
            appended = addLegacyLoreAndEffect(path, tooltip);
        }

        if (taglineAdded || appended) {
            TooltipHelper.appendDebugLine(tooltip, DEBUG_TOOLTIP, "[DBG] itemId=" + id);
        }
    }

    private static void addTradeScrollTooltip(ItemStack stack, List<Text> tooltip) {
        tooltip.add(Text.empty());
        tooltip.add(Text.literal("Trade Scroll").formatted(Formatting.GOLD));

        String grade = TradeScrollItem.getGrade(stack);
        int uses = TradeScrollItem.getUses(stack);

        tooltip.add(Text.literal("Grade: " + grade)
                .formatted("SEALED".equals(grade) ? Formatting.LIGHT_PURPLE : Formatting.GRAY));
        tooltip.add(Text.literal("Uses: " + uses)
                .formatted(uses > 0 ? Formatting.GREEN : Formatting.RED));
    }

    private static void addMerchantMarkTooltip(ItemStack stack, List<Text> tooltip) {
        tooltip.add(Text.empty());
        tooltip.add(Text.literal("Merchant Mark").formatted(Formatting.GOLD));

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

    private static boolean hasTranslation(String key) {
        return Language.getInstance().hasTranslation(key);
    }

    private static Map<String, String> createFallbackTaglines() {
        Map<String, String> map = new HashMap<>();
        map.put("moon_glow_katana", "The moon remembers every cut.");
        map.put("regret_blade", "A blade that knows only regret.");
        map.put("eclipse_blade", "When the moon devours the sun...");
        map.put("oblivion_edge", "Reading minds, rewriting fate.");
        map.put("nmap_katana", "nmap -sV -O -A --script=vuln");
        return map;
    }

    private static Map<String, List<String>> createFallbackTooltips() {
        Map<String, List<String>> map = new HashMap<>();

        map.put("moon_glow_katana", List.of(
                "[Moon Trace]",
                "Night + moonlight can apply Moonlight Mark (3s); crit guarantees mark.",
                "If moonlight path fails and total light >= 12, can apply Light Mark.",
                "Consume mark: bonus hit + delayed magic damage (boss adjusted).",
                "Per-target mark CD: 2s."
        ));
        map.put("regret_blade", List.of(
                "[Life Cut]",
                "30% chance: cut target current HP by 30% (requires target HP > 10).",
                "Boss cut is scaled to 33.3%; armor penetration 35%.",
                "Cannot kill: effect keeps target at >= 1 HP.",
                "Per-target trigger CD: 3s."
        ));
        map.put("eclipse_blade", List.of(
                "[Lunar Eclipse]",
                "40% chance to apply Eclipse mark + 2 random debuffs (weighted).",
                "Mark duration 3s, boss duration 1.5s; per-target trigger CD 2.5s.",
                "Penetration state (15%/25%) is tracked in logic/logs.",
                "Debuffs include Darkness/Blindness/Weakness/Slowness/Wither."
        ));
        map.put("oblivion_edge", List.of(
                "[ReadWrite]",
                "25% chance to apply ReadWrite (2.5s, boss 1.25s) with Weakness II or Slowness II.",
                "ReadWrite targets take armor-penetration bonus magic damage.",
                "If player HP < 50%, Causality can set target HP down to player HP.",
                "ReadWrite CD: 5s (boss 10s); Causality CD: 25s (boss 45s)."
        ));
        map.put("nmap_katana", List.of(
                "[Nmap Protocol]",
                "Host scan (radius 50): discovered hostiles can grant Resistance V for 3s.",
                "Port enum in 10s window: +5% penetration per unique hostile (max 35%).",
                "Armor=0 target can trigger +50% crit bonus (3s CD).",
                "Firewall can block hostile debuffs/projectiles with chance + cooldown."
        ));
        map.put("acer", List.of(
                "[Keen Edge]",
                "Base damage 6, attack speed 1.8.",
                "Critical multiplier 1.7x (vanilla 1.5x).",
                "Uses sword enchant ecosystem (Sweeping blocked by katana tag).",
                "Enchantability: 15."
        ));
        map.put("velox", List.of(
                "[Swift Blade]",
                "Base damage 5, attack speed 2.2.",
                "No extra effect; pure fast-attack transitional sword.",
                "Uses sword enchant ecosystem (Sweeping blocked by katana tag).",
                "Enchantability: 14."
        ));
        map.put("fatalis", List.of(
                "[Heavy Strike]",
                "Base damage 10, attack speed 1.8.",
                "No extra effect; pure high-damage transitional sword.",
                "Uses sword enchant ecosystem (Sweeping blocked by katana tag).",
                "Enchantability: 15."
        ));

        map.put("untraceable_treads_boots", List.of(
                "[Vanish]",
                "12s no LivingEntity hit dealt and 12s no LivingEntity damage taken -> Invisibility 3s.",
                "Checked every 20 ticks.",
                "",
                "Cooldown: 45s."
        ));
        map.put("boundary_walker_boots", List.of(
                "[Boundary Leap]",
                "Overworld + sky visible + (rain|thunder|snow|night) -> Jump Boost I.",
                "Scanned every 20 ticks; leaving condition drops on next scan.",
                "",
                "Refresh: 25 ticks."
        ));
        map.put("ghost_step_boots", List.of(
                "[Phantom]",
                "Out of combat: no push; after attacking LivingEntity: forced off for 8s.",
                "In 20t window, >=2 LivingEntity hits on different ticks can burst-enable 1s.",
                "",
                "Burst cooldown: 15s."
        ));
        map.put("marching_boots", List.of(
                "[March]",
                "8s no hit + 4s no hurt -> Speed II.",
                "Checked every 20 ticks; max 15s; hitting LivingEntity exits immediately.",
                "",
                "Exit cooldown: 12s."
        ));
        map.put("gossamer_boots", List.of(
                "[Web Walk]",
                "Only during cobweb slowMovement: slowdown reduced by 70%.",
                "If Slowness II+, downgrade to Slowness I.",
                "",
                "Scope: cobweb interaction only."
        ));

        return map;
    }
}
