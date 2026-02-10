package mod.test.mymodtest.trade;

import mod.test.mymodtest.katana.item.KatanaItems;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class KatanaIdUtil {
    public static final String MM_KATANA_ID = "MM_KATANA_ID";
    public static final String SECRET_MARKER_ID = "MoonTradeSecretId";
    public static final String SECRET_MARKER = "MoonTradeSecret";

    private static final Set<String> SECRET_KATANAS = Set.of(
        "moonglow",
        "regret",
        "eclipse",
        "oblivion",
        "nmap"
    );

    private static final Pattern UUID8 = Pattern.compile("^[0-9a-f]{8}$");

    private static final Map<String, String> LEGACY_ID_ALIAS = Map.of(
        "moon_glow_katana", "moonglow",
        "regret_blade", "regret",
        "eclipse_blade", "eclipse",
        "oblivion_edge", "oblivion",
        "nmap_katana", "nmap"
    );

    private static final Map<Item, String> KATANA_ID_BY_ITEM = Map.of(
        KatanaItems.MOON_GLOW_KATANA, "moonglow",
        KatanaItems.REGRET_BLADE, "regret",
        KatanaItems.ECLIPSE_BLADE, "eclipse",
        KatanaItems.OBLIVION_EDGE, "oblivion",
        KatanaItems.NMAP_KATANA, "nmap"
    );

    private KatanaIdUtil() {
    }

    public static String extractCanonicalKatanaId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }

        String fromData = extractFromCustomData(stack);
        if (!fromData.isEmpty()) {
            return fromData;
        }

        return KATANA_ID_BY_ITEM.getOrDefault(stack.getItem(), "");
    }

    public static boolean isSecretKatana(String katanaId) {
        String canonical = canonicalizeKatanaId(katanaId);
        return !canonical.isEmpty();
    }

    public static String canonicalizeKatanaId(String rawId) {
        if (rawId == null) {
            return "";
        }
        String id = rawId.trim();
        if (id.isEmpty()) {
            return "";
        }

        String candidate = id.toLowerCase(Locale.ROOT).replace('-', '_');
        if (candidate.startsWith("katana:")) {
            candidate = candidate.substring("katana:".length());
            int nextColon = candidate.indexOf(':');
            if (nextColon >= 0) {
                candidate = candidate.substring(0, nextColon);
            }
            return canonicalFromToken(candidate);
        }

        if (candidate.startsWith("katana_")) {
            candidate = candidate.substring("katana_".length());
            if (UUID8.matcher(candidate).matches()) {
                return "";
            }
            return canonicalFromToken(candidate);
        }

        String fromSegments = canonicalFromSegments(candidate);
        if (!fromSegments.isEmpty()) {
            return fromSegments;
        }

        return canonicalFromToken(candidate);
    }

    private static String canonicalFromSegments(String candidate) {
        if (candidate == null || candidate.isEmpty() || !candidate.contains(":")) {
            return "";
        }
        String[] parts = candidate.split(":");
        for (String part : parts) {
            String canonical = canonicalFromToken(part);
            if (!canonical.isEmpty()) {
                return canonical;
            }
        }
        return "";
    }

    private static String canonicalFromToken(String token) {
        if (token == null) {
            return "";
        }
        String candidate = token.trim();
        if (candidate.isEmpty() || UUID8.matcher(candidate).matches()) {
            return "";
        }

        String aliased = LEGACY_ID_ALIAS.getOrDefault(candidate, candidate);
        if (SECRET_KATANAS.contains(aliased)) {
            return aliased;
        }

        int split = candidate.lastIndexOf('_');
        if (split > 0 && split < candidate.length() - 1) {
            String suffix = candidate.substring(split + 1);
            if (UUID8.matcher(suffix).matches()) {
                String prefix = candidate.substring(0, split);
                aliased = LEGACY_ID_ALIAS.getOrDefault(prefix, prefix);
                if (SECRET_KATANAS.contains(aliased)) {
                    return aliased;
                }
            }
        }
        return "";
    }

    private static String extractFromCustomData(ItemStack stack) {
        NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (data == null) {
            return "";
        }
        NbtCompound nbt = data.copyNbt();

        String canonical = canonicalizeKatanaId(nbt.getString(MM_KATANA_ID));
        if (!canonical.isEmpty()) {
            return canonical;
        }

        canonical = canonicalizeKatanaId(nbt.getString(SECRET_MARKER_ID));
        if (!canonical.isEmpty()) {
            return canonical;
        }

        // Legacy fallback: boolean marker existed but ID could be missing on very old offers.
        if (nbt.getBoolean(SECRET_MARKER)) {
            return KATANA_ID_BY_ITEM.getOrDefault(stack.getItem(), "");
        }

        return "";
    }
}
