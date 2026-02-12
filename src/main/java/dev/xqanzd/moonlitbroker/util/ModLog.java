package dev.xqanzd.moonlitbroker.util;

public final class ModLog {
    public static final String MOD_TAG = "MysteriousMerchant";

    private ModLog() {
    }

    public static String buildPrefix(String domain, String module) {
        return "[" + MOD_TAG + "|" + domain + "|" + module + "]";
    }

    public static String armorBootPrefix() {
        return buildPrefix("Armor", "BOOT");
    }
}
