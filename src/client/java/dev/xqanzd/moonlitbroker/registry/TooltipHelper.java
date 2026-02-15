package dev.xqanzd.moonlitbroker.registry;

import java.util.Set;

public final class TooltipHelper {
    private static final Set<String> KATANA_PATHS = Set.of(
            "moon_glow_katana", "regret_blade", "eclipse_blade", "oblivion_edge", "nmap_katana"
    );

    private TooltipHelper() {}

    public static boolean isKatana(String path) {
        return KATANA_PATHS.contains(path);
    }
}
