package dev.xqanzd.moonlitbroker.katana.effect.nmap;

public class NmapConfig {
    // === Debug Switch ===
    public static boolean DEBUG = true;

    // ========== Host Discovery ==========
    public static final double SCAN_RADIUS = 24.0;
    public static final double ABS_DY_MAX = 10.0;
    public static final int SCAN_INTERVAL_TICKS = 80;            // 4 seconds
    public static final int RESISTANCE_DURATION_TICKS = 100;     // 5 seconds
    public static final int RESISTANCE_AMPLIFIER = 4;            // Level V
    public static final int MAX_CHAIN_REFRESHES = 3;
    public static final int COOLDOWN_TICKS = 240;                // 12 seconds
    public static final double THREAT_FALLBACK_DISTANCE = 12.0;
    public static final double THREAT_MOVE_SPEED_SQ = 0.0025;    // ~0.05 blocks/tick
    public static final double THREAT_MOVE_DOT_MIN = 0.01;

    // ========== Port Enumeration ==========
    public static final float BASE_ARMOR_PENETRATION = 0.25f;   // 全 katana 基础穿透 25%
    public static final int ENUM_WINDOW_TICKS = 200;             // 10 seconds
    public static final float PENETRATION_PER_HOSTILE = 0.05f;   // +5%
    public static final float PENETRATION_CAP = 0.35f;           // 35%
    public static final int PENETRATION_MAX_COUNT = 7;

    // ========== Vulnerability Scan ==========
    public static final int VULN_CRIT_COOLDOWN_TICKS = 60;       // 3 seconds
    public static final float VULN_CRIT_MULTIPLIER = 1.5f;       // +50%

    // ========== Firewall Bypass - Debuff ==========
    public static final float FIREWALL_DEBUFF_CHANCE = 0.40f;
    public static final float FIREWALL_DEBUFF_CHANCE_BOSS = 0.40f;
    public static final int FIREWALL_DEBUFF_COOLDOWN_TICKS = 120;

    // ========== Firewall Bypass - Projectile ==========
    public static final float FIREWALL_PROJ_CHANCE = 0.35f;
    public static final float FIREWALL_PROJ_CHANCE_BOSS = 0.35f;
    public static final int FIREWALL_PROJ_COOLDOWN_TICKS = 120;
}
