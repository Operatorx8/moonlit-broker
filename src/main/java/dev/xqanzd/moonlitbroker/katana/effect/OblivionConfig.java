package dev.xqanzd.moonlitbroker.katana.effect;

/**
 * 窃念之黯（Oblivion Edge）配置参数
 *
 * 机制层级：
 * Layer 1: ReadWrite 标记（基础）
 * Layer 2: Debuff（伴随 ReadWrite）
 * Layer 3: 倒因噬果（条件触发）
 * Layer 4: 护甲穿透
 */
public class OblivionConfig {
    // === 调试开关 ===
    public static boolean DEBUG = true;

    // === ReadWrite 标记 ===
    public static final float READWRITE_CHANCE = 0.25f;              // 25%
    public static final int READWRITE_DURATION_TICKS = 50;           // 2.5s
    public static final int READWRITE_COOLDOWN_TICKS = 100;          // 5s

    // Boss 调整
    public static final float BOSS_DURATION_MULTIPLIER = 0.5f;       // 持续 ×0.5
    public static final float BOSS_COOLDOWN_MULTIPLIER = 2.0f;       // 冷却 ×2

    // === 伴随 Debuff ===
    public static final int DEBUFF_AMPLIFIER = 1;                    // II 级
    // 50% 虚弱, 50% 缓慢

    // === 倒因噬果 ===
    public static final float PLAYER_HP_THRESHOLD = 0.5f;            // 玩家 < 50% HP
    public static final float CAUSALITY_CHANCE = 0.20f;              // 20%
    public static final float CAUSALITY_CHANCE_BOSS = 0.066667f;     // 6.67%
    public static final int CAUSALITY_COOLDOWN_TICKS = 500;          // 25s
    public static final int CAUSALITY_COOLDOWN_BOSS_TICKS = 900;     // 45s

    // === 护甲穿透（仅对 ReadWrite 目标生效） ===
    public static final float ARMOR_PENETRATION = 0.35f;             // 35%
    public static final float ARMOR_PENETRATION_BOSS = 0.175f;       // Boss 减半 17.5%
}
