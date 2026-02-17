package dev.xqanzd.moonlitbroker.registry;

import dev.xqanzd.moonlitbroker.trade.item.BountyContractItem;
import dev.xqanzd.moonlitbroker.trade.item.MerchantMarkItem;
import dev.xqanzd.moonlitbroker.trade.item.TradeScrollItem;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.entity.EntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.Registries;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Identifier;
import net.minecraft.util.Formatting;
import net.minecraft.util.Language;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class TooltipComposer {
    private static final Set<String> KATANA_HINT_ACCENT_ITEMS = Set.of(
            "moon_glow_katana",
            "regret_blade",
            "eclipse_blade",
            "oblivion_edge",
            "nmap_katana"
    );

    private static final Map<String, List<String>> KATANA_HINT_KEYWORDS_ZH = Map.of(
            "moon_glow_katana", List.of("月痕", "追伤"),
            "regret_blade", List.of("当前生命", "不致命"),
            "eclipse_blade", List.of("月蚀", "负面效果"),
            "oblivion_edge", List.of("读写", "因果拉平"),
            "nmap_katana", List.of("扫描", "护体")
    );

    private static final Map<String, List<String>> KATANA_HINT_KEYWORDS_EN = Map.of(
            "moon_glow_katana", List.of("Moontrace", "delayed damage"),
            "regret_blade", List.of("current HP", "final blow"),
            "eclipse_blade", List.of("Eclipse marks", "debuffs"),
            "oblivion_edge", List.of("rewrites", "equalize life"),
            "nmap_katana", List.of("Scans threats", "resistance")
    );

    private TooltipComposer() {}

    public static void compose(
            String modId,
            String itemPath,
            ItemStack stack,
            TooltipType tooltipType,
            List<Text> tooltip,
            List<String> inscriptions
    ) {
        // ── Phase A: extract all vanilla attribute blocks before any insertion ──
        List<Text> extractedAttributes = extractVanillaAttributeBlocks(tooltip);

        // ── Build the custom lore block ──
        List<Text> block = new ArrayList<>();
        boolean useZhStyle = isChineseLanguage();
        boolean isEnglish = !useZhStyle;

        addInscription(block, inscriptions);
        addTagline(modId, itemPath, block, isEnglish);
        addHint(modId, itemPath, block, isEnglish);
        addShiftDetails(modId, itemPath, stack, block, isEnglish);
        addAdvancedDebug(modId, itemPath, stack, tooltipType, block);

        // ── Phase B: insert attribute block, then lore block, after item name ──
        int insertIndex = Math.min(1, tooltip.size());

        // Insert extracted attributes first (right after item name)
        if (!extractedAttributes.isEmpty()) {
            tooltip.addAll(insertIndex, extractedAttributes);
            // lore block goes after the attribute block
            insertIndex += extractedAttributes.size();
        }

        // Insert custom lore block after attributes
        if (!block.isEmpty()) {
            if (!tooltip.isEmpty()) {
                block.add(Text.empty());
            }
            tooltip.addAll(insertIndex, block);
        }
    }

    /**
     * Extracts and removes all vanilla attribute blocks (header + attribute lines)
     * from the tooltip list, returning them as a single ordered list.
     * Handles mainhand, offhand, head, chest, legs, feet slots.
     */
    private static List<Text> extractVanillaAttributeBlocks(List<Text> tooltip) {
        if (tooltip == null || tooltip.isEmpty()) {
            return new ArrayList<>();
        }

        List<Text> extracted = new ArrayList<>();
        List<IndexRange> removeRanges = new ArrayList<>();

        int cursor = 0;
        while (cursor < tooltip.size()) {
            if (!isVanillaModifierSlotHeader(tooltip.get(cursor))) {
                cursor++;
                continue;
            }

            // Found a header — include the blank line before it if present
            int removeStart = cursor;
            if (removeStart > 0 && isEmptyTooltipLine(tooltip.get(removeStart - 1))) {
                removeStart--;
            }

            // Scan forward to collect all attribute lines belonging to this header
            int blockEnd = cursor + 1;
            while (blockEnd < tooltip.size()) {
                Text line = tooltip.get(blockEnd);
                if (isVanillaModifierAttributeLine(line) || isVanillaModifierSlotHeader(line)) {
                    blockEnd++;
                    continue;
                }
                // Empty line might separate two slot headers — peek ahead
                if (isEmptyTooltipLine(line)) {
                    if (blockEnd + 1 < tooltip.size() && isVanillaModifierSlotHeader(tooltip.get(blockEnd + 1))) {
                        blockEnd++;
                        continue;
                    }
                    // Trailing blank after the block — include it in removal but not in extracted content
                    blockEnd++;
                }
                break;
            }

            // Trim empty edges from the content we keep
            List<Text> segment = trimEmptyEdgeLines(tooltip.subList(cursor, blockEnd));
            if (!segment.isEmpty()) {
                if (!extracted.isEmpty()) {
                    extracted.add(Text.empty());
                }
                extracted.addAll(segment);
            }

            removeRanges.add(new IndexRange(removeStart, blockEnd));
            cursor = blockEnd;
        }

        // Remove from bottom to top so indices stay valid
        for (int i = removeRanges.size() - 1; i >= 0; i--) {
            IndexRange range = removeRanges.get(i);
            int safeEnd = Math.min(range.end, tooltip.size());
            if (range.start < safeEnd) {
                tooltip.subList(range.start, safeEnd).clear();
            }
        }

        return extracted;
    }

    public static void addInscription(List<Text> out, List<String> inscriptions) {
        if (inscriptions == null || inscriptions.isEmpty()) {
            return;
        }
        for (String line : inscriptions) {
            if (line == null || line.isBlank()) {
                continue;
            }
            out.add(Text.literal(line).formatted(Formatting.GRAY));
        }
        out.add(Text.empty());
    }


    public static void addTagline(String modId, String itemPath, List<Text> out, boolean isEnglish) {
        MutableText line1 = resolveTagline1(modId, itemPath);
        MutableText line2 = resolveKey(translatableKey(modId, itemPath, "tagline.2"));
        if (line1 != null) {
            out.add(styleTagline(line1, isEnglish));
        }
        if (line2 != null) {
            out.add(styleTagline(line2, isEnglish));
        }
    }

    public static void addHint(String modId, String itemPath, List<Text> out, boolean isEnglish) {
        MutableText hint = resolveHint(modId, itemPath);
        if (hint == null) {
            return;
        }
        out.add(styleHint(itemPath, hint, isEnglish));
    }

    public static void addShiftDetails(
            String modId,
            String itemPath,
            ItemStack stack,
            List<Text> out,
            boolean isEnglish
    ) {
        List<MutableText> details = resolveDetails(modId, itemPath);
        details.addAll(resolveRuntimeDetails(modId, itemPath, stack));
        if (details.isEmpty()) {
            return;
        }

        if (Screen.hasShiftDown()) {
            for (MutableText detail : details) {
                out.add(styleDetail(detail, isEnglish));
            }
            return;
        }

        MutableText holdShift = Text.translatable("tooltip." + modId + ".hold_shift");
        out.add(styleDetail(holdShift, isEnglish));
    }

    public static void addAdvancedDebug(
            String modId,
            String itemPath,
            ItemStack stack,
            TooltipType tooltipType,
            List<Text> out
    ) {
        if (!tooltipType.isAdvanced()) {
            return;
        }

        int idx = 1;
        while (true) {
            MutableText debugLine = resolveKey(translatableKey(modId, itemPath, "debug." + idx));
            if (debugLine == null) {
                break;
            }
            out.add(debugLine.formatted(Formatting.DARK_GRAY));
            idx++;
        }

        MutableText debugId = resolveKey("tooltip." + modId + ".debug.item_id");
        if (debugId != null) {
            out.add(Text.translatable("tooltip." + modId + ".debug.item_id", itemPath).formatted(Formatting.DARK_GRAY));
        }
    }

    private static MutableText resolveTagline1(String modId, String itemPath) {
        MutableText line = resolveKey(translatableKey(modId, itemPath, "tagline.1"));
        if (line != null) {
            return line;
        }
        return resolveKey(translatableKey(modId, itemPath, "tagline"));
    }

    private static MutableText resolveHint(String modId, String itemPath) {
        MutableText hint = resolveKey(translatableKey(modId, itemPath, "hint"));
        if (hint != null) {
            return hint;
        }
        hint = resolveKey("item." + modId + "." + itemPath + ".effect");
        if (hint != null) {
            return hint;
        }
        hint = resolveKey("item." + modId + "." + itemPath + ".desc");
        if (hint != null) {
            return hint;
        }
        return resolveKey(translatableKey(modId, itemPath, "line1"));
    }

    private static List<MutableText> resolveDetails(String modId, String itemPath) {
        List<MutableText> details = new ArrayList<>();

        int idx = 1;
        while (true) {
            MutableText line = resolveKey(translatableKey(modId, itemPath, "detail." + idx));
            if (line == null) {
                break;
            }
            details.add(line);
            idx++;
        }
        if (!details.isEmpty()) {
            return details;
        }

        String[] legacy = {"line2", "line3", "params"};
        for (String suffix : legacy) {
            MutableText line = resolveKey(translatableKey(modId, itemPath, suffix));
            if (line != null) {
                details.add(line);
            }
        }
        return details;
    }

    private static List<MutableText> resolveRuntimeDetails(String modId, String itemPath, ItemStack stack) {
        List<MutableText> runtime = new ArrayList<>();

        if ("trade_scroll".equals(itemPath)) {
            String grade = TradeScrollItem.getGrade(stack);
            int uses = TradeScrollItem.getUses(stack);
            runtime.add(Text.translatable("tooltip." + modId + ".trade_scroll.grade", localizeGrade(modId, grade)));
            runtime.add(Text.translatable("tooltip." + modId + ".trade_scroll.uses", uses));
            return runtime;
        }

        if ("merchant_mark".equals(itemPath)) {
            UUID owner = MerchantMarkItem.getOwnerUUID(stack);
            if (owner == null) {
                runtime.add(Text.translatable("tooltip." + modId + ".merchant_mark.unbound"));
                return runtime;
            }
            runtime.add(Text.translatable("tooltip." + modId + ".merchant_mark.bound"));
            runtime.add(Text.translatable("tooltip." + modId + ".merchant_mark.uuid_short",
                    owner.toString().substring(0, 8) + "..."));
            return runtime;
        }

        if ("bounty_contract".equals(itemPath)) {
            String target = BountyContractItem.getTarget(stack);
            if (target.isEmpty()) {
                runtime.add(Text.translatable("tooltip." + modId + ".bounty_contract.blank"));
                return runtime;
            }

            Text targetName = Text.literal(target);
            Identifier targetId = Identifier.tryParse(target);
            if (targetId != null) {
                EntityType<?> entityType = Registries.ENTITY_TYPE.get(targetId);
                if (entityType != null) {
                    targetName = entityType.getName();
                }
            }

            int progress = BountyContractItem.getProgress(stack);
            int required = BountyContractItem.getRequired(stack);
            boolean completed = BountyContractItem.isCompleted(stack);

            runtime.add(Text.translatable("tooltip." + modId + ".bounty_contract.target", targetName));
            runtime.add(Text.translatable("tooltip." + modId + ".bounty_contract.progress", progress, required));
            runtime.add(Text.translatable("tooltip." + modId + ".bounty_contract."
                    + (completed ? "completed" : "incomplete")));
            return runtime;
        }

        return runtime;
    }

    private static Text localizeGrade(String modId, String grade) {
        String normalized = grade == null ? "" : grade.toLowerCase(Locale.ROOT);
        String key = "tooltip." + modId + ".trade_scroll.grade." + normalized;
        if (Language.getInstance().hasTranslation(key)) {
            return Text.translatable(key);
        }
        return Text.literal(grade == null ? "" : grade);
    }

    private static MutableText resolveKey(String key) {
        if (!Language.getInstance().hasTranslation(key)) {
            return null;
        }
        return Text.translatable(key);
    }

    private static String translatableKey(String modId, String itemPath, String suffix) {
        return "tooltip." + modId + "." + itemPath + "." + suffix;
    }

    private static MutableText styleTagline(MutableText text, boolean isEnglish) {
        return isEnglish
                ? text.formatted(Formatting.DARK_AQUA, Formatting.ITALIC)
                : text.formatted(Formatting.AQUA);
    }

    private static MutableText styleHint(String itemPath, MutableText text, boolean isEnglish) {
        MutableText accentedHint = buildHintWithAccent(itemPath, text.getString(), isEnglish);
        if (accentedHint != null) {
            return accentedHint;
        }
        return isEnglish
                ? text.formatted(Formatting.GRAY, Formatting.ITALIC)
                : text.formatted(Formatting.GOLD);
    }

    private static MutableText buildHintWithAccent(String itemPath, String rawHint, boolean isEnglish) {
        if (!KATANA_HINT_ACCENT_ITEMS.contains(itemPath) || rawHint == null || rawHint.isEmpty()) {
            return null;
        }

        List<String> keywords = isEnglish
                ? KATANA_HINT_KEYWORDS_EN.get(itemPath)
                : KATANA_HINT_KEYWORDS_ZH.get(itemPath);
        if (keywords == null || keywords.isEmpty()) {
            return null;
        }

        List<HintSpan> spans = findHintAccentSpans(rawHint, keywords, isEnglish);
        if (spans.isEmpty() || spans.size() > 2) {
            return null;
        }

        MutableText result = Text.empty();
        int cursor = 0;
        for (HintSpan span : spans) {
            if (span.start > cursor) {
                result.append(styleHintChunk(rawHint.substring(cursor, span.start), isEnglish, false));
            }
            result.append(styleHintChunk(rawHint.substring(span.start, span.end), isEnglish, true));
            cursor = span.end;
        }
        if (cursor < rawHint.length()) {
            result.append(styleHintChunk(rawHint.substring(cursor), isEnglish, false));
        }
        return result;
    }

    private static List<HintSpan> findHintAccentSpans(String source, List<String> keywords, boolean isEnglish) {
        List<HintSpan> spans = new ArrayList<>();
        String haystack = isEnglish ? source.toLowerCase(Locale.ROOT) : source;
        List<String> needles = new ArrayList<>();
        for (String keyword : keywords) {
            if (keyword == null || keyword.isBlank()) {
                continue;
            }
            needles.add(isEnglish ? keyword.toLowerCase(Locale.ROOT) : keyword);
        }
        if (needles.isEmpty()) {
            return spans;
        }

        int cursor = 0;
        while (cursor < haystack.length()) {
            int bestStart = -1;
            int bestLength = -1;
            for (String needle : needles) {
                int hit = haystack.indexOf(needle, cursor);
                if (hit < 0) {
                    continue;
                }
                if (bestStart == -1 || hit < bestStart || (hit == bestStart && needle.length() > bestLength)) {
                    bestStart = hit;
                    bestLength = needle.length();
                }
            }
            if (bestStart < 0) {
                break;
            }
            spans.add(new HintSpan(bestStart, bestStart + bestLength));
            if (spans.size() > 2) {
                return spans;
            }
            cursor = bestStart + bestLength;
        }
        return spans;
    }

    private static MutableText styleHintChunk(String value, boolean isEnglish, boolean accent) {
        MutableText chunk = Text.literal(value);
        if (accent) {
            return isEnglish
                    ? chunk.formatted(Formatting.LIGHT_PURPLE, Formatting.ITALIC)
                    : chunk.formatted(Formatting.LIGHT_PURPLE);
        }
        return isEnglish
                ? chunk.formatted(Formatting.GRAY, Formatting.ITALIC)
                : chunk.formatted(Formatting.GOLD);
    }

    private static MutableText styleDetail(MutableText text, boolean isEnglish) {
        return isEnglish
                ? text.formatted(Formatting.DARK_GRAY, Formatting.ITALIC)
                : text.formatted(Formatting.DARK_GRAY);
    }

    private static boolean isVanillaModifierSlotHeader(Text line) {
        String key = getTranslatableKey(line);
        return key != null && key.startsWith("item.modifiers.");
    }

    private static boolean isVanillaModifierAttributeLine(Text line) {
        // Check the line itself
        String key = getTranslatableKey(line);
        if (key != null && key.startsWith("attribute.modifier.")) {
            return true;
        }
        // Check siblings (child components) for attribute.modifier.* or attribute.name.* keys
        for (Text sibling : line.getSiblings()) {
            String sibKey = getTranslatableKey(sibling);
            if (sibKey != null && (sibKey.startsWith("attribute.modifier.") || sibKey.startsWith("attribute.name."))) {
                return true;
            }
        }
        return false;
    }

    private static String getTranslatableKey(Text line) {
        if (line.getContent() instanceof TranslatableTextContent translatable) {
            return translatable.getKey();
        }
        return null;
    }

    private static boolean isEmptyTooltipLine(Text line) {
        return line.getString().isBlank();
    }

    private static List<Text> trimEmptyEdgeLines(List<Text> lines) {
        int start = 0;
        int end = lines.size();

        while (start < end && isEmptyTooltipLine(lines.get(start))) {
            start++;
        }
        while (end > start && isEmptyTooltipLine(lines.get(end - 1))) {
            end--;
        }

        if (start >= end) {
            return List.of();
        }
        return new ArrayList<>(lines.subList(start, end));
    }

    private static boolean isChineseLanguage() {
        return isChineseLanguageCode(Language.getInstance().get("language.code"));
    }

    private static boolean isChineseLanguageCode(String langCode) {
        return langCode != null && langCode.toLowerCase(Locale.ROOT).startsWith("zh_");
    }

    private record IndexRange(int start, int end) {}

    private record HintSpan(int start, int end) {}
}
