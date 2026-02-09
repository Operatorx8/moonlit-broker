package mod.test.mymodtest.trade;

import net.fabricmc.loader.api.FabricLoader;

/**
 * 交易系统配置常量
 * 集中管理所有可调参数
 */
public final class TradeConfig {
    private TradeConfig() {}

    // ========== 调试开关 ==========
    // 总开关：开发环境自动 true；发布环境默认 false
    public static final boolean MASTER_DEBUG =
            FabricLoader.getInstance().isDevelopmentEnvironment()
            || Boolean.getBoolean("mm.debug"); // 允许 -Dmm.debug=true 强制开

    // 现有验证项开关全部从这里派生（默认跟随总开关）
    public static final boolean TRADE_DEBUG = MASTER_DEBUG;
    public static final boolean AI_DEBUG = MASTER_DEBUG;
    public static final boolean SPAWN_DEBUG = MASTER_DEBUG;
    public static final boolean SCROLL_DEBUG = MASTER_DEBUG;

    // ========== 门槛阈值 ==========
    /** 进入隐藏页所需声望 */
    public static final int SECRET_REP_THRESHOLD = 15;
    /** 进入隐藏页所需卷轴最小次数 */
    public static final int SECRET_SCROLL_USES_MIN = 2;

    // ========== 卷轴消耗 ==========
    /** 打开普通页消耗 */
    public static final int COST_OPEN_NORMAL = 1;
    /** 切换隐藏页消耗 */
    public static final int COST_SWITCH_SECRET = 2;
    /** 刷新当前页消耗 */
    public static final int COST_REFRESH = 1;

    // ========== 卷轴初始值 ==========
    /** 普通卷轴初始次数 */
    public static final int SCROLL_USES_NORMAL = 3;
    /** 封印卷轴初始次数 */
    public static final int SCROLL_USES_SEALED = 5;

    // ========== 掉落概率 ==========
    /** 宝箱卷轴掉率 (15%) */
    public static final float CHEST_SCROLL_CHANCE = 0.15f;
    /** 怪物卷轴掉率 (0.5%) */
    public static final float MOB_SCROLL_CHANCE = 0.005f;

    // ========== 银币经济 ==========
    /** 每窗口期最大银币掉落 */
    public static final int SILVER_MOB_DROP_CAP = 10;
    /** 银币限制窗口 (10分钟 = 12000 ticks) */
    public static final long SILVER_CAP_WINDOW_TICKS = 12000L;
    /** 悬赏银币奖励 */
    public static final int BOUNTY_SILVER_REWARD = 3;

    // ========== 冷却时间 ==========
    /** 页面操作冷却 (0.5秒 = 10 ticks) */
    public static final int PAGE_ACTION_COOLDOWN_TICKS = 10;
    /** 隐藏页刷新冷却 (5秒 = 100 ticks) */
    public static final int SECRET_REFRESH_COOLDOWN_TICKS = 100;

    // ========== NBT 键名 ==========
    public static final String NBT_SCROLL_USES = "Uses";
    public static final String NBT_SCROLL_GRADE = "Grade";
    public static final String NBT_MARK_OWNER_UUID = "OwnerUUID";
    public static final String NBT_SECRET_SOLD = "SecretSold";
    public static final String NBT_SECRET_KATANA_ID = "SecretKatanaId";

    // ========== 卷轴等级 ==========
    public static final String GRADE_NORMAL = "NORMAL";
    public static final String GRADE_SEALED = "SEALED";
}
