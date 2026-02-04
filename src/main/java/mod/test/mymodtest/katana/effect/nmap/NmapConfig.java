package mod.test.mymodtest.katana.effect.nmap;

public class NmapConfig {
    // === Debug Switch ===
    public static boolean DEBUG = true;

    // ========== Host Discovery ==========
    public static final int SCAN_RADIUS = 50;
    public static final int SCAN_INTERVAL_TICKS = 60;            // 3 seconds
    public static final int RESISTANCE_DURATION_TICKS = 60;     // 3 seconds
    public static final int RESISTANCE_AMPLIFIER = 4;            // Level V

    public static final int COOLDOWN_HOSTILE_HIT_TICKS = 1200;   // 60 seconds
    public static final int COOLDOWN_OTHER_DAMAGE_TICKS = 3600;  // 180 seconds
    public static final int COOLDOWN_SCAN_INTERVAL_TICKS = 40;   // 2 seconds

    // ========== Port Enumeration ==========
    public static final int ENUM_WINDOW_TICKS = 200;             // 10 seconds
    public static final float PENETRATION_PER_HOSTILE = 0.05f;   // +5%
    public static final float PENETRATION_CAP = 0.35f;           // 35%
    public static final int PENETRATION_MAX_COUNT = 7;

    // ========== Vulnerability Scan ==========
    public static final int VULN_CRIT_COOLDOWN_TICKS = 60;       // 3 seconds
    public static final float VULN_CRIT_MULTIPLIER = 1.5f;       // +50%

    // ========== Firewall Bypass - Debuff ==========
    public static final float FIREWALL_DEBUFF_CHANCE = 0.40f;
    public static final float FIREWALL_DEBUFF_CHANCE_BOSS = 0.20f;
    public static final int FIREWALL_DEBUFF_COOLDOWN_TICKS = 120;

    // ========== Firewall Bypass - Projectile ==========
    public static final float FIREWALL_PROJ_CHANCE = 0.35f;
    public static final float FIREWALL_PROJ_CHANCE_BOSS = 0.15f;
    public static final int FIREWALL_PROJ_COOLDOWN_TICKS = 120;
}
