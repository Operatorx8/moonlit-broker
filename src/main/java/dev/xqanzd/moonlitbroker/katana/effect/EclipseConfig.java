package dev.xqanzd.moonlitbroker.katana.effect;

/**
 * 暗月之蚀（Eclipse Blade）配置参数
 *
 * 核心机制：
 * - 40% 触发概率，无环境限制
 * - 施加月蚀标记 + 随机 2 个 Debuff（含中毒轮盘）
 * - 护甲穿透：基础 25%，对标记目标 25%（仅用于日志展示）
 */
public class EclipseConfig {
    // === 调试开关 ===
    public static boolean DEBUG = true;

    // === 触发条件 ===
    public static final float TRIGGER_CHANCE = 0.40f;  // 40%
    public static final int TRIGGER_CD_TICKS = 50;     // 每目标触发冷却 2.5秒

    // === 标记持续时间 ===
    public static final int MARK_DURATION_TICKS = 60;       // 3 秒
    public static final float BOSS_DURATION_MULTIPLIER = 1.0f;  // Boss 不减半

    // === 护甲穿透（简化版：仅用于日志展示，不再做额外伤害补偿）===
    public static final float BASE_ARMOR_PENETRATION = 0.25f;    // 基础 25%
    public static final float MARKED_ARMOR_PENETRATION = 0.25f;  // 标记目标 25%

    // === Debuff 配置 ===
    public static final int DEBUFF_COUNT = 2;  // 每次触发 2 个

    // Debuff 等级（0 = I级）
    public static final int WEAKNESS_AMPLIFIER = 0;
    public static final int WITHER_AMPLIFIER = 0;
    public static final int SLOWNESS_AMPLIFIER = 0;
    public static final int BLINDNESS_AMPLIFIER = 0;
    public static final int DARKNESS_AMPLIFIER = 0;
    public static final int POISON_AMPLIFIER = 0;

    // === 组合权重（总权重由运行时动态累加）===
    public static final int WEIGHT_DARKNESS_BLINDNESS = 30;
    public static final int WEIGHT_DARKNESS_WEAKNESS = 12;
    public static final int WEIGHT_DARKNESS_SLOWNESS = 12;
    public static final int WEIGHT_BLINDNESS_WEAKNESS = 12;
    public static final int WEIGHT_BLINDNESS_SLOWNESS = 12;
    public static final int WEIGHT_WEAKNESS_SLOWNESS = 10;
    public static final int WEIGHT_DARKNESS_POISON = 8;
    public static final int WEIGHT_BLINDNESS_POISON = 8;
    public static final int WEIGHT_WEAKNESS_POISON = 10;
    public static final int WEIGHT_SLOWNESS_POISON = 10;
    public static final int WEIGHT_DARKNESS_WITHER = 4;
    public static final int WEIGHT_BLINDNESS_WITHER = 4;
    public static final int WEIGHT_WEAKNESS_WITHER = 2;
    public static final int WEIGHT_SLOWNESS_WITHER = 2;
}
