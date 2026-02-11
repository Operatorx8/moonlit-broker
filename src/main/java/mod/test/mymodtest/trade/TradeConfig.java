package mod.test.mymodtest.trade;

import net.fabricmc.loader.api.FabricLoader;

/**
 * 交易系统配置常量
 * 集中管理所有可调参数
 */
public final class TradeConfig {
    private TradeConfig() {
    }

    // ========== 调试开关 ==========
    // 总开关：开发环境自动 true；发布环境默认 false
    public static final boolean MASTER_DEBUG = FabricLoader.getInstance().isDevelopmentEnvironment()
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

    // ========== 商人召唤 ==========
    /** Bell 召唤消耗银币数量 */
    public static final int SUMMON_SILVER_NOTE_COST = 3;
    /** 每玩家召唤冷却 (40分钟 = 48000 ticks) */
    public static final long SUMMON_COOLDOWN_TICKS = 48000L;
    /** 黄昏窗口开始 (dayTime % 24000) */
    public static final long SUMMON_DUSK_START_TICK = 12000L;
    /** 黄昏窗口结束 (dayTime % 24000) */
    public static final long SUMMON_DUSK_END_TICK = 13000L;
    /** 已到黄昏/夜晚时的延迟生成 */
    public static final long SUMMON_AFTER_DUSK_DELAY_TICKS = 600L;
    /** 召唤生成位置搜索半径（以 Bell 为中心） */
    public static final int SUMMON_SPAWN_RANGE = 16;
    /** 召唤生成位置最大尝试次数 */
    public static final int SUMMON_SPAWN_ATTEMPTS = 20;
    /** 召唤失败后的重试延迟 */
    public static final long SUMMON_RETRY_DELAY_TICKS = 600L;
    /** 召唤商人的预期生命周期（用于 active 保险机制） */
    public static final long SUMMON_EXPECTED_LIFETIME_TICKS = 120000L;

    // ========== NBT 键名 ==========
    public static final String NBT_SCROLL_USES = "Uses";
    public static final String NBT_SCROLL_GRADE = "Grade";
    public static final String NBT_MARK_OWNER_UUID = "OwnerUUID";
    public static final String NBT_SECRET_SOLD = "SecretSold";
    public static final String NBT_SECRET_KATANA_ID = "SecretKatanaId";

    // ========== 解封成本 ==========
    /** Unseal 交易所需 Sigil 数量 */
    public static final int UNSEAL_SIGIL_COST = 2;

    // ========== BASE 页卷轴交易 ==========
    /** Silver Note → Trade Scroll 成本 */
    public static final int SILVER_TO_SCROLL_COST = 8;
    /** Silver Note → Trade Scroll 最大购买次数 */
    public static final int SILVER_TO_SCROLL_MAX_USES = 6;

    // ========== 卷轴等级 ==========
    public static final String GRADE_NORMAL = "NORMAL";
    public static final String GRADE_SEALED = "SEALED";
}
