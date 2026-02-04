package mod.test.mymodtest.armor;

import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * 盔甲系统配置常量
 * 所有数值来自 docs/features/armor/ARMOR_PARAMS.md
 */
public final class ArmorConfig {
    private ArmorConfig() {}

    public static final String MOD_ID = "mymodtest";

    // ==================== 调试开关 ====================
    public static final boolean DEBUG = false;

    // ==================== 基础属性 ====================
    /** 耐久度乘数（头盔槽位基础 11，总耐久 = 25 × 11 = 275） */
    public static final int DURABILITY_BASE = 25;

    /** 护甲值 */
    public static final int PROTECTION = 3;

    /** 韧性 */
    public static final float TOUGHNESS = 2.0f;

    /** 击退抗性 */
    public static final float KNOCKBACK_RESISTANCE = 0.1f;

    /** 附魔等级 */
    public static final int ENCHANTABILITY = 15;

    // ==================== 哨兵的最后瞭望 (sentinel_helmet) ====================
    public static final String SENTINEL_EFFECT_ID = "sentinel_glow";
    /** 发光效果持续时间 (ticks) */
    public static final int SENTINEL_GLOW_DURATION = 100;
    /** 扫描范围 (blocks) */
    public static final float SENTINEL_SCAN_RANGE = 18.0f;
    /** 光照阈值 (光照 <= 此值触发) */
    public static final int SENTINEL_LIGHT_THRESHOLD = 7;
    /** 扫描间隔 (ticks) */
    public static final int SENTINEL_SCAN_INTERVAL = 20;
    /** 冷却时间 (ticks) */
    public static final int SENTINEL_COOLDOWN = 800;

    // ==================== 沉默之誓约 (silent_oath_helmet) ====================
    public static final String SILENT_OATH_EFFECT_ID = "silent_oath_reduction";
    /** 伤害减免值 */
    public static final float SILENT_OATH_REDUCTION = 2.0f;
    /** 触发伤害阈值 */
    public static final float SILENT_OATH_MIN_DAMAGE = 2.0f;
    /** 冷却时间 (ticks) */
    public static final int SILENT_OATH_COOLDOWN = 600;

    // ==================== 流亡者的面甲 (exile_mask_helmet) ====================
    public static final String EXILE_MASK_EFFECT_ID = "exile_mask_damage";
    /** 血量阈值 (血量 < 50% 激活) */
    public static final float EXILE_HEALTH_THRESHOLD = 0.5f;
    /** 每层增伤 */
    public static final float EXILE_DAMAGE_PER_STACK = 1.0f;
    /** 层数阈值 (每损失 7.5% 血量一层) */
    public static final float EXILE_STACK_THRESHOLD = 0.075f;
    /** 最大层数 */
    public static final int EXILE_MAX_STACKS = 4;
    /** 最大增伤 */
    public static final float EXILE_MAX_DAMAGE_BONUS = 4.0f;
    /** 更新间隔 (ticks) */
    public static final int EXILE_UPDATE_INTERVAL = 20;
    /** 属性修改器 Identifier (1.21+ API) */
    public static final Identifier EXILE_MODIFIER_ID = Identifier.of(MOD_ID, "exile_mask_damage");

    // ==================== 遗世之环 (relic_circlet_helmet) ====================
    public static final String RELIC_CIRCLET_EFFECT_ID = "relic_circlet_shield";
    /** 吸收效果持续时间 (ticks) */
    public static final int RELIC_ABSORPTION_DURATION = 60;
    /** 吸收效果等级 (0 = I级) */
    public static final int RELIC_ABSORPTION_AMPLIFIER = 0;
    /** 冷却时间 (ticks) */
    public static final int RELIC_CIRCLET_COOLDOWN = 600;
    /** 愤怒检测间隔 (ticks) */
    public static final int RELIC_ANGERED_CHECK_INTERVAL = 20;

    // ==================== 回溯者的额饰 (retracer_ornament_helmet) ====================
    public static final String RETRACER_EFFECT_ID = "retracer_save";
    /** 触发后设置的血量 */
    public static final float RETRACER_HEALTH_SET = 2.0f;
    /** 抗性效果持续时间 (ticks) */
    public static final int RETRACER_RESISTANCE_DURATION = 40;
    /** 抗性效果等级 (4 = V级 = 100%减伤) */
    public static final int RETRACER_RESISTANCE_AMPLIFIER = 4;
    /** 冷却时间 (ticks) */
    public static final int RETRACER_COOLDOWN = 18000;
}
