package dev.xqanzd.moonlitbroker.armor;

import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;

import java.util.UUID;

/**
 * 盔甲系统配置常量
 * 所有数值来自 docs/features/armor/ARMOR_PARAMS.md
 */
public final class ArmorConfig {
    private ArmorConfig() {}

    public static final String MOD_ID = "xqanzd_moonlit_broker";

    // ==================== 调试开关 ====================
    public static final boolean DEBUG = false;

    // ==================== 基础属性（头盔） ====================
    /** 耐久度乘数（头盔槽位基础 11，总耐久 = 25 × 11 = 275） */
    public static final int DURABILITY_BASE = 25;

    /** 头盔护甲值 */
    public static final int HELMET_PROTECTION = 3;

    /** 胸甲护甲值 */
    public static final int CHESTPLATE_PROTECTION = 8;

    /** 头盔韧性（默认值/兜底，可被 ArmorSpecs 按单件覆写） */
    public static final float HELMET_TOUGHNESS = 1.0f;

    /** 胸甲韧性（默认值/兜底，可被 ArmorSpecs 按单件覆写） */
    public static final float CHESTPLATE_TOUGHNESS = 5.0f;

    /** 护腿韧性（默认值/兜底，可被 ArmorSpecs 按单件覆写） */
    public static final float LEGGINGS_TOUGHNESS = 1.2f;

    /** 兼容旧代码：等价于胸甲韧性 */
    public static final float TOUGHNESS = CHESTPLATE_TOUGHNESS;

    /** 默认击退抗性（头盔） */
    public static final float KNOCKBACK_RESISTANCE = 0.1f;

    /** 附魔等级（兼容旧代码） */
    public static final int ENCHANTABILITY = 15;

    /** 头盔护甲值（兼容旧代码） */
    public static final int PROTECTION = HELMET_PROTECTION;

    // ==================== 稀有度分档（头盔） ====================
    public static final Rarity SENTINEL_HELMET_RARITY = Rarity.UNCOMMON;
    public static final Rarity SILENT_OATH_HELMET_RARITY = Rarity.RARE;
    public static final Rarity EXILE_MASK_HELMET_RARITY = Rarity.RARE;
    public static final Rarity RELIC_CIRCLET_HELMET_RARITY = Rarity.UNCOMMON;
    public static final Rarity RETRACER_ORNAMENT_HELMET_RARITY = Rarity.EPIC;

    // ==================== 稀有度分档（胸甲） ====================
    public static final Rarity OLD_MARKET_CHESTPLATE_RARITY = Rarity.RARE;
    public static final Rarity BLOOD_PACT_CHESTPLATE_RARITY = Rarity.RARE;
    public static final Rarity GHOST_GOD_CHESTPLATE_RARITY = Rarity.EPIC;
    public static final Rarity WINDBREAKER_CHESTPLATE_RARITY = Rarity.UNCOMMON;
    public static final Rarity VOID_DEVOURER_CHESTPLATE_RARITY = Rarity.RARE;

    // ==================== 哨兵的最后瞭望 (sentinel_helmet) ====================
    public static final String SENTINEL_EFFECT_ID = "sentinel_echo_pulse";
    /** 扫描范围 (blocks) */
    public static final float SENTINEL_SCAN_RANGE = 16.0f;
    /** 光照阈值 (光照 <= 此值触发) */
    public static final int SENTINEL_LIGHT_THRESHOLD = 7;
    /** 扫描间隔 (ticks) */
    public static final int SENTINEL_SCAN_INTERVAL = 20;
    /** Speed 持续时间 (ticks) */
    public static final int SENTINEL_SPEED_DURATION = 30;
    /** Speed 等级 (0 = I级) */
    public static final int SENTINEL_SPEED_AMPLIFIER = 0;
    /** 回声提示音 ID */
    public static final String SENTINEL_SOUND_ID = "xqanzd_moonlit_broker:armor.sentinel_pulse";
    /** 提示音音量 */
    public static final float SENTINEL_SOUND_VOLUME = 0.7f;
    /** 提示音音调 */
    public static final float SENTINEL_SOUND_PITCH = 1.2f;
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

    // ==================== 胸甲效果参数 ====================

    // ==================== 旧市护甲 (old_market_chestplate) ====================
    public static final String OLD_MARKET_TRADE_XP_EFFECT_ID = "old_market_trade_xp";
    public static final String OLD_MARKET_KILL_XP_EFFECT_ID = "old_market_kill_xp";
    /** 交易经验触发概率 */
    public static final float OLD_MARKET_TRADE_XP_CHANCE = 0.5f;
    /** 交易经验倍率 */
    public static final float OLD_MARKET_TRADE_XP_MULTIPLIER = 1.5f;
    /** 交易经验冷却 (ticks) */
    public static final int OLD_MARKET_TRADE_XP_COOLDOWN = 1200;
    /** 击杀经验触发概率 */
    public static final float OLD_MARKET_KILL_XP_CHANCE = 0.25f;
    /** 击杀经验倍率 */
    public static final float OLD_MARKET_KILL_XP_MULTIPLIER = 2.0f;
    /** 击杀经验冷却 (ticks) */
    public static final int OLD_MARKET_KILL_XP_COOLDOWN = 600;

    // ==================== 流血契约之胸铠 (blood_pact_chestplate) ====================
    public static final String BLOOD_PACT_EFFECT_ID = "blood_pact";
    /** 触发概率 */
    public static final float BLOOD_PACT_CHARGE_CHANCE = 0.5f;
    /** 额外扣血比例（本次伤害的 50%） */
    public static final float BLOOD_PACT_EXTRA_DAMAGE_RATIO = 0.5f;
    /** 单次额外扣血上限 (2♥ = 4.0 HP) */
    public static final float BLOOD_PACT_EXTRA_DAMAGE_CAP = 4.0f;
    /** 储能池上限 (4♥ = 8.0 HP) */
    public static final float BLOOD_PACT_POOL_CAP = 8.0f;
    /** 储能窗口 (ticks, 10s) */
    public static final int BLOOD_PACT_POOL_WINDOW = 200;

    // ==================== 鬼神之铠 (ghost_god_chestplate) ====================
    public static final String GHOST_GOD_DAMAGE_EFFECT_ID = "ghost_god_damage";
    public static final String GHOST_GOD_DEBUFF_EFFECT_ID = "ghost_god_debuff";
    /** 伤害减免概率（普通亡灵） */
    public static final float GHOST_GOD_DAMAGE_REDUCTION_CHANCE = 0.30f;
    /** 伤害减免概率（Boss） */
    public static final float GHOST_GOD_DAMAGE_REDUCTION_CHANCE_BOSS = 0.30f;
    /** 伤害减免比例 */
    public static final float GHOST_GOD_DAMAGE_REDUCTION_AMOUNT = 0.15f;
    /** Debuff 免疫概率（普通亡灵） */
    public static final float GHOST_GOD_DEBUFF_IMMUNE_CHANCE = 0.50f;
    /** Debuff 免疫概率（Boss） */
    public static final float GHOST_GOD_DEBUFF_IMMUNE_CHANCE_BOSS = 0.50f;

    // ==================== 商人的防风衣 (windbreaker_chestplate) ====================
    public static final String WINDBREAKER_SPEED_EFFECT_ID = "windbreaker_speed";
    /** 击退抗性 */
    public static final float WINDBREAKER_KNOCKBACK_RESISTANCE = 0.3f;
    /** 击退抗性修改器 ID */
    public static final Identifier WINDBREAKER_KB_MODIFIER_ID = Identifier.of(MOD_ID, "windbreaker_kb");
    /** 血量触发阈值 (<50%) */
    public static final float WINDBREAKER_HEALTH_TRIGGER = 0.5f;
    /** 血量解锁阈值 (>=60%) */
    public static final float WINDBREAKER_HEALTH_REARM = 0.6f;
    /** 速度效果持续时间 (ticks) */
    public static final int WINDBREAKER_SPEED_DURATION = 100;
    /** 速度效果等级 (0 = I级) */
    public static final int WINDBREAKER_SPEED_AMPLIFIER = 0;
    /** 冷却时间 (ticks) */
    public static final int WINDBREAKER_SPEED_COOLDOWN = 1800;
    /** 检查间隔 (ticks) */
    public static final int WINDBREAKER_CHECK_INTERVAL = 20;

    // ==================== 虚空之噬 (void_devourer_chestplate) ====================
    public static final String VOID_DEVOURER_EFFECT_ID = "void_devourer";
    /** 真实伤害比例（普通） */
    public static final float VOID_DEVOURER_TRUE_DAMAGE_RATIO = 0.04f;
    /** 真实伤害比例（Boss） */
    public static final float VOID_DEVOURER_TRUE_DAMAGE_RATIO_BOSS = 0.04f;
    /** 冷却时间 (ticks) */
    public static final int VOID_DEVOURER_COOLDOWN = 100;

    // ==================== 护腿基础属性 ====================
    /** 护腿护甲值 */
    public static final int LEGGINGS_PROTECTION = 6;

    // ==================== 稀有度分档（护腿） ====================
    public static final Rarity SMUGGLER_SHIN_LEGGINGS_RARITY = Rarity.UNCOMMON;
    public static final Rarity SMUGGLER_POUCH_LEGGINGS_RARITY = Rarity.UNCOMMON;
    public static final Rarity GRAZE_GUARD_LEGGINGS_RARITY = Rarity.EPIC;
    public static final Rarity STEALTH_SHIN_LEGGINGS_RARITY = Rarity.RARE;
    public static final Rarity CLEAR_LEDGER_LEGGINGS_RARITY = Rarity.RARE;

    // ==================== 护腿效果参数 ====================

    // ==================== 走私者之胫 (smuggler_shin_leggings) ====================
    public static final String SMUGGLER_SHIN_EFFECT_ID = "smuggler_loot_bonus";
    /** 额外掉落概率（普通） */
    public static final float SMUGGLER_SHIN_LOOT_BONUS_CHANCE = 0.20f;
    /** 额外掉落概率（Boss/核心资源） */
    public static final float SMUGGLER_SHIN_LOOT_BONUS_CHANCE_BOSS = 0.20f;
    /** 双倍掉落概率（普通） */
    public static final float SMUGGLER_SHIN_DOUBLE_LOOT_CHANCE = 0.10f;
    /** 双倍掉落概率（Boss/核心资源） */
    public static final float SMUGGLER_SHIN_DOUBLE_LOOT_CHANCE_BOSS = 0.10f;
    /** 冷却时间 (ticks) */
    public static final int SMUGGLER_SHIN_COOLDOWN = 800;

    // ==================== 走私者的暗袋 (smuggler_pouch_leggings) ====================
    public static final String SMUGGLER_POUCH_EFFECT_ID = "magnet_activate";
    /** 吸附半径 */
    public static final float SMUGGLER_POUCH_RADIUS = 6.0f;
    /** 吸附持续时间 (ticks) */
    public static final int SMUGGLER_POUCH_DURATION = 100;
    /** 扫描间隔 (ticks) */
    public static final int SMUGGLER_POUCH_SCAN_INTERVAL = 20;
    /** 牵引速度 */
    public static final float SMUGGLER_POUCH_PULL_SPEED = 0.3f;
    /** 牵引速度Y（向上） */
    public static final float SMUGGLER_POUCH_PULL_SPEED_Y = 0.1f;
    /** 冷却时间 (ticks) */
    public static final int SMUGGLER_POUCH_COOLDOWN = 700;

    // ==================== 擦身护胫 (graze_guard_leggings) ====================
    public static final String GRAZE_GUARD_EFFECT_ID = "graze_guard";
    /** 触发概率 */
    public static final float GRAZE_GUARD_TRIGGER_CHANCE = 0.18f;
    /** 减伤比例（减伤60%，即×0.40） */
    public static final float GRAZE_GUARD_REDUCTION = 0.60f;
    /** 冷却时间 (ticks) */
    public static final int GRAZE_GUARD_COOLDOWN = 240;

    // ==================== 潜行之胫 (stealth_shin_leggings) ====================
    public static final String STEALTH_SHIN_EFFECT_ID = "stealth_shin_charge";
    /** 充能间隔 (ticks, 45s) */
    public static final int STEALTH_SHIN_CHARGE_INTERVAL = 900;
    /** 最大层数 */
    public static final int STEALTH_SHIN_MAX_CHARGES = 2;
    /** 摔落减伤比例（减伤80%，即×0.20） */
    public static final float STEALTH_SHIN_FALL_REDUCTION = 0.80f;
    /** 最小摔落伤害门槛 (HP) */
    public static final float STEALTH_SHIN_MIN_FALL_DAMAGE = 3.0f;
    /** 检查间隔 (ticks) */
    public static final int STEALTH_SHIN_CHECK_INTERVAL = 20;

    // ==================== 清账步态 (clear_ledger_leggings) ====================
    public static final String CLEAR_LEDGER_EFFECT_ID = "clear_ledger_speed";
    /** 初始速度持续时间 (ticks, 3s) */
    public static final int CLEAR_LEDGER_INITIAL_DURATION = 60;
    /** 延长时间 (ticks, +1s) */
    public static final int CLEAR_LEDGER_EXTEND_DURATION = 20;
    /** 最大持续时间 (ticks, 6s) */
    public static final int CLEAR_LEDGER_MAX_DURATION = 120;
    /** 冷却时间 (ticks, 16s) */
    public static final int CLEAR_LEDGER_COOLDOWN = 320;
    /** 速度效果等级 (0 = I级) */
    public static final int CLEAR_LEDGER_SPEED_AMPLIFIER = 0;
}
