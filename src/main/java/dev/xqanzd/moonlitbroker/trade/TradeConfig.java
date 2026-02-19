package dev.xqanzd.moonlitbroker.trade;

import dev.xqanzd.moonlitbroker.armor.item.ArmorItems;
import dev.xqanzd.moonlitbroker.armor.transitional.TransitionalArmorItems;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.Item;
import net.minecraft.util.Rarity;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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

    // ========== Debug Trade Gate ==========
    // ========== Routine Night Invisibility ==========
    /** 黄昏后禁止日常隐身的恩惠窗口 (2 分钟 = 2400 ticks) */
    public static final long  MERCHANT_DUSK_NO_INVIS_TICKS = 2400L;
    /** 恩惠窗口结束后，每夜 roll 一次的隐身概率 (25% → 大概率不喝) */
    public static final float MERCHANT_NIGHT_INVIS_CHANCE  = 0.25f;

    /** 仅在 debug 时出现 Emerald->Diamond / Emerald->Netherite 等测试交易 */
    public static final boolean DEBUG_TRADES = MASTER_DEBUG;
    /** DEBUG only: 创造模式送别跳过全局冷却推进（仅 DEBUG_TRADES=true 时生效） */
    public static final boolean DEBUG_SENDOFF_BYPASS_GLOBAL_COOLDOWN = false;

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

    // ========== Coin 经济规则 ==========
    /** Bounty 发 Coin 的 per-player 冷却 (2 MC 日 = 48000 ticks)，按"尝试"冷却 */
    public static final long COIN_BOUNTY_CD_TICKS = 48000L;
    /** Bounty 发 Coin 的概率 (15%) */
    public static final float COIN_BOUNTY_CHANCE = 0.15f;
    /** Katana 替代路线：Silver Note 成本 */
    public static final int KATANA_ALT_SILVER_COST = 16;
    /** 供奉（右键交互）连点冷却 (20 ticks = 1 秒) */
    public static final int COIN_OFFER_CD_TICKS = 20;
    /** 高级结构箱 Coin 掉率：stronghold_library */
    public static final float CHEST_COIN_CHANCE_STRONGHOLD = 0.12f;
    /** 高级结构箱 Coin 掉率：ancient_city */
    public static final float CHEST_COIN_CHANCE_ANCIENT_CITY = 0.08f;
    /** 高级结构箱 Coin 掉率：trial_chambers */
    public static final float CHEST_COIN_CHANCE_TRIAL = 0.10f;

    // ===== Coin: exploration-friendly sources =====
    /** 首次完成悬赏保底 1 Coin 开关 */
    public static final boolean COIN_FIRST_BOUNTY_GUARANTEE = true;
    /** 探索结构箱 Coin 掉率：buried_treasure */
    public static final float CHEST_COIN_CHANCE_BURIED_TREASURE = 0.04f;
    /** 探索结构箱 Coin 掉率：shipwreck_treasure */
    public static final float CHEST_COIN_CHANCE_SHIPWRECK_TREASURE = 0.03f;
    /** 探索结构箱 Coin 掉率：desert_pyramid */
    public static final float CHEST_COIN_CHANCE_DESERT_PYRAMID = 0.02f;
    /** 探索结构箱 Coin 掉率：jungle_temple */
    public static final float CHEST_COIN_CHANCE_JUNGLE_TEMPLE = 0.02f;
    /** 探索结构箱 Coin 掉率：abandoned_mineshaft */
    public static final float CHEST_COIN_CHANCE_ABANDONED_MINESHAFT = 0.01f;

    // ===== Elite drop tuning (P0 minimal) =====
    /** elite 银票概率倍率（不改 cap 逻辑） */
    public static final float SILVER_ELITE_MULTIPLIER = 2.0f;
    /** elite 契约概率倍率（乘在原概率上） */
    public static final float BOUNTY_ELITE_CHANCE_MULTIPLIER = 3.0f;
    /** 是否启用 elite 掉落加成 */
    public static final boolean ENABLE_ELITE_DROP_BONUS = true;

    // ===== Bounty contract drop cooldown (prevents multi-drop on same tick) =====
    /** 契约掉落 per-player 冷却 (20 ticks = 1 秒) */
    public static final int BOUNTY_DROP_COOLDOWN_TICKS = 20;
    /** 失效契约回收返还 Silver Note 数量 */
    public static final int BOUNTY_EXPIRED_REFUND_SILVER = 1;
    /** 契约 NBT schema 版本号（未来迁移用） */
    public static final int BOUNTY_SCHEMA_VERSION = 1;
    /** DEBUG only: 强制契约 100% 掉落（需同时 MASTER_DEBUG + TRADE_DEBUG） */
    public static final boolean FORCE_BOUNTY_DROP = false;

    // ===== Bounty reward scaling (coin chance scales with required kills) =====
    /** 总开关：是否启用悬赏奖励缩放 */
    public static final boolean BOUNTY_ENABLE_REWARD_SCALING = true;
    /** 每 5 击杀额外银票数（默认 0，稳后再开） */
    public static final int BOUNTY_REWARD_SILVER_PER_5_KILLS = 0;
    /** Normal 悬赏：coin 概率加成上限 */
    public static final float BOUNTY_COIN_BONUS_MAX_NORMAL = 0.05f;
    /** Elite 悬赏：coin 概率加成上限 */
    public static final float BOUNTY_COIN_BONUS_MAX_ELITE = 0.10f;
    /** Normal 悬赏：每多 1 required 的 coin 概率增量 (0.05 / 15) */
    public static final float BOUNTY_COIN_BONUS_PER_REQUIRED_NORMAL = BOUNTY_COIN_BONUS_MAX_NORMAL / 15f;
    /** Elite 悬赏：每多 1 required 的 coin 概率增量 (0.10 / 10) */
    public static final float BOUNTY_COIN_BONUS_PER_REQUIRED_ELITE = BOUNTY_COIN_BONUS_MAX_ELITE / 10f;

    // ========== 冷却时间 ==========
    /** 页面操作冷却 (0.5秒 = 10 ticks) */
    public static final int PAGE_ACTION_COOLDOWN_TICKS = 10;
    /** 隐藏页刷新冷却 (5秒 = 100 ticks) */
    public static final int SECRET_REFRESH_COOLDOWN_TICKS = 100;

    // ===== Bootstrap Phase (totalSpawnedCount == 0 && !bootstrapComplete) =====
    public static final long   BOOTSTRAP_CHECK_INTERVAL = 1200L;   // 1 min
    public static final float  BOOTSTRAP_SPAWN_CHANCE   = 0.20f;   // 20%
    public static final boolean BOOTSTRAP_REQUIRE_RAIN  = false;

    // ===== Normal Phase =====
    public static final long   NORMAL_CHECK_INTERVAL    = 2400L;   // 2 min
    public static final float  NORMAL_SPAWN_CHANCE      = 0.06f;   // 6%
    public static final float  RAIN_MULTIPLIER          = 3.0f;    // 雨天 ×3 = 18%
    public static final boolean NORMAL_REQUIRE_RAIN     = false;   // 雨不是门槛
    public static final int    VILLAGE_SEARCH_RADIUS    = 16;      // chunks (256 blocks)

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
    /** 召唤成功后的全局冷却（与自然生成全局冷却对齐） */
    public static final long SUMMON_GLOBAL_COOLDOWN_TICKS = 18000L;
    /** 仪式召唤后禁止隐身的可见窗口（~一整晚，够玩家找人） */
    public static final long SUMMON_RITUAL_REVEAL_TICKS = 14000L;

    // ========== Reclaim（补发/契约迁移） ==========
    /** Reclaim 冷却时间 (3 MC 天 = 72000 ticks) */
    public static final long RECLAIM_CD_TICKS = 24000L * 3;
    /** Reclaim 第一输入：下界合金锭数量 */
    public static final int RECLAIM_COST_NETHERITE = 1;
    /** Reclaim 第二输入：钻石数量 */
    public static final int RECLAIM_COST_DIAMONDS = 16;

    // ========== Contract Gate Scope ==========
    /** true: 仅对 5 把神器应用契约 gate；false: 对所有可识别 katanaId 应用 */
    public static final boolean CONTRACT_ENFORCE_ONLY_MYTHIC = true;
    /** true: 创造模式跳过契约 gate（用于测试/排障） */
    public static final boolean CONTRACT_ALLOW_CREATIVE_BYPASS = true;

    // ========== Dormant Hint（契约失效提示） ==========
    /** 是否在 dormant katana 被 gate 拦截时显示 actionbar 提示 */
    public static final boolean DORMANT_SHOW_ACTIONBAR_HINT = true;
    /** 同一玩家提示节流 (60 ticks = 3s) */
    public static final int DORMANT_HINT_COOLDOWN_TICKS = 60;

    // ========== Bounty Progress Hint ==========
    /** 悬赏进度 actionbar 提示冷却 (30 ticks = 1.5s) */
    public static final int BOUNTY_PROGRESS_HINT_CD_TICKS = 30;

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
    /** Page2 稳定来源：Silver Note -> Trade Scroll 成本 */
    public static final int PAGE2_SCROLL_SOURCE_SILVER_COST = 10;
    /** Page2 稳定来源：Silver Note -> Trade Scroll 最大次数 */
    public static final int PAGE2_SCROLL_SOURCE_MAX_USES = 4;
    /** Page2 稳定来源：Merchant Mark 的 Silver 成本 */
    public static final int PAGE2_TICKET_SOURCE_SILVER_COST = 14;
    /** Page2 稳定来源：Merchant Mark 的 Emerald 附加税 */
    public static final int PAGE2_TICKET_SOURCE_EMERALD_TAX = 2;
    /** Page2 稳定来源：Silver Note -> Merchant Mark 最大次数 */
    public static final int PAGE2_TICKET_SOURCE_MAX_USES = 2;

    // ========== Arcane Page Gate Costs ==========
    public static final int ARCANE_SCROLL_COST = 1;
    public static final int ARCANE_TICKET_COST = 1;
    public static final int ARCANE_P3_01_SILVER_COST = 8;
    public static final int ARCANE_P3_02_SILVER_COST = 10;
    public static final int ARCANE_P3_03_SILVER_COST = 16;
    public static final int ARCANE_P3_04_SILVER_COST = 6;
    public static final int ARCANE_P3_05_SILVER_COST = 12;
    public static final int ARCANE_P3_06_SILVER_COST = 6;
    public static final int ARCANE_P3_07_SILVER_COST = 8;
    public static final int ARCANE_P3_08_SILVER_COST = 24;

    // ========== Shelf Spark / Visible Top Slots ==========
    public static final int VISIBLE_TOP_SLOTS = 7;
    public static final int SPARK_SLOTS_PER_PAGE = 1;
    public static final int SPARK_COMMON_WEIGHT = 0;
    public static final int SPARK_UNCOMMON_WEIGHT = 95;
    public static final int SPARK_RARE_WEIGHT = 5;

    // ========== 卷轴等级 ==========
    public static final String GRADE_NORMAL = "NORMAL";
    public static final String GRADE_SEALED = "SEALED";

    // ========== Variant Anchors + Light RNG ==========

    /**
     * A) Normal 页：每变体固定 1 件 A 特效过渡装备（按主题映射）。
     * key = MerchantVariant.name()  (STANDARD / ARID / COLD / WET / EXOTIC)
     * 延迟初始化，因为 Item 静态字段在 mod init 后才可用。
     */
    private static volatile Map<String, Item> VARIANT_A_ANCHOR_CACHE;

    public static Map<String, Item> variantAAnchor() {
        if (VARIANT_A_ANCHOR_CACHE == null) {
            VARIANT_A_ANCHOR_CACHE = Map.of(
                    "STANDARD", TransitionalArmorItems.CARGO_PANTS,       // Moonglow → Cargo Pants
                    "ARID",     TransitionalArmorItems.REACTIVE_BUG_PLATE,// Regret  → Reactive Bug Plate
                    "COLD",     TransitionalArmorItems.SANCTIFIED_HOOD,   // Eclipse → Sanctified Hood
                    "WET",      TransitionalArmorItems.CUSHION_HIKING_BOOTS,// Oblivion → Cushion Hiking Boots
                    "EXOTIC",   TransitionalArmorItems.CARGO_PANTS        // Nmap    → Cargo Pants (允许重复)
            );
        }
        return VARIANT_A_ANCHOR_CACHE;
    }

    /**
     * B) Arcane 页：每变体固定 1 件 B 招牌锚点（解锁后必出）。
     */
    private static volatile Map<String, Item> VARIANT_B_ANCHOR_CACHE;

    public static Map<String, Item> variantBAnchor() {
        if (VARIANT_B_ANCHOR_CACHE == null) {
            VARIANT_B_ANCHOR_CACHE = Map.of(
                    "STANDARD", ArmorItems.SENTINEL_HELMET,
                    "ARID",     ArmorItems.BLOOD_PACT_CHESTPLATE,
                    "COLD",     ArmorItems.RETRACER_ORNAMENT_HELMET,
                    "WET",      ArmorItems.UNTRACEABLE_TREADS_BOOTS,
                    "EXOTIC",   ArmorItems.SMUGGLER_POUCH_LEGGINGS
            );
        }
        return VARIANT_B_ANCHOR_CACHE;
    }

    /**
     * C) Arcane 随机层候选集合（每变体抽 1）。
     */
    private static volatile Map<String, List<Item>> VARIANT_B_RANDOM_CACHE;

    public static Map<String, List<Item>> variantBRandomPool() {
        if (VARIANT_B_RANDOM_CACHE == null) {
            VARIANT_B_RANDOM_CACHE = Map.of(
                    "STANDARD", List.of(
                            ArmorItems.RELIC_CIRCLET_HELMET,
                            ArmorItems.BOUNDARY_WALKER_BOOTS,
                            ArmorItems.GOSSAMER_BOOTS,
                            ArmorItems.WINDBREAKER_CHESTPLATE),
                    "ARID", List.of(
                            ArmorItems.VOID_DEVOURER_CHESTPLATE,
                            ArmorItems.EXILE_MASK_HELMET,
                            ArmorItems.CLEAR_LEDGER_LEGGINGS,
                            ArmorItems.MARCHING_BOOTS),
                    "COLD", List.of(
                            ArmorItems.SILENT_OATH_HELMET,
                            ArmorItems.GHOST_GOD_CHESTPLATE,
                            ArmorItems.GRAZE_GUARD_LEGGINGS,
                            ArmorItems.GHOST_STEP_BOOTS),
                    "WET", List.of(
                            ArmorItems.STEALTH_SHIN_LEGGINGS,
                            ArmorItems.GHOST_STEP_BOOTS,
                            ArmorItems.BOUNDARY_WALKER_BOOTS,
                            ArmorItems.GOSSAMER_BOOTS),
                    "EXOTIC", List.of(
                            ArmorItems.SMUGGLER_SHIN_LEGGINGS,
                            ArmorItems.OLD_MARKET_CHESTPLATE,
                            ArmorItems.WINDBREAKER_CHESTPLATE,
                            ArmorItems.MARCHING_BOOTS)
            );
        }
        return VARIANT_B_RANDOM_CACHE;
    }

    /**
     * Random B 的卷轴引导子池：当输入包含 Trade Scroll 时使用。
     */
    private static volatile Map<String, List<Item>> VARIANT_B_SCROLL_GUIDED_CACHE;

    public static Map<String, List<Item>> variantBScrollGuidedPool() {
        if (VARIANT_B_SCROLL_GUIDED_CACHE == null) {
            VARIANT_B_SCROLL_GUIDED_CACHE = Map.of(
                    "STANDARD", List.of(
                            ArmorItems.RELIC_CIRCLET_HELMET,
                            ArmorItems.WINDBREAKER_CHESTPLATE),
                    "ARID", List.of(
                            ArmorItems.VOID_DEVOURER_CHESTPLATE,
                            ArmorItems.CLEAR_LEDGER_LEGGINGS),
                    "COLD", List.of(
                            ArmorItems.SILENT_OATH_HELMET,
                            ArmorItems.GHOST_GOD_CHESTPLATE),
                    "WET", List.of(
                            ArmorItems.STEALTH_SHIN_LEGGINGS,
                            ArmorItems.BOUNDARY_WALKER_BOOTS),
                    "EXOTIC", List.of(
                            ArmorItems.OLD_MARKET_CHESTPLATE,
                            ArmorItems.SMUGGLER_SHIN_LEGGINGS)
            );
        }
        return VARIANT_B_SCROLL_GUIDED_CACHE;
    }

    // ========== Anti-Repeat: (playerUuid, variantKey) -> lastRandomBItemId ==========
    // 内存级，不持久化；商人消失/服务器重启后自然清零。
    private static final ConcurrentHashMap<String, String> LAST_RANDOM_B = new ConcurrentHashMap<>();

    private static String antiRepeatKey(UUID playerUuid, String variantKey) {
        return playerUuid.toString() + "|" + variantKey;
    }

    public static String getLastRandomB(UUID playerUuid, String variantKey) {
        return LAST_RANDOM_B.get(antiRepeatKey(playerUuid, variantKey));
    }

    public static void setLastRandomB(UUID playerUuid, String variantKey, String itemId) {
        LAST_RANDOM_B.put(antiRepeatKey(playerUuid, variantKey), itemId);
    }

    /** B 装备交易价格：Silver Note 数量 */
    public static final int B_ARMOR_SILVER_COST = 16;
    /** B 招牌锚点门槛：Merchant Mark 数量 */
    public static final int B_ARMOR_TICKET_COST = 1;
    /** B 装备交易价格：Emerald 附加税（保留兼容，当前未使用） */
    public static final int B_ARMOR_EMERALD_TAX = 8;
    /** B 装备交易最大购买次数 */
    public static final int B_ARMOR_MAX_USES = 1;
    /** Random B 默认门槛：Merchant Mark 数量 */
    public static final int B_RANDOM_TICKET_COST = 1;
    /** Random B 卷轴引导门槛：Trade Scroll 数量 */
    public static final int B_RANDOM_SCROLL_COST = 1;
    /** Random B 统一货币成本：Silver Note 数量 */
    public static final int B_RANDOM_SILVER_COST = 12;

    /** A 特效过渡装备交易价格：Silver Note 数量 */
    public static final int A_ANCHOR_SILVER_COST = 10;
    /** A 特效过渡装备交易价格：Emerald 附加税 */
    public static final int A_ANCHOR_EMERALD_TAX = 4;
    /** A 特效过渡装备交易最大购买次数 */
    public static final int A_ANCHOR_MAX_USES = 2;

    /**
     * 判断 Item 是否为 EPIC 稀有度
     */
    public static boolean isEpic(Item item) {
        if (item == null) return false;
        return item.getDefaultStack().getRarity() == Rarity.EPIC;
    }
}
