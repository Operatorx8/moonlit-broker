package dev.xqanzd.moonlitbroker.katana.effect;

/**
 * 残念之刃配置 - 处刑型太刀
 * 特点：百分比伤害，护甲穿透，不能击杀，对Boss效果减弱
 */
public class LifeCutConfig {
    // === 调试开关 ===
    public static boolean DEBUG = true;

    // === 触发条件 ===
    public static final float TRIGGER_CHANCE = 0.30f;           // 30% 概率
    public static final float MIN_HEALTH_TO_TRIGGER = 10.0f;    // 血量 > 10 才触发
    public static final int LIFECUT_TRIGGER_CD_TICKS = 60;      // 每目标冷却 60 ticks (3秒)

    // === 伤害计算 ===
    public static final float HEALTH_CUT_RATIO = 0.30f;         // 削减 30% 当前血量

    // === 护甲穿透 ===
    // 穿透值表示无视多少比例的护甲，剩余护甲参与减伤计算
    // effectiveArmor = armor * (1 - penetration)
    public static final float ARMOR_PENETRATION_NORMAL = 0.35f; // 普通目标：无视 35% 护甲
    public static final float ARMOR_PENETRATION_BOSS = 0.35f;   // Boss：无视 35% 护甲

    // === Boss 处理 ===
    public static final boolean ALLOW_BOSS = true;              // 允许对 Boss 生效
    public static final float BOSS_EFFECT_MULTIPLIER = 0.333f;  // Boss 效果只有 1/3

    // === 安全限制 ===
    public static final boolean ONLY_UNDEAD = false;            // 安全阀：允许任意 LivingEntity
    public static final boolean CANNOT_KILL = true;             // 不能通过特效击杀
    public static final float MIN_HEALTH_AFTER_CUT = 1.0f;      // 至少保留 1 血
}
