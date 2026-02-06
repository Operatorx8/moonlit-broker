package mod.test.mymodtest.armor;

/**
 * 靴子效果常量
 * 所有数值来自 docs/BOOTS_TASKS.md Task 0
 * 不要硬编码任何数值到逻辑类中
 */
public final class BootsEffectConstants {
    private BootsEffectConstants() {}

    // ==================== 通用 ====================
    /** 靴子扫描间隔 (ticks, 1s) */
    public static final int BOOT_SCAN_INTERVAL_TICKS = 20;

    // ==================== Boot1 - 无追索步履 (Untraceable Treads) ====================
    /** 脱战窗口 (ticks, 12s) — 12s 未攻击且未受伤才触发 */
    public static final int UNTRACEABLE_IDLE_WINDOW_TICKS = 240;
    /** 隐身持续时间 (ticks, 3s) */
    public static final int UNTRACEABLE_INVIS_TICKS = 60;
    /** 冷却时间 (ticks, 45s) */
    public static final int UNTRACEABLE_CD_TICKS = 900;

    // ==================== Boot2 - 边界行走 (Boundary Walker) ====================
    /** 跳跃提升等级 (0 = I级) */
    public static final int BOUNDARY_JUMP_LEVEL = 1;
    /** 效果刷新间隔 (ticks, 1.25s) — 滚动刷新 */
    public static final int BOUNDARY_REFRESH_TICKS = 25;

    // ==================== Boot3 - 幽灵步伐 (Ghost Step) ====================
    /** 战斗锁定窗口 (ticks, 8s) — 攻击后 8s 内视为战斗中 */
    public static final int GHOST_COMBAT_LOCK_TICKS = 160;
    /** 受击窗口 (ticks, 1s) — 在此窗口内累计受击次数 */
    public static final int GHOST_HIT_WINDOW_TICKS = 20;
    /** 受击次数阈值 — 窗口内被打 N 次触发应急幽灵 */
    public static final int GHOST_HIT_COUNT_THRESHOLD = 2;
    /** 应急幽灵持续时间 (ticks, 1s) */
    public static final int GHOST_BURST_TICKS = 20;
    /** 应急幽灵冷却 (ticks, 15s) */
    public static final int GHOST_BURST_CD_TICKS = 300;

    // ==================== Boot4 - 急行之靴 (Marching Boots) ====================
    /** 未命中窗口 (ticks, 8s) — 8s 未攻击才可进入急行 */
    public static final int MARCH_NO_HIT_TICKS = 160;
    /** 未受伤窗口 (ticks, 4s) — 4s 未受伤才可进入急行 */
    public static final int MARCH_NO_HURT_TICKS = 80;
    /** 速度等级 (1 = II级) */
    public static final int MARCH_SPEED_LEVEL = 2;
    /** 急行最大持续时间 (ticks, 15s) */
    public static final int MARCH_MAX_DURATION_TICKS = 300;
    /** 急行冷却 (ticks, 12s) */
    public static final int MARCH_CD_TICKS = 240;

    // ==================== Boot5 - 轻灵之靴 (Gossamer Boots) ====================
    /** 蛛网减速降低比例 (70%) */
    public static final double GOSSAMER_WEB_SLOW_REDUCE = 0.70;
    /** 缓慢降阶等级 (1 = 降一阶，即 II→I) */
    public static final int GOSSAMER_SLOWNESS_DOWNGRADE = 1;
    /** 效果刷新间隔 (ticks, 1.25s) */
    public static final int GOSSAMER_REFRESH_TICKS = 25;

    // ==================== 靴子基础属性 ====================
    /** 韧性 (所有靴子) */
    public static final float BOOTS_TOUGHNESS = 0.0f;
    /** 击退抗性 (所有靴子) */
    public static final float BOOTS_KNOCKBACK_RESISTANCE = 0.0f;

    // ==================== 靴子耐久 ====================
    public static final int UNTRACEABLE_TREADS_DURABILITY = 380;
    public static final int BOUNDARY_WALKER_DURABILITY = 380;
    public static final int GHOST_STEP_DURABILITY = 320;
    public static final int MARCHING_BOOTS_DURABILITY = 400;
    public static final int GOSSAMER_BOOTS_DURABILITY = 375;

    // ==================== 靴子护甲值 ====================
    public static final int UNTRACEABLE_TREADS_PROTECTION = 3;
    public static final int BOUNDARY_WALKER_PROTECTION = 2;
    public static final int GHOST_STEP_PROTECTION = 1;
    public static final int MARCHING_BOOTS_PROTECTION = 2;
    public static final int GOSSAMER_BOOTS_PROTECTION = 2;

    // ==================== 靴子稀有度 ====================
    public static final net.minecraft.util.Rarity UNTRACEABLE_TREADS_RARITY = net.minecraft.util.Rarity.EPIC;
    public static final net.minecraft.util.Rarity BOUNDARY_WALKER_RARITY = net.minecraft.util.Rarity.UNCOMMON;
    public static final net.minecraft.util.Rarity GHOST_STEP_RARITY = net.minecraft.util.Rarity.RARE;
    public static final net.minecraft.util.Rarity MARCHING_BOOTS_RARITY = net.minecraft.util.Rarity.UNCOMMON;
    public static final net.minecraft.util.Rarity GOSSAMER_BOOTS_RARITY = net.minecraft.util.Rarity.RARE;
}
