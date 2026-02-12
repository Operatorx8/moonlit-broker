package dev.xqanzd.moonlitbroker.registry;

import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Language;

import java.util.List;

public final class TooltipHelper {
    private TooltipHelper() {}

    /**
     * Appends tagline on the first line below item name:
     * tooltip.<modid>.<path>.tagline
     *
     * This method inserts at index 1, so tagline is always above attribute blocks.
     */
    public static boolean appendTagline(
            String modId,
            String path,
            List<Text> tooltip,
            String fallbackTagline
    ) {
        String key = "tooltip." + modId + "." + path + ".tagline";
        MutableText line = resolveLine(key, fallbackTagline);
        if (line == null) {
            return false;
        }

        int insertIndex = Math.min(1, tooltip.size());
        tooltip.add(insertIndex, line.formatted(Formatting.DARK_AQUA));
        return true;
    }

    /**
     * Appends a standardized lore block with keys:
     * tooltip.<modid>.<path>.subtitle / line1 / line2 / line3 / params
     *
     * @param fallbackLines line order matches subtitle, line1, line2, line3, params
     * @return true if any line was appended
     */
    public static boolean appendLore(
            String modId,
            String path,
            List<Text> tooltip,
            List<String> fallbackLines
    ) {
        String[] suffixes = {"subtitle", "line1", "line2", "line3", "params"};
        boolean addedAny = false;

        for (int i = 0; i < suffixes.length; i++) {
            String key = "tooltip." + modId + "." + path + "." + suffixes[i];
            String fallback = fallbackAt(fallbackLines, i);
            MutableText line = resolveLine(key, fallback);
            if (line == null) {
                continue;
            }
            if (!addedAny) {
                tooltip.add(Text.empty());
                addedAny = true;
            }
            tooltip.add(line.formatted(styleForSuffix(suffixes[i])));
        }

        return addedAny;
    }

    public static void appendDebugLine(List<Text> tooltip, boolean debugEnabled, String debugText) {
        if (!debugEnabled) {
            return;
        }
        tooltip.add(Text.literal(debugText).formatted(Formatting.DARK_GRAY));
    }

    private static MutableText resolveLine(String key, String fallback) {
        if (Language.getInstance().hasTranslation(key)) {
            return Text.translatable(key);
        }
        if (fallback != null && !fallback.isBlank()) {
            return Text.literal(fallback);
        }
        return null;
    }

    private static String fallbackAt(List<String> fallbackLines, int index) {
        if (fallbackLines == null || index < 0 || index >= fallbackLines.size()) {
            return null;
        }
        return fallbackLines.get(index);
    }

    private static Formatting styleForSuffix(String suffix) {
        return switch (suffix) {
            case "subtitle" -> Formatting.GOLD;
            case "params" -> Formatting.DARK_AQUA;
            default -> Formatting.GRAY;
        };
    }
}
