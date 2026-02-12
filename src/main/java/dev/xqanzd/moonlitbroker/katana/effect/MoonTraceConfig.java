package dev.xqanzd.moonlitbroker.katana.effect;

/**
 * 月痕配置 - 月之光芒太刀特效参数
 * 所有数值集中管理，便于调参
 */
public class MoonTraceConfig {
    // === 调试开关 ===
    public static boolean DEBUG = true;  // 生产环境改为 false

    // === 触发条件 ===
    public static final boolean REQUIRE_NIGHT = true;
    public static final boolean REQUIRE_MOONLIGHT = true;         // 月光判定（天空可见 + 光照）
    public static final int SKY_LIGHT_THRESHOLD = 8;              // 天空光照阈值（0-15）
    public static final boolean FORBID_BOSS = false;              // 不再禁止 Boss（可对 Boss 生效，但效果减弱）
    public static final boolean LIGHT_MARK_ENABLED = true;        // 非月光条件下可用光照标记
    public static final int LIGHT_MARK_MIN_LIGHT = 12;            // 总光照阈值（max(block, sky)）
    public static final float LIGHT_MARK_DAMAGE_MULT = 0.70f;     // 光照标记消耗伤害倍率

    // === Mark 概率：finalChance = clamp(BASE + SCALE * moonFactor, MIN, MAX) ===
    public static final boolean CRIT_GUARANTEES_MARK = true;      // 暴击必触发 Mark（跳过概率 roll）
    public static final float CHANCE_BASE = 0.20f;                // 基础概率 20%
    public static final float CHANCE_MOON_SCALE = 0.30f;          // 月相缩放系数
    public static final float CHANCE_MIN = 0.15f;                 // 概率下限 15%
    public static final float CHANCE_MAX = 0.55f;                 // 概率上限 55%

    // === 月相倍率（影响 mark 概率）===
    public static final float MOON_PHASE_NEW = 0.20f;             // 新月：moonFactor = 0.20
    public static final float MOON_PHASE_FULL = 1.00f;            // 满月：moonFactor = 1.00
    // 中间月相线性插值

    // === 即时效果（触发月痕时）===
    public static final float INSTANT_DAMAGE_MIN = 1.0f;          // 0.5❤
    public static final float INSTANT_DAMAGE_MAX = 2.0f;          // 1.0❤
    public static final int SLOWNESS_DURATION = 15;               // 0.75秒
    public static final int SLOWNESS_LEVEL = 0;                   // Slowness I

    // === 月痕状态 ===
    public static final int MARK_DURATION = 60;                   // 3秒
    public static final int MARK_COOLDOWN = 40;                   // 同一目标 mark 冷却 2秒

    // === 标记反馈 ===
    public static final int GLOWING_DURATION = 12;                // 发光持续 0.6秒

    // === 消耗增伤（物理部分）===
    public static final float ARMOR_PEN_NORMAL = 0.50f;           // 普通目标 50% 护甲穿透
    public static final float ARMOR_PEN_BOSS = 0.25f;             // Boss 25% 护甲穿透

    public static final float CONSUME_DAMAGE_MIN = 5.0f;          // 2.5❤
    public static final float CONSUME_DAMAGE_MAX = 9.0f;          // 4.5❤

    // === 消耗时魔法补偿：magicBonus = BASE + min(maxHP * PERCENT, CAP) ===
    // 绕过护甲，专补高甲场景
    public static final float MAGIC_BASE_NORMAL = 1.0f;           // 普通：基础魔法伤害 0.5❤
    public static final float MAGIC_BASE_BOSS = 0.5f;             // Boss：基础魔法伤害 0.25❤
    public static final float MAGIC_PERCENT_HP_NORMAL = 0.02f;    // 普通：最大血量 2%
    public static final float MAGIC_PERCENT_HP_BOSS = 0.01f;      // Boss：最大血量 1%
    public static final float MAGIC_PERCENT_CAP_NORMAL = 4.0f;    // 普通：百分比伤害上限 2❤
    public static final float MAGIC_PERCENT_CAP_BOSS = 2.0f;      // Boss：百分比伤害上限 1❤

    // === 夜间速度 Buff ===
    public static final int SPEED_BUFF_DURATION = 60;             // Speed buff 持续 3秒（刷新制）
    public static final int SPEED_BUFF_LEVEL = 0;                 // Speed I
    public static final int SPEED_BUFF_REFRESH_INTERVAL = 40;     // 每 2秒刷新一次

    // === 夜间夜视 Buff ===
    public static final int NIGHT_VISION_DURATION = 60;           // 持续 3秒（与 Speed 同步刷新）
}
