package dev.xqanzd.moonlitbroker.entity;

import dev.xqanzd.moonlitbroker.entity.ai.DrinkPotionGoal;
import dev.xqanzd.moonlitbroker.entity.ai.EnhancedFleeGoal;
import dev.xqanzd.moonlitbroker.entity.ai.SeekLightGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.HoldInHandsGoal;
import net.minecraft.entity.ai.goal.PrioritizedGoal;
import dev.xqanzd.moonlitbroker.trade.item.BountyContractItem;
import dev.xqanzd.moonlitbroker.trade.item.TradeScrollItem;
import dev.xqanzd.moonlitbroker.armor.transitional.TransitionalArmorItems;
import dev.xqanzd.moonlitbroker.katana.item.KatanaItems;
import dev.xqanzd.moonlitbroker.weapon.transitional.item.TransitionalWeaponItems;
import dev.xqanzd.moonlitbroker.registry.ModBlocks;
import dev.xqanzd.moonlitbroker.registry.ModEntities;
import dev.xqanzd.moonlitbroker.registry.ModItems;
import dev.xqanzd.moonlitbroker.trade.KatanaIdUtil;
import dev.xqanzd.moonlitbroker.trade.TradeConfig;
import dev.xqanzd.moonlitbroker.util.KatanaContractUtil;
import dev.xqanzd.moonlitbroker.world.KatanaOwnershipState;
import dev.xqanzd.moonlitbroker.world.MerchantSpawnerState;
import dev.xqanzd.moonlitbroker.world.MerchantUnlockState;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.WanderingTraderEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.stat.Stats;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;
import net.minecraft.village.TradedItem;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public class MysteriousMerchantEntity extends WanderingTraderEntity {
    private static final Logger LOGGER = LoggerFactory.getLogger(MysteriousMerchantEntity.class);

    public enum OfferBuildSource {
        OPEN_NORMAL,
        REFRESH_NORMAL,
        OPEN_SECRET,
        REFRESH_SECRET,
        ROLLBACK_NO_CHANGE,
        ROLLBACK_CONSUME_FAIL,
        INTERACT_PREOPEN,
        UNKNOWN;

        public boolean isRefreshFlow() {
            return this == REFRESH_NORMAL
                    || this == REFRESH_SECRET
                    || this == ROLLBACK_NO_CHANGE
                    || this == ROLLBACK_CONSUME_FAIL;
        }
    }

    // ========== 调试开关 ==========
    /** 发布版默认关闭；开启后启用 AI 行为调试日志 */
    public static final boolean DEBUG_AI = false;
    /** 发布版默认关闭；开启后使用更短的 despawn 时间（用于测试） */
    public static final boolean DEBUG_DESPAWN = false;
    /** 发布版默认关闭；仅用于验证滚动条可滚动（额外注入测试交易） */
    private static final boolean DEBUG_SCROLL_INJECT = false;
    /** 发布版默认关闭；开启后在 REFRESH_NORMAL 时追加 10 条 debug trade，验证 refresh 确实重建 offers */
    private static final boolean DEBUG_REFRESH_INJECT = false;
    /** 发布版默认关闭；开启后启用商人变体系统调试日志 */
    public static final boolean DEBUG_VARIANT = false;

    // ========== Phase 8: 商人变体系统 ==========

    private static final class WeightedKatanaEntry {
        private final String katanaType;
        private final int weight;

        private WeightedKatanaEntry(String katanaType, int weight) {
            this.katanaType = katanaType;
            this.weight = weight;
        }
    }

    private static final class SecretRollResult {
        private final String chosenType;
        private final int roll;

        private SecretRollResult(String chosenType, int roll) {
            this.chosenType = chosenType;
            this.roll = roll;
        }
    }

    private record OfferUsageState(int uses, int demandBonus, int specialPrice) {
    }

    private enum SparkTier {
        UNCOMMON,
        RARE
    }

    /**
     * 商人变体配置：EntityType -> {typeKey, weightedPool, namePool}
     * 默认权重：本气候 70%，其余按 10/10/8/2 乱入。
     */
    public enum MerchantVariant {
        STANDARD("standard", new WeightedKatanaEntry[] {
                new WeightedKatanaEntry("moonglow", 70),
                new WeightedKatanaEntry("regret", 10),
                new WeightedKatanaEntry("eclipse", 10),
                new WeightedKatanaEntry("oblivion", 8),
                new WeightedKatanaEntry("nmap", 2)
        }, new String[] {
                "Moonlit Broker", "Dusk Wayfarer", "Stardust Trader", "Dawn Peddler", "Mistbound Merchant",
                "Windrunner Luo", "Night Trader Ying", "Wandering Arlen"
        }),
        ARID("arid", new WeightedKatanaEntry[] {
                new WeightedKatanaEntry("regret", 70),
                new WeightedKatanaEntry("moonglow", 10),
                new WeightedKatanaEntry("eclipse", 10),
                new WeightedKatanaEntry("oblivion", 8),
                new WeightedKatanaEntry("nmap", 2)
        }, new String[] {
                "Sunscorch Broker", "Redwind Wayfarer", "Dunewalker Trader", "Heatwave Merchant", "Sandstrider",
                "Flare Runner Yan", "Desert Trader Sha", "Wandering Hassan"
        }),
        COLD("cold", new WeightedKatanaEntry[] {
                new WeightedKatanaEntry("eclipse", 70),
                new WeightedKatanaEntry("moonglow", 10),
                new WeightedKatanaEntry("regret", 10),
                new WeightedKatanaEntry("oblivion", 8),
                new WeightedKatanaEntry("nmap", 2)
        }, new String[] {
                "Frostfield Broker", "Snowbound Wayfarer", "Aurora Trader", "Northwind Merchant", "Tundra Walker",
                "Ice Runner Shuang", "Snowland Trader Rin", "Wandering Yuri"
        }),
        WET("wet", new WeightedKatanaEntry[] {
                new WeightedKatanaEntry("oblivion", 70),
                new WeightedKatanaEntry("moonglow", 10),
                new WeightedKatanaEntry("regret", 10),
                new WeightedKatanaEntry("eclipse", 8),
                new WeightedKatanaEntry("nmap", 2)
        }, new String[] {
                "Marsh Broker", "Rainforest Wayfarer", "Mossbound Trader", "Mistwater Merchant", "Swamp Walker",
                "Tide Runner Lan", "Bog Trader Wa", "Wandering Moss"
        }),
        EXOTIC("exotic", new WeightedKatanaEntry[] {
                new WeightedKatanaEntry("nmap", 70),
                new WeightedKatanaEntry("moonglow", 10),
                new WeightedKatanaEntry("regret", 10),
                new WeightedKatanaEntry("eclipse", 8),
                new WeightedKatanaEntry("oblivion", 2)
        }, new String[] {
                "Jungle Broker", "Exotic Wayfarer", "Sanctum Trader", "Wildland Merchant", "Canopy Walker",
                "Vine Runner Teng", "Canopy Trader Bao", "Wandering Tarzan"
        });

        public final String typeKey;
        public final WeightedKatanaEntry[] weightedKatanaPool;
        public final String[] namePool;

        MerchantVariant(String typeKey, WeightedKatanaEntry[] weightedKatanaPool, String[] namePool) {
            this.typeKey = typeKey;
            this.weightedKatanaPool = weightedKatanaPool;
            this.namePool = namePool;
        }

    }

    private static final Map<String, String> LEGACY_MERCHANT_NAME_MIGRATION = Map.ofEntries(
            Map.entry("月影行商", "Moonlit Broker"),
            Map.entry("暮色旅人", "Dusk Wayfarer"),
            Map.entry("星尘商贩", "Stardust Trader"),
            Map.entry("晨曦行者", "Dawn Peddler"),
            Map.entry("雾隐商人", "Mistbound Merchant"),
            Map.entry("风行者·洛", "Windrunner Luo"),
            Map.entry("夜行商·影", "Night Trader Ying"),
            Map.entry("流浪的阿尔", "Wandering Arlen"),
            Map.entry("沙漠行商", "Sunscorch Broker"),
            Map.entry("赤风旅人", "Redwind Wayfarer"),
            Map.entry("灼日商贩", "Dunewalker Trader"),
            Map.entry("黄沙行者", "Heatwave Merchant"),
            Map.entry("热浪商人", "Sandstrider"),
            Map.entry("沙行者·炎", "Flare Runner Yan"),
            Map.entry("荒漠商·砂", "Desert Trader Sha"),
            Map.entry("流浪的哈桑", "Wandering Hassan"),
            Map.entry("冰原行商", "Frostfield Broker"),
            Map.entry("霜雪旅人", "Snowbound Wayfarer"),
            Map.entry("极光商贩", "Aurora Trader"),
            Map.entry("寒风行者", "Northwind Merchant"),
            Map.entry("冻土商人", "Tundra Walker"),
            Map.entry("冰行者·霜", "Ice Runner Shuang"),
            Map.entry("雪域商·凛", "Snowland Trader Rin"),
            Map.entry("流浪的尤里", "Wandering Yuri"),
            Map.entry("沼泽行商", "Marsh Broker"),
            Map.entry("雨林旅人", "Rainforest Wayfarer"),
            Map.entry("苔藓商贩", "Mossbound Trader"),
            Map.entry("潮湿行者", "Mistwater Merchant"),
            Map.entry("迷雾商人", "Swamp Walker"),
            Map.entry("水行者·澜", "Tide Runner Lan"),
            Map.entry("沼地商·蛙", "Bog Trader Wa"),
            Map.entry("流浪的莫斯", "Wandering Moss"),
            Map.entry("丛林行商", "Jungle Broker"),
            Map.entry("异域旅人", "Exotic Wayfarer"),
            Map.entry("秘境商贩", "Sanctum Trader"),
            Map.entry("野性行者", "Wildland Merchant"),
            Map.entry("奇珍商人", "Canopy Walker"),
            Map.entry("林行者·藤", "Vine Runner Teng"),
            Map.entry("密林商·豹", "Canopy Trader Bao"),
            Map.entry("流浪的塔赞", "Wandering Tarzan"));

    /**
     * 根据 EntityType 推导商人变体。
     */
    public static MerchantVariant variantOf(EntityType<?> entityType) {
        if (entityType == ModEntities.MYSTERIOUS_MERCHANT_ARID)
            return MerchantVariant.ARID;
        if (entityType == ModEntities.MYSTERIOUS_MERCHANT_COLD)
            return MerchantVariant.COLD;
        if (entityType == ModEntities.MYSTERIOUS_MERCHANT_WET)
            return MerchantVariant.WET;
        if (entityType == ModEntities.MYSTERIOUS_MERCHANT_EXOTIC)
            return MerchantVariant.EXOTIC;
        return MerchantVariant.STANDARD;
    }

    public String getVariantKey() {
        return variantOf(this.getType()).name();
    }

    /** Base 交易闭环防护：禁止双向货币互兑形成永动机 */
    private static final Set<Item> LOOP_GUARD_CURRENCIES = Set.of(
            Items.EMERALD,
            Items.DIAMOND,
            Items.GOLD_INGOT);
    /** 发布版禁止直接产出的资源（debug 交易除外）。 */
    private static final Set<Item> RELEASE_FORBIDDEN_OUTPUTS = Set.of(
            ModItems.MYSTERIOUS_COIN,
            Items.DIAMOND,
            Items.DIAMOND_BLOCK,
            Items.DIAMOND_SWORD,
            Items.DIAMOND_PICKAXE,
            Items.DIAMOND_AXE,
            Items.DIAMOND_SHOVEL,
            Items.DIAMOND_HOE,
            Items.DIAMOND_HELMET,
            Items.DIAMOND_CHESTPLATE,
            Items.DIAMOND_LEGGINGS,
            Items.DIAMOND_BOOTS,
            Items.DIAMOND_HORSE_ARMOR,
            Items.NETHERITE_INGOT,
            Items.NETHERITE_BLOCK,
            Items.NETHERITE_SWORD,
            Items.NETHERITE_PICKAXE,
            Items.NETHERITE_AXE,
            Items.NETHERITE_SHOVEL,
            Items.NETHERITE_HOE,
            Items.NETHERITE_HELMET,
            Items.NETHERITE_CHESTPLATE,
            Items.NETHERITE_LEGGINGS,
            Items.NETHERITE_BOOTS,
            Items.NETHERITE_SCRAP,
            Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE);
    /** 去重默认按输出 item；仅白名单物品按 item + NBT 分流，避免误杀不同内容。 */
    private static final Set<Item> NBT_SENSITIVE_DEDUP_OUTPUTS = Set.of(
            Items.ENCHANTED_BOOK,
            Items.POTION,
            Items.SPLASH_POTION,
            Items.LINGERING_POTION,
            Items.TIPPED_ARROW,
            Items.WRITTEN_BOOK,
            Items.FIREWORK_ROCKET,
            ModItems.TRADE_SCROLL,
            ModItems.SIGIL);
    /** 页面成本审计：仅统计这些门槛货币。 */
    private static final Set<Item> GATE_COST_ITEMS = Set.of(
            Items.EMERALD,
            ModItems.SILVER_NOTE,
            ModItems.TRADE_SCROLL,
            ModItems.MERCHANT_MARK);
    /** Arcane 基础奖励键（用于一次性领取标记）。 */
    private static final String ARCANE_REWARD_P3_01 = "p3_01_xp_bottles";
    private static final String ARCANE_REWARD_P3_02 = "p3_02_ender_pearls";
    private static final String ARCANE_REWARD_P3_03 = "p3_03_totem";
    private static final String ARCANE_REWARD_P3_04 = "p3_04_golden_apple";
    private static final String ARCANE_REWARD_P3_05 = "p3_05_sacrifice";
    private static final String ARCANE_REWARD_P3_06 = "p3_06_silver_notes";
    private static final String ARCANE_REWARD_P3_07 = "p3_07_blaze_powder";
    private static final String ARCANE_REWARD_P3_08 = "p3_08_ancient_debris";

    // ========== Phase 3: AI 行为常量 ==========
    /** 基础移动速度 */
    public static final double BASE_MOVEMENT_SPEED = 0.5;

    // ========== Phase 4: Despawn 常量 ==========
    /** 1 Minecraft 天 = 24000 ticks */
    private static final int TICKS_PER_DAY = 24000;
    /** 正常模式：2天后开始消失预警（事件 NPC 模式） */
    private static final int WARNING_TIME_NORMAL = 2 * TICKS_PER_DAY; // 48000 ticks
    /** 正常模式：5天后强制消失（事件 NPC 模式） */
    private static final int DESPAWN_TIME_NORMAL = 5 * TICKS_PER_DAY; // 120000 ticks
    /** 调试模式：30秒后开始消失预警 */
    private static final int WARNING_TIME_DEBUG = 30 * 20; // 600 ticks
    /** 调试模式：60秒后强制消失 */
    private static final int DESPAWN_TIME_DEBUG = 60 * 20; // 1200 ticks
    /** 闪烁间隔（ticks）- 20 ticks = 1秒 */
    private static final int BLINK_INTERVAL = 20;

    // ========== Phase 5: 惩罚常量 ==========
    /** 攻击惩罚：失明持续时间（ticks） */
    private static final int ATTACK_BLINDNESS_DURATION = 100; // 5秒
    /** 攻击惩罚：反胃持续时间（ticks） */
    private static final int ATTACK_NAUSEA_DURATION = 140; // 7秒
    /** 击杀惩罚：效果持续时间倍率 */
    private static final int KILL_EFFECT_MULTIPLIER = 4;
    /** 击杀惩罚：额外的不幸效果持续时间（ticks） */
    private static final int KILL_UNLUCK_DURATION = 24000; // 20分钟

    // Phase 2.3: 交易统计
    private boolean hasEverTraded = false;
    /** P0-2: Same-tick guard — afterUsing skips katana sync if already done this tick. */
    private long lastKatanaSyncTick = -1;
    /** P2-1: Grace window counter for soft stale-customer conditions (distance/UI). */
    private int staleCustomerSoftTicks = 0;
    private static final int STALE_CUSTOMER_GRACE_TICKS = 40; // ~2 seconds

    // Phase 4: Despawn 数据
    private long spawnTick = -1;
    private boolean isInWarningPhase = false;

    // ========== Routine Night Invisibility (transient, no NBT) ==========
    private long nightInvisRollDay = Long.MIN_VALUE;
    private boolean nightInvisRollResult = false;
    private long lastGraceLogDay = Long.MIN_VALUE;

    // Ritual Reveal: 仪式召唤后的可见窗口（禁止隐身）
    private long ritualRevealUntilTick = 0L;
    /** P0-1: 是否已通知 SpawnerState 清除（防 discard 重入） */
    private boolean stateClearNotified = false;

    // Phase 8: 解封系统交易
    private String merchantName = "";
    /** 供奉交互的 runtime 防连点冷却（不持久化）。 */
    private long lastCoinOfferTick = -1L;
    /** 送别二次确认：记录 (playerUuid -> 首次潜行右键 tick)，3秒内再次右键才真正送别。 */
    private UUID sendoffPendingPlayer = null;
    private long sendoffPendingTick = -1L;
    private static final int SENDOFF_CONFIRM_WINDOW_TICKS = 60; // 3 秒

    // ========== Trade System: 隐藏交易限制 ==========
    /** 是否已售出隐藏物品 */
    private boolean secretSold = false;
    /** 隐藏物品 canonical ID（moonglow/regret/eclipse/oblivion/nmap） */
    private String secretKatanaId = "";

    // P0-A FIX: entity-level sigil seed DEPRECATED - seed is now derived
    // per-(merchant,player)
    // Kept only for NBT backward-compat read; never written to new saves.
    @SuppressWarnings("unused")
    private long sigilRollSeed_DEPRECATED = 0;
    @SuppressWarnings("unused")
    private boolean sigilRollInitialized_DEPRECATED = false;

    private static final int REFRESH_GUARANTEE_COUNT = 3;
    private static final int TRADE_PAGE_SIZE = 18;
    private static final int TRADE_TOTAL_PAGES = 4;
    private static final int PAGE_FIXED_COUNT = TradeConfig.VISIBLE_TOP_SLOTS;
    private static final int PAGE_SHELF_COUNT = TRADE_PAGE_SIZE - PAGE_FIXED_COUNT;
    private static final int HIDDEN_FIXED_COUNT = TradeConfig.VISIBLE_TOP_SLOTS;
    private static final int HIDDEN_SHELF_COUNT = TRADE_PAGE_SIZE - HIDDEN_FIXED_COUNT;
    private static final int KATANA_OFFER_MAX_USES = 1;
    private static final int RECLAIM_OFFER_MAX_USES = 1;

    public MysteriousMerchantEntity(EntityType<? extends WanderingTraderEntity> type, World world) {
        super(type, world);
    }

    // ========== Phase 4: Despawn 逻辑 ==========

    /**
     * 仪式召唤可见窗口是否激活（窗口内禁止隐身）。
     */
    public boolean isRitualRevealActive(long now) {
        return ritualRevealUntilTick > 0L && now < ritualRevealUntilTick;
    }

    /**
     * 日常夜间隐身是否允许（grace window + 概率 roll）。
     * 仅用于 DrinkPotionGoal 的日常夜间分支；不影响受伤/威胁隐身。
     */
    public boolean shouldAllowRoutineNightInvis(ServerWorld world) {
        long tod = world.getTimeOfDay() % 24000L;
        long duskStart = 12000L;

        // Grace window：黄昏刚开始的 N ticks 内禁止日常隐身
        if (tod >= duskStart && tod < duskStart + TradeConfig.MERCHANT_DUSK_NO_INVIS_TICKS) {
            if (DEBUG_AI) {
                long dayIndex = world.getTimeOfDay() / 24000L;
                if (dayIndex != lastGraceLogDay) {
                    lastGraceLogDay = dayIndex;
                    LOGGER.debug("[MerchantAI] ROUTINE_INVIS_BLOCKED reason=DUSK_GRACE tod={} grace={} entity={}",
                            tod, TradeConfig.MERCHANT_DUSK_NO_INVIS_TICKS, this.getUuidAsString());
                }
            }
            return false;
        }

        // 夜间窗口 (13000..23000)
        boolean isNight = (tod >= 13000L && tod <= 23000L);
        if (!isNight) {
            return false;
        }

        // 每个 MC 日只 roll 一次
        long dayIndex = world.getTimeOfDay() / 24000L;
        if (dayIndex != nightInvisRollDay) {
            nightInvisRollDay = dayIndex;
            nightInvisRollResult = this.random.nextFloat() < TradeConfig.MERCHANT_NIGHT_INVIS_CHANCE;
            if (DEBUG_AI) {
                LOGGER.debug("[MerchantAI] NIGHT_INVIS_ROLL day={} result={} chance={}",
                        dayIndex, nightInvisRollResult, TradeConfig.MERCHANT_NIGHT_INVIS_CHANCE);
            }
        }
        return nightInvisRollResult;
    }

    /**
     * 标记仪式召唤可见窗口（仅由召唤路径调用）。
     */
    public void markRitualReveal(ServerWorld world) {
        long now = world.getTime();
        long until = now + TradeConfig.SUMMON_RITUAL_REVEAL_TICKS;
        if (until > this.ritualRevealUntilTick) this.ritualRevealUntilTick = until;
        LOGGER.info("[Merchant] RITUAL_REVEAL merchant={} now={} until={}", this.getUuidAsString(), now, this.ritualRevealUntilTick);
    }

    @Override
    public void tick() {
        super.tick();

        // 只在服务端处理 despawn 逻辑
        if (this.getEntityWorld().isClient()) {
            return;
        }

        // P2-1: Stale customer cleanup — release busy lock if customer is gone
        cleanupStaleCustomer();

        // P2-3: Periodic pending claims cleanup (every ~1200 ticks = 60s)
        long tickTime = this.getEntityWorld().getTime();
        if (tickTime % 1200 == 0 && this.getEntityWorld() instanceof ServerWorld sw) {
            KatanaOwnershipState.getServerState(sw).cleanExpiredPending(tickTime);
        }

        // 幂等 ensure：防止首次 tick 前交互路径读取到空名字/空固定神器 ID
        ensureVariantIdentityIfNeeded();

        // 初始化 spawnTick（首次 tick 时记录，仅当未从 NBT 加载时）
        if (spawnTick < 0) {
            spawnTick = this.getEntityWorld().getTime();
            if (DEBUG_DESPAWN) {
                LOGGER.debug("[Merchant] INIT_SPAWN_TICK side=SERVER spawnTick={} worldTime={}",
                        spawnTick, this.getEntityWorld().getTime());
            }
        }

        long currentTick = this.getEntityWorld().getTime();

        // Ritual Reveal: 窗口内移除已有隐身效果（阻止 DrinkPotionGoal 在 canStart 中另行处理）
        if (isRitualRevealActive(currentTick)) {
            if (this.hasStatusEffect(StatusEffects.INVISIBILITY)) {
                this.removeStatusEffect(StatusEffects.INVISIBILITY);
            }
        }

        long aliveTicks = currentTick - spawnTick;

        int warningTime = DEBUG_DESPAWN ? WARNING_TIME_DEBUG : WARNING_TIME_NORMAL;
        int despawnTime = DEBUG_DESPAWN ? DESPAWN_TIME_DEBUG : DESPAWN_TIME_NORMAL;

        // 检查强制消失
        if (aliveTicks >= despawnTime) {
            performDespawn();
            return;
        }

        // 检查消失预警阶段
        if (aliveTicks >= warningTime) {
            if (!isInWarningPhase) {
                isInWarningPhase = true;
                if (DEBUG_DESPAWN) {
                    int remainingSec = (int) ((despawnTime - aliveTicks) / 20);
                    LOGGER.debug(
                            "[Merchant] WARNING_ENTER side=SERVER remaining={}s aliveTicks={} spawnTick={} worldTime={}",
                            remainingSec, aliveTicks, spawnTick, currentTick);
                }
            }
            performWarningEffect(currentTick);
        }
    }

    /**
     * 执行消失预警效果：身体闪烁 + 粒子
     */
    private void performWarningEffect(long currentTick) {
        // 闪烁效果：每 BLINK_INTERVAL ticks 切换可见性
        boolean shouldGlow = (currentTick / BLINK_INTERVAL) % 2 == 0;
        this.setGlowing(shouldGlow);

        // 每秒生成粒子效果（20 ticks = 1秒）
        if (currentTick % 20 == 0 && this.getEntityWorld() instanceof ServerWorld serverWorld) {
            serverWorld.spawnParticles(
                    ParticleTypes.PORTAL,
                    this.getX(), this.getY() + 1.0, this.getZ(),
                    10, // count
                    0.3, 0.5, 0.3, // offset
                    0.02 // speed
            );
        }
    }

    // ========== P0-1: 禁用原版 despawn，确保清理 ==========

    /**
     * P0-1: 钳死原版 WanderingTrader 的 despawnDelay 计时器。
     * 使用自定义 spawnTick-based 逻辑替代。
     */
    @Override
    public void setDespawnDelay(int delay) {
        super.setDespawnDelay(Integer.MAX_VALUE);
    }

    /**
     * P0-1: 终极清理保险 - 不管谁调的 remove（discard/kill/unload 等），
     * 都确保 SpawnerState 被清理。discard() 是 final，改为覆写 remove()。
     */
    @Override
    public void remove(Entity.RemovalReason reason) {
        if (!stateClearNotified && this.getEntityWorld() instanceof ServerWorld serverWorld) {
            notifySpawnerStateClear(serverWorld, reason.name());
        }
        super.remove(reason);
    }

    /**
     * 执行消失：播放效果并移除实体
     */
    private void performDespawn() {
        long lifetime = this.getEntityWorld().getTime() - spawnTick;
        if (DEBUG_DESPAWN) {
            LOGGER.info("[Merchant] DESPAWN_TRIGGER lifetime={}s uuid={}...",
                    lifetime / 20,
                    this.getUuid().toString().substring(0, 8));
        }

        // 通知 SpawnerState 清除活跃商人追踪
        if (this.getEntityWorld() instanceof ServerWorld serverWorld) {
            notifySpawnerStateClear(serverWorld, "DESPAWN");
        }

        if (this.getEntityWorld() instanceof ServerWorld serverWorld) {
            // 播放消失音效
            serverWorld.playSound(
                    null,
                    this.getX(), this.getY(), this.getZ(),
                    SoundEvents.ENTITY_ENDERMAN_TELEPORT,
                    SoundCategory.NEUTRAL,
                    1.0f, 1.0f);

            // 生成大量传送门粒子
            serverWorld.spawnParticles(
                    ParticleTypes.PORTAL,
                    this.getX(), this.getY() + 1.0, this.getZ(),
                    50, // count
                    0.5, 1.0, 0.5, // offset
                    0.1 // speed
            );

            // 生成烟雾粒子
            serverWorld.spawnParticles(
                    ParticleTypes.LARGE_SMOKE,
                    this.getX(), this.getY() + 0.5, this.getZ(),
                    20,
                    0.3, 0.5, 0.3,
                    0.05);

            if (DEBUG_DESPAWN) {
                LOGGER.debug("[Merchant] FX_SPAWN portal=50 smoke=20 pos={}",
                        String.format("%.1f,%.1f,%.1f", this.getX(), this.getY(), this.getZ()));
            }
        }

        // 移除实体
        this.discard();
    }

    /**
     * 获取剩余存活时间（秒）
     */
    public int getRemainingTimeSeconds() {
        if (spawnTick < 0)
            return -1;
        long aliveTicks = this.getEntityWorld().getTime() - spawnTick;
        int despawnTime = DEBUG_DESPAWN ? DESPAWN_TIME_DEBUG : DESPAWN_TIME_NORMAL;
        return Math.max(0, (int) ((despawnTime - aliveTicks) / 20));
    }

    /**
     * 是否处于消失预警阶段
     */
    public boolean isInWarningPhase() {
        return isInWarningPhase;
    }

    /**
     * 通知 SpawnerState 清除活跃商人追踪
     * 
     * @param serverWorld 服务端世界
     * @param reason      清除原因（DESPAWN / DEATH）
     */
    private void notifySpawnerStateClear(ServerWorld serverWorld, String reason) {
        this.stateClearNotified = true;
        try {
            MerchantSpawnerState state = MerchantSpawnerState.getServerState(serverWorld);
            boolean cleared = state.clearActiveMerchantIfMatch(this.getUuid());
            LOGGER.info("[MerchantSpawn] cleanup reason={} merchantUuid={} cleared={} entityId={} spawnTick={}",
                    reason, this.getUuid().toString().substring(0, 8), cleared, this.getId(), this.spawnTick);
        } catch (Exception e) {
            // 异常性日志，始终保留
            LOGGER.error("[Merchant] Failed to notify SpawnerState: {}", e.getMessage());
        }
    }

    // ========== Phase 5: 攻击与击杀惩罚 ==========

    /**
     * 覆写伤害处理：当玩家攻击商人时施加惩罚
     * 1.21.1 API: damage(DamageSource, float)
     */
    @Override
    public boolean damage(DamageSource source, float amount) {
        // 检查攻击者是否为玩家
        if (source.getAttacker() instanceof PlayerEntity player) {
            applyAttackPunishment(player);
        }
        return super.damage(source, amount);
    }

    /**
     * 施加攻击惩罚：失明 + 反胃
     */
    private void applyAttackPunishment(PlayerEntity player) {
        // P1-1: 创造模式不受惩罚
        if (player.isCreative())
            return;

        // 失明效果
        player.addStatusEffect(new StatusEffectInstance(
                StatusEffects.BLINDNESS,
                ATTACK_BLINDNESS_DURATION,
                0, // amplifier
                false, // ambient
                true, // showParticles
                true // showIcon
        ));

        // 反胃效果
        player.addStatusEffect(new StatusEffectInstance(
                StatusEffects.NAUSEA,
                ATTACK_NAUSEA_DURATION,
                0,
                false,
                true,
                true));

        // 播放不祥音效
        if (this.getEntityWorld() instanceof ServerWorld serverWorld) {
            serverWorld.playSound(
                    null,
                    player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE,
                    SoundCategory.HOSTILE,
                    0.5f, 1.5f);
        }

        if (DEBUG_AI) {
            LOGGER.debug("[MysteriousMerchant] 玩家 {} 攻击了商人，施加惩罚效果",
                    player.getName().getString());
        }
    }

    /**
     * 覆写死亡处理：当被玩家击杀时施加更强惩罚
     */
    @Override
    public void onDeath(DamageSource source) {
        // 检查击杀者是否为玩家
        if (source.getAttacker() instanceof PlayerEntity player) {
            applyKillPunishment(player);
        }

        // 通知 SpawnerState 清除活跃商人追踪
        if (this.getEntityWorld() instanceof ServerWorld serverWorld) {
            notifySpawnerStateClear(serverWorld, "DEATH");
        }

        super.onDeath(source);
    }

    /**
     * 施加击杀惩罚：效果翻倍 + 不幸 + 警告消息
     */
    private void applyKillPunishment(PlayerEntity player) {
        // P1-1: 创造模式不受惩罚
        if (player.isCreative())
            return;

        // 失明效果（翻倍时长）
        player.addStatusEffect(new StatusEffectInstance(
                StatusEffects.BLINDNESS,
                ATTACK_BLINDNESS_DURATION * KILL_EFFECT_MULTIPLIER,
                1, // amplifier 提升
                false,
                true,
                true));

        // 反胃效果（翻倍时长）
        player.addStatusEffect(new StatusEffectInstance(
                StatusEffects.NAUSEA,
                ATTACK_NAUSEA_DURATION * KILL_EFFECT_MULTIPLIER,
                1,
                false,
                true,
                true));

        // 不幸效果（长时间）
        player.addStatusEffect(new StatusEffectInstance(
                StatusEffects.UNLUCK,
                KILL_UNLUCK_DURATION,
                1,
                false,
                true,
                true));

        // 缓慢效果
        player.addStatusEffect(new StatusEffectInstance(
                StatusEffects.SLOWNESS,
                ATTACK_BLINDNESS_DURATION * KILL_EFFECT_MULTIPLIER,
                1,
                false,
                true,
                true));

        // 发送警告消息
        if (player instanceof ServerPlayerEntity serverPlayer) {
            serverPlayer.sendMessage(
                    Text.translatable("msg.xqanzd_moonlit_broker.merchant.kill_curse")
                            .formatted(Formatting.DARK_RED, Formatting.BOLD),
                    false);
            serverPlayer.sendMessage(
                    Text.translatable("actionbar.xqanzd_moonlit_broker.merchant.kill_omen")
                            .formatted(Formatting.DARK_PURPLE, Formatting.ITALIC),
                    true // actionBar
            );
        }

        // 播放恐怖音效
        if (this.getEntityWorld() instanceof ServerWorld serverWorld) {
            serverWorld.playSound(
                    null,
                    player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENTITY_WITHER_SPAWN,
                    SoundCategory.HOSTILE,
                    0.8f, 0.5f);

            // 生成不祥粒子
            serverWorld.spawnParticles(
                    ParticleTypes.WITCH,
                    player.getX(), player.getY() + 1.0, player.getZ(),
                    30,
                    0.5, 1.0, 0.5,
                    0.1);
        }

        if (DEBUG_AI) {
            LOGGER.info("[MysteriousMerchant] 玩家 {} 击杀了商人，施加严重惩罚！",
                    player.getName().getString());
        }
    }

    // ========== Book UI v2: 使用自定义 ScreenHandler ==========

    @Override
    public void sendOffers(PlayerEntity player, Text name, int levelProgress) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            return;
        }
        java.util.OptionalInt syncId = serverPlayer.openHandledScreen(
            new net.minecraft.screen.SimpleNamedScreenHandlerFactory(
                (sid, inv, p) -> new dev.xqanzd.moonlitbroker.screen.MoonlitMerchantScreenHandler(sid, inv, this),
                name
            )
        );
        if (syncId.isPresent()) {
            TradeOfferList offers = this.getOffers();
            serverPlayer.sendTradeOffers(
                syncId.getAsInt(), offers, levelProgress,
                this.getExperience(), this.isLeveledMerchant(), this.canRefreshTrades()
            );
        }
    }

    /**
     * P0-1 FIX: Send offers packet to the player WITHOUT reopening the screen handler.
     * Use this for REFRESH / OPEN_NORMAL / SWITCH_SECRET actions that happen while
     * the UI is already open, to avoid triggering onClosed → setCustomer(null).
     */
    public void syncOffersToCustomer(ServerPlayerEntity player) {
        if (!(player.currentScreenHandler instanceof MerchantScreenHandler handler)) {
            return;
        }
        // P1-3: Verify the handler's merchant is actually this entity, not a stale/different one
        if (handler instanceof dev.xqanzd.moonlitbroker.mixin.MerchantScreenHandlerAccessor accessor
                && accessor.getMerchant() != this) {
            LOGGER.warn("[MoonTrade] SYNC_OFFERS_MERCHANT_MISMATCH player={} syncId={} handlerType={} expectedMerchant={} actualMerchant={}",
                    player.getName().getString(), handler.syncId,
                    handler.getClass().getSimpleName(), this.getUuid(),
                    accessor.getMerchant() instanceof net.minecraft.entity.Entity e ? e.getUuid() : "non-entity");
            return;
        }
        TradeOfferList offers = this.getOffers();
        player.sendTradeOffers(
            handler.syncId, offers, 0,
            this.getExperience(), this.isLeveledMerchant(), this.canRefreshTrades()
        );
    }

    /**
     * P2-1: Release stale customer reference if the player is gone.
     * Hard conditions (offline/removed/different world) release immediately.
     * Soft conditions (distance/UI closed) require {@link #STALE_CUSTOMER_GRACE_TICKS}
     * consecutive ticks before releasing, to avoid transient false positives
     * (e.g. knockback, screen switch frame).
     */
    private void cleanupStaleCustomer() {
        PlayerEntity customer = this.getCustomer();
        if (customer == null) {
            staleCustomerSoftTicks = 0;
            return;
        }

        // Hard conditions — immediate release
        String hardReason = null;
        if (customer.isRemoved()) {
            hardReason = "removed";
        } else if (customer instanceof ServerPlayerEntity sp && sp.isDisconnected()) {
            hardReason = "disconnected";
        } else if (customer.getWorld() != this.getWorld()) {
            hardReason = "different_world";
        }
        if (hardReason != null) {
            LOGGER.info("[MoonTrade] STALE_CUSTOMER_CLEANUP merchant={} customer={} reason={}",
                    this.getUuid(), customer.getUuid(), hardReason);
            this.setCustomer(null);
            staleCustomerSoftTicks = 0;
            return;
        }

        // Soft conditions — require grace window
        boolean softStale = false;
        String softReason = null;
        if (customer.squaredDistanceTo(this) > 64 * 64) {
            softStale = true;
            softReason = "distance_gt_64";
        } else if (!(customer.currentScreenHandler instanceof MerchantScreenHandler)) {
            softStale = true;
            softReason = "ui_closed";
        }

        if (softStale) {
            staleCustomerSoftTicks++;
            if (staleCustomerSoftTicks >= STALE_CUSTOMER_GRACE_TICKS) {
                LOGGER.info("[MoonTrade] STALE_CUSTOMER_CLEANUP merchant={} customer={} reason={} graceTicks={}",
                        this.getUuid(), customer.getUuid(), softReason, staleCustomerSoftTicks);
                this.setCustomer(null);
                staleCustomerSoftTicks = 0;
            }
        } else {
            // Customer is healthy — reset grace counter
            staleCustomerSoftTicks = 0;
        }
    }

    // ========== Phase 5: 特殊交互 ==========

    /**
     * 覆写交互：检测神秘硬币、首次见面赠送指南
     */
    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        ItemStack heldItem = player.getStackInHand(hand);

        // Guard: 任何特殊交互都不能绕过这些前置约束
        if (!this.isAlive() || this.isBaby()) {
            return super.interactMob(player, hand);
        }
        if (heldItem.getItem() instanceof SpawnEggItem) {
            return super.interactMob(player, hand);
        }
        // P0: 商人忙碌时，阻止其他玩家交互（不再 fallthrough 到 super 以避免重开 screen handler）
        if (this.hasCustomer()) {
            if (heldItem.isOf(ModItems.BOUNTY_CONTRACT) && player instanceof ServerPlayerEntity sp) {
                sp.sendMessage(
                        Text.translatable("actionbar.xqanzd_moonlit_broker.merchant.busy_try_later")
                                .formatted(Formatting.YELLOW),
                        true);
            }
            return ActionResult.CONSUME;
        }

        // ========== 送别机制：潜行 + 手持 Trade Scroll 右键 ==========
        if (player.isSneaking() && heldItem.isOf(ModItems.TRADE_SCROLL)) {
            // 多人服保护：有其他玩家正在交易时阻止送别
            if (this.hasCustomer() && this.getCustomer() != player) {
                if (player instanceof ServerPlayerEntity sp) {
                    sp.sendMessage(
                            Text.translatable("actionbar.xqanzd_moonlit_broker.merchant.busy_with_other")
                                    .formatted(Formatting.YELLOW),
                            true);
                }
                return ActionResult.CONSUME;
            }
            if (!this.getEntityWorld().isClient() && player instanceof ServerPlayerEntity sp) {
                ServerWorld serverWorld = sp.getServerWorld();
                long now = serverWorld.getTime();
                // 二次确认：首次潜行右键仅提示，3秒内再次右键才执行送别
                if (sendoffPendingPlayer == null
                        || !sendoffPendingPlayer.equals(player.getUuid())
                        || now - sendoffPendingTick > SENDOFF_CONFIRM_WINDOW_TICKS) {
                    // 首次（或超时）：记录 nonce，提示确认
                    sendoffPendingPlayer = player.getUuid();
                    sendoffPendingTick = now;
                    sp.sendMessage(
                            Text.translatable(
                                    "actionbar.xqanzd_moonlit_broker.merchant.sendoff_confirm",
                                    new ItemStack(ModItems.TRADE_SCROLL).getName(),
                                    1
                            )
                                    .formatted(Formatting.GOLD),
                            true);
                    return ActionResult.CONSUME;
                }
                // 二次确认通过，清除 nonce
                sendoffPendingPlayer = null;
                sendoffPendingTick = -1L;
                // survival 消耗 1 Scroll
                if (!player.isCreative()) {
                    heldItem.decrement(1);
                }
                // 推进全局冷却（DEBUG + 创造模式可旁路）
                boolean bypassCooldown = TradeConfig.DEBUG_TRADES
                        && TradeConfig.DEBUG_SENDOFF_BYPASS_GLOBAL_COOLDOWN
                        && player.isCreative();
                if (!bypassCooldown) {
                    MerchantSpawnerState.getServerState(serverWorld)
                            .applySendoffCooldown(serverWorld, TradeConfig.SUMMON_GLOBAL_COOLDOWN_TICKS, player.getUuid());
                } else {
                    LOGGER.warn("[MoonTrade] SENDOFF_DEBUG_BYPASS_COOLDOWN player={} merchant={}",
                            sp.getName().getString(), this.getUuid().toString().substring(0, 8));
                }
                // actionbar 提示
                sp.sendMessage(
                        Text.translatable("actionbar.xqanzd_moonlit_broker.merchant.sendoff_done")
                                .formatted(Formatting.LIGHT_PURPLE, Formatting.ITALIC),
                        true);
                LOGGER.info("[MoonTrade] SENDOFF player={} merchant={} pos={}",
                        sp.getName().getString(),
                        this.getUuid().toString().substring(0, 8),
                        String.format("%.0f,%.0f,%.0f", this.getX(), this.getY(), this.getZ()));
                // 复用现有 despawn 链路
                performDespawn();
            }
            return ActionResult.CONSUME;
        }

        // 手持指南时优先打开书本阅读，避免被商人交互抢占。
        if (heldItem.getItem() == ModItems.GUIDE_SCROLL) {
            return heldItem.use(this.getEntityWorld(), player, hand).getResult();
        }

        // 检查是否手持神秘硬币
        if (heldItem.getItem() == ModItems.MYSTERIOUS_COIN) {
            return handleMysteriousCoinInteraction(player, heldItem, hand);
        }

        // Bounty v1: 非潜行且无人占用时提交
        if (heldItem.getItem() == ModItems.BOUNTY_CONTRACT && !player.isSneaking()) {
            return handleBountyContractSubmit(player, heldItem, hand);
        }

        // P0: 潜行 + 手持契约 → actionbar 提示松开潜行以提交，不打开交易界面
        if (heldItem.getItem() == ModItems.BOUNTY_CONTRACT && player.isSneaking()) {
            if (player instanceof ServerPlayerEntity sp) {
                sp.sendMessage(
                        Text.translatable("actionbar.xqanzd_moonlit_broker.bounty.release_sneak_submit")
                                .formatted(Formatting.GOLD),
                        true);
            }
            return ActionResult.CONSUME;
        }

        if (hand == Hand.MAIN_HAND) {
            player.incrementStat(Stats.TALKED_TO_VILLAGER);
        }

        if (this.getEntityWorld().isClient()) {
            return ActionResult.SUCCESS;
        }

        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            return ActionResult.CONSUME;
        }

        ensureVariantIdentityIfNeeded();
        // MarkBound: 首次交互时绑定，并发送一次性 actionbar 提示
        if (this.getEntityWorld() instanceof ServerWorld sw) {
            boolean firstBind = MerchantUnlockState.bindMarkOnInteract(sw, serverPlayer, this.getUuid());
            if (firstBind) {
                serverPlayer.sendMessage(
                        Text.translatable("actionbar.xqanzd_moonlit_broker.mark.bound")
                                .formatted(Formatting.GREEN),
                        true);
                serverPlayer.sendMessage(
                        Text.translatable("msg.xqanzd_moonlit_broker.bounty.register_enabled")
                                .formatted(Formatting.GREEN),
                        false);
                LOGGER.info("[Bounty] MARK_BOUND player={} merchant={} reason=FIRST_INTERACT",
                        serverPlayer.getName().getString(), this.getUuid().toString().substring(0, 8));
            }
        }
        boolean justGrantedFirstMeet = grantFirstMeetGuideIfNeeded(serverPlayer);
        if (!justGrantedFirstMeet) {
            reissueMarkIfNeeded(serverPlayer);
        }
        initSecretKatanaIdIfNeeded();

        OfferBuildAudit audit = rebuildOffersForPlayer(serverPlayer, OfferBuildSource.INTERACT_PREOPEN);
        if (audit.offersTotal() <= 0) {
            LOGGER.warn(
                    "[MoonTrade] action=OPEN_UI side=S player={} merchant={} source={} cache={} unlock={} offersTotal={} base={} sigil={} hidden={} offersHash={} seed={} refreshSeenCount={} durationMs={} pageSize={} reason=no_offers",
                    playerTag(serverPlayer), merchantTag(), audit.source(), audit.cache(), audit.unlock(),
                    audit.offersTotal(), audit.baseCount(), audit.sigilCount(), audit.hiddenCount(),
                    Integer.toHexString(audit.offersHash()), audit.seed(), audit.refreshSeenCount(), audit.durationMs(),
                    TRADE_PAGE_SIZE);
            return ActionResult.CONSUME;
        }

        this.setCustomer(serverPlayer);
        this.sendOffers(serverPlayer, this.getDisplayName(), this.getExperience());

        LOGGER.info(
                "[MoonTrade] action=OPEN_UI side=S player={} merchant={} source={} cache={} unlock={} offersTotal={} base={} sigil={} hidden={} offersHash={} seed={} refreshSeenCount={} durationMs={} pageSize={}",
                playerTag(serverPlayer), merchantTag(), audit.source(), audit.cache(), audit.unlock(),
                audit.offersTotal(), audit.baseCount(), audit.sigilCount(), audit.hiddenCount(),
                Integer.toHexString(audit.offersHash()), audit.seed(), audit.refreshSeenCount(), audit.durationMs(),
                TRADE_PAGE_SIZE);

        return ActionResult.CONSUME;
    }

    /**
     * 尝试将物品放入玩家背包；背包满则掉落在脚下（发光 + 无拾取延迟）。
     * @return true 如果物品成功交付（背包或掉落），false 如果彻底失败
     */
    private static boolean giveOrDrop(ServerPlayerEntity player, ItemStack stack, String logLabel) {
        if (stack == null || stack.isEmpty()) return true;

        boolean inserted = player.getInventory().insertStack(stack);
        if (inserted && stack.isEmpty()) {
            LOGGER.info("[Gift] {} result=INVENTORY player={}", logLabel, player.getName().getString());
            return true;
        }

        // 背包满或部分剩余，掉落在脚下
        ItemEntity dropped = player.dropItem(stack, false);
        if (dropped != null) {
            dropped.setGlowing(true);
            dropped.setPickupDelay(0);
            player.sendMessage(
                    Text.translatable("actionbar.xqanzd_moonlit_broker.gift.dropped")
                            .formatted(Formatting.YELLOW),
                    true);
            LOGGER.info("[Gift] {} result=DROP player={} pos={}", logLabel, player.getName().getString(), player.getBlockPos());
            return true;
        }

        LOGGER.error("[Gift] {} result=FAILED player={}", logLabel, player.getName().getString());
        return false;
    }

    // markBountyEligible removed — replaced by MerchantUnlockState.bindMarkOnInteract()

    /**
     * 首次见面按缺失项发放指南卷轴和/或商人印记。
     * <ul>
     *   <li>缺 guide → 发 guide（需 1 空位）</li>
     *   <li>缺首次 mark → 发 mark（需 1 空位）</li>
     *   <li>两者都缺 → 需 2 空位</li>
     * </ul>
     * insertStack 成功才写对应进度标记，防止"进度走了但玩家没拿到"。
     *
     * @return true 如果首次赠送分支被触发（无论成功或背包满），调用方应跳过补发逻辑
     */
    private boolean grantFirstMeetGuideIfNeeded(ServerPlayerEntity player) {
        if (!(this.getEntityWorld() instanceof ServerWorld serverWorld)) {
            return false;
        }

        MerchantUnlockState state = MerchantUnlockState.getServerState(serverWorld);
        MerchantUnlockState.Progress progress = state.getOrCreateProgress(player.getUuid());

        boolean needGuide = !progress.isFirstMeetGuideGiven();
        boolean needMark  = !progress.isInitialMarkGranted();

        if (!needGuide && !needMark) {
            return false; // 首次赠送流程已全部完成
        }

        // 按实际缺失项计算所需空位
        int required = (needGuide ? 1 : 0) + (needMark ? 1 : 0);
        if (countFreeInventorySlots(player) < required) {
            player.sendMessage(
                    Text.translatable("actionbar.xqanzd_moonlit_broker.gift.delivery_failed")
                            .formatted(Formatting.RED),
                    true);
            LOGGER.info("[Gift] FIRST_MEET_NO_SPACE player={} need={} freeSlots={}",
                    player.getName().getString(), required, countFreeInventorySlots(player));
            return true; // 跳过补发，避免白白消耗补发机会
        }

        boolean changed = false;

        if (needGuide) {
            ItemStack guideScroll = new ItemStack(ModItems.GUIDE_SCROLL, 1);
            dev.xqanzd.moonlitbroker.trade.item.GuideScrollItem.ensureGuideContent(guideScroll);

            boolean ok = player.getInventory().insertStack(guideScroll) && guideScroll.isEmpty();
            if (ok) {
                progress.setFirstMeetGuideGiven(true);
                changed = true;
            } else {
                player.sendMessage(
                        Text.translatable("actionbar.xqanzd_moonlit_broker.gift.delivery_failed")
                                .formatted(Formatting.RED),
                        true);
                LOGGER.error("[Gift] FIRST_MEET_GUIDE_INSERT_FAILED player={}", player.getName().getString());
                if (changed) state.markDirty();
                return true;
            }
        }

        if (needMark) {
            ItemStack merchantMark = new ItemStack(ModItems.MERCHANT_MARK, 1);
            dev.xqanzd.moonlitbroker.trade.item.MerchantMarkItem.bindToPlayer(merchantMark, player);

            boolean ok = player.getInventory().insertStack(merchantMark) && merchantMark.isEmpty();
            if (ok) {
                progress.setInitialMarkGranted(true);
                changed = true;
            } else {
                player.sendMessage(
                        Text.translatable("actionbar.xqanzd_moonlit_broker.gift.delivery_failed")
                                .formatted(Formatting.RED),
                        true);
                LOGGER.error("[Gift] FIRST_MEET_MARK_INSERT_FAILED player={}", player.getName().getString());
                if (changed) state.markDirty();
                return true;
            }
        }

        if (changed) {
            state.markDirty();

            if (serverWorld.getRegistryKey() == World.OVERWORLD) {
                MerchantSpawnerState spawnerState = MerchantSpawnerState.getServerState(serverWorld);
                spawnerState.markBootstrapComplete(serverWorld, player.getUuid());
            }

            player.sendMessage(
                    Text.translatable("msg.xqanzd_moonlit_broker.merchant.first_meet_gift")
                            .formatted(Formatting.GOLD),
                    false);

            LOGGER.info("[MoonTrade] FIRST_MEET_GRANT player={} guide={} mark={}",
                    player.getName().getString(), needGuide, needMark);
        }

        return true;
    }

    /**
     * Mark 补发：已解锁玩家背包无 Mark 时自动补发 1 个绑定印记（仅限一次）。
     * 补发后 Progress.reissuedMark 置 true；后续丢失需通过交易重新获取。
     * 背包满时不补发也不消耗补发机会，提示清理后重试。
     */
    private void reissueMarkIfNeeded(ServerPlayerEntity player) {
        if (!(this.getEntityWorld() instanceof ServerWorld serverWorld)) return;

        if (!MerchantUnlockState.isMerchantUnlocked(serverWorld, player.getUuid())) return;
        if (playerHasAnyMerchantMark(player)) return;

        MerchantUnlockState state = MerchantUnlockState.getServerState(serverWorld);
        MerchantUnlockState.Progress progress = state.getOrCreateProgress(player.getUuid());

        if (progress.isReissuedMark()) {
            player.sendMessage(
                    Text.translatable("actionbar.xqanzd_moonlit_broker.mark.reissue_exhausted")
                            .formatted(Formatting.YELLOW),
                    true);
            return;
        }

        // 背包至少需要 1 个空位，否则提示清理后重试（不消耗补发机会）
        if (countFreeInventorySlots(player) < 1) {
            player.sendMessage(
                    Text.translatable("actionbar.xqanzd_moonlit_broker.gift.delivery_failed")
                            .formatted(Formatting.RED),
                    true);
            return;
        }

        ItemStack mark = new ItemStack(ModItems.MERCHANT_MARK, 1);
        dev.xqanzd.moonlitbroker.trade.item.MerchantMarkItem.bindToPlayer(mark, player);

        boolean ok = player.getInventory().insertStack(mark) && mark.isEmpty();
        if (!ok) {
            player.sendMessage(
                    Text.translatable("actionbar.xqanzd_moonlit_broker.gift.delivery_failed")
                            .formatted(Formatting.RED),
                    true);
            LOGGER.error("[Gift] REISSUE_INSERT_FAILED player={}", player.getName().getString());
            return;
        }

        progress.setReissuedMark(true);
        state.markDirty();

        player.sendMessage(
                Text.translatable("actionbar.xqanzd_moonlit_broker.mark.reissued")
                        .formatted(Formatting.GREEN),
                true);

        LOGGER.info("[Mark] REISSUE player={} reason=NO_MARK_IN_INVENTORY", player.getName().getString());
    }

    /**
     * 检查玩家背包（主手/副手/main）是否有任意 MerchantMark（不要求绑定）。
     */
    private static boolean playerHasAnyMerchantMark(PlayerEntity player) {
        for (ItemStack stack : player.getInventory().main) {
            if (stack.isOf(ModItems.MERCHANT_MARK)) return true;
        }
        for (ItemStack stack : player.getInventory().offHand) {
            if (stack.isOf(ModItems.MERCHANT_MARK)) return true;
        }
        return false;
    }

    /**
     * 统计玩家主背包（36 格）中的空槽位数量。
     */
    private static int countFreeInventorySlots(PlayerEntity player) {
        int free = 0;
        for (ItemStack stack : player.getInventory().main) {
            if (stack.isEmpty()) free++;
        }
        return free;
    }

    /**
     * 处理神秘硬币交互
     */
    private ActionResult handleMysteriousCoinInteraction(PlayerEntity player, ItemStack coinStack, Hand hand) {
        if (this.getEntityWorld().isClient()) {
            return ActionResult.SUCCESS;
        }

        if (!(this.getEntityWorld() instanceof ServerWorld serverWorld)) {
            return ActionResult.CONSUME;
        }

        long now = serverWorld.getTime();
        if (lastCoinOfferTick >= 0L && now - lastCoinOfferTick < TradeConfig.COIN_OFFER_CD_TICKS) {
            return ActionResult.CONSUME;
        }
        lastCoinOfferTick = now;

        // 消耗一个硬币
        if (!player.isCreative()) {
            coinStack.decrement(1);
        }

        // 给予强化的正面效果
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.LUCK, 6000, 1)); // 5分钟幸运 II
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 1200, 1)); // 1分钟速度 II
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 600, 1)); // 30秒再生 II
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.HERO_OF_THE_VILLAGE, 12000, 0)); // 10分钟村庄英雄

        // 额外奖励：Trade Scroll x1（背包满则掉落）
        ItemStack rewardScroll = new ItemStack(ModItems.TRADE_SCROLL, 1);
        TradeScrollItem.initialize(rewardScroll, TradeConfig.GRADE_NORMAL);
        if (!player.giveItemStack(rewardScroll)) {
            player.dropItem(rewardScroll, false);
        }

        // 发送消息
        if (player instanceof ServerPlayerEntity serverPlayer) {
            serverPlayer.sendMessage(
                    Text.translatable("msg.xqanzd_moonlit_broker.merchant.coin_offer.accepted")
                            .formatted(Formatting.GOLD),
                    false);
            serverPlayer.sendMessage(
                    Text.translatable("actionbar.xqanzd_moonlit_broker.merchant.coin_offer.blessing")
                            .formatted(Formatting.YELLOW, Formatting.ITALIC),
                    true);
            serverPlayer.sendMessage(
                    Text.translatable(
                            "msg.xqanzd_moonlit_broker.merchant.coin_offer.extra_item",
                            new ItemStack(ModItems.TRADE_SCROLL).getName(),
                            1
                    )
                            .formatted(Formatting.AQUA),
                    false);
        }

        // 播放神秘音效和粒子
        serverWorld.playSound(
                null,
                this.getX(), this.getY(), this.getZ(),
                SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE,
                SoundCategory.NEUTRAL,
                1.0f, 1.0f);

        serverWorld.spawnParticles(
                ParticleTypes.ENCHANT,
                this.getX(), this.getY() + 1.5, this.getZ(),
                50,
                0.5, 0.5, 0.5,
                0.5);

        serverWorld.spawnParticles(
                ParticleTypes.HAPPY_VILLAGER,
                player.getX(), player.getY() + 1.0, player.getZ(),
                20,
                0.3, 0.5, 0.3,
                0.1);

        if (DEBUG_AI) {
            LOGGER.debug("[MysteriousMerchant] 玩家 {} 使用了神秘硬币，获得祝福效果",
                    player.getName().getString());
        }

        return ActionResult.SUCCESS;
    }

    /**
     * Bounty v1: 处理悬赏契约提交
     * 原子流程：验证 → grantRewards → decrement
     */
    private ActionResult handleBountyContractSubmit(PlayerEntity player, ItemStack contractStack, Hand hand) {
        // Client-side: PASS，让服务器裁决
        if (this.getEntityWorld().isClient()) {
            return ActionResult.PASS;
        }

        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            return ActionResult.CONSUME;
        }

        // 1) 验证有效契约
        if (!BountyContractItem.isValidContract(contractStack)) {
            serverPlayer.sendMessage(
                    Text.translatable("error.xqanzd_moonlit_broker.bounty.invalid_contract")
                            .formatted(Formatting.RED),
                    true
            );
            LOGGER.info("[MoonTrade] action=BOUNTY_SUBMIT_REJECT reason=INVALID side=S player={}",
                    serverPlayer.getName().getString());
            return ActionResult.CONSUME;
        }

        // 1.5) 失效契约回收：目标已不在 bounty_targets 或 registry 无法解析
        if (!BountyContractItem.isTargetStillValid(contractStack)) {
            String expiredTarget = BountyContractItem.getTarget(contractStack);
            String reason = BountyContractItem.validateTarget(contractStack);
            // 消耗契约
            contractStack.decrement(1);
            // 返还少量银票补偿
            int refund = TradeConfig.BOUNTY_EXPIRED_REFUND_SILVER;
            if (refund > 0) {
                ItemStack silver = new ItemStack(ModItems.SILVER_NOTE, refund);
                if (!serverPlayer.giveItemStack(silver)) {
                    serverPlayer.dropItem(silver, false);
                }
            }
            serverPlayer.sendMessage(
                    Text.translatable("msg.xqanzd_moonlit_broker.bounty.contract_expired_refunded",
                            expiredTarget, refund, new ItemStack(ModItems.SILVER_NOTE).getName())
                            .formatted(Formatting.YELLOW),
                    false);
            LOGGER.info("[MoonTrade] action=BOUNTY_SUBMIT_EXPIRED_RECYCLE side=S player={} target={} reason={} refund={}",
                    serverPlayer.getName().getString(), expiredTarget, reason, refund);
            return ActionResult.CONSUME;
        }

        // 2) 严格完成判定：boolean + progress >= required
        if (!BountyContractItem.isCompletedStrict(contractStack)) {
            int progress = BountyContractItem.getProgress(contractStack);
            int required = BountyContractItem.getRequired(contractStack);
            String target = BountyContractItem.getTarget(contractStack);
            serverPlayer.sendMessage(
                    Text.translatable("error.xqanzd_moonlit_broker.bounty.not_completed", progress, required)
                            .formatted(Formatting.YELLOW),
                    false);
            LOGGER.info("[MoonTrade] action=BOUNTY_SUBMIT_REJECT reason=NOT_DONE side=S player={} target={} progress={}/{}",
                    serverPlayer.getName().getString(), target, progress, required);
            return ActionResult.CONSUME;
        }

        // 3) 原子提交：先发奖励，成功后再消耗契约
        String target = BountyContractItem.getTarget(contractStack);
        int progress = BountyContractItem.getProgress(contractStack);
        int required = BountyContractItem.getRequired(contractStack);

        try {
            // Resolve isElite from target entity ID for reward scaling
            boolean isElite = false;
            net.minecraft.util.Identifier targetId = net.minecraft.util.Identifier.tryParse(target);
            if (targetId != null) {
                net.minecraft.entity.EntityType<?> targetType = net.minecraft.registry.Registries.ENTITY_TYPE.get(targetId);
                if (targetType != null) {
                    isElite = targetType.isIn(dev.xqanzd.moonlitbroker.registry.ModEntityTypeTags.BOUNTY_ELITE_TARGETS);
                }
            }
            dev.xqanzd.moonlitbroker.trade.loot.BountyHandler.grantRewards(serverPlayer, required, isElite);
            contractStack.decrement(1);
        } catch (Exception e) {
            LOGGER.error("[MoonTrade] action=BOUNTY_SUBMIT_ERROR side=S player={} target={} error={}",
                    serverPlayer.getName().getString(), target, e.getMessage(), e);
            serverPlayer.sendMessage(
                    Text.translatable("error.xqanzd_moonlit_broker.bounty.submit_failed")
                            .formatted(Formatting.RED),
                    false
            );
            return ActionResult.CONSUME;
        }

        LOGGER.info("[MoonTrade] action=BOUNTY_SUBMIT_ACCEPT side=S player={} target={} progress={}/{} rewardScroll=1 rewardSilver={}",
                serverPlayer.getName().getString(), target, progress, required,
                dev.xqanzd.moonlitbroker.trade.TradeConfig.BOUNTY_SILVER_REWARD);

        serverPlayer.sendMessage(
                Text.translatable(
                        "msg.xqanzd_moonlit_broker.bounty.rewards_granted",
                        new ItemStack(ModItems.TRADE_SCROLL).getName(),
                        1,
                        new ItemStack(ModItems.SILVER_NOTE).getName(),
                        TradeConfig.BOUNTY_SILVER_REWARD
                )
                        .formatted(Formatting.GREEN),
                false);

        return ActionResult.SUCCESS;
    }

    // ========== Phase 3: 注册自定义 AI Goals ==========
    @Override
    protected void initGoals() {
        super.initGoals();

        // 移除原版 WanderingTrader 的 HoldInHandsGoal（隐身药水 + 牛奶）
        // 按类型精确匹配，不按 priority 大扫除，避免误删未来新增的 Goal。
        List<Goal> drinkGoals = new ArrayList<>();
        for (PrioritizedGoal pg : this.goalSelector.getGoals()) {
            if (pg.getGoal() instanceof HoldInHandsGoal) {
                drinkGoals.add(pg.getGoal());
            }
        }
        for (Goal g : drinkGoals) {
            this.goalSelector.remove(g);
        }
        if (DEBUG_AI && !drinkGoals.isEmpty()) {
            LOGGER.debug("[MysteriousMerchant] Removed {} vanilla HoldInHandsGoal instances", drinkGoals.size());
        }

        // Phase 3.1: 强化逃跑 - 优先级 1（与 panic 同级，但会更积极）
        this.goalSelector.add(1, new EnhancedFleeGoal(this, BASE_MOVEMENT_SPEED));

        // Phase 3.2: 自保机制 - 优先级 2（比闲逛高，可以边跑边喝）
        this.goalSelector.add(2, new DrinkPotionGoal(this));

        // Phase 3.3: 趋光性 - 优先级 7（比闲逛低，但比漫无目的的走动高）
        this.goalSelector.add(7, new SeekLightGoal(this));

        if (DEBUG_AI) {
            LOGGER.debug("[MysteriousMerchant] AI Goals 已注册");
        }
    }

    public boolean hasEverTraded() {
        return hasEverTraded;
    }

    public void setHasEverTraded(boolean hasEverTraded) {
        this.hasEverTraded = hasEverTraded;
    }

    // Phase 2.4: 交易完成回调
    @Override
    protected void afterUsing(TradeOffer offer) {
        super.afterUsing(offer);

        // 1. 标记已交易过
        this.hasEverTraded = true;

        // 2. 获取当前交易的玩家
        PlayerEntity player = this.getCustomer();
        if (player == null) {
            return;
        }

        if (!(this.getEntityWorld() instanceof ServerWorld serverWorld)) {
            return;
        }

        // Server-authoritative ownership write on successful trade.
        // This is a hard fallback path even if output-slot mixin path misses.
        String soldKatanaId = KatanaIdUtil.extractCanonicalKatanaId(offer.getSellItem());
        if (KatanaIdUtil.isSecretKatana(soldKatanaId)) {
            KatanaOwnershipState ownershipState = KatanaOwnershipState.getServerState(serverWorld);
            boolean added = ownershipState.addOwned(player.getUuid(), soldKatanaId);
            if (added) {
                LOGGER.info("[MoonTrade] MM_KATANA_OWNED_ADD player={} katanaId={} merchant={} source=afterUsing",
                        player.getUuid(), soldKatanaId, this.getUuid());
            }

            // Contract: register activeInstanceId from the sell item template
            UUID instanceId = KatanaContractUtil.getInstanceId(offer.getSellItem());
            if (instanceId != null) {
                boolean isReclaim = KatanaContractUtil.isReclaimOutput(offer.getSellItem());
                ownershipState.setActiveInstanceId(player.getUuid(), soldKatanaId, instanceId);
                if (isReclaim) {
                    ownershipState.setLastReclaimTick(player.getUuid(), soldKatanaId, serverWorld.getTime());
                    player.sendMessage(
                            Text.translatable("msg.xqanzd_moonlit_broker.reclaim.reforged")
                                    .formatted(Formatting.YELLOW),
                            true);
                }
                LOGGER.info("[MoonTrade] CONTRACT_ACTIVATE player={} katanaId={} instanceId={} reclaim={} merchant={}",
                        player.getUuid(), soldKatanaId, instanceId, isReclaim, this.getUuid());
            }

            // Task C: After successful katana delivery, disable all same-type katana offers
            // and sync to client so UI immediately reflects sold-out state.
            // P0-2: Same-tick guard — skip if already synced this tick (e.g. from takeStack commit path).
            long currentTick = serverWorld.getTime();
            if (currentTick != this.lastKatanaSyncTick) {
                TradeOfferList currentOffers = this.getOffers();
                int disabledCount = 0;
                for (TradeOffer o : currentOffers) {
                    if (KatanaContractUtil.isReclaimOutput(o.getSellItem())) continue;
                    String offerKatanaId = KatanaIdUtil.extractCanonicalKatanaId(o.getSellItem());
                    if (soldKatanaId.equals(offerKatanaId) && !o.isDisabled()) {
                        o.disable();
                        disabledCount++;
                    }
                }
                if (disabledCount > 0 && player instanceof ServerPlayerEntity sp
                        && sp.currentScreenHandler instanceof MerchantScreenHandler) {
                    sp.sendTradeOffers(
                            sp.currentScreenHandler.syncId,
                            currentOffers,
                            0,
                            this.getExperience(),
                            this.isLeveledMerchant(),
                            this.canRefreshTrades());
                    this.lastKatanaSyncTick = currentTick;
                    LOGGER.info("[MoonTrade] POST_TRADE_KATANA_SYNC player={} katanaId={} disabled={} merchant={}",
                            player.getUuid(), soldKatanaId, disabledCount, this.getUuid());
                }
            }
        }

        MerchantUnlockState state = MerchantUnlockState.getServerState(serverWorld);
        String variantKey = getVariantKey();
        MerchantUnlockState.Progress progress = state.getOrCreateProgress(player.getUuid(), variantKey);

        boolean unlockStateChanged = false;
        String arcaneRewardKey = resolveArcaneRewardKey(offer);
        if (arcaneRewardKey != null) {
            boolean firstClaim = progress.markArcaneRewardClaimed(variantKey, arcaneRewardKey);
            if (firstClaim) {
                unlockStateChanged = true;
            }
            LOGGER.info(
                    "[MoonTrade] ARCANE_REWARD_CLAIM player={} merchant={} variant={} rewardKey={} firstClaim={}",
                    player.getUuid(), this.getUuid(), variantKey, arcaneRewardKey, firstClaim);
        }

        // 成本门槛模式：不再以 totalTrades / merchantXp 作为解锁条件。
        // 仍保留交易完成的短时增益反馈。
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 100, 0));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 100, 0));

        // 识别解封交易（仅由成本物品触发）
        if (isUnsealOffer(offer)) {
            if (!progress.isUnlockedKatanaHidden(variantKey)) {
                progress.setUnlockedKatanaHidden(variantKey, true);
                unlockStateChanged = true;
                LOGGER.info("[MerchantUnlock] UNLOCK player={} uuid={} variant={}",
                        player.getName().getString(), player.getUuid().toString().substring(0, 8), variantKey);
            }
            if (!progress.isUnlockedNotified(variantKey)) {
                progress.setUnlockedNotified(variantKey, true);
                unlockStateChanged = true;
                if (player instanceof ServerPlayerEntity serverPlayer) {
                    serverPlayer.sendMessage(
                            Text.translatable("msg.xqanzd_moonlit_broker.merchant.unseal_unlocked")
                                    .formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD),
                            false);
                    // P0-6 修复：提示玩家重新打开交易界面以查看隐藏交易
                    serverPlayer.sendMessage(
                            Text.translatable("msg.xqanzd_moonlit_broker.merchant.unseal_reopen")
                                    .formatted(Formatting.GRAY, Formatting.ITALIC),
                            false);
                }
            }
        }

        if (unlockStateChanged) {
            state.markDirty();
        }

        LOGGER.info(
                "[MoonTrade] UNLOCK_SCOPE_AUDIT player={} variant={} eligible={} unlocked={} scope=cost_gate_only",
                player.getName().getString(), variantKey,
                true, progress.isUnlockedKatanaHidden(variantKey));

        if (DEBUG_AI) {
            LOGGER.debug("[MysteriousMerchant] TRADE_COMPLETE player={} unlocked={}",
                    player.getName().getString(), progress.isUnlockedKatanaHidden(variantKey));
        }
    }

    @Override
    protected void fillRecipes() {
        TradeOfferList offers = this.getOffers();
        int offersBefore = offers.size();
        if (offersBefore > 0 || hasHiddenOffers(offers)) {
            LOGGER.info(
                    "[MoonTrade] action=FILL_RECIPES_CALLED side=S merchant={} offersBefore={} offersAfter={} reason=already_built_noop",
                    merchantTag(), offersBefore, offersBefore);
            return;
        }
        addBaseOffers(offers);
        normalizeMerchantExperienceInPlace(offers);
        sanitizeReleaseOffersInPlace(offers, "FILL_RECIPES_FALLBACK");
        LOGGER.info(
                "[MoonTrade] action=FILL_RECIPES_CALLED side=S merchant={} offersBefore={} offersAfter={} reason=empty_fallback_add_base",
                merchantTag(), offersBefore, offers.size());
    }

    private void addBaseOffers(TradeOfferList offers) {
        addBaseOffers(offers, null, null, getVariantKey(), new int[TRADE_TOTAL_PAGES]);
    }

    private void addBaseOffers(TradeOfferList offers,
            UUID playerUuid,
            MerchantUnlockState.Progress progress,
            String variantKey,
            int[] pageRefreshNonces) {
        addPage1Offers(offers, playerUuid, getPageRefreshNonce(pageRefreshNonces, 1));
        addPage2Offers(offers, playerUuid, getPageRefreshNonce(pageRefreshNonces, 2));
        addPage3Offers(offers, playerUuid, progress, variantKey, getPageRefreshNonce(pageRefreshNonces, 3));

        if (TradeConfig.DEBUG_TRADES) {
            addDebugOffers(offers);
        }

        MerchantVariant variant = variantOf(this.getType());
        LOGGER.info("[MoonTrade] BASE_BUILD variant={} offersCount={}",
                variant != null ? variant.typeKey : "UNKNOWN", offers.size());
    }

    private static int getPageRefreshNonce(int[] nonces, int pageIndex) {
        if (nonces == null || pageIndex <= 0 || pageIndex > nonces.length) {
            return 0;
        }
        return Math.max(0, nonces[pageIndex - 1]);
    }

    /**
     * PAGE 1 / BASE: fixed 12 + shelf 6
     */
    private void addPage1Offers(TradeOfferList offers, UUID playerUuid, int refreshNonce) {
        int pageStart = offers.size();

        // Fixed 12: high-frequency convenience
        offers.add(new TradeOffer(new TradedItem(Items.IRON_INGOT, 1), new ItemStack(Items.TORCH, 64), 16, 0, 0.05f));
        offers.add(new TradeOffer(new TradedItem(Items.IRON_INGOT, 1), new ItemStack(Items.ARROW, 64), 14, 0, 0.05f));
        offers.add(new TradeOffer(new TradedItem(Items.IRON_INGOT, 2), new ItemStack(Items.BREAD, 32), 12, 0, 0.05f));
        offers.add(new TradeOffer(new TradedItem(Items.IRON_INGOT, 3), new ItemStack(Items.COOKED_BEEF, 24), 10, 0, 0.05f));
        offers.add(new TradeOffer(new TradedItem(Items.IRON_INGOT, 2), new ItemStack(Items.OAK_LOG, 32), 10, 0, 0.05f));
        offers.add(new TradeOffer(new TradedItem(Items.IRON_INGOT, 2), new ItemStack(Items.GLASS, 64), 10, 0, 0.05f));
        offers.add(new TradeOffer(new TradedItem(Items.IRON_INGOT, 2), new ItemStack(Items.SMOOTH_STONE, 64), 10, 0, 0.05f));
        offers.add(new TradeOffer(new TradedItem(Items.IRON_INGOT, 2), new ItemStack(Items.GLASS_PANE, 64), 10, 0, 0.05f));
        offers.add(new TradeOffer(new TradedItem(Items.IRON_INGOT, 3), new ItemStack(Items.RAIL, 48), 8, 0, 0.05f));
        offers.add(new TradeOffer(new TradedItem(Items.IRON_INGOT, 3), new ItemStack(Items.BRICKS, 64), 8, 0, 0.05f));
        offers.add(new TradeOffer(new TradedItem(Items.IRON_INGOT, 2), new ItemStack(Items.STRING, 48), 8, 0, 0.05f));
        offers.add(new TradeOffer(new TradedItem(Items.IRON_INGOT, 2), new ItemStack(Items.BONE_MEAL, 64), 8, 0, 0.05f));

        ArrayList<TradeOffer> shelfCandidates = new ArrayList<>();
        moveFixedOverflowToShelf(offers, pageStart, shelfCandidates);
        deterministicShuffleBaseSegment(offers, pageStart, pageStart + PAGE_FIXED_COUNT, 1, mustSeeFixedOffsets());
        shelfCandidates.add(new TradeOffer(new TradedItem(Items.IRON_INGOT, 2), new ItemStack(Items.OAK_PLANKS, 64), 10, 0, 0.05f));
        shelfCandidates.add(new TradeOffer(new TradedItem(Items.IRON_INGOT, 2), new ItemStack(Items.LADDER, 64), 12, 0, 0.05f));
        shelfCandidates.add(new TradeOffer(new TradedItem(Items.IRON_INGOT, 2), new ItemStack(Items.COBBLED_DEEPSLATE, 64), 8, 0, 0.05f));
        shelfCandidates.add(new TradeOffer(new TradedItem(Items.IRON_INGOT, 2), new ItemStack(Items.SCAFFOLDING, 48), 10, 0, 0.05f));
        shelfCandidates.add(new TradeOffer(new TradedItem(Items.IRON_INGOT, 2), new ItemStack(Items.LEATHER, 32), 8, 0, 0.05f));
        shelfCandidates.add(new TradeOffer(new TradedItem(Items.IRON_INGOT, 2), new ItemStack(Items.STONE_BRICKS, 64), 10, 0, 0.05f));
        shelfCandidates.add(new TradeOffer(new TradedItem(Items.IRON_INGOT, 2), new ItemStack(Items.COBBLESTONE, 64), 8, 0, 0.05f));
        shelfCandidates.add(new TradeOffer(new TradedItem(Items.IRON_INGOT, 2), new ItemStack(Items.SAND, 64), 8, 0, 0.05f));
        shelfCandidates.add(new TradeOffer(new TradedItem(Items.IRON_INGOT, 2), new ItemStack(Items.GRAVEL, 64), 8, 0, 0.05f));
        shelfCandidates.add(new TradeOffer(new TradedItem(Items.IRON_INGOT, 3), new ItemStack(Items.COAL, 64), 8, 0, 0.05f));
        shelfCandidates.add(new TradeOffer(new TradedItem(Items.IRON_INGOT, 3), new ItemStack(Items.MOSSY_STONE_BRICKS, 64), 6, 0, 0.05f));
        shelfCandidates.add(new TradeOffer(new TradedItem(Items.IRON_INGOT, 3), new ItemStack(Items.POWERED_RAIL, 32), 6, 0, 0.05f));
        injectVisibleSparkOffer(offers, pageStart, 1, refreshNonce, playerUuid, shelfCandidates);
        addShelfOffersToPage(offers, pageStart, shelfCandidates, 1, refreshNonce, playerUuid, PAGE_SHELF_COUNT);

        logPageGateCostSummary("PAGE1", offers, pageStart);
    }

    /**
     * PAGE 2 / BASE: fixed 12 + shelf 6
     */
    private void addPage2Offers(TradeOfferList offers, UUID playerUuid, int refreshNonce) {
        int pageStart = offers.size();

        // Fixed 12
        addBaseOfferWithLoopGuard(offers, new TradeOffer(
                new TradedItem(Items.EMERALD, 6),
                Optional.of(new TradedItem(Items.BOOK, 1)),
                createEnchantedBook(Enchantments.EFFICIENCY, 2),
                4, 0, 0f), "p2_fixed_book_efficiency_2");
        addBaseOfferWithLoopGuard(offers, new TradeOffer(
                new TradedItem(Items.EMERALD, 6),
                Optional.of(new TradedItem(Items.BOOK, 1)),
                createEnchantedBook(Enchantments.UNBREAKING, 2),
                4, 0, 0f), "p2_fixed_book_unbreaking_2");
        addBaseOfferWithLoopGuard(offers, new TradeOffer(
                new TradedItem(Items.EMERALD, 8),
                Optional.of(new TradedItem(Items.BOOK, 1)),
                createEnchantedBook(Enchantments.FIRE_ASPECT, 2),
                1, 0, 0f), "p2_fixed_book_fire_aspect_2");
        addBaseOfferWithLoopGuard(offers, new TradeOffer(
                new TradedItem(Items.EMERALD, 16),
                Optional.of(new TradedItem(ModItems.SILVER_NOTE, 2)),
                createEnchantedBook(Enchantments.MENDING, 1),
                1, 0, 0f), "p2_fixed_book_mending_1");
        addBaseOfferWithLoopGuard(offers, new TradeOffer(
                new TradedItem(Items.EMERALD, 12),
                Optional.of(new TradedItem(ModItems.SILVER_NOTE, 1)),
                createEnchantedBook(Enchantments.SILK_TOUCH, 1),
                1, 0, 0f), "p2_fixed_book_silk_touch_1");
        addBaseOfferWithLoopGuard(offers, new TradeOffer(
                new TradedItem(Items.EMERALD, 14),
                Optional.of(new TradedItem(ModItems.SILVER_NOTE, 1)),
                createEnchantedBook(Enchantments.FORTUNE, 2),
                1, 0, 0f), "p2_fixed_book_fortune_2");
        addBaseOfferWithLoopGuard(offers, new TradeOffer(
                new TradedItem(Items.EMERALD, 16),
                Optional.of(new TradedItem(ModItems.SILVER_NOTE, 2)),
                createEnchantedBook(Enchantments.FEATHER_FALLING, 4),
                1, 0, 0f), "p2_fixed_book_feather_falling_4");
        offers.add(new TradeOffer(
                new TradedItem(Items.EMERALD, 6),
                Optional.of(new TradedItem(ModItems.SILVER_NOTE, 1)),
                createEnchantedTool(Items.IRON_PICKAXE, Enchantments.EFFICIENCY, 2, Enchantments.UNBREAKING, 1),
                3, 0, 0.05f));
        offers.add(new TradeOffer(
                new TradedItem(Items.EMERALD, 6),
                Optional.of(new TradedItem(ModItems.SILVER_NOTE, 1)),
                createEnchantedTool(Items.IRON_SWORD, Enchantments.SHARPNESS, 2, Enchantments.UNBREAKING, 1),
                3, 0, 0.05f));
        offers.add(new TradeOffer(
                new TradedItem(Items.EMERALD, 10),
                Optional.of(new TradedItem(ModItems.SILVER_NOTE, 1)),
                createEnchantedTool(Items.BOW, Enchantments.POWER, 2, Enchantments.UNBREAKING, 2),
                2, 0, 0.05f));
        offers.add(new TradeOffer(new TradedItem(Items.EMERALD, 6), new ItemStack(Items.OBSIDIAN, 8), 6, 0, 0.05f));
        offers.add(new TradeOffer(new TradedItem(Items.EMERALD, 4), new ItemStack(Items.REDSTONE, 64), 6, 0, 0.05f));

        ArrayList<TradeOffer> shelfCandidates = new ArrayList<>();
        moveFixedOverflowToShelf(offers, pageStart, shelfCandidates);
        deterministicShuffleBaseSegment(offers, pageStart, pageStart + PAGE_FIXED_COUNT, 2, mustSeeFixedOffsets());
        addBaseOfferCandidate(shelfCandidates, new TradeOffer(
                new TradedItem(Items.EMERALD, 8),
                Optional.of(new TradedItem(ModItems.SILVER_NOTE, 1)),
                createEnchantedBook(Enchantments.LOOTING, 2),
                2, 0, 0f));
        addBaseOfferCandidate(shelfCandidates, new TradeOffer(
                new TradedItem(Items.EMERALD, 8),
                Optional.of(new TradedItem(ModItems.SILVER_NOTE, 1)),
                createEnchantedBook(Enchantments.FLAME, 1),
                2, 0, 0f));
        addBaseOfferCandidate(shelfCandidates, new TradeOffer(
                new TradedItem(Items.EMERALD, 8),
                Optional.of(new TradedItem(ModItems.SILVER_NOTE, 1)),
                createEnchantedBook(Enchantments.KNOCKBACK, 2),
                1, 0, 0f));
        addBaseOfferCandidate(shelfCandidates, new TradeOffer(
                new TradedItem(Items.EMERALD, 10),
                Optional.of(new TradedItem(ModItems.SILVER_NOTE, 1)),
                createEnchantedBook(Enchantments.EFFICIENCY, 3),
                2, 0, 0f));
        addBaseOfferCandidate(shelfCandidates, new TradeOffer(
                new TradedItem(Items.EMERALD, 10),
                Optional.of(new TradedItem(ModItems.SILVER_NOTE, 1)),
                createEnchantedBook(Enchantments.UNBREAKING, 3),
                2, 0, 0f));
        addBaseOfferCandidate(shelfCandidates, new TradeOffer(
                new TradedItem(Items.EMERALD, 12),
                Optional.of(new TradedItem(ModItems.SILVER_NOTE, 1)),
                createEnchantedBook(Enchantments.SWEEPING_EDGE, 3),
                1, 0, 0f));
        shelfCandidates.add(new TradeOffer(
                new TradedItem(Items.EMERALD, 6),
                Optional.of(new TradedItem(ModItems.SILVER_NOTE, 1)),
                createEnchantedTool(Items.IRON_AXE, Enchantments.EFFICIENCY, 2, Enchantments.UNBREAKING, 1),
                3, 0, 0.05f));
        shelfCandidates.add(new TradeOffer(
                new TradedItem(Items.EMERALD, 6),
                Optional.of(new TradedItem(ModItems.SILVER_NOTE, 1)),
                createEnchantedTool(Items.IRON_SHOVEL, Enchantments.EFFICIENCY, 2, Enchantments.UNBREAKING, 1),
                3, 0, 0.05f));
        shelfCandidates.add(new TradeOffer(
                new TradedItem(Items.EMERALD, 6),
                Optional.of(new TradedItem(ModItems.SILVER_NOTE, 1)),
                createEnchantedTool(Items.IRON_HOE, Enchantments.EFFICIENCY, 2, Enchantments.UNBREAKING, 1),
                3, 0, 0.05f));
        injectVisibleSparkOffer(offers, pageStart, 2, refreshNonce, playerUuid, shelfCandidates);
        addShelfOffersToPage(offers, pageStart, shelfCandidates, 2, refreshNonce, playerUuid, PAGE_SHELF_COUNT);

        logPageGateCostSummary("PAGE2", offers, pageStart);
    }

    /**
     * PAGE 3 / GATE: fixed 12 + shelf 6
     */
    private void addPage3Offers(TradeOfferList offers,
            UUID playerUuid,
            MerchantUnlockState.Progress progress,
            String variantKey,
            int refreshNonce) {
        int pageStart = offers.size();

        // Fixed 12: gate chain + workshop
        offers.add(createSealedLedgerOffer());
        offers.add(createUnsealOffer());
        offers.add(new TradeOffer(
                new TradedItem(ModItems.SILVER_NOTE, TradeConfig.PAGE2_SCROLL_SOURCE_SILVER_COST),
                new ItemStack(ModItems.TRADE_SCROLL, 1),
                TradeConfig.PAGE2_SCROLL_SOURCE_MAX_USES, 0, 0.0f));
        offers.add(new TradeOffer(
                new TradedItem(ModItems.SILVER_NOTE, TradeConfig.PAGE2_TICKET_SOURCE_SILVER_COST),
                Optional.of(new TradedItem(Items.EMERALD, TradeConfig.PAGE2_TICKET_SOURCE_EMERALD_TAX)),
                new ItemStack(ModItems.MERCHANT_MARK, 1),
                TradeConfig.PAGE2_TICKET_SOURCE_MAX_USES, 0, 0.0f));

        offers.add(new TradeOffer(
                new TradedItem(Items.EMERALD, 8),
                Optional.of(new TradedItem(ModItems.SILVER_NOTE, 1)),
                new ItemStack(ModBlocks.MYSTERIOUS_ANVIL_ITEM, 1),
                2, 0, 0.0f));
        offers.add(new TradeOffer(
                new TradedItem(Items.DIAMOND, 2),
                Optional.of(new TradedItem(ModItems.SILVER_NOTE, 2)),
                new ItemStack(ModItems.SACRIFICE, 1),
                4, 0, 0.0f));
        offers.add(new TradeOffer(
                new TradedItem(Items.NETHERITE_INGOT, 1),
                new ItemStack(ModItems.SIGIL, 1),
                1, 0, 0.0f));
        offers.add(new TradeOffer(
                new TradedItem(Items.BLAZE_ROD, 2),
                new ItemStack(ModItems.SIGIL, 1),
                2, 0, 0.0f));
        addBaseOfferWithLoopGuard(offers, new TradeOffer(
                new TradedItem(Items.EMERALD, 12),
                Optional.of(new TradedItem(ModItems.SILVER_NOTE, 1)),
                createEnchantedBook(Enchantments.SWEEPING_EDGE, 3),
                1, 0, 0.0f), "p3_fixed_book_sweeping_edge_3");
        addBaseOfferWithLoopGuard(offers, new TradeOffer(
                new TradedItem(Items.EMERALD, 6),
                Optional.of(new TradedItem(ModItems.SILVER_NOTE, 1)),
                new ItemStack(TransitionalWeaponItems.ACER, 1),
                3, 0, 0.0f), "p3_fixed_trans_weapon_acer");
        addBaseOfferWithLoopGuard(offers, new TradeOffer(
                new TradedItem(Items.EMERALD, 8),
                Optional.of(new TradedItem(ModItems.SILVER_NOTE, 1)),
                new ItemStack(TransitionalWeaponItems.VELOX, 1),
                3, 0, 0.0f), "p3_fixed_trans_weapon_velox");
        addBaseOfferWithLoopGuard(offers, new TradeOffer(
                new TradedItem(Items.EMERALD, 10),
                Optional.of(new TradedItem(ModItems.SILVER_NOTE, 2)),
                new ItemStack(TransitionalWeaponItems.FATALIS, 1),
                2, 0, 0.0f), "p3_fixed_trans_weapon_fatalis");
        addBaseOfferWithLoopGuard(offers, new TradeOffer(
                new TradedItem(Items.EMERALD, 8),
                Optional.of(new TradedItem(ModItems.SILVER_NOTE, 1)),
                createEnchantedBook(Enchantments.KNOCKBACK, 2),
                1, 0, 0.0f), "p3_fixed_book_knockback_2");

        ArrayList<TradeOffer> shelfCandidates = new ArrayList<>();
        moveFixedOverflowToShelf(offers, pageStart, shelfCandidates);
        addBaseOfferCandidate(shelfCandidates, new TradeOffer(
                new TradedItem(ModItems.MYSTERIOUS_COIN, 1),
                createEnchantedBook(Enchantments.SMITE, 5),
                1, 0, 0.0f));
        addBaseOfferCandidate(shelfCandidates, new TradeOffer(
                new TradedItem(ModItems.MYSTERIOUS_COIN, 1),
                createEnchantedBook(Enchantments.POWER, 4),
                1, 0, 0.0f));
        addBaseOfferCandidate(shelfCandidates, new TradeOffer(
                new TradedItem(ModItems.MYSTERIOUS_COIN, 1),
                createEnchantedBook(Enchantments.SWEEPING_EDGE, 3),
                1, 0, 0.0f));
        addBaseOfferCandidate(shelfCandidates, new TradeOffer(
                new TradedItem(ModItems.MYSTERIOUS_COIN, 1),
                createEnchantedBook(Enchantments.KNOCKBACK, 2),
                1, 0, 0.0f));
        addBaseOfferCandidate(shelfCandidates, new TradeOffer(
                new TradedItem(ModItems.MYSTERIOUS_COIN, 1),
                createEnchantedBook(Enchantments.LOOTING, 3),
                1, 0, 0.0f));
        addBaseOfferCandidate(shelfCandidates, new TradeOffer(
                new TradedItem(ModItems.MYSTERIOUS_COIN, 1),
                createEnchantedBook(Enchantments.EFFICIENCY, 4),
                1, 0, 0.0f));
        addBaseOfferCandidate(shelfCandidates, new TradeOffer(
                new TradedItem(ModItems.MYSTERIOUS_COIN, 1),
                createEnchantedBook(Enchantments.UNBREAKING, 3),
                1, 0, 0.0f));
        addBaseOfferCandidate(shelfCandidates, new TradeOffer(
                new TradedItem(ModItems.MYSTERIOUS_COIN, 1),
                createEnchantedBook(Enchantments.FLAME, 1),
                1, 0, 0.0f));

        TradeOfferList variantCandidates = new TradeOfferList();
        addVariantSpecialtyOffers(variantCandidates);
        shelfCandidates.addAll(variantCandidates);

        injectVisibleSparkOffer(offers, pageStart, 3, refreshNonce, playerUuid, shelfCandidates);
        addShelfOffersToPage(offers, pageStart, shelfCandidates, 3, refreshNonce, playerUuid, PAGE_SHELF_COUNT);
        logPageGateCostSummary("PAGE3", offers, pageStart);
    }

    private void addBaseOfferCandidate(ArrayList<TradeOffer> list, TradeOffer offer) {
        if (!TradeConfig.DEBUG_TRADES && isReleaseForbiddenCoinMintOffer(offer)) {
            return;
        }
        list.add(offer);
    }

    private void moveFixedOverflowToShelf(TradeOfferList offers, int pageStart, java.util.List<TradeOffer> shelfCandidates) {
        int keepFixedCount = Math.max(1, Math.min(TRADE_PAGE_SIZE, TradeConfig.VISIBLE_TOP_SLOTS));
        int overflowIndex = pageStart + keepFixedCount;
        while (offers.size() > overflowIndex) {
            TradeOffer overflow = offers.remove(overflowIndex);
            if (overflow != null) {
                shelfCandidates.add(overflow);
            }
        }
    }

    private Set<Integer> mustSeeFixedOffsets() {
        Set<Integer> offsets = new HashSet<>();
        int limit = Math.max(0, Math.min(3, TradeConfig.VISIBLE_TOP_SLOTS));
        for (int i = 0; i < limit; i++) {
            offsets.add(i);
        }
        return offsets;
    }

    private SparkTier pickSparkTier(Random random) {
        int uncommonWeight = Math.max(0, TradeConfig.SPARK_UNCOMMON_WEIGHT);
        int rareWeight = Math.max(0, TradeConfig.SPARK_RARE_WEIGHT);
        int totalWeight = uncommonWeight + rareWeight;
        if (totalWeight <= 0) {
            return SparkTier.UNCOMMON;
        }
        int roll = random.nextInt(totalWeight);
        return roll < rareWeight ? SparkTier.RARE : SparkTier.UNCOMMON;
    }

    private int normalizeSparkPageUi(int pageIndex) {
        if (pageIndex >= 1 && pageIndex <= TRADE_TOTAL_PAGES) {
            return pageIndex;
        }
        if (pageIndex >= 0 && pageIndex < TRADE_TOTAL_PAGES) {
            return pageIndex + 1;
        }
        return 1;
    }

    private ArrayList<TradeOffer> buildUncommonSparkPoolForPage(int pageIndex) {
        int pageUi = normalizeSparkPageUi(pageIndex);
        ArrayList<TradeOffer> pool = new ArrayList<>();
        if (pageUi == 1) {
            pool.add(new TradeOffer(
                    new TradedItem(Items.IRON_INGOT, 4),
                    Optional.of(new TradedItem(ModItems.SILVER_NOTE, 1)),
                    new ItemStack(Items.EXPERIENCE_BOTTLE, 12),
                    2, 0, 0.0f));
            pool.add(new TradeOffer(
                    new TradedItem(Items.IRON_INGOT, 4),
                    Optional.of(new TradedItem(ModItems.SILVER_NOTE, 1)),
                    new ItemStack(Items.ENDER_PEARL, 6),
                    2, 0, 0.0f));
            pool.add(new TradeOffer(
                    new TradedItem(Items.IRON_INGOT, 5),
                    Optional.of(new TradedItem(ModItems.SILVER_NOTE, 1)),
                    new ItemStack(Items.BLAZE_POWDER, 12),
                    2, 0, 0.0f));
            return pool;
        }
        if (pageUi == 2) {
            pool.add(new TradeOffer(
                    new TradedItem(Items.EMERALD, 12),
                    Optional.of(new TradedItem(ModItems.SILVER_NOTE, 1)),
                    new ItemStack(Items.EXPERIENCE_BOTTLE, 24),
                    2, 0, 0.0f));
            pool.add(new TradeOffer(
                    new TradedItem(Items.EMERALD, 10),
                    Optional.of(new TradedItem(ModItems.SILVER_NOTE, 1)),
                    new ItemStack(Items.ENDER_PEARL, 8),
                    2, 0, 0.0f));
            pool.add(new TradeOffer(
                    new TradedItem(Items.EMERALD, 10),
                    Optional.of(new TradedItem(ModItems.SILVER_NOTE, 1)),
                    new ItemStack(Items.BLAZE_POWDER, 16),
                    2, 0, 0.0f));
            pool.add(new TradeOffer(
                    new TradedItem(Items.EMERALD, 8),
                    Optional.of(new TradedItem(ModItems.SILVER_NOTE, 1)),
                    createEnchantedTool(Items.IRON_PICKAXE, Enchantments.EFFICIENCY, 3, Enchantments.UNBREAKING, 2),
                    1, 0, 0.0f));
            return pool;
        }
        if (pageUi == 3) {
            pool.add(new TradeOffer(
                    new TradedItem(ModItems.MYSTERIOUS_COIN, 1),
                    Optional.of(new TradedItem(ModItems.SILVER_NOTE, 1)),
                    new ItemStack(Items.EXPERIENCE_BOTTLE, 24),
                    1, 0, 0.0f));
            pool.add(new TradeOffer(
                    new TradedItem(ModItems.MYSTERIOUS_COIN, 1),
                    Optional.of(new TradedItem(ModItems.SILVER_NOTE, 1)),
                    new ItemStack(Items.ENDER_PEARL, 12),
                    1, 0, 0.0f));
            pool.add(new TradeOffer(
                    new TradedItem(ModItems.MYSTERIOUS_COIN, 1),
                    Optional.of(new TradedItem(ModItems.SILVER_NOTE, 1)),
                    new ItemStack(Items.BLAZE_POWDER, 20),
                    1, 0, 0.0f));
            return pool;
        }
        pool.add(new TradeOffer(
                new TradedItem(ModItems.ARCANE_LEDGER, 1),
                Optional.of(new TradedItem(ModItems.SILVER_NOTE, 2)),
                new ItemStack(Items.EXPERIENCE_BOTTLE, 32),
                1, 0, 0.0f));
        pool.add(new TradeOffer(
                new TradedItem(ModItems.ARCANE_LEDGER, 1),
                Optional.of(new TradedItem(ModItems.SILVER_NOTE, 2)),
                new ItemStack(Items.ENDER_PEARL, 16),
                1, 0, 0.0f));
        pool.add(new TradeOffer(
                new TradedItem(ModItems.ARCANE_LEDGER, 1),
                Optional.of(new TradedItem(ModItems.TRADE_SCROLL, 1)),
                new ItemStack(Items.BLAZE_POWDER, 24),
                1, 0, 0.0f));
        return pool;
    }

    private ArrayList<TradeOffer> buildRareSparkPoolForPage(int pageIndex) {
        int pageUi = normalizeSparkPageUi(pageIndex);
        ArrayList<TradeOffer> pool = new ArrayList<>();
        if (pageUi == 4) {
            pool.add(new TradeOffer(
                    new TradedItem(ModItems.ARCANE_LEDGER, 1),
                    Optional.of(new TradedItem(ModItems.TRADE_SCROLL, 1)),
                    createEnchantedBook(Enchantments.FIRE_ASPECT, 2),
                    1, 0, 0.0f));
            pool.add(new TradeOffer(
                    new TradedItem(ModItems.ARCANE_LEDGER, 1),
                    Optional.of(new TradedItem(ModItems.TRADE_SCROLL, 1)),
                    createEnchantedBook(Enchantments.LOOTING, 2),
                    1, 0, 0.0f));
            pool.add(new TradeOffer(
                    new TradedItem(ModItems.ARCANE_LEDGER, 1),
                    Optional.of(new TradedItem(ModItems.MYSTERIOUS_COIN, 1)),
                    createEnchantedBook(Enchantments.FLAME, 1),
                    1, 0, 0.0f));
            return pool;
        }
        pool.add(new TradeOffer(
                new TradedItem(Items.EMERALD, 14),
                Optional.of(new TradedItem(ModItems.SILVER_NOTE, 2)),
                createEnchantedBook(Enchantments.FIRE_ASPECT, 2),
                1, 0, 0.0f));
        pool.add(new TradeOffer(
                new TradedItem(Items.EMERALD, 14),
                Optional.of(new TradedItem(ModItems.SILVER_NOTE, 2)),
                createEnchantedBook(Enchantments.FLAME, 1),
                1, 0, 0.0f));
        pool.add(new TradeOffer(
                new TradedItem(ModItems.MYSTERIOUS_COIN, 1),
                Optional.of(new TradedItem(ModItems.SILVER_NOTE, 1)),
                createEnchantedBook(Enchantments.LOOTING, 2),
                1, 0, 0.0f));
        return pool;
    }

    private TradeOffer selectSparkOfferForPage(int pageIndex, SparkTier tier, Random random, Set<String> usedKeys) {
        ArrayList<TradeOffer> sparkPool = tier == SparkTier.RARE
                ? buildRareSparkPoolForPage(pageIndex)
                : buildUncommonSparkPoolForPage(pageIndex);
        Collections.shuffle(sparkPool, random);
        for (TradeOffer sparkCandidate : sparkPool) {
            if (sparkCandidate == null || !isReleaseSafeRefillOffer(sparkCandidate)) {
                continue;
            }
            String key = exactOfferKey(sparkCandidate);
            if (usedKeys.contains(key)) {
                continue;
            }
            return sparkCandidate;
        }
        return null;
    }

    private void injectVisibleSparkOffer(TradeOfferList offers,
            int pageStart,
            int pageIndex,
            int refreshNonce,
            UUID playerUuid,
            java.util.List<TradeOffer> shelfCandidates) {
        if (TradeConfig.SPARK_SLOTS_PER_PAGE <= 0) {
            return;
        }
        int pageOfferCount = Math.max(0, offers.size() - pageStart);
        int visibleCount = Math.max(0, Math.min(TradeConfig.VISIBLE_TOP_SLOTS, pageOfferCount));
        if (visibleCount <= 0) {
            return;
        }
        Set<String> usedKeys = new HashSet<>();
        for (int i = pageStart; i < offers.size(); i++) {
            usedKeys.add(exactOfferKey(offers.get(i)));
        }
        for (TradeOffer shelfCandidate : shelfCandidates) {
            usedKeys.add(exactOfferKey(shelfCandidate));
        }
        Random random = new Random(deriveShelfSeed(this.getUuid(), playerUuid, pageIndex, refreshNonce));
        SparkTier sparkTier = pickSparkTier(random);
        TradeOffer sparkOffer = selectSparkOfferForPage(pageIndex, sparkTier, random, usedKeys);
        if (sparkOffer == null && sparkTier == SparkTier.RARE) {
            sparkTier = SparkTier.UNCOMMON;
            sparkOffer = selectSparkOfferForPage(pageIndex, sparkTier, random, usedKeys);
        }
        if (sparkOffer == null) {
            if (TradeConfig.TRADE_DEBUG) {
                LOGGER.info("[MoonTrade] PAGE_SPARK_SLOT page={} tier={} inserted=0 reason=no_candidate",
                        normalizeSparkPageUi(pageIndex), sparkTier.name());
            }
            return;
        }
        int sparkSlotIndex = pageStart + visibleCount - 1;
        TradeOffer displaced = offers.get(sparkSlotIndex);
        String displacedKey = exactOfferKey(displaced);
        usedKeys.remove(displacedKey);
        String sparkKey = exactOfferKey(sparkOffer);
        if (!usedKeys.add(sparkKey)) {
            return;
        }
        offers.set(sparkSlotIndex, sparkOffer);
        if (!usedKeys.contains(displacedKey)) {
            shelfCandidates.add(0, displaced);
            usedKeys.add(displacedKey);
        }
        if (TradeConfig.TRADE_DEBUG) {
            LOGGER.info("[MoonTrade] PAGE_SPARK_SLOT page={} tier={} inserted=1 topSlot={}",
                    normalizeSparkPageUi(pageIndex), sparkTier.name(), sparkSlotIndex - pageStart);
        }
    }

    private boolean tryAddShelfOffer(TradeOfferList offers, Set<String> usedKeys, TradeOffer candidate) {
        if (candidate == null || !isReleaseSafeRefillOffer(candidate)) {
            return false;
        }
        String key = exactOfferKey(candidate);
        if (!usedKeys.add(key)) {
            return false;
        }
        offers.add(candidate);
        return true;
    }

    private void addShelfOffersToPage(TradeOfferList offers,
            int pageStart,
            java.util.List<TradeOffer> candidates,
            int pageIndex,
            int refreshNonce,
            UUID playerUuid,
            int shelfSlots) {
        Random random = new Random(deriveShelfSeed(this.getUuid(), playerUuid, pageIndex, refreshNonce));
        ArrayList<TradeOffer> shuffled = new ArrayList<>(candidates);
        Collections.shuffle(shuffled, random);
        Set<String> usedKeys = new HashSet<>();
        for (int i = pageStart; i < offers.size(); i++) {
            usedKeys.add(exactOfferKey(offers.get(i)));
        }
        int added = 0;
        for (TradeOffer candidate : shuffled) {
            if (!tryAddShelfOffer(offers, usedKeys, candidate)) {
                continue;
            }
            added++;
            if (added >= shelfSlots) {
                break;
            }
        }
        int serial = 0;
        while (added < shelfSlots && serial < 64) {
            TradeOffer fallback = createShelfFallbackOffer(pageIndex, serial++);
            if (!tryAddShelfOffer(offers, usedKeys, fallback)) {
                continue;
            }
            added++;
        }
        if (added < shelfSlots) {
            LOGGER.warn("[MoonTrade] PAGE_SHELF_UNDERFILL page={} expected={} actual={}",
                    pageIndex, shelfSlots, added);
        }
    }

    private TradeOffer createShelfFallbackOffer(int pageIndex, int serial) {
        int idx = Math.max(0, serial % 8);
        if (pageIndex == 1) {
            return switch (idx) {
                case 0 -> new TradeOffer(new TradedItem(Items.IRON_INGOT, 2), new ItemStack(Items.LADDER, 64), 8, 0, 0.05f);
                case 1 -> new TradeOffer(new TradedItem(Items.IRON_INGOT, 2), new ItemStack(Items.COBBLED_DEEPSLATE, 64), 8, 0, 0.05f);
                case 2 -> new TradeOffer(new TradedItem(Items.IRON_INGOT, 2), new ItemStack(Items.SCAFFOLDING, 48), 8, 0, 0.05f);
                case 3 -> new TradeOffer(new TradedItem(Items.IRON_INGOT, 3), new ItemStack(Items.RAIL, 48), 8, 0, 0.05f);
                case 4 -> new TradeOffer(new TradedItem(Items.IRON_INGOT, 3), new ItemStack(Items.BRICKS, 64), 8, 0, 0.05f);
                case 5 -> new TradeOffer(new TradedItem(Items.IRON_INGOT, 2), new ItemStack(Items.GLASS_PANE, 64), 8, 0, 0.05f);
                case 6 -> new TradeOffer(new TradedItem(Items.IRON_INGOT, 2), new ItemStack(Items.STONE_BRICKS, 64), 8, 0, 0.05f);
                default -> new TradeOffer(new TradedItem(Items.IRON_INGOT, 2), new ItemStack(Items.OAK_PLANKS, 64), 8, 0, 0.05f);
            };
        }
        if (pageIndex == 2) {
            return switch (idx) {
                case 0 -> new TradeOffer(new TradedItem(Items.EMERALD, 9), Optional.of(new TradedItem(ModItems.SILVER_NOTE, 1)),
                        createEnchantedBook(Enchantments.KNOCKBACK, 2), 1, 0, 0f);
                case 1 -> new TradeOffer(new TradedItem(Items.EMERALD, 9), Optional.of(new TradedItem(ModItems.SILVER_NOTE, 1)),
                        createEnchantedBook(Enchantments.SMITE, 4), 1, 0, 0f);
                case 2 -> new TradeOffer(new TradedItem(Items.EMERALD, 10), Optional.of(new TradedItem(ModItems.SILVER_NOTE, 1)),
                        createEnchantedBook(Enchantments.PROTECTION, 3), 1, 0, 0f);
                case 3 -> new TradeOffer(new TradedItem(Items.EMERALD, 10), Optional.of(new TradedItem(ModItems.SILVER_NOTE, 1)),
                        createEnchantedBook(Enchantments.POWER, 3), 1, 0, 0f);
                case 4 -> new TradeOffer(new TradedItem(Items.EMERALD, 6), Optional.of(new TradedItem(ModItems.SILVER_NOTE, 1)),
                        createEnchantedTool(Items.IRON_AXE, Enchantments.EFFICIENCY, 2, Enchantments.UNBREAKING, 1), 3, 0, 0.05f);
                case 5 -> new TradeOffer(new TradedItem(Items.EMERALD, 6), Optional.of(new TradedItem(ModItems.SILVER_NOTE, 1)),
                        createEnchantedTool(Items.IRON_SHOVEL, Enchantments.EFFICIENCY, 2, Enchantments.UNBREAKING, 1), 3, 0, 0.05f);
                case 6 -> new TradeOffer(new TradedItem(Items.EMERALD, 6), Optional.of(new TradedItem(ModItems.SILVER_NOTE, 1)),
                        createEnchantedTool(Items.IRON_HOE, Enchantments.EFFICIENCY, 2, Enchantments.UNBREAKING, 1), 3, 0, 0.05f);
                default -> new TradeOffer(new TradedItem(Items.EMERALD, 10), Optional.of(new TradedItem(ModItems.SILVER_NOTE, 1)),
                        createEnchantedBook(Enchantments.UNBREAKING, 3), 1, 0, 0f);
            };
        }
        if (pageIndex == 3) {
            return switch (idx) {
                case 0 -> new TradeOffer(new TradedItem(ModItems.MYSTERIOUS_COIN, 1), createEnchantedBook(Enchantments.SWEEPING_EDGE, 3), 1, 0, 0f);
                case 1 -> new TradeOffer(new TradedItem(ModItems.MYSTERIOUS_COIN, 1), createEnchantedBook(Enchantments.KNOCKBACK, 2), 1, 0, 0f);
                case 2 -> new TradeOffer(new TradedItem(ModItems.MYSTERIOUS_COIN, 1), createEnchantedBook(Enchantments.LOOTING, 3), 1, 0, 0f);
                case 3 -> new TradeOffer(new TradedItem(ModItems.MYSTERIOUS_COIN, 1), createEnchantedBook(Enchantments.EFFICIENCY, 4), 1, 0, 0f);
                case 4 -> new TradeOffer(new TradedItem(Items.IRON_INGOT, 16), new ItemStack(ModItems.SIGIL, 1), 3, 0, 0f);
                case 5 -> new TradeOffer(new TradedItem(Items.REDSTONE, 24), new ItemStack(ModItems.SIGIL, 1), 3, 0, 0f);
                case 6 -> new TradeOffer(new TradedItem(ModItems.SILVER_NOTE, 12), Optional.of(new TradedItem(Items.EMERALD, 4)),
                        new ItemStack(TransitionalArmorItems.CARGO_PANTS), 2, 0, 0.05f);
                default -> new TradeOffer(new TradedItem(ModItems.SILVER_NOTE, 6),
                        new ItemStack(TransitionalArmorItems.WRAPPED_LEGGINGS), 3, 0, 0.05f);
            };
        }
        if (pageIndex == 4) {
            return switch (idx) {
                case 0 -> new TradeOffer(
                        new TradedItem(ModItems.ARCANE_LEDGER, 1),
                        Optional.of(new TradedItem(ModItems.TRADE_SCROLL, 1)),
                        createEnchantedBook(Enchantments.PROTECTION, 4),
                        1, 0, 0f);
                case 1 -> new TradeOffer(
                        new TradedItem(ModItems.ARCANE_LEDGER, 1),
                        Optional.of(new TradedItem(ModItems.TRADE_SCROLL, 1)),
                        createEnchantedBook(Enchantments.SHARPNESS, 4),
                        1, 0, 0f);
                case 2 -> new TradeOffer(
                        new TradedItem(ModItems.ARCANE_LEDGER, 1),
                        Optional.of(new TradedItem(ModItems.TRADE_SCROLL, 1)),
                        createEnchantedBook(Enchantments.EFFICIENCY, 4),
                        1, 0, 0f);
                case 3 -> new TradeOffer(
                        new TradedItem(ModItems.ARCANE_LEDGER, 1),
                        Optional.of(new TradedItem(ModItems.TRADE_SCROLL, 1)),
                        createEnchantedBook(Enchantments.UNBREAKING, 3),
                        1, 0, 0f);
                case 4 -> new TradeOffer(
                        new TradedItem(ModItems.ARCANE_LEDGER, 1),
                        Optional.of(new TradedItem(ModItems.MYSTERIOUS_COIN, 1)),
                        createEnchantedBook(Enchantments.SWEEPING_EDGE, 3),
                        1, 0, 0f);
                case 5 -> new TradeOffer(
                        new TradedItem(ModItems.ARCANE_LEDGER, 1),
                        Optional.of(new TradedItem(ModItems.MYSTERIOUS_COIN, 1)),
                        createEnchantedBook(Enchantments.KNOCKBACK, 2),
                        1, 0, 0f);
                case 6 -> new TradeOffer(
                        new TradedItem(ModItems.ARCANE_LEDGER, 1),
                        Optional.of(new TradedItem(ModItems.SILVER_NOTE, 2)),
                        new ItemStack(Items.EXPERIENCE_BOTTLE, 16),
                        1, 0, 0f);
                default -> new TradeOffer(
                        new TradedItem(ModItems.ARCANE_LEDGER, 1),
                        Optional.of(new TradedItem(ModItems.SILVER_NOTE, 2)),
                        new ItemStack(Items.END_CRYSTAL, 4),
                        1, 0, 0f);
            };
        }
        return null;
    }

    private static long deriveShelfSeed(UUID merchantUuid, UUID playerUuid, int pageIndex, int refreshNonce) {
        long seed = derivePageShuffleSeed(merchantUuid, pageIndex);
        long playerBits = playerUuid == null ? 0L
                : playerUuid.getMostSignificantBits() ^ Long.rotateLeft(playerUuid.getLeastSignificantBits(), 13);
        seed ^= Long.rotateLeft(playerBits, 23);
        seed ^= (long) Math.max(0, refreshNonce) * 0xD6E8FEB86659FD93L;
        return seed;
    }

    private void refillPageToExpectedSize(TradeOfferList offers, int pageStart, int logicalPage, int expectedSize) {
        int safeStart = Math.max(0, pageStart);
        int boundedEnd = Math.min(offers.size(), safeStart + Math.max(0, expectedSize));
        int currentCount = Math.max(0, boundedEnd - safeStart);
        if (currentCount >= expectedSize) {
            return;
        }

        Set<String> existingKeys = new HashSet<>();
        for (int i = safeStart; i < boundedEnd; i++) {
            existingKeys.add(exactOfferKey(offers.get(i)));
        }

        int inserted = 0;
        int serial = 0;
        while (currentCount + inserted < expectedSize && serial < 256) {
            TradeOffer fallback = createShelfFallbackOffer(logicalPage, serial++);
            if (fallback == null) {
                continue;
            }
            if (!isReleaseSafeRefillOffer(fallback)) {
                continue;
            }
            String key = exactOfferKey(fallback);
            if (!existingKeys.add(key)) {
                continue;
            }
            int insertAt = Math.min(offers.size(), safeStart + currentCount + inserted);
            offers.add(insertAt, fallback);
            inserted++;
        }
        int emergencyAttempts = 0;
        while (currentCount + inserted < expectedSize && emergencyAttempts < 256) {
            emergencyAttempts++;
            TradeOffer emergencyFallback = createEmergencyPageFallbackOffer(logicalPage);
            if (!isReleaseSafeRefillOffer(emergencyFallback)) {
                LOGGER.warn(
                        "[MoonTrade] PAGE_REFILL_BLOCKED page={} reason=emergency_fallback_not_release_safe source=final_sanitize_refill",
                        logicalPage);
                break;
            }
            int insertAt = Math.min(offers.size(), safeStart + currentCount + inserted);
            offers.add(insertAt, emergencyFallback);
            inserted++;
        }
        if (inserted > 0) {
            LOGGER.warn(
                    "[MoonTrade] PAGE_REFILL_APPLY page={} expected={} inserted={} source=final_sanitize_refill",
                    logicalPage,
                    expectedSize,
                    inserted);
        }
    }

    private boolean isReleaseSafeRefillOffer(TradeOffer offer) {
        if (offer == null) {
            return false;
        }
        if (TradeConfig.DEBUG_TRADES) {
            return true;
        }
        if (isReleaseForbiddenCoinOutput(offer)) {
            return false;
        }
        if (isReleaseForbiddenCoinMintOffer(offer)) {
            return false;
        }
        return !RELEASE_FORBIDDEN_OUTPUTS.contains(offer.getSellItem().getItem());
    }

    private TradeOffer createEmergencyPageFallbackOffer(int logicalPage) {
        if (logicalPage == 2) {
            return new TradeOffer(
                    new TradedItem(Items.EMERALD, 10),
                    Optional.of(new TradedItem(ModItems.SILVER_NOTE, 1)),
                    createEnchantedBook(Enchantments.UNBREAKING, 3),
                    1, 0, 0f);
        }
        if (logicalPage == 3) {
            return new TradeOffer(
                    new TradedItem(ModItems.MYSTERIOUS_COIN, 1),
                    createEnchantedBook(Enchantments.LOOTING, 3),
                    1, 0, 0f);
        }
        if (logicalPage == 4) {
            return new TradeOffer(
                    new TradedItem(ModItems.ARCANE_LEDGER, 1),
                    Optional.of(new TradedItem(ModItems.TRADE_SCROLL, 1)),
                    createEnchantedBook(Enchantments.PROTECTION, 4),
                    1, 0, 0f);
        }
        return new TradeOffer(
                new TradedItem(Items.IRON_INGOT, 2),
                new ItemStack(Items.TORCH, 64),
                8, 0, 0.05f);
    }

    private void refillAndVerifyFinalPages(TradeOfferList offers, int[] logicalPages) {
        if (logicalPages == null || logicalPages.length == 0) {
            return;
        }
        for (int i = 0; i < logicalPages.length; i++) {
            int logicalPage = logicalPages[i];
            int pageStart = i * TRADE_PAGE_SIZE;
            refillPageToExpectedSize(offers, pageStart, logicalPage, TRADE_PAGE_SIZE);
            verifyPageSize("PAGE" + logicalPage, offers, pageStart, TRADE_PAGE_SIZE);
        }
    }

    private void verifyPageSize(String pageKey, TradeOfferList offers, int pageStart, int expectedSize) {
        int safeStart = Math.max(0, pageStart);
        int boundedEnd = Math.min(offers.size(), safeStart + Math.max(0, expectedSize));
        int actual = Math.max(0, boundedEnd - safeStart);
        if (actual != expectedSize) {
            LOGGER.warn("[MoonTrade] PAGE_SIZE_MISMATCH page={} expected={} actual={}", pageKey, expectedSize, actual);
        }
    }

    private void deterministicShuffleBaseSegment(TradeOfferList offers,
            int startInclusive,
            int endExclusive,
            int pageIndex,
            Set<Integer> fixedOffsets) {
        int start = Math.max(0, startInclusive);
        int end = Math.min(offers.size(), endExclusive);
        int size = end - start;
        if (size <= 1) {
            return;
        }
        ArrayList<Integer> movableOffsets = new ArrayList<>();
        ArrayList<TradeOffer> movableOffers = new ArrayList<>();
        for (int offset = 0; offset < size; offset++) {
            if (fixedOffsets.contains(offset)) {
                continue;
            }
            movableOffsets.add(offset);
            movableOffers.add(offers.get(start + offset));
        }
        if (movableOffers.size() <= 1) {
            return;
        }
        Collections.shuffle(movableOffers, new Random(derivePageShuffleSeed(this.getUuid(), pageIndex)));
        for (int i = 0; i < movableOffsets.size(); i++) {
            offers.set(start + movableOffsets.get(i), movableOffers.get(i));
        }
    }

    private static long derivePageShuffleSeed(UUID merchantUuid, int pageIndex) {
        if (merchantUuid == null) {
            return 0x9E3779B97F4A7C15L ^ (long) pageIndex;
        }
        long msb = merchantUuid.getMostSignificantBits();
        long lsb = merchantUuid.getLeastSignificantBits();
        long seed = 0x9E3779B97F4A7C15L;
        seed ^= Long.rotateLeft(msb, 17);
        seed ^= Long.rotateLeft(lsb, 41);
        seed ^= (long) (pageIndex + 1) * 0xBF58476D1CE4E5B9L;
        return seed;
    }

    private void normalizeMerchantExperienceInPlace(TradeOfferList offers) {
        for (int i = 0; i < offers.size(); i++) {
            TradeOffer offer = offers.get(i);
            if (offer.getMerchantExperience() == 0) {
                continue;
            }
            TradeOffer normalized = new TradeOffer(
                    new TradedItem(offer.getOriginalFirstBuyItem().getItem(), offer.getOriginalFirstBuyItem().getCount()),
                    offer.getSecondBuyItem().map(
                            traded -> new TradedItem(traded.itemStack().getItem(), traded.itemStack().getCount())),
                    offer.getSellItem().copy(),
                    offer.getUses(),
                    offer.getMaxUses(),
                    0,
                    offer.getPriceMultiplier(),
                    offer.getDemandBonus());
            normalized.setSpecialPrice(offer.getSpecialPrice());
            offers.set(i, normalized);
        }
    }

    private static Map<String, OfferUsageState> snapshotOfferUsageStates(TradeOfferList offers) {
        Map<String, OfferUsageState> snapshot = new java.util.HashMap<>();
        for (TradeOffer offer : offers) {
            if (offer.getUses() <= 0 && offer.getDemandBonus() == 0 && offer.getSpecialPrice() == 0) {
                continue;
            }
            snapshot.put(exactOfferKey(offer),
                    new OfferUsageState(offer.getUses(), offer.getDemandBonus(), offer.getSpecialPrice()));
        }
        return snapshot;
    }

    private void restoreOfferUsageStatesInPlace(TradeOfferList offers, Map<String, OfferUsageState> snapshot) {
        if (snapshot == null || snapshot.isEmpty()) {
            return;
        }
        for (int i = 0; i < offers.size(); i++) {
            TradeOffer offer = offers.get(i);
            OfferUsageState state = snapshot.get(exactOfferKey(offer));
            if (state == null) {
                continue;
            }
            int restoredUses = Math.min(Math.max(0, state.uses()), offer.getMaxUses());
            TradeOffer restored = new TradeOffer(
                    new TradedItem(offer.getOriginalFirstBuyItem().getItem(), offer.getOriginalFirstBuyItem().getCount()),
                    offer.getSecondBuyItem().map(
                            traded -> new TradedItem(traded.itemStack().getItem(), traded.itemStack().getCount())),
                    offer.getSellItem().copy(),
                    restoredUses,
                    offer.getMaxUses(),
                    offer.getMerchantExperience(),
                    offer.getPriceMultiplier(),
                    state.demandBonus());
            restored.setSpecialPrice(state.specialPrice());
            offers.set(i, restored);
        }
    }

    private void sanitizeReleaseOffersInPlace(TradeOfferList offers, String sourceTag) {
        if (TradeConfig.DEBUG_TRADES) {
            return;
        }
        int removed = 0;
        for (int i = 0; i < offers.size(); i++) {
            TradeOffer offer = offers.get(i);
            Item sellItem = offer.getSellItem().getItem();
            if (!RELEASE_FORBIDDEN_OUTPUTS.contains(sellItem)) {
                continue;
            }
            LOGGER.warn(
                    "[MoonTrade] RELEASE_SANITIZE_REMOVE source={} sellItem={} offerKey={}",
                    sourceTag, Registries.ITEM.getId(sellItem), exactOfferKey(offer));
            offers.remove(i);
            i--;
            removed++;
        }
        int remain = countForbiddenReleaseOutputs(offers);
        if (removed > 0 || remain > 0) {
            LOGGER.warn("[MoonTrade] RELEASE_SANITIZE_SUMMARY source={} removed={} remainingForbidden={}",
                    sourceTag, removed, remain);
        }
        if (remain > 0) {
            for (int i = 0; i < offers.size(); i++) {
                TradeOffer offer = offers.get(i);
                Item sellItem = offer.getSellItem().getItem();
                if (!RELEASE_FORBIDDEN_OUTPUTS.contains(sellItem)) {
                    continue;
                }
                LOGGER.warn(
                        "[MoonTrade] RELEASE_SANITIZE_FORCE_REMOVE source={} sellItem={} offerKey={}",
                        sourceTag, Registries.ITEM.getId(sellItem), exactOfferKey(offer));
                offers.remove(i);
                i--;
            }
        }
    }

    /**
     * 创建带双附魔的工具 ItemStack（1.21.1 component API）。
     */
    private ItemStack createEnchantedTool(
            Item tool,
            RegistryKey<Enchantment> ench1Key, int level1,
            RegistryKey<Enchantment> ench2Key, int level2) {
        ItemStack stack = new ItemStack(tool, 1);
        if (this.getEntityWorld() == null) {
            LOGGER.warn("[MoonTrade] Entity world unavailable, returning plain tool");
            return stack;
        }
        Registry<Enchantment> reg = this.getEntityWorld().getRegistryManager().get(RegistryKeys.ENCHANTMENT);
        if (reg == null) {
            LOGGER.warn("[MoonTrade] Enchantment registry unavailable from world manager, returning plain tool");
            return stack;
        }
        ItemEnchantmentsComponent.Builder builder =
                new ItemEnchantmentsComponent.Builder(ItemEnchantmentsComponent.DEFAULT);
        boolean added = false;
        Optional<? extends net.minecraft.registry.entry.RegistryEntry.Reference<Enchantment>> ench1 = reg.getEntry(ench1Key);
        if (ench1.isPresent()) {
            builder.add(ench1.get(), level1);
            added = true;
        }
        Optional<? extends net.minecraft.registry.entry.RegistryEntry.Reference<Enchantment>> ench2 = reg.getEntry(ench2Key);
        if (ench2.isPresent()) {
            builder.add(ench2.get(), level2);
            added = true;
        }
        if (!added) {
            LOGGER.warn("[MoonTrade] No enchantments resolved for tool {}, returning plain stack",
                    Registries.ITEM.getId(tool));
            return stack;
        }
        stack.set(DataComponentTypes.ENCHANTMENTS, builder.build());
        return stack;
    }

    /**
     * 创建带 stored enchantment 的附魔书 ItemStack（1.21.1 component API）。
     * 使用 STORED_ENCHANTMENTS 而非 ENCHANTMENTS，与原版附魔书行为一致。
     */
    private ItemStack createEnchantedBook(RegistryKey<Enchantment> enchKey, int level) {
        ItemStack stack = new ItemStack(Items.ENCHANTED_BOOK, 1);
        if (this.getEntityWorld() == null) {
            LOGGER.warn("[MoonTrade] Entity world unavailable, returning plain enchanted book");
            return stack;
        }
        Registry<Enchantment> reg = this.getEntityWorld().getRegistryManager().get(RegistryKeys.ENCHANTMENT);
        if (reg == null) {
            LOGGER.warn("[MoonTrade] Enchantment registry unavailable from world manager, returning plain enchanted book");
            return stack;
        }
        ItemEnchantmentsComponent.Builder builder =
                new ItemEnchantmentsComponent.Builder(ItemEnchantmentsComponent.DEFAULT);
        Optional<? extends net.minecraft.registry.entry.RegistryEntry.Reference<Enchantment>> ench = reg.getEntry(enchKey);
        if (ench.isEmpty()) {
            LOGGER.warn("[MoonTrade] Enchantment key {} not resolved, returning plain enchanted book", enchKey.getValue());
            return stack;
        }
        builder.add(ench.get(), level);
        stack.set(DataComponentTypes.STORED_ENCHANTMENTS, builder.build());
        return stack;
    }

    /**
     * DEBUG-ONLY: 仅在 TradeConfig.DEBUG_TRADES == true 时出现。
     * 发布版默认隐藏。
     */
    private void addDebugOffers(TradeOfferList offers) {
        // D-01: 5E + Book -> EnchBook(Fire Aspect II)
        offers.add(new TradeOffer(
                new TradedItem(Items.EMERALD, 5),
                Optional.of(new TradedItem(Items.BOOK, 1)),
                createEnchantedBook(Enchantments.FIRE_ASPECT, 2),
                64, 0, 0.0f));
        // D-02: 5E + Book -> EnchBook(Looting II)
        offers.add(new TradeOffer(
                new TradedItem(Items.EMERALD, 5),
                Optional.of(new TradedItem(Items.BOOK, 1)),
                createEnchantedBook(Enchantments.LOOTING, 2),
                64, 0, 0.0f));
        LOGGER.info("[MoonTrade] DEBUG_OFFERS_ADDED count=2");
    }

    /**
     * PAGE 3 / ARCANE: 解锁奖励 + 高门槛实用交易 (arcane 解锁后可见)
     * 所有核心奖励均引入 Scroll/Ticket 门槛输入，并保持 maxUses=1 + 领取标记。
     */
    private void addArcaneBaseOffers(TradeOfferList offers,
            MerchantUnlockState.Progress progress,
            String variantKey) {
        int added = 0;
        int skippedClaimed = 0;

        // P3-01: Scroll + Silver -> 16 Experience Bottle
        if (addArcaneOfferIfUnclaimed(offers, progress, variantKey, ARCANE_REWARD_P3_01, new TradeOffer(
                new TradedItem(ModItems.TRADE_SCROLL, TradeConfig.ARCANE_SCROLL_COST),
                Optional.of(new TradedItem(ModItems.SILVER_NOTE, TradeConfig.ARCANE_P3_01_SILVER_COST)),
                new ItemStack(Items.EXPERIENCE_BOTTLE, 16),
                1, 15, 0.0f))) {
            added++;
        } else {
            skippedClaimed++;
        }
        // P3-02: Scroll + Silver -> 8 Ender Pearl
        if (addArcaneOfferIfUnclaimed(offers, progress, variantKey, ARCANE_REWARD_P3_02, new TradeOffer(
                new TradedItem(ModItems.TRADE_SCROLL, TradeConfig.ARCANE_SCROLL_COST),
                Optional.of(new TradedItem(ModItems.SILVER_NOTE, TradeConfig.ARCANE_P3_02_SILVER_COST)),
                new ItemStack(Items.ENDER_PEARL, 8),
                1, 15, 0.0f))) {
            added++;
        } else {
            skippedClaimed++;
        }
        // P3-03: Ticket + Silver -> 1 Totem of Undying
        if (addArcaneOfferIfUnclaimed(offers, progress, variantKey, ARCANE_REWARD_P3_03, new TradeOffer(
                new TradedItem(ModItems.MERCHANT_MARK, TradeConfig.ARCANE_TICKET_COST),
                Optional.of(new TradedItem(ModItems.SILVER_NOTE, TradeConfig.ARCANE_P3_03_SILVER_COST)),
                new ItemStack(Items.TOTEM_OF_UNDYING, 1),
                1, 20, 0.0f))) {
            added++;
        } else {
            skippedClaimed++;
        }
        // P3-04: Scroll + Silver -> 1 Golden Apple
        if (addArcaneOfferIfUnclaimed(offers, progress, variantKey, ARCANE_REWARD_P3_04, new TradeOffer(
                new TradedItem(ModItems.TRADE_SCROLL, TradeConfig.ARCANE_SCROLL_COST),
                Optional.of(new TradedItem(ModItems.SILVER_NOTE, TradeConfig.ARCANE_P3_04_SILVER_COST)),
                new ItemStack(Items.GOLDEN_APPLE, 1),
                1, 10, 0.0f))) {
            added++;
        } else {
            skippedClaimed++;
        }
        // P3-05: Ticket + Silver -> 1 Sacrifice
        if (addArcaneOfferIfUnclaimed(offers, progress, variantKey, ARCANE_REWARD_P3_05, new TradeOffer(
                new TradedItem(ModItems.MERCHANT_MARK, TradeConfig.ARCANE_TICKET_COST),
                Optional.of(new TradedItem(ModItems.SILVER_NOTE, TradeConfig.ARCANE_P3_05_SILVER_COST)),
                new ItemStack(ModItems.SACRIFICE, 1),
                1, 15, 0.0f))) {
            added++;
        } else {
            skippedClaimed++;
        }
        // P3-06: Ticket + Silver -> 12 Silver Note
        if (addArcaneOfferIfUnclaimed(offers, progress, variantKey, ARCANE_REWARD_P3_06, new TradeOffer(
                new TradedItem(ModItems.MERCHANT_MARK, TradeConfig.ARCANE_TICKET_COST),
                Optional.of(new TradedItem(ModItems.SILVER_NOTE, TradeConfig.ARCANE_P3_06_SILVER_COST)),
                new ItemStack(ModItems.SILVER_NOTE, 12),
                1, 10, 0.0f))) {
            added++;
        } else {
            skippedClaimed++;
        }
        // P3-07: Scroll + Silver -> 4 Blaze Powder
        if (addArcaneOfferIfUnclaimed(offers, progress, variantKey, ARCANE_REWARD_P3_07, new TradeOffer(
                new TradedItem(ModItems.TRADE_SCROLL, TradeConfig.ARCANE_SCROLL_COST),
                Optional.of(new TradedItem(ModItems.SILVER_NOTE, TradeConfig.ARCANE_P3_07_SILVER_COST)),
                new ItemStack(Items.BLAZE_POWDER, 4),
                1, 10, 0.0f))) {
            added++;
        } else {
            skippedClaimed++;
        }
        // P3-08: Ticket + Silver -> 1 Ancient Debris
        if (addArcaneOfferIfUnclaimed(offers, progress, variantKey, ARCANE_REWARD_P3_08, new TradeOffer(
                new TradedItem(ModItems.MERCHANT_MARK, TradeConfig.ARCANE_TICKET_COST),
                Optional.of(new TradedItem(ModItems.SILVER_NOTE, TradeConfig.ARCANE_P3_08_SILVER_COST)),
                new ItemStack(Items.ANCIENT_DEBRIS, 1),
                1, 30, 0.0f))) {
            added++;
        } else {
            skippedClaimed++;
        }

        LOGGER.info("[MoonTrade] ARCANE_BASE_OFFERS_ADDED count={} skippedClaimed={} merchant={} variant={}",
                added, skippedClaimed, this.getUuid(), variantKey);
    }

    /**
     * Task E: 按变体添加特色交易（过渡装备 + 1 特色物品）
     * 主货币: Silver Note，绿宝石仅作少量附加税
     *
     * Variant Anchors: 每变体额外固定 1 件 A 特效过渡装备（按主题映射，必出）。
     */
    private void addVariantSpecialtyOffers(TradeOfferList offers) {
        MerchantVariant variant = variantOf(this.getType());
        LOGGER.info("[MoonTrade] VARIANT_SPECIALTY_BUILD variant={}", variant.typeKey);

        // ---- A 特效过渡装备固定锚点（Normal 页必出） ----
        Item aAnchorItem = TradeConfig.variantAAnchor().get(variant.name());
        if (aAnchorItem != null) {
            addBaseOfferWithLoopGuard(offers, new TradeOffer(
                    new TradedItem(ModItems.SILVER_NOTE, TradeConfig.A_ANCHOR_SILVER_COST),
                    Optional.of(new TradedItem(Items.EMERALD, TradeConfig.A_ANCHOR_EMERALD_TAX)),
                    new ItemStack(aAnchorItem),
                    TradeConfig.A_ANCHOR_MAX_USES, 15, 0.05f),
                    "variant_a_anchor_" + variant.typeKey);
        }

        switch (variant) {
            case STANDARD -> {
                addBaseOfferWithLoopGuard(offers, new TradeOffer(
                        new TradedItem(ModItems.SILVER_NOTE, 12),
                        Optional.of(new TradedItem(Items.EMERALD, 4)),
                        new ItemStack(TransitionalArmorItems.SCAVENGER_GOGGLES), 2, 15, 0.05f),
                        "transitional_scavenger_goggles");
                addBaseOfferWithLoopGuard(offers, new TradeOffer(
                        new TradedItem(ModItems.SILVER_NOTE, 6),
                        new ItemStack(TransitionalArmorItems.WRAPPED_LEGGINGS), 3, 10, 0.05f),
                        "transitional_wrapped_leggings");
                addBaseOfferWithLoopGuard(offers, new TradeOffer(
                        new TradedItem(ModItems.MYSTERIOUS_COIN, 1),
                        createEnchantedBook(Enchantments.SWEEPING_EDGE, 3), 1, 0, 0.0f), "specialty_sweeping_edge_3");
            }
            case ARID -> {
                addBaseOfferWithLoopGuard(offers, new TradeOffer(
                        new TradedItem(ModItems.SILVER_NOTE, 12),
                        Optional.of(new TradedItem(Items.EMERALD, 4)),
                        new ItemStack(TransitionalArmorItems.CAST_IRON_SALLET), 2, 15, 0.05f),
                        "transitional_cast_iron_sallet");
                addBaseOfferWithLoopGuard(offers, new TradeOffer(
                        new TradedItem(ModItems.SILVER_NOTE, 6),
                        new ItemStack(TransitionalArmorItems.REINFORCED_GREAVES), 3, 10, 0.05f),
                        "transitional_reinforced_greaves");
                addBaseOfferWithLoopGuard(offers, new TradeOffer(
                        new TradedItem(ModItems.SILVER_NOTE, 5),
                        new ItemStack(Items.FIRE_CHARGE, 8), 5, 5, 0.05f), "specialty_fire_charge");
            }
            case COLD -> {
                addBaseOfferWithLoopGuard(offers, new TradeOffer(
                        new TradedItem(ModItems.SILVER_NOTE, 12),
                        Optional.of(new TradedItem(Items.EMERALD, 4)),
                        new ItemStack(TransitionalArmorItems.SANCTIFIED_HOOD), 2, 15, 0.05f),
                        "transitional_sanctified_hood");
                addBaseOfferWithLoopGuard(offers, new TradeOffer(
                        new TradedItem(ModItems.SILVER_NOTE, 6),
                        new ItemStack(TransitionalArmorItems.STANDARD_IRON_BOOTS), 3, 10, 0.05f),
                        "transitional_standard_iron_boots");
                addBaseOfferWithLoopGuard(offers, new TradeOffer(
                        new TradedItem(ModItems.SILVER_NOTE, 5),
                        new ItemStack(Items.POWDER_SNOW_BUCKET, 1), 3, 5, 0.05f), "specialty_powder_snow");
            }
            case WET -> {
                addBaseOfferWithLoopGuard(offers, new TradeOffer(
                        new TradedItem(ModItems.SILVER_NOTE, 12),
                        Optional.of(new TradedItem(Items.EMERALD, 4)),
                        new ItemStack(TransitionalArmorItems.REACTIVE_BUG_PLATE), 2, 15, 0.05f),
                        "transitional_reactive_bug_plate");
                addBaseOfferWithLoopGuard(offers, new TradeOffer(
                        new TradedItem(ModItems.SILVER_NOTE, 6),
                        new ItemStack(TransitionalArmorItems.PENITENT_BOOTS), 3, 10, 0.05f),
                        "transitional_penitent_boots");
                addBaseOfferWithLoopGuard(offers, new TradeOffer(
                        new TradedItem(ModItems.SILVER_NOTE, 5),
                        new ItemStack(Items.LILY_PAD, 16), 5, 5, 0.05f), "specialty_lily_pad");
            }
            case EXOTIC -> {
                addBaseOfferWithLoopGuard(offers, new TradeOffer(
                        new TradedItem(ModItems.SILVER_NOTE, 12),
                        Optional.of(new TradedItem(Items.EMERALD, 4)),
                        new ItemStack(TransitionalArmorItems.RITUAL_ROBE), 2, 15, 0.05f), "transitional_ritual_robe");
                addBaseOfferWithLoopGuard(offers, new TradeOffer(
                        new TradedItem(ModItems.SILVER_NOTE, 6),
                        new ItemStack(TransitionalArmorItems.CUSHION_HIKING_BOOTS), 3, 10, 0.05f),
                        "transitional_cushion_hiking_boots");
                addBaseOfferWithLoopGuard(offers, new TradeOffer(
                        new TradedItem(ModItems.SILVER_NOTE, 5),
                        new ItemStack(Items.COCOA_BEANS, 16), 5, 5, 0.05f), "specialty_cocoa_beans");
            }
        }
    }

    private boolean addOfferUniqueBySellItem(TradeOfferList offers, TradeOffer offer, String tradeDesc) {
        ItemStack sellStack = offer.getSellItem();
        Item sellItem = sellStack.getItem();
        if (!TradeConfig.DEBUG_TRADES && isReleaseForbiddenCoinOutput(offer)) {
            LOGGER.warn("[MoonTrade] MM_TRADE_SKIP trade={} reason=release_coin_output_forbidden", tradeDesc);
            return false;
        }
        boolean katanaSell = isKatanaSellItem(sellItem);
        String dedupKey = katanaSell ? exactOfferKey(offer) : sellDedupKey(sellStack);
        for (TradeOffer existing : offers) {
            Item existingSellItem = existing.getSellItem().getItem();
            boolean existingKatanaSell = isKatanaSellItem(existingSellItem);
            String existingKey = katanaSell && existingKatanaSell
                    ? exactOfferKey(existing)
                    : sellDedupKey(existing.getSellItem());
            if (dedupKey.equals(existingKey)) {
                LOGGER.warn(
                        "[MoonTrade] MM_TRADE_DEDUP_SKIP trade={} sellItem={} dedupKey={} reason=duplicate_sell_output",
                        tradeDesc, Registries.ITEM.getId(sellItem), dedupKey);
                return false;
            }
        }
        offers.add(offer);
        return true;
    }

    private static String sellDedupKey(ItemStack sellStack) {
        Item item = sellStack.getItem();
        String itemId = Registries.ITEM.getId(item).toString();
        if (!NBT_SENSITIVE_DEDUP_OUTPUTS.contains(item)) {
            return itemId;
        }
        // Stable enough for one rebuild pass: item id + count + component hash.
        return itemId + "#" + sellStack.getCount() + "#" + Integer.toHexString(hashItemStack(sellStack));
    }

    private int dedupeExactOffersInPlace(TradeOfferList offers) {
        Set<String> seen = new HashSet<>();
        int removed = 0;
        for (int i = 0; i < offers.size(); i++) {
            TradeOffer offer = offers.get(i);
            String key = exactOfferKey(offer);
            if (seen.add(key)) {
                continue;
            }
            offers.remove(i);
            i--;
            removed++;
        }
        return removed;
    }

    private static String exactOfferKey(TradeOffer offer) {
        ItemStack first = offer.getOriginalFirstBuyItem();
        ItemStack second = offer.getSecondBuyItem().map(TradedItem::itemStack).orElse(ItemStack.EMPTY);
        ItemStack sell = offer.getSellItem();
        return hashItemStack(first)
                + "|"
                + hashItemStack(second)
                + "|"
                + hashItemStack(sell)
                + "|"
                + offer.getMaxUses()
                + "|"
                + offer.getMerchantExperience()
                + "|"
                + Float.floatToIntBits(offer.getPriceMultiplier());
    }

    private void addBaseOfferWithLoopGuard(TradeOfferList offers, TradeOffer offer, String tradeDesc) {
        Item buyItem = offer.getOriginalFirstBuyItem().getItem();
        Item sellItem = offer.getSellItem().getItem();
        if (!TradeConfig.DEBUG_TRADES && isReleaseForbiddenCoinMintOffer(offer)) {
            LOGGER.warn("[MoonTrade] MM_TRADE_SKIP trade={} reason=release_coin_mint_forbidden", tradeDesc);
            return;
        }
        if (LOOP_GUARD_CURRENCIES.contains(buyItem)
                && LOOP_GUARD_CURRENCIES.contains(sellItem)
                && hasReverseCurrencyOffer(offers, buyItem, sellItem)) {
            LOGGER.warn("[MoonTrade] MM_TRADE_LOOP_GUARD removed={} reason=reverse_currency_pair_exists",
                    tradeDesc);
            return;
        }
        addOfferUniqueBySellItem(offers, offer, tradeDesc);
    }

    private boolean addArcaneOfferIfUnclaimed(TradeOfferList offers,
            MerchantUnlockState.Progress progress,
            String variantKey,
            String rewardKey,
            TradeOffer offer) {
        if (progress != null && progress.hasArcaneRewardClaimed(variantKey, rewardKey)) {
            LOGGER.info("[MoonTrade] ARCANE_REWARD_SKIP merchant={} variant={} rewardKey={} reason=already_claimed",
                    this.getUuid(), variantKey, rewardKey);
            return false;
        }
        return addOfferUniqueBySellItem(offers, offer, "arcane_reward_" + rewardKey);
    }

    private String resolveArcaneRewardKey(TradeOffer offer) {
        if (offer == null) {
            return null;
        }
        if (matchesOffer(
                offer,
                ModItems.TRADE_SCROLL,
                TradeConfig.ARCANE_SCROLL_COST,
                ModItems.SILVER_NOTE,
                TradeConfig.ARCANE_P3_01_SILVER_COST,
                Items.EXPERIENCE_BOTTLE,
                16)) {
            return ARCANE_REWARD_P3_01;
        }
        if (matchesOffer(
                offer,
                ModItems.TRADE_SCROLL,
                TradeConfig.ARCANE_SCROLL_COST,
                ModItems.SILVER_NOTE,
                TradeConfig.ARCANE_P3_02_SILVER_COST,
                Items.ENDER_PEARL,
                8)) {
            return ARCANE_REWARD_P3_02;
        }
        if (matchesOffer(
                offer,
                ModItems.MERCHANT_MARK,
                TradeConfig.ARCANE_TICKET_COST,
                ModItems.SILVER_NOTE,
                TradeConfig.ARCANE_P3_03_SILVER_COST,
                Items.TOTEM_OF_UNDYING,
                1)) {
            return ARCANE_REWARD_P3_03;
        }
        if (matchesOffer(
                offer,
                ModItems.TRADE_SCROLL,
                TradeConfig.ARCANE_SCROLL_COST,
                ModItems.SILVER_NOTE,
                TradeConfig.ARCANE_P3_04_SILVER_COST,
                Items.GOLDEN_APPLE,
                1)) {
            return ARCANE_REWARD_P3_04;
        }
        if (matchesOffer(
                offer,
                ModItems.MERCHANT_MARK,
                TradeConfig.ARCANE_TICKET_COST,
                ModItems.SILVER_NOTE,
                TradeConfig.ARCANE_P3_05_SILVER_COST,
                ModItems.SACRIFICE,
                1)) {
            return ARCANE_REWARD_P3_05;
        }
        if (matchesOffer(
                offer,
                ModItems.MERCHANT_MARK,
                TradeConfig.ARCANE_TICKET_COST,
                ModItems.SILVER_NOTE,
                TradeConfig.ARCANE_P3_06_SILVER_COST,
                ModItems.SILVER_NOTE,
                12)) {
            return ARCANE_REWARD_P3_06;
        }
        if (matchesOffer(
                offer,
                ModItems.TRADE_SCROLL,
                TradeConfig.ARCANE_SCROLL_COST,
                ModItems.SILVER_NOTE,
                TradeConfig.ARCANE_P3_07_SILVER_COST,
                Items.BLAZE_POWDER,
                4)) {
            return ARCANE_REWARD_P3_07;
        }
        if (matchesOffer(
                offer,
                ModItems.MERCHANT_MARK,
                TradeConfig.ARCANE_TICKET_COST,
                ModItems.SILVER_NOTE,
                TradeConfig.ARCANE_P3_08_SILVER_COST,
                Items.ANCIENT_DEBRIS,
                1)) {
            return ARCANE_REWARD_P3_08;
        }
        return null;
    }

    private static boolean matchesOffer(TradeOffer offer,
            Item firstBuy,
            int firstCount,
            Item secondBuyOrNull,
            int secondCount,
            Item sellItem,
            int sellCount) {
        if (offer.getOriginalFirstBuyItem().getItem() != firstBuy || offer.getOriginalFirstBuyItem().getCount() != firstCount) {
            return false;
        }
        Optional<TradedItem> second = offer.getSecondBuyItem();
        if (secondBuyOrNull == null) {
            if (second.isPresent()) {
                return false;
            }
        } else {
            if (second.isEmpty()) {
                return false;
            }
            ItemStack secondStack = second.get().itemStack();
            if (secondStack.getItem() != secondBuyOrNull || secondStack.getCount() != secondCount) {
                return false;
            }
        }
        return offer.getSellItem().isOf(sellItem) && offer.getSellItem().getCount() == sellCount;
    }

    private static boolean hasReverseCurrencyOffer(TradeOfferList offers, Item buyItem, Item sellItem) {
        for (TradeOffer existing : offers) {
            Item existingBuy = existing.getOriginalFirstBuyItem().getItem();
            Item existingSell = existing.getSellItem().getItem();
            if (existingBuy == sellItem
                    && existingSell == buyItem
                    && LOOP_GUARD_CURRENCIES.contains(existingBuy)
                    && LOOP_GUARD_CURRENCIES.contains(existingSell)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isKatanaSellItem(Item item) {
        return item != null && KATANA_WHITELIST.containsValue(item);
    }

    private static boolean isReleaseForbiddenCoinOutput(TradeOffer offer) {
        return offer != null && offer.getSellItem().isOf(ModItems.MYSTERIOUS_COIN);
    }

    private static boolean isReleaseForbiddenCoinMintOffer(TradeOffer offer) {
        if (!isReleaseForbiddenCoinOutput(offer)) {
            return false;
        }
        Item first = offer.getOriginalFirstBuyItem().getItem();
        if (first == Items.EMERALD || first == ModItems.SILVER_NOTE) {
            return true;
        }
        Optional<TradedItem> second = offer.getSecondBuyItem();
        if (second.isPresent()) {
            Item secondItem = second.get().itemStack().getItem();
            return secondItem == Items.EMERALD || secondItem == ModItems.SILVER_NOTE;
        }
        return false;
    }

    private void logPageGateCostSummary(String pageKey, TradeOfferList offers, int startIndex) {
        if (!TradeConfig.TRADE_DEBUG) {
            return;
        }
        int safeStart = Math.max(0, Math.min(startIndex, offers.size()));
        Set<String> gateItems = new LinkedHashSet<>();
        int gatedOfferCount = 0;
        for (int i = safeStart; i < offers.size(); i++) {
            TradeOffer offer = offers.get(i);
            boolean gated = collectGateCostItem(offer.getOriginalFirstBuyItem(), gateItems);
            Optional<TradedItem> second = offer.getSecondBuyItem();
            if (second.isPresent()) {
                gated |= collectGateCostItem(second.get().itemStack(), gateItems);
            }
            if (gated) {
                gatedOfferCount++;
            }
        }
        LOGGER.info(
                "[MoonTrade] PAGE_GATE_COSTS page={} merchant={} offersAdded={} gatedOffers={} gateItems={}",
                pageKey, merchantTag(), offers.size() - safeStart, gatedOfferCount,
                gateItems.isEmpty() ? "none" : String.join(",", gateItems));
    }

    private static boolean collectGateCostItem(ItemStack stack, Set<String> gateItems) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        Item item = stack.getItem();
        if (!GATE_COST_ITEMS.contains(item)) {
            return false;
        }
        gateItems.add(Registries.ITEM.getId(item).toString());
        return true;
    }

    private static boolean hasAtLeastItem(ServerPlayerEntity player, Item item, int requiredCount) {
        if (player == null || item == null || requiredCount <= 0) {
            return false;
        }
        int count = 0;
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (!stack.isOf(item)) {
                continue;
            }
            count += stack.getCount();
            if (count >= requiredCount) {
                return true;
            }
        }
        return false;
    }

    private boolean hasHiddenOffers(TradeOfferList offers) {
        for (TradeOffer offer : offers) {
            if (offer.getSellItem().isOf(ModItems.SEALED_LEDGER)
                    || offer.getSellItem().isOf(ModItems.ARCANE_LEDGER)
                    || offer.getSellItem().isOf(ModItems.SIGIL)
                    || KATANA_WHITELIST.containsValue(offer.getSellItem().getItem())) {
                return true;
            }
            ItemStack first = offer.getOriginalFirstBuyItem();
            if (first.isOf(ModItems.SIGIL)) {
                return true;
            }
            Optional<TradedItem> second = offer.getSecondBuyItem();
            if (second.isPresent() && second.get().itemStack().isOf(ModItems.SIGIL)) {
                return true;
            }
        }
        return false;
    }

    private int appendDebugScrollOffers(TradeOfferList offers) {
        int before = offers.size();
        offers.add(new TradeOffer(new TradedItem(Items.STICK, 1), new ItemStack(Items.STRING, 1), 64, 0, 0.0f));
        offers.add(new TradeOffer(new TradedItem(Items.ROTTEN_FLESH, 4), new ItemStack(Items.PAPER, 1), 64, 0, 0.0f));
        offers.add(new TradeOffer(new TradedItem(Items.FLINT, 1), new ItemStack(Items.TORCH, 4), 64, 0, 0.0f));
        offers.add(new TradeOffer(new TradedItem(Items.WHEAT_SEEDS, 8), new ItemStack(Items.APPLE, 1), 64, 0, 0.0f));
        offers.add(
                new TradeOffer(new TradedItem(Items.COBBLESTONE, 16), new ItemStack(Items.CLAY_BALL, 2), 64, 0, 0.0f));
        offers.add(new TradeOffer(new TradedItem(Items.DIRT, 16), new ItemStack(Items.BRICK, 1), 64, 0, 0.0f));
        offers.add(new TradeOffer(new TradedItem(Items.SAND, 8), new ItemStack(Items.GLASS, 2), 64, 0, 0.0f));
        offers.add(new TradeOffer(new TradedItem(Items.GRAVEL, 8), new ItemStack(Items.CLAY_BALL, 1), 64, 0, 0.0f));
        offers.add(new TradeOffer(new TradedItem(Items.KELP, 8), new ItemStack(Items.DRIED_KELP, 2), 64, 0, 0.0f));
        offers.add(new TradeOffer(new TradedItem(Items.BONE, 1), new ItemStack(Items.BONE_MEAL, 3), 64, 0, 0.0f));
        return offers.size() - before;
    }

    /**
     * DEBUG only: Append 10 visually distinct trades after a REFRESH_NORMAL
     * rebuild.
     * Each trade has a unique displayName "DBG REFRESH=<refreshSeenCount> #<i>" so
     * you can
     * confirm in-game that refresh truly rebuilt the offer list with a new
     * refreshSeenCount.
     * Uses different result items (paper, stick, dirt, etc.) for easy visual
     * distinction.
     */
    private int appendDebugRefreshOffers(TradeOfferList offers, int refreshSeenCount) {
        int before = offers.size();
        Item[] debugItems = {
                Items.PAPER, Items.STICK, Items.DIRT, Items.COBBLESTONE, Items.OAK_LOG,
                Items.SAND, Items.GRAVEL, Items.CLAY_BALL, Items.FLINT, Items.BONE
        };
        for (int i = 0; i < 10; i++) {
            ItemStack sellStack = new ItemStack(debugItems[i], 1);
            sellStack.set(
                    net.minecraft.component.DataComponentTypes.CUSTOM_NAME,
                    Text.literal("DBG REFRESH=" + refreshSeenCount + " #" + (i + 1))
                            .formatted(Formatting.RED, Formatting.BOLD));
            offers.add(new TradeOffer(
                    new TradedItem(Items.WHEAT_SEEDS, 1),
                    sellStack,
                    64, 0, 0.0f));
        }
        return offers.size() - before;
    }

    public OfferBuildAudit rebuildOffersForPlayer(ServerPlayerEntity player, OfferBuildSource source) {
        return rebuildOffersForPlayer(player, source, -1);
    }

    public OfferBuildAudit rebuildOffersForPlayer(ServerPlayerEntity player, OfferBuildSource source, int refreshedPageIndex) {
        long startNanos = System.nanoTime();
        ensureVariantIdentityIfNeeded();
        String variantKey = getVariantKey();
        TradeOfferList offers = this.getOffers();
        Map<String, OfferUsageState> usageSnapshot = snapshotOfferUsageStates(offers);
        boolean eligible = true;
        boolean unlocked = false;
        long seedForLog = -1L;
        int refreshForThisMerchant = -1;
        int[] pageRefreshNonces = new int[TRADE_TOTAL_PAGES];
        MerchantUnlockState state = null;
        MerchantUnlockState.Progress progress = null;
        KatanaOwnershipState ownershipState = null;
        String cacheResult = "BYPASS";

        if (this.getEntityWorld() instanceof ServerWorld serverWorld) {
            state = MerchantUnlockState.getServerState(serverWorld);
            progress = state.getOrCreateProgress(player.getUuid(), variantKey);
            ownershipState = KatanaOwnershipState.getServerState(serverWorld);
            unlocked = progress.isUnlockedKatanaHidden(variantKey);
            initSecretKatanaIdIfNeeded();

            for (int page = 1; page <= TRADE_TOTAL_PAGES; page++) {
                pageRefreshNonces[page - 1] = Math.max(0, progress.getShelfRefreshNonce(this.getUuid(), page));
            }
            refreshForThisMerchant = pageRefreshNonces[0] + pageRefreshNonces[1] + pageRefreshNonces[2] + pageRefreshNonces[3];
            int seedPage = refreshedPageIndex >= 0 ? (refreshedPageIndex + 1) : 1;
            seedForLog = deriveShelfSeed(this.getUuid(), player.getUuid(), seedPage, refreshForThisMerchant);
        }

        offers.clear();
        addBaseOffers(offers, player.getUuid(), progress, variantKey, pageRefreshNonces);

        if (state != null && progress != null) {
            if (unlocked) {
                addPage4Offers(offers, player, progress, variantKey, getPageRefreshNonce(pageRefreshNonces, 4));
            }
        }

        int injectedCount = 0;
        if (DEBUG_SCROLL_INJECT && unlocked) {
            injectedCount = appendDebugScrollOffers(offers);
        }
        LOGGER.debug("[MoonTrade] MM_SCROLL_INJECT enabled={} added={}",
                DEBUG_SCROLL_INJECT, injectedCount);

        int debugRefreshAdded = 0;
        if (DEBUG_REFRESH_INJECT && source == OfferBuildSource.REFRESH_NORMAL) {
            debugRefreshAdded = appendDebugRefreshOffers(offers, refreshForThisMerchant);
            LOGGER.info("[MoonTrade] MM_DEBUG_REFRESH_INJECT refreshSeenCount={} debugAdded={}",
                    refreshForThisMerchant, debugRefreshAdded);
        }

        normalizeMerchantExperienceInPlace(offers);

        int exactDuplicateRemoved = dedupeExactOffersInPlace(offers);
        if (exactDuplicateRemoved > 0) {
            LOGGER.warn("[MoonTrade] MM_TRADE_DEDUP_EXACT removed={} source={} merchant={}",
                    exactDuplicateRemoved, source, this.getUuid());
        }
        sanitizeReleaseOffersInPlace(offers, source.name());
        refillAndVerifyFinalPages(offers, unlocked ? new int[] { 1, 2, 3, 4 } : new int[] { 1, 2, 3 });
        restoreOfferUsageStatesInPlace(offers, usageSnapshot);
        if (ownershipState != null) {
            applyKatanaOwnershipSoldOut(offers, player.getUuid(), ownershipState);
            sanitizeKatanaOffersForPlayer(offers, player.getUuid(), ownershipState, source.name());
        }

        auditMinCoinPathForFirstKatana(offers, source.name(), unlocked);

        OfferCounters counters = classifyOffers(offers);
        int offersHash = computeOffersHash(offers);
        int epicSellCount = countEpicSellOffers(offers);
        int forbiddenReleaseOutputs = countForbiddenReleaseOutputs(offers);
        String forbiddenOutputIds = forbiddenReleaseOutputs > 0 ? collectForbiddenOutputIds(offers) : "none";
        int forbiddenCoinMintRoutes = countForbiddenCoinMintRoutes(offers);
        String forbiddenCoinMintRouteIds = forbiddenCoinMintRoutes > 0 ? collectForbiddenCoinMintRouteIds(offers) : "none";
        String unlockState = toUnlockState(eligible, unlocked);
        if (state != null && progress != null) {
            int cacheFingerprint = computeCacheFingerprint(
                    offersHash,
                    player.getUuid(),
                    unlockState,
                    refreshForThisMerchant,
                    seedForLog);
            int lastHash = progress.getLastSigilOffersHash(this.getUuid());
            cacheResult = (lastHash == cacheFingerprint && lastHash != 0) ? "HIT" : "MISS";
            if (lastHash != cacheFingerprint) {
                progress.setLastSigilOffersHash(this.getUuid(), cacheFingerprint);
                state.markDirty();
            }
        }

        long durationMs = Math.max(0L, (System.nanoTime() - startNanos) / 1_000_000L);
        OfferBuildAudit audit = new OfferBuildAudit(
                source.name(),
                cacheResult,
                unlockState,
                counters.totalCount(),
                counters.baseCount(),
                counters.sigilCount(),
                counters.hiddenCount(),
                offersHash,
                seedForLog,
                refreshForThisMerchant,
                durationMs);

        LOGGER.info(
                "[MoonTrade] action=REBUILD_DONE side=S player={} merchant={} source={} cache={} unlock={} offersTotal={} base={} sigil={} hidden={} offersHash={} seed={} refreshSeenCount={} durationMs={} pageSize={} debugAdded={}",
                playerTag(player), merchantTag(), audit.source(), audit.cache(), audit.unlock(),
                audit.offersTotal(), audit.baseCount(), audit.sigilCount(), audit.hiddenCount(),
                Integer.toHexString(audit.offersHash()), audit.seed(), audit.refreshSeenCount(), audit.durationMs(),
                TRADE_PAGE_SIZE, debugRefreshAdded);
        LOGGER.info(
                "[MoonTrade] action=REBUILD_GUARD_CHECK side=S player={} merchant={} source={} epicSellCount={} forbiddenReleaseOutputs={} forbiddenCoinMintRoutes={} debugTrades={} forbiddenItems={} forbiddenCoinMint={}",
                playerTag(player), merchantTag(), audit.source(), epicSellCount, forbiddenReleaseOutputs,
                forbiddenCoinMintRoutes, TradeConfig.DEBUG_TRADES, forbiddenOutputIds, forbiddenCoinMintRouteIds);
        if (!TradeConfig.DEBUG_TRADES && forbiddenReleaseOutputs > 0) {
            LOGGER.warn(
                    "[MoonTrade] RELEASE_FORBIDDEN_OUTPUT_DETECTED player={} merchant={} source={} forbiddenItems={} note=debug_trades_disabled",
                    playerTag(player), merchantTag(), audit.source(), forbiddenOutputIds);
        }
        if (!TradeConfig.DEBUG_TRADES && forbiddenCoinMintRoutes > 0) {
            LOGGER.warn(
                    "[MoonTrade] RELEASE_FORBIDDEN_COIN_MINT_DETECTED player={} merchant={} source={} routes={} note=debug_trades_disabled",
                    playerTag(player), merchantTag(), audit.source(), forbiddenCoinMintRouteIds);
        }
        return audit;
    }

    private void auditMinCoinPathForFirstKatana(TradeOfferList offers, String sourceTag, boolean unlocked) {
        if (!TradeConfig.TRADE_DEBUG || offers == null || offers.isEmpty()) {
            return;
        }

        int sealedCoinCost = Integer.MAX_VALUE;
        int unsealCoinCost = Integer.MAX_VALUE;
        int katanaCoinCost = Integer.MAX_VALUE;
        boolean hasSealedOffer = false;
        boolean hasUnsealOffer = false;
        boolean hasKatanaCoinRoute = false;

        for (TradeOffer offer : offers) {
            if (offer == null) {
                continue;
            }
            int coinCost = getCoinCost(offer);
            if (isSealedLedgerOffer(offer)) {
                hasSealedOffer = true;
                sealedCoinCost = Math.min(sealedCoinCost, coinCost);
            }
            if (isUnsealOffer(offer)) {
                hasUnsealOffer = true;
                unsealCoinCost = Math.min(unsealCoinCost, coinCost);
            }
            if (isKatanaCoinRouteOffer(offer)) {
                hasKatanaCoinRoute = true;
                katanaCoinCost = Math.min(katanaCoinCost, coinCost);
            }
        }

        int sealed = hasSealedOffer ? sealedCoinCost : -1;
        int unseal = hasUnsealOffer ? unsealCoinCost : -1;
        int katana = hasKatanaCoinRoute ? katanaCoinCost : -1;
        int minCoinToFirstKatana = (sealed >= 0 && unseal >= 0 && katana >= 0) ? sealed + unseal + katana : -1;

        LOGGER.info(
                "[MoonTrade] COIN_PATH_AUDIT source={} unlock={} sealedCoinCost={} unsealCoinCost={} katanaCoinCost={} MIN_COIN_TO_FIRST_KATANA={}",
                sourceTag,
                unlocked ? 1 : 0,
                sealed,
                unseal,
                katana,
                minCoinToFirstKatana);

        if (hasUnsealOffer && unseal != 0) {
            LOGGER.error(
                    "[MoonTrade] COIN_PATH_ASSERT_FAIL source={} reason=unseal_coin_gate_detected unsealCoinCost={}",
                    sourceTag,
                    unseal);
        }
        if (unlocked && !hasKatanaCoinRoute) {
            LOGGER.error(
                    "[MoonTrade] COIN_PATH_ASSERT_FAIL source={} reason=missing_katana_coin_route",
                    sourceTag);
        }
        if (minCoinToFirstKatana >= 0 && minCoinToFirstKatana > 2) {
            LOGGER.error(
                    "[MoonTrade] COIN_PATH_ASSERT_FAIL source={} reason=min_coin_raised expectedMax=2 actual={}",
                    sourceTag,
                    minCoinToFirstKatana);
        }
    }

    private static boolean isSealedLedgerOffer(TradeOffer offer) {
        return offer.getSellItem().isOf(ModItems.SEALED_LEDGER);
    }

    private static boolean isKatanaCoinRouteOffer(TradeOffer offer) {
        if (!offer.getOriginalFirstBuyItem().isOf(ModItems.ARCANE_LEDGER)) {
            return false;
        }
        if (!KATANA_WHITELIST.containsValue(offer.getSellItem().getItem())) {
            return false;
        }
        Optional<TradedItem> second = offer.getSecondBuyItem();
        return second.isPresent() && second.get().itemStack().isOf(ModItems.MYSTERIOUS_COIN);
    }

    private static int getCoinCost(TradeOffer offer) {
        int coinCost = 0;
        if (offer.getOriginalFirstBuyItem().isOf(ModItems.MYSTERIOUS_COIN)) {
            coinCost += offer.getOriginalFirstBuyItem().getCount();
        }
        Optional<TradedItem> second = offer.getSecondBuyItem();
        if (second.isPresent() && second.get().itemStack().isOf(ModItems.MYSTERIOUS_COIN)) {
            coinCost += second.get().itemStack().getCount();
        }
        return coinCost;
    }

    /**
     * Rebuild secret page offers for player (public for TradeActionHandler).
     * Ownership never removes chain offers; UI sold-out is handled by disabling
     * matching katana offers.
     */
    public void rebuildSecretOffersForPlayer(ServerPlayerEntity player) {
        ensureVariantIdentityIfNeeded();
        String variantKey = getVariantKey();
        TradeOfferList offers = this.getOffers();
        Map<String, OfferUsageState> usageSnapshot = snapshotOfferUsageStates(offers);
        offers.clear();

        if (!(this.getEntityWorld() instanceof ServerWorld serverWorld)) {
            return;
        }
        MerchantUnlockState state = MerchantUnlockState.getServerState(serverWorld);
        KatanaOwnershipState ownershipState = KatanaOwnershipState.getServerState(serverWorld);
        MerchantUnlockState.Progress progress = state.getOrCreateProgress(player.getUuid(), variantKey);
        boolean unlocked = progress.isUnlockedKatanaHidden(variantKey);
        if (!unlocked) {
            LOGGER.info("[MoonTrade] HIDDEN_BUILD_DENY player={} merchant={} variant={} reason=variant_not_unlocked",
                    player.getUuid(), this.getUuid(), variantKey);
            return;
        }
        initSecretKatanaIdIfNeeded();

        // Keep the katana trade chain intact; sold-out is visual-only via disable().
        addKatanaHiddenOffers(offers, player, progress, variantKey);

        int injectedCount = 0;
        if (DEBUG_SCROLL_INJECT && unlocked) {
            injectedCount = appendDebugScrollOffers(offers);
        }
        LOGGER.debug("[MoonTrade] MM_SCROLL_INJECT enabled={} added={}",
                DEBUG_SCROLL_INJECT, injectedCount);

        normalizeMerchantExperienceInPlace(offers);

        int exactDuplicateRemoved = dedupeExactOffersInPlace(offers);
        if (exactDuplicateRemoved > 0) {
            LOGGER.warn("[MoonTrade] MM_TRADE_DEDUP_EXACT removed={} source=OPEN_SECRET merchant={}",
                    exactDuplicateRemoved, this.getUuid());
        }
        sanitizeReleaseOffersInPlace(offers, OfferBuildSource.OPEN_SECRET.name());
        refillAndVerifyFinalPages(offers, new int[] { 4 });
        restoreOfferUsageStatesInPlace(offers, usageSnapshot);
        applyKatanaOwnershipSoldOut(offers, player.getUuid(), ownershipState);
        sanitizeKatanaOffersForPlayer(offers, player.getUuid(), ownershipState, OfferBuildSource.OPEN_SECRET.name());

        // P0-C FIX: Structured HIDDEN_BUILD log
        String resolvedItem = "EMPTY";
        if (!offers.isEmpty()) {
            resolvedItem = Registries.ITEM.getId(offers.get(0).getSellItem().getItem()).toString();
        }
        String skip = offers.isEmpty() ? "empty_or_resolve_failed" : "none";
        LOGGER.info(
                "[MoonTrade] HIDDEN_BUILD player={} merchant={} secretSold={} katanaId={} resolved={} offersCount={} skip={}",
                player.getUuid(), this.getUuid(), this.secretSold ? 1 : 0,
                this.secretKatanaId, resolvedItem, offers.size(), skip);
    }

    private TradeOffer createSealedLedgerOffer() {
        return new TradeOffer(
                new TradedItem(ModItems.MYSTERIOUS_COIN, 1),
                new ItemStack(ModItems.SEALED_LEDGER, 1),
                1,
                0,
                0.0f);
    }

    private TradeOffer createUnsealOffer() {
        return new TradeOffer(
                new TradedItem(ModItems.SEALED_LEDGER, 1),
                Optional.of(new TradedItem(ModItems.SIGIL, TradeConfig.UNSEAL_SIGIL_COST)),
                new ItemStack(ModItems.ARCANE_LEDGER, 1),
                1,
                0,
                0.0f);
    }

    private void addSigilOffers(TradeOfferList offers, long seed, int refreshSeenCount) {
        Random rollRng = new Random(seed);
        ArrayList<SigilOfferEntry> pool = createSigilOfferPool();
        ArrayList<SigilOfferEntry> aList = new ArrayList<>();
        ArrayList<SigilOfferEntry> bList = new ArrayList<>();
        ArrayList<SigilOfferEntry> cList = new ArrayList<>();

        for (SigilOfferEntry entry : pool) {
            switch (entry.tier) {
                case A -> aList.add(entry);
                case B -> bList.add(entry);
                case C -> cList.add(entry);
            }
        }

        int targetCount = 3 + rollRng.nextInt(3);
        boolean guaranteeB1 = refreshSeenCount >= REFRESH_GUARANTEE_COUNT;
        int maxA = guaranteeB1 ? 0 : 1;

        ArrayList<SigilOfferEntry> chosen = new ArrayList<>();

        if (guaranteeB1) {
            SigilOfferEntry b1 = extractById(bList, "B1");
            if (b1 != null) {
                chosen.add(b1);
            }
        } else {
            SigilOfferEntry firstBc = extractRandomFrom(bList, cList, rollRng);
            if (firstBc != null) {
                chosen.add(firstBc);
            }
        }

        if (maxA > 0 && !aList.isEmpty() && rollRng.nextBoolean() && chosen.size() < targetCount) {
            chosen.add(pickRandomEntry(aList, rollRng));
        }

        ArrayList<SigilOfferEntry> remaining = new ArrayList<>();
        remaining.addAll(bList);
        remaining.addAll(cList);
        if (maxA > 0) {
            remaining.addAll(aList);
        }
        Collections.shuffle(remaining, new Random(rollRng.nextLong()));

        for (SigilOfferEntry entry : remaining) {
            if (chosen.size() >= targetCount) {
                break;
            }
            chosen.add(entry);
        }

        for (SigilOfferEntry entry : chosen) {
            offers.add(entry.offer);
        }
    }

    private void addKatanaHiddenOffers(TradeOfferList offers,
            ServerPlayerEntity player,
            MerchantUnlockState.Progress progress,
            String variantKey) {
        int refreshNonce = progress == null ? 0 : Math.max(0, progress.getShelfRefreshNonce(this.getUuid(), 4));
        addPage4Offers(offers, player, progress, variantKey, refreshNonce);
    }

    private void addPage4Offers(TradeOfferList offers,
            ServerPlayerEntity player,
            MerchantUnlockState.Progress progress,
            String variantKey,
            int refreshNonce) {
        int pageStart = offers.size();
        initSecretKatanaIdIfNeeded();

        java.util.List<TradeOffer> katanaOffers = createKatanaOffers(player);
        int fixedAdded = 0;
        for (TradeOffer katanaOffer : katanaOffers) {
            offers.add(katanaOffer);
            fixedAdded++;
            if (fixedAdded >= 2) {
                break;
            }
        }
        TradeOffer reclaim = tryCreateReclaimOffer(player);
        if (reclaim != null) {
            offers.add(reclaim);
            fixedAdded++;
        }
        while (fixedAdded < HIDDEN_FIXED_COUNT) {
            TradeOffer fallback = createShelfFallbackOffer(4, fixedAdded);
            if (fallback != null) {
                offers.add(fallback);
                fixedAdded++;
            } else {
                break;
            }
        }

        ArrayList<TradeOffer> shelfCandidates = new ArrayList<>();

        TradeOfferList arcaneCandidates = new TradeOfferList();
        addArcaneBaseOffers(arcaneCandidates, progress, variantKey);
        shelfCandidates.addAll(arcaneCandidates);

        MerchantVariant variant = variantOf(this.getType());
        String variantName = variant.name();
        Item bAnchorItem = TradeConfig.variantBAnchor().get(variantName);
        if (bAnchorItem != null) {
            shelfCandidates.add(new TradeOffer(
                    new TradedItem(ModItems.MERCHANT_MARK, TradeConfig.B_ARMOR_TICKET_COST),
                    Optional.of(new TradedItem(ModItems.SILVER_NOTE, TradeConfig.B_ARMOR_SILVER_COST)),
                    new ItemStack(bAnchorItem),
                    TradeConfig.B_ARMOR_MAX_USES, 0, 0.05f));
        }
        java.util.List<Item> randomPool = TradeConfig.variantBRandomPool().getOrDefault(variantName, java.util.List.of());
        for (Item item : randomPool) {
            shelfCandidates.add(new TradeOffer(
                    new TradedItem(ModItems.MERCHANT_MARK, TradeConfig.B_RANDOM_TICKET_COST),
                    Optional.of(new TradedItem(ModItems.SILVER_NOTE, TradeConfig.B_RANDOM_SILVER_COST)),
                    new ItemStack(item),
                    TradeConfig.B_ARMOR_MAX_USES, 0, 0.05f));
        }
        java.util.List<Item> guidedPool = TradeConfig.variantBScrollGuidedPool().getOrDefault(variantName, java.util.List.of());
        for (Item item : guidedPool) {
            shelfCandidates.add(new TradeOffer(
                    new TradedItem(ModItems.TRADE_SCROLL, TradeConfig.B_RANDOM_SCROLL_COST),
                    Optional.of(new TradedItem(ModItems.SILVER_NOTE, TradeConfig.B_RANDOM_SILVER_COST)),
                    new ItemStack(item),
                    TradeConfig.B_ARMOR_MAX_USES, 0, 0.05f));
        }

        addBaseOfferCandidate(shelfCandidates, new TradeOffer(
                new TradedItem(ModItems.ARCANE_LEDGER, 1),
                Optional.of(new TradedItem(ModItems.TRADE_SCROLL, 1)),
                createEnchantedBook(Enchantments.PROTECTION, 4),
                1, 0, 0f));
        addBaseOfferCandidate(shelfCandidates, new TradeOffer(
                new TradedItem(ModItems.ARCANE_LEDGER, 1),
                Optional.of(new TradedItem(ModItems.TRADE_SCROLL, 1)),
                createEnchantedBook(Enchantments.SHARPNESS, 4),
                1, 0, 0f));
        addBaseOfferCandidate(shelfCandidates, new TradeOffer(
                new TradedItem(ModItems.ARCANE_LEDGER, 1),
                Optional.of(new TradedItem(ModItems.TRADE_SCROLL, 1)),
                createEnchantedBook(Enchantments.EFFICIENCY, 4),
                1, 0, 0f));
        addBaseOfferCandidate(shelfCandidates, new TradeOffer(
                new TradedItem(ModItems.ARCANE_LEDGER, 1),
                Optional.of(new TradedItem(ModItems.TRADE_SCROLL, 1)),
                createEnchantedBook(Enchantments.UNBREAKING, 3),
                1, 0, 0f));
        addBaseOfferCandidate(shelfCandidates, new TradeOffer(
                new TradedItem(ModItems.ARCANE_LEDGER, 1),
                Optional.of(new TradedItem(ModItems.TRADE_SCROLL, 1)),
                createEnchantedBook(Enchantments.LOOTING, 3),
                1, 0, 0f));
        addBaseOfferCandidate(shelfCandidates, new TradeOffer(
                new TradedItem(ModItems.ARCANE_LEDGER, 1),
                Optional.of(new TradedItem(ModItems.TRADE_SCROLL, 1)),
                createEnchantedBook(Enchantments.FIRE_ASPECT, 2),
                1, 0, 0f));
        addBaseOfferCandidate(shelfCandidates, new TradeOffer(
                new TradedItem(ModItems.ARCANE_LEDGER, 1),
                Optional.of(new TradedItem(ModItems.MERCHANT_MARK, 1)),
                createEnchantedBook(Enchantments.MENDING, 1),
                1, 0, 0f));
        shelfCandidates.add(new TradeOffer(
                new TradedItem(ModItems.ARCANE_LEDGER, 1),
                Optional.of(new TradedItem(ModItems.SILVER_NOTE, 2)),
                new ItemStack(Items.END_CRYSTAL, 4),
                1, 0, 0.0f));
        shelfCandidates.add(new TradeOffer(
                new TradedItem(ModItems.ARCANE_LEDGER, 1),
                Optional.of(new TradedItem(ModItems.SILVER_NOTE, 2)),
                new ItemStack(Items.EXPERIENCE_BOTTLE, 16),
                1, 0, 0.0f));
        shelfCandidates.add(new TradeOffer(
                new TradedItem(ModItems.ARCANE_LEDGER, 1),
                Optional.of(new TradedItem(ModItems.SILVER_NOTE, 2)),
                new ItemStack(Items.GOLDEN_APPLE, 2),
                1, 0, 0.0f));

        injectVisibleSparkOffer(offers, pageStart, 4, refreshNonce, player.getUuid(), shelfCandidates);
        addShelfOffersToPage(offers, pageStart, shelfCandidates, 4, refreshNonce, player.getUuid(), HIDDEN_SHELF_COUNT);
        logPageGateCostSummary("PAGE4", offers, pageStart);
    }

    /**
     * Variant Anchors: 在 Arcane/隐藏页添加 B 招牌锚点（固定）+ 随机层 1 件 B。
     * Rule 1: 随机层只抽 1 件。
     * Rule 2: Anti-Repeat per (player, variant)。
     * Rule 3: EPIC Cap — 锚点若 EPIC，随机层过滤掉 EPIC 候选。
     */
    private void addVariantBAnchorAndRandom(TradeOfferList offers, ServerPlayerEntity player) {
        MerchantVariant variant = variantOf(this.getType());
        String variantKey = variant.name();

        // ---- B 招牌锚点（固定必出） ----
        Item bAnchorItem = TradeConfig.variantBAnchor().get(variantKey);
        if (bAnchorItem == null) {
            LOGGER.warn("[MoonTrade] B_ANCHOR_SKIP variant={} reason=no_anchor_mapping", variantKey);
            return;
        }
        boolean anchorAdded = addOfferUniqueBySellItem(offers, new TradeOffer(
                new TradedItem(ModItems.MERCHANT_MARK, TradeConfig.B_ARMOR_TICKET_COST),
                Optional.of(new TradedItem(ModItems.SILVER_NOTE, TradeConfig.B_ARMOR_SILVER_COST)),
                new ItemStack(bAnchorItem),
                TradeConfig.B_ARMOR_MAX_USES, 20, 0.05f), "variant_b_anchor_" + variantKey);
        if (anchorAdded) {
            LOGGER.info("[MoonTrade] B_ANCHOR_ADD variant={} item={}", variantKey,
                    Registries.ITEM.getId(bAnchorItem));
        } else {
            LOGGER.info("[MoonTrade] B_ANCHOR_SKIP variant={} item={} reason=duplicate_sell_item",
                    variantKey, Registries.ITEM.getId(bAnchorItem));
        }

        // ---- 随机层：Draw 1 ----
        java.util.List<Item> defaultPool = TradeConfig.variantBRandomPool().get(variantKey);
        if (defaultPool == null || defaultPool.isEmpty()) {
            LOGGER.info("[MoonTrade] B_RANDOM_SKIP variant={} reason=empty_pool", variantKey);
            return;
        }
        boolean useScrollGuided = hasAtLeastItem(player, ModItems.TRADE_SCROLL, TradeConfig.B_RANDOM_SCROLL_COST);
        java.util.List<Item> pool = defaultPool;
        if (useScrollGuided) {
            java.util.List<Item> guidedPool = TradeConfig.variantBScrollGuidedPool().get(variantKey);
            if (guidedPool != null && !guidedPool.isEmpty()) {
                pool = guidedPool;
            } else {
                useScrollGuided = false;
            }
        }

        // Rule 3: EPIC Cap — 如果锚点是 EPIC，过滤掉 EPIC 候选
        boolean anchorIsEpic = TradeConfig.isEpic(bAnchorItem);
        java.util.List<Item> candidates;
        if (anchorIsEpic) {
            candidates = new ArrayList<>();
            for (Item item : pool) {
                if (!TradeConfig.isEpic(item)) {
                    candidates.add(item);
                }
            }
            LOGGER.info("[MoonTrade] B_RANDOM_EPIC_FILTER variant={} poolSize={} afterFilter={}",
                    variantKey, pool.size(), candidates.size());
        } else {
            candidates = new ArrayList<>(pool);
        }

        if (candidates.isEmpty()) {
            LOGGER.info("[MoonTrade] B_RANDOM_SKIP variant={} reason=all_filtered_epic", variantKey);
            return;
        }

        // 用 (merchantUuid ^ playerUuid ^ worldTime) 作为随机种子
        long seed = this.getUuid().getLeastSignificantBits()
                ^ player.getUuid().getMostSignificantBits()
                ^ this.getEntityWorld().getTime();
        Random rng = new Random(seed);
        Item drawn = candidates.get(rng.nextInt(candidates.size()));

        // Rule 2: Anti-Repeat — 与上次相同则重抽 1 次
        String drawnId = Registries.ITEM.getId(drawn).toString();
        String antiRepeatKey = variantKey + (useScrollGuided ? "|SCROLL" : "|DEFAULT");
        String lastB = TradeConfig.getLastRandomB(player.getUuid(), antiRepeatKey);
        if (drawnId.equals(lastB) && candidates.size() > 1) {
            Item redraw = drawn;
            int attempt = 0;
            while (redraw == drawn && attempt < 1) {
                redraw = candidates.get(rng.nextInt(candidates.size()));
                attempt++;
            }
            drawn = redraw;
            drawnId = Registries.ITEM.getId(drawn).toString();
            LOGGER.info("[MoonTrade] B_RANDOM_ANTIREPEAT variant={} lastB={} redrawn={}", variantKey, lastB, drawnId);
        }

        // 记录本次结果
        TradeConfig.setLastRandomB(player.getUuid(), antiRepeatKey, drawnId);

        TradeOffer randomOffer = useScrollGuided
                ? new TradeOffer(
                        new TradedItem(ModItems.TRADE_SCROLL, TradeConfig.B_RANDOM_SCROLL_COST),
                        Optional.of(new TradedItem(ModItems.SILVER_NOTE, TradeConfig.B_RANDOM_SILVER_COST)),
                        new ItemStack(drawn),
                        TradeConfig.B_ARMOR_MAX_USES, 20, 0.05f)
                : new TradeOffer(
                        new TradedItem(ModItems.MERCHANT_MARK, TradeConfig.B_RANDOM_TICKET_COST),
                        Optional.of(new TradedItem(ModItems.SILVER_NOTE, TradeConfig.B_RANDOM_SILVER_COST)),
                        new ItemStack(drawn),
                        TradeConfig.B_ARMOR_MAX_USES, 20, 0.05f);
        boolean randomAdded = addOfferUniqueBySellItem(offers, randomOffer, "variant_b_random_" + variantKey);
        if (randomAdded) {
            LOGGER.info("[MoonTrade] B_RANDOM_ADD variant={} item={} anchorEpic={} mode={} gate={}",
                    variantKey, drawnId, anchorIsEpic,
                    useScrollGuided ? "SCROLL_GUIDED" : "DEFAULT",
                    useScrollGuided ? Registries.ITEM.getId(ModItems.TRADE_SCROLL)
                            : Registries.ITEM.getId(ModItems.MERCHANT_MARK));
        } else {
            LOGGER.info("[MoonTrade] B_RANDOM_SKIP variant={} item={} reason=duplicate_sell_item", variantKey, drawnId);
        }
    }

    private TradeOffer tryCreateReclaimOffer(ServerPlayerEntity player) {
        if (!(this.getEntityWorld() instanceof ServerWorld serverWorld)) return null;

        String katanaType = resolveKatanaType();
        if (katanaType == null) return null;

        KatanaOwnershipState ownershipState = KatanaOwnershipState.getServerState(serverWorld);
        UUID playerUuid = player.getUuid();

        if (!ownershipState.hasOwned(playerUuid, katanaType)) return null;

        MerchantUnlockState unlockState = MerchantUnlockState.getServerState(serverWorld);
        String variantKey = getVariantKey();
        MerchantUnlockState.Progress progress = unlockState.getOrCreateProgress(playerUuid, variantKey);
        if (!progress.isUnlockedKatanaHidden(variantKey)) return null;

        UUID activeInstanceId = ownershipState.getActiveInstanceId(playerUuid, katanaType);
        if (activeInstanceId != null) {
            if (hasActiveKatanaInstanceInInventory(player, katanaType, activeInstanceId)) {
                LOGGER.info("[MoonTrade] RECLAIM_SKIP player={} type={} reason=active_instance_present activeInstanceId={}",
                        playerUuid, katanaType, activeInstanceId);
                return null;
            }
        } else if (hasKatanaTypeInInventory(player, katanaType)) {
            LOGGER.info("[MoonTrade] RECLAIM_SKIP player={} type={} reason=type_present_no_active_instance",
                    playerUuid, katanaType);
            return null;
        }

        long lastReclaim = ownershipState.getLastReclaimTick(playerUuid, katanaType);
        long nowTick = serverWorld.getTime();
        if (lastReclaim > 0 && (nowTick - lastReclaim) < TradeConfig.RECLAIM_CD_TICKS) {
            LOGGER.info("[MoonTrade] RECLAIM_COOLDOWN player={} type={} remaining={}",
                    playerUuid, katanaType, TradeConfig.RECLAIM_CD_TICKS - (nowTick - lastReclaim));
            return null;
        }

        Item katanaItem = KATANA_WHITELIST.get(katanaType);
        if (katanaItem == null) return null;

        ItemStack reclaimStack = new ItemStack(katanaItem, 1);
        UUID instanceId = UUID.randomUUID();
        KatanaContractUtil.writeKatanaContract(reclaimStack, playerUuid, katanaType, instanceId);
        NbtComponent component = reclaimStack.get(DataComponentTypes.CUSTOM_DATA);
        NbtCompound nbt = component == null ? new NbtCompound() : component.copyNbt();
        nbt.putBoolean(NBT_SECRET_MARKER, true);
        nbt.putString(NBT_SECRET_MARKER_ID, katanaType);
        nbt.putBoolean(KatanaContractUtil.NBT_RECLAIM, true);
        reclaimStack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));

        LOGGER.info("[MoonTrade] RECLAIM_OFFER_ADDED player={} type={} instanceId={} merchant={}",
                playerUuid, katanaType, instanceId, this.getUuid());
        return new TradeOffer(
                new TradedItem(Items.NETHERITE_INGOT, TradeConfig.RECLAIM_COST_NETHERITE),
                Optional.of(new TradedItem(Items.DIAMOND, TradeConfig.RECLAIM_COST_DIAMONDS)),
                reclaimStack,
                RECLAIM_OFFER_MAX_USES,
                80,
                0.0f);
    }

    /**
     * Add reclaim offer if eligible.
     */
    private void addReclaimOffer(TradeOfferList offers, ServerPlayerEntity player) {
        TradeOffer reclaimOffer = tryCreateReclaimOffer(player);
        if (reclaimOffer != null) {
            offers.add(reclaimOffer);
        }
    }

    private void applyKatanaOwnershipSoldOut(TradeOfferList offers, java.util.UUID playerUuid,
            KatanaOwnershipState ownershipState) {
        if (ownershipState == null || playerUuid == null) {
            return;
        }
        for (TradeOffer offer : offers) {
            if (KatanaContractUtil.isReclaimOutput(offer.getSellItem())) {
                // Reclaim must remain purchasable after first ownership.
                continue;
            }
            String katanaId = KatanaIdUtil.extractCanonicalKatanaId(offer.getSellItem());
            if (!KatanaIdUtil.isSecretKatana(katanaId)) {
                continue;
            }
            if (ownershipState.hasOwned(playerUuid, katanaId)) {
                if (!offer.isDisabled()) {
                    offer.disable();
                }
                LOGGER.info("[MoonTrade] MM_SOLD_OUT_SHOWN player={} katanaId={} merchant={}",
                        playerUuid, katanaId, this.getUuid());
            }
        }
    }

    /**
     * Task A: Post-build katana sanitization.
     * 1. Keeps owned normal-buy katana offers so sold-out rows stay visible.
     * 2. Deduplicates normal-buy katana offers by type, preferring coin route
     *    (second buy item = MYSTERIOUS_COIN) over silver route.
     * Reclaim offers are never removed here.
     * Returns total number of deduplicated offers removed.
     */
    private int sanitizeKatanaOffersForPlayer(TradeOfferList offers, UUID playerUuid,
            KatanaOwnershipState ownershipState, String sourceTag) {
        if (ownershipState == null || playerUuid == null) {
            return 0;
        }
        // Pass 1: collect indices of non-preferred duplicate normal-buy offers.
        // Key: katanaId → index of the preferred offer to keep
        java.util.Map<String, Integer> keptIndexByType = new java.util.HashMap<>();
        Set<Integer> indicesToRemove = new HashSet<>();
        int removedDupe = 0;

        for (int i = 0; i < offers.size(); i++) {
            TradeOffer offer = offers.get(i);
            if (KatanaContractUtil.isReclaimOutput(offer.getSellItem())) {
                continue;
            }
            String katanaId = KatanaIdUtil.extractCanonicalKatanaId(offer.getSellItem());
            if (katanaId.isEmpty() || !KatanaIdUtil.isSecretKatana(katanaId)) {
                continue;
            }

            // Dedup: prefer coin route (second buy = MYSTERIOUS_COIN)
            Integer existingIdx = keptIndexByType.get(katanaId);
            if (existingIdx == null) {
                keptIndexByType.put(katanaId, i);
            } else {
                boolean newIsCoin = isCoinRoute(offer);
                boolean existingIsCoin = isCoinRoute(offers.get(existingIdx));
                if (newIsCoin && !existingIsCoin) {
                    // New offer is coin route, old is not: evict old, keep new
                    indicesToRemove.add(existingIdx);
                    keptIndexByType.put(katanaId, i);
                } else {
                    // Keep existing (either both coin, both silver, or existing is coin)
                    indicesToRemove.add(i);
                }
                removedDupe++;
            }
        }

        // Pass 2: remove marked indices (reverse order to preserve indices)
        StringBuilder remaining = new StringBuilder();
        for (String type : keptIndexByType.values().stream()
                .sorted().map(idx -> KatanaIdUtil.extractCanonicalKatanaId(offers.get(idx).getSellItem()))
                .toList()) {
            if (remaining.length() > 0) remaining.append(',');
            remaining.append(type);
        }
        java.util.List<Integer> sortedRemoval = new java.util.ArrayList<>(indicesToRemove);
        sortedRemoval.sort(java.util.Comparator.reverseOrder());
        for (int idx : sortedRemoval) {
            offers.remove(idx);
        }

        if (removedDupe > 0) {
            LOGGER.warn("[MoonTrade] SANITIZE_KATANA player={} merchant={} source={} removedDupe={} remaining=[{}]",
                    playerUuid, this.getUuid(), sourceTag, removedDupe, remaining);
        } else if (!keptIndexByType.isEmpty()) {
            LOGGER.info("[MoonTrade] SANITIZE_KATANA player={} merchant={} source={} types=[{}] clean",
                    playerUuid, this.getUuid(), sourceTag, remaining);
        }
        return removedDupe;
    }

    private static boolean isCoinRoute(TradeOffer offer) {
        ItemStack secondBuy = offer.getDisplayedSecondBuyItem();
        return !secondBuy.isEmpty() && secondBuy.isOf(ModItems.MYSTERIOUS_COIN);
    }

    private static boolean hasKatanaTypeInInventory(ServerPlayerEntity player, String katanaId) {
        if (player == null) {
            return false;
        }
        String normalized = KatanaOwnershipState.normalizeKatanaId(katanaId);
        if (normalized.isEmpty()) {
            return false;
        }
        for (ItemStack stack : player.getInventory().main) {
            String stackKatanaId = KatanaIdUtil.extractCanonicalKatanaId(stack);
            if (normalized.equals(stackKatanaId)) return true;
        }
        for (ItemStack stack : player.getInventory().offHand) {
            String stackKatanaId = KatanaIdUtil.extractCanonicalKatanaId(stack);
            if (normalized.equals(stackKatanaId)) return true;
        }
        for (ItemStack stack : player.getInventory().armor) {
            String stackKatanaId = KatanaIdUtil.extractCanonicalKatanaId(stack);
            if (normalized.equals(stackKatanaId)) return true;
        }
        return false;
    }

    private static boolean hasActiveKatanaInstanceInInventory(ServerPlayerEntity player, String katanaId, UUID activeInstanceId) {
        if (activeInstanceId == null || player == null) {
            return false;
        }
        String normalized = KatanaOwnershipState.normalizeKatanaId(katanaId);
        if (normalized.isEmpty()) {
            return false;
        }
        for (ItemStack stack : player.getInventory().main) {
            String stackKatanaId = KatanaIdUtil.extractCanonicalKatanaId(stack);
            if (!normalized.equals(stackKatanaId)) continue;
            UUID stackInstanceId = KatanaContractUtil.getInstanceId(stack);
            if (activeInstanceId.equals(stackInstanceId)) return true;
        }
        for (ItemStack stack : player.getInventory().offHand) {
            String stackKatanaId = KatanaIdUtil.extractCanonicalKatanaId(stack);
            if (!normalized.equals(stackKatanaId)) continue;
            UUID stackInstanceId = KatanaContractUtil.getInstanceId(stack);
            if (activeInstanceId.equals(stackInstanceId)) return true;
        }
        for (ItemStack stack : player.getInventory().armor) {
            String stackKatanaId = KatanaIdUtil.extractCanonicalKatanaId(stack);
            if (!normalized.equals(stackKatanaId)) continue;
            UUID stackInstanceId = KatanaContractUtil.getInstanceId(stack);
            if (activeInstanceId.equals(stackInstanceId)) return true;
        }
        return false;
    }

    private java.util.List<TradeOffer> createKatanaOffers(ServerPlayerEntity player) {
        ItemStack katanaStack = resolveKatanaStack();
        if (katanaStack.isEmpty()) {
            return Collections.emptyList();
        }
        markSecretTradeOutput(katanaStack, player);

        TradeOffer coinRoute = new TradeOffer(
                new TradedItem(ModItems.ARCANE_LEDGER, 1),
                Optional.of(new TradedItem(ModItems.MYSTERIOUS_COIN, 1)),
                katanaStack.copy(),
                KATANA_OFFER_MAX_USES,
                80,
                0.0f);

        TradeOffer silverRoute = new TradeOffer(
                new TradedItem(ModItems.ARCANE_LEDGER, 1),
                Optional.of(new TradedItem(ModItems.SILVER_NOTE, TradeConfig.KATANA_ALT_SILVER_COST)),
                katanaStack.copy(),
                KATANA_OFFER_MAX_USES,
                80,
                0.0f);

        LOGGER.info("[MoonTrade] KATANA_OFFER_BUILD variant={} katanaId={} routes=coin_or_silver silverCost={}",
                variantOf(this.getType()).typeKey, this.secretKatanaId, TradeConfig.KATANA_ALT_SILVER_COST);
        return java.util.List.of(coinRoute, silverRoute);
    }

    /**
     * P0-C FIX: Whitelist of valid katana IDs -> Item mappings.
     * Only IDs in this map can be resolved to actual items.
     * Invariant: id must be live - unknown/invalid IDs produce EMPTY.
     */
    private static final Map<String, net.minecraft.item.Item> KATANA_WHITELIST = Map.of(
            "moonglow", KatanaItems.MOON_GLOW_KATANA,
            "regret", KatanaItems.REGRET_BLADE,
            "eclipse", KatanaItems.ECLIPSE_BLADE,
            "oblivion", KatanaItems.OBLIVION_EDGE,
            "nmap", KatanaItems.NMAP_KATANA);

    private static final Map<Item, String> KATANA_ID_BY_ITEM = Map.of(
            KatanaItems.MOON_GLOW_KATANA, "moonglow",
            KatanaItems.REGRET_BLADE, "regret",
            KatanaItems.ECLIPSE_BLADE, "eclipse",
            KatanaItems.OBLIVION_EDGE, "oblivion",
            KatanaItems.NMAP_KATANA, "nmap");

    /**
     * P0-C FIX: Resolve secretKatanaId to actual item via whitelist.
     * Fail-safe: if id unknown/invalid, return EMPTY and log warning.
     */
    private ItemStack resolveKatanaStack() {
        // Validate secretKatanaId is initialized
        if (this.secretKatanaId == null || this.secretKatanaId.isEmpty()) {
            LOGGER.warn("[MoonTrade] KATANA_RESOLVE_FAIL player={} merchant={} secretKatanaId={} reason=empty_id",
                    getCurrentPlayerForLog(), this.getUuid(), this.secretKatanaId);
            return ItemStack.EMPTY;
        }

        String katanaType = resolveKatanaType();
        if (katanaType == null) {
            LOGGER.warn(
                    "[MoonTrade] KATANA_RESOLVE_FAIL player={} merchant={} secretKatanaId={} reason=unknown_or_legacy_id",
                    getCurrentPlayerForLog(), this.getUuid(), this.secretKatanaId);
            return ItemStack.EMPTY;
        }

        net.minecraft.item.Item item = KATANA_WHITELIST.get(katanaType);
        if (item == null) {
            LOGGER.warn(
                    "[MoonTrade] KATANA_RESOLVE_FAIL player={} merchant={} secretKatanaId={} katanaType={} reason=not_in_whitelist",
                    getCurrentPlayerForLog(), this.getUuid(), this.secretKatanaId, katanaType);
            return ItemStack.EMPTY;
        }

        return new ItemStack(item, 1);
    }

    private void markSecretTradeOutput(ItemStack stack, ServerPlayerEntity player) {
        NbtComponent component = stack.get(DataComponentTypes.CUSTOM_DATA);
        NbtCompound nbt = component == null ? new NbtCompound() : component.copyNbt();
        nbt.putBoolean(NBT_SECRET_MARKER, true);
        nbt.putString(NBT_SECRET_MARKER_ID, this.secretKatanaId);
        nbt.putString(NBT_MM_KATANA_ID, this.secretKatanaId);
        // Contract: stamp owner + instanceId so the sold copy carries contract data
        UUID instanceId = UUID.randomUUID();
        nbt.putString(KatanaContractUtil.NBT_OWNER_UUID, player.getUuid().toString());
        nbt.putString(KatanaContractUtil.NBT_INSTANCE_ID, instanceId.toString());
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
    }

    /**
     * Resolve canonical katana type from persisted secret id.
     */
    private String resolveKatanaType() {
        if (this.secretKatanaId == null || this.secretKatanaId.isEmpty()) {
            return null;
        }
        String normalized = normalizeSecretKatanaId(this.secretKatanaId);
        if (normalized.isEmpty()) {
            return null;
        }
        return KATANA_WHITELIST.containsKey(normalized) ? normalized : null;
    }

    private static String normalizeKatanaIdStrict(String rawId) {
        return KatanaIdUtil.canonicalizeKatanaId(rawId);
    }

    private String normalizeSecretKatanaId(String rawId) {
        String normalized = normalizeKatanaIdStrict(rawId);
        if (!normalized.isEmpty()) {
            return normalized;
        }
        String id = rawId == null ? "" : rawId.trim();
        if (isLegacyKatanaId(id)) {
            return buildSecretKatanaId(pickKatanaTypeForMerchant());
        }
        return "";
    }

    private ArrayList<SigilOfferEntry> createSigilOfferPool() {
        ArrayList<SigilOfferEntry> pool = new ArrayList<>();

        pool.add(new SigilOfferEntry("A1", SigilTier.A, new TradeOffer(
                new TradedItem(Items.EMERALD, 8),
                Optional.of(new TradedItem(ModItems.SILVER_NOTE, 1)),
                new ItemStack(ModBlocks.MYSTERIOUS_ANVIL_ITEM, 1),
                2, 0, 0.0f)));
        pool.add(new SigilOfferEntry("A2", SigilTier.A, new TradeOffer(
                new TradedItem(Items.DIAMOND, 2),
                Optional.of(new TradedItem(ModItems.SILVER_NOTE, 2)),
                new ItemStack(ModItems.SACRIFICE, 1),
                4, 0, 0.0f)));
        pool.add(new SigilOfferEntry("A3", SigilTier.A, new TradeOffer(
                new TradedItem(Items.NETHERITE_INGOT, 1),
                new ItemStack(ModItems.SIGIL, 1),
                1, 60, 0.0f)));

        pool.add(new SigilOfferEntry("B1", SigilTier.B, new TradeOffer(
                new TradedItem(Items.BLAZE_ROD, 2),
                new ItemStack(ModItems.SIGIL, 1),
                2, 30, 0.0f)));
        pool.add(new SigilOfferEntry("B2", SigilTier.B, new TradeOffer(
                new TradedItem(Items.GOLD_INGOT, 12),
                new ItemStack(ModItems.SIGIL, 1),
                2, 25, 0.0f)));
        pool.add(new SigilOfferEntry("B3", SigilTier.B, new TradeOffer(
                new TradedItem(Items.ENDER_PEARL, 6),
                new ItemStack(ModItems.SIGIL, 1),
                2, 25, 0.0f)));
        pool.add(new SigilOfferEntry("B4", SigilTier.B, new TradeOffer(
                new TradedItem(Items.GHAST_TEAR, 2),
                new ItemStack(ModItems.SIGIL, 1),
                2, 30, 0.0f)));

        pool.add(new SigilOfferEntry("C1", SigilTier.C, new TradeOffer(
                new TradedItem(Items.EMERALD, 24),
                new ItemStack(ModItems.SIGIL, 1),
                3, 15, 0.0f)));
        pool.add(new SigilOfferEntry("C2", SigilTier.C, new TradeOffer(
                new TradedItem(Items.IRON_INGOT, 16),
                new ItemStack(ModItems.SIGIL, 1),
                3, 15, 0.0f)));
        pool.add(new SigilOfferEntry("C3", SigilTier.C, new TradeOffer(
                new TradedItem(Items.REDSTONE, 24),
                new ItemStack(ModItems.SIGIL, 1),
                3, 15, 0.0f)));

        return pool;
    }

    private SigilOfferEntry extractById(ArrayList<SigilOfferEntry> list, String id) {
        for (int i = 0; i < list.size(); i++) {
            SigilOfferEntry entry = list.get(i);
            if (entry.id.equals(id)) {
                list.remove(i);
                return entry;
            }
        }
        return null;
    }

    private SigilOfferEntry extractRandomFrom(ArrayList<SigilOfferEntry> first, ArrayList<SigilOfferEntry> second,
            Random rng) {
        ArrayList<SigilOfferEntry> combined = new ArrayList<>();
        combined.addAll(first);
        combined.addAll(second);
        if (combined.isEmpty()) {
            return null;
        }
        SigilOfferEntry pick = combined.get(rng.nextInt(combined.size()));
        first.remove(pick);
        second.remove(pick);
        return pick;
    }

    private SigilOfferEntry pickRandomEntry(ArrayList<SigilOfferEntry> list, Random rng) {
        SigilOfferEntry entry = list.remove(rng.nextInt(list.size()));
        return entry;
    }

    /**
     * P0-A FIX: Derive sigil seed deterministically from (merchant, player,
     * refreshSeenCount).
     * Invariant: same inputs always produce same seed -> per player isolation.
     */
    private static long deriveSigilSeed(java.util.UUID merchantUuid, java.util.UUID playerUuid, int refreshSeenCount) {
        if (merchantUuid == null || playerUuid == null) {
            LOGGER.warn("[MoonTrade] SIGIL_SEED_FALLBACK merchant={} player={} refreshSeenCount={} reason=null_uuid",
                    merchantUuid, playerUuid, refreshSeenCount);
            long safeMerchant = merchantUuid == null ? 0L : merchantUuid.getLeastSignificantBits();
            long safePlayer = playerUuid == null ? 0L : playerUuid.getMostSignificantBits();
            return (safeMerchant ^ safePlayer) * 31L + refreshSeenCount * 6364136223846793005L;
        }
        long base = merchantUuid.getLeastSignificantBits() ^ playerUuid.getMostSignificantBits();
        // Mix in refreshSeenCount to change seed on each refresh
        return base * 31L + refreshSeenCount * 6364136223846793005L;
    }

    /**
     * P0-A/P0-B FIX: Compute a fingerprint hash of the current offers list.
     * Used for cache HIT/MISS detection and refresh-change verification.
     */
    private static int computeOffersHash(TradeOfferList offers) {
        int hash = 1;
        for (TradeOffer offer : offers) {
            // Include both buy slots, output slot and key trade metadata.
            ItemStack first = offer.getOriginalFirstBuyItem();
            ItemStack sell = offer.getSellItem();
            ItemStack second = offer.getSecondBuyItem().map(TradedItem::itemStack).orElse(ItemStack.EMPTY);
            hash = 31 * hash + hashItemStack(first);
            hash = 31 * hash + hashItemStack(second);
            hash = 31 * hash + hashItemStack(sell);
            hash = 31 * hash + offer.getUses();
            hash = 31 * hash + offer.getMaxUses();
            hash = 31 * hash + offer.getMerchantExperience();
        }
        return hash;
    }

    private static int countEpicSellOffers(TradeOfferList offers) {
        int count = 0;
        for (TradeOffer offer : offers) {
            if (TradeConfig.isEpic(offer.getSellItem().getItem())) {
                count++;
            }
        }
        return count;
    }

    private static int countForbiddenReleaseOutputs(TradeOfferList offers) {
        int count = 0;
        for (TradeOffer offer : offers) {
            if (RELEASE_FORBIDDEN_OUTPUTS.contains(offer.getSellItem().getItem())) {
                count++;
            }
        }
        return count;
    }

    private static int countForbiddenCoinMintRoutes(TradeOfferList offers) {
        int count = 0;
        for (TradeOffer offer : offers) {
            if (isReleaseForbiddenCoinMintOffer(offer)) {
                count++;
            }
        }
        return count;
    }

    private static String collectForbiddenCoinMintRouteIds(TradeOfferList offers) {
        StringBuilder sb = new StringBuilder();
        for (TradeOffer offer : offers) {
            if (!isReleaseForbiddenCoinMintOffer(offer)) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(",");
            }
            Item first = offer.getOriginalFirstBuyItem().getItem();
            Optional<TradedItem> second = offer.getSecondBuyItem();
            String secondText = second.map(traded -> Registries.ITEM.getId(traded.itemStack().getItem()).toString())
                    .orElse("none");
            sb.append(Registries.ITEM.getId(first))
                    .append("+")
                    .append(secondText)
                    .append("->")
                    .append(Registries.ITEM.getId(offer.getSellItem().getItem()));
        }
        return sb.length() == 0 ? "none" : sb.toString();
    }

    private static String collectForbiddenOutputIds(TradeOfferList offers) {
        StringBuilder sb = new StringBuilder();
        for (TradeOffer offer : offers) {
            Item sellItem = offer.getSellItem().getItem();
            if (!RELEASE_FORBIDDEN_OUTPUTS.contains(sellItem)) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(",");
            }
            sb.append(Registries.ITEM.getId(sellItem));
        }
        return sb.length() == 0 ? "none" : sb.toString();
    }

    private static int computeCacheFingerprint(
            int offersHash,
            java.util.UUID playerUuid,
            String unlockState,
            int refreshSeenCount,
            long seed) {
        int hash = 1;
        hash = 31 * hash + offersHash;
        hash = 31 * hash + (playerUuid == null ? 0 : playerUuid.hashCode());
        hash = 31 * hash + (unlockState == null ? 0 : unlockState.hashCode());
        hash = 31 * hash + refreshSeenCount;
        hash = 31 * hash + Long.hashCode(seed);
        return hash;
    }

    public int snapshotOffersHash() {
        return computeOffersHash(this.getOffers());
    }

    public OfferCounters snapshotOfferCounters() {
        return classifyOffers(this.getOffers());
    }

    public static record OfferBuildAudit(
            String source,
            String cache,
            String unlock,
            int offersTotal,
            int baseCount,
            int sigilCount,
            int hiddenCount,
            int offersHash,
            long seed,
            int refreshSeenCount,
            long durationMs) {
    }

    public static record OfferCounters(
            int totalCount,
            int baseCount,
            int sigilCount,
            int hiddenCount,
            int offersHash) {
    }

    private static OfferCounters classifyOffers(TradeOfferList offers) {
        int base = 0;
        int sigil = 0;
        int hidden = 0;
        for (TradeOffer offer : offers) {
            if (isBaseOffer(offer)) {
                base++;
                continue;
            }
            if (isSigilOffer(offer)) {
                sigil++;
                continue;
            }
            hidden++;
        }
        return new OfferCounters(offers.size(), base, sigil, hidden, computeOffersHash(offers));
    }

    private static boolean isSigilOffer(TradeOffer offer) {
        if (offer.getSellItem().isOf(ModItems.SIGIL)) {
            return true;
        }
        if (offer.getOriginalFirstBuyItem().isOf(ModItems.SIGIL)) {
            return true;
        }
        Optional<TradedItem> second = offer.getSecondBuyItem();
        return second.isPresent() && second.get().itemStack().isOf(ModItems.SIGIL);
    }

    private static boolean isBaseOffer(TradeOffer offer) {
        ItemStack sell = offer.getSellItem();
        // Base offers: anything that is NOT a sigil-chain offer and NOT a hidden/katana offer
        if (isSigilOffer(offer)) return false;
        // Katana whitelist items are hidden offers
        if (KATANA_WHITELIST.containsValue(sell.getItem())) return false;
        // Sealed/Arcane ledger are sigil-chain
        if (sell.isOf(ModItems.SEALED_LEDGER) || sell.isOf(ModItems.ARCANE_LEDGER)) return false;
        return true;
    }

    private static String toUnlockState(boolean eligible, boolean unlocked) {
        if (unlocked) {
            return "UNLOCKED";
        }
        if (eligible) {
            return "ELIGIBLE";
        }
        return "LOCKED";
    }

    private static String playerTag(ServerPlayerEntity player) {
        return player.getName().getString() + "(" + uuidShort(player.getUuid()) + ")";
    }

    private String merchantTag() {
        return uuidShort(this.getUuid()) + "#" + this.getId();
    }

    private static String uuidShort(java.util.UUID uuid) {
        if (uuid == null) {
            return "null";
        }
        String text = uuid.toString();
        return text.length() >= 8 ? text.substring(0, 8) : text;
    }

    private static int hashItemStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        int hash = 1;
        hash = 31 * hash + Registries.ITEM.getId(stack.getItem()).hashCode();
        hash = 31 * hash + stack.getCount();
        hash = 31 * hash + stableComponentHash(stack);
        return hash;
    }

    /**
     * Deterministic component hash in fixed key order.
     * Avoids relying on component container iteration order.
     */
    private static int stableComponentHash(ItemStack stack) {
        int hash = 1;
        hash = 31 * hash + Objects.hashCode(stack.get(DataComponentTypes.CUSTOM_NAME));
        hash = 31 * hash + Objects.hashCode(stack.get(DataComponentTypes.CUSTOM_DATA));
        hash = 31 * hash + Objects.hashCode(stack.get(DataComponentTypes.ENCHANTMENTS));
        hash = 31 * hash + Objects.hashCode(stack.get(DataComponentTypes.STORED_ENCHANTMENTS));
        hash = 31 * hash + Objects.hashCode(stack.get(DataComponentTypes.POTION_CONTENTS));
        hash = 31 * hash + Objects.hashCode(stack.get(DataComponentTypes.WRITTEN_BOOK_CONTENT));
        hash = 31 * hash + Objects.hashCode(stack.get(DataComponentTypes.FIREWORKS));
        return hash;
    }

    private boolean isUnsealOffer(TradeOffer offer) {
        if (!offer.getSellItem().isOf(ModItems.ARCANE_LEDGER)) {
            return false;
        }
        ItemStack first = offer.getOriginalFirstBuyItem();
        if (!first.isOf(ModItems.SEALED_LEDGER)) {
            return false;
        }
        Optional<TradedItem> second = offer.getSecondBuyItem();
        // P1-10 修复：使用 itemStack.isOf() 进行物品比较，更稳健
        return second.isPresent() && second.get().itemStack().isOf(ModItems.SIGIL);
    }

    private enum SigilTier {
        A,
        B,
        C
    }

    private static class SigilOfferEntry {
        private final String id;
        private final SigilTier tier;
        private final TradeOffer offer;

        private SigilOfferEntry(String id, SigilTier tier, TradeOffer offer) {
            this.id = id;
            this.tier = tier;
            this.offer = offer;
        }
    }

    /**
     * 获取商人自定义名称
     */
    public String getMerchantName() {
        return merchantName;
    }

    /**
     * 设置商人自定义名称
     */
    public void setMerchantName(String name) {
        this.merchantName = name;
        if (name != null && !name.isEmpty()) {
            this.setCustomName(Text.literal(name).formatted(Formatting.GOLD));
            this.setCustomNameVisible(true);
        }
    }

    private static String migrateLegacyMerchantName(String name) {
        if (name == null || name.isEmpty()) {
            return "";
        }
        return LEGACY_MERCHANT_NAME_MIGRATION.getOrDefault(name, name);
    }

    /**
     * Phase 8: 首次生成时，用稳定 seed 从变体名字池中选一个名字。
     * 仅当 merchantName 为空时执行（NBT 加载后不会覆盖）。
     * seed 基于 entity UUID，保证所有玩家看到一致、重进不变。
     */
    private void assignVariantNameIfNeeded() {
        if (this.merchantName != null && !this.merchantName.isEmpty()) {
            return; // 已有名字（从 NBT 加载），不覆盖
        }
        MerchantVariant variant = variantOf(this.getType());
        String[] pool = variant.namePool;
        // 稳定 seed：基于 entity UUID，不依赖任何 player
        long seed = this.getUuid().getLeastSignificantBits() ^ (this.getUuid().getMostSignificantBits() * 31L);
        int index = Math.floorMod((int) (seed ^ (seed >>> 16)), pool.length);
        String chosenName = pool[index];
        setMerchantName(chosenName);
        if (DEBUG_VARIANT) {
            LOGGER.info("[Merchant] action=MM_NAME_PICK type={} name={} uuid={} seed={} poolSize={}",
                    variant.typeKey, chosenName, this.getUuid().toString().substring(0, 8), seed, pool.length);
        }
    }

    private String buildSecretKatanaId(String katanaType) {
        String normalized = normalizeKatanaIdStrict(katanaType);
        return normalized.isEmpty() ? "moonglow" : normalized;
    }

    private long deriveSecretKatanaSeed() {
        long seed = this.getUuid().getMostSignificantBits() ^ this.getUuid().getLeastSignificantBits();
        if (this.getEntityWorld() instanceof ServerWorld serverWorld) {
            seed ^= serverWorld.getSeed();
        }
        return seed;
    }

    /**
     * 首次生成时按变体权重随机分配隐藏神器（已存在则不覆盖）。
     * seed 仅由稳定实体信息构成：worldSeed ^ uuidMost ^ uuidLeast。
     */
    private void assignSecretKatanaIfNeeded() {
        if (this.secretKatanaId != null && !this.secretKatanaId.isEmpty()) {
            return;
        }
        long seed = deriveSecretKatanaSeed();
        SecretRollResult rollResult = rollSecretKatanaType(seed, TradeConfig.TRADE_DEBUG);
        String type = rollResult.chosenType;
        this.secretKatanaId = buildSecretKatanaId(type);
        if (TradeConfig.TRADE_DEBUG) {
            LOGGER.info("[MoonTrade] MM_SECRET_PICK variant={} pickedKatanaId={}", getVariantKey(),
                    this.secretKatanaId);
            logSecretPick(type, seed);
        }
        if (DEBUG_VARIANT) {
            LOGGER.info("[Merchant] action=MM_SECRET_ASSIGN variant={} secretId={} seed={} uuid={}",
                    getVariantKey(), this.secretKatanaId, seed, this.getUuid().toString().substring(0, 8));
        }
    }

    /**
     * 变体标识幂等初始化：
     * - 名字：用于 UI 标题/头顶名
     * - 隐藏神器 ID：用于隐藏交易解析
     */
    private void ensureVariantIdentityIfNeeded() {
        assignVariantNameIfNeeded();
        assignSecretKatanaIfNeeded();
    }

    public static DefaultAttributeContainer.Builder createMerchantAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.5);
    }

    // Phase 2.5 & 4 & 6: NBT 持久化
    private static final String NBT_HAS_EVER_TRADED = "HasEverTraded";
    private static final String NBT_SPAWN_TICK = "SpawnTick";
    private static final String NBT_IN_WARNING_PHASE = "InWarningPhase";
    private static final String NBT_MERCHANT_NAME = "MerchantName";
    // Trade System NBT 键
    private static final String NBT_SECRET_SOLD = "SecretSold";
    private static final String NBT_SECRET_KATANA_ID = "SecretKatanaId";
    public static final String NBT_SECRET_MARKER = KatanaIdUtil.SECRET_MARKER;
    public static final String NBT_SECRET_MARKER_ID = KatanaIdUtil.SECRET_MARKER_ID;
    public static final String NBT_MM_KATANA_ID = KatanaIdUtil.MM_KATANA_ID;
    // P0-2: Sigil seed NBT 键
    private static final String NBT_SIGIL_ROLL_SEED = "SigilRollSeed";
    private static final String NBT_SIGIL_ROLL_INIT = "SigilRollInitialized";

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);

        // 保存 hasEverTraded
        nbt.putBoolean(NBT_HAS_EVER_TRADED, this.hasEverTraded);

        // Phase 4: 保存 spawnTick
        nbt.putLong(NBT_SPAWN_TICK, this.spawnTick);
        nbt.putBoolean(NBT_IN_WARNING_PHASE, this.isInWarningPhase);

        // Phase 6: 保存商人名称
        nbt.putString(NBT_MERCHANT_NAME, this.merchantName);

        // Trade System: 保存隐藏交易状态
        nbt.putBoolean(NBT_SECRET_SOLD, this.secretSold);
        nbt.putString(NBT_SECRET_KATANA_ID, this.secretKatanaId);

        // P0-A FIX: sigil seed no longer stored on entity (deprecated, write zeros for
        // compat)
        nbt.putLong(NBT_SIGIL_ROLL_SEED, 0L);
        nbt.putBoolean(NBT_SIGIL_ROLL_INIT, false);

        // Ritual Reveal: 持久化可见窗口
        if (this.ritualRevealUntilTick > 0) {
            nbt.putLong("RitualRevealUntil", this.ritualRevealUntilTick);
        }

        if (DEBUG_DESPAWN) {
            LOGGER.debug("[Merchant] NBT_SAVE spawnTick={} isInWarningPhase={} hasEverTraded={} secretSold={}",
                    spawnTick, isInWarningPhase, hasEverTraded, secretSold);
        }
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);

        // 读取 hasEverTraded
        this.hasEverTraded = nbt.getBoolean(NBT_HAS_EVER_TRADED);

        // P1-2: Phase 4: 读取 spawnTick（修复 spawnTick==0 边界）
        if (nbt.contains(NBT_SPAWN_TICK)) {
            this.spawnTick = nbt.getLong(NBT_SPAWN_TICK);
        } else {
            this.spawnTick = -1; // 兼容旧数据：无此 key 视为未初始化
        }
        this.isInWarningPhase = nbt.getBoolean(NBT_IN_WARNING_PHASE);

        // Phase 6: 读取商人名称
        this.merchantName = nbt.getString(NBT_MERCHANT_NAME);
        this.merchantName = migrateLegacyMerchantName(this.merchantName);
        if (!this.merchantName.isEmpty()) {
            this.setCustomName(Text.literal(this.merchantName).formatted(Formatting.GOLD));
            this.setCustomNameVisible(true);
        }

        // Trade System: 读取隐藏交易状态
        this.secretSold = nbt.getBoolean(NBT_SECRET_SOLD);
        this.secretKatanaId = nbt.getString(NBT_SECRET_KATANA_ID);

        // P0-A FIX: sigil seed no longer stored on entity; ignore legacy NBT values
        // (old saves may have SigilRollSeed/SigilRollInitialized - just skip them)

        // Ritual Reveal: 读取可见窗口
        this.ritualRevealUntilTick = nbt.contains("RitualRevealUntil") ? nbt.getLong("RitualRevealUntil") : 0L;

        if (DEBUG_DESPAWN) {
            LOGGER.debug("[Merchant] NBT_LOAD spawnTick={} isInWarningPhase={} hasEverTraded={} secretSold={}",
                    spawnTick, isInWarningPhase, hasEverTraded, secretSold);
        }
    }

    // ========== Trade System: Getter/Setter ==========

    public boolean isSecretSold() {
        return secretSold;
    }

    public void setSecretSold(boolean secretSold) {
        this.secretSold = secretSold;
    }

    public String getSecretKatanaId() {
        return secretKatanaId;
    }

    public void setSecretKatanaId(String secretKatanaId) {
        this.secretKatanaId = normalizeSecretKatanaId(secretKatanaId);
    }

    /**
     * 初始化隐藏物品ID（仅在首次调用时生成）
     * seed=worldSeed ^ uuidMost ^ uuidLeast（不使用 playerUuid）
     */
    public void initSecretKatanaIdIfNeeded() {
        if (this.secretKatanaId == null || this.secretKatanaId.isEmpty()) {
            assignSecretKatanaIfNeeded();
            return;
        }
        String normalized = normalizeSecretKatanaId(this.secretKatanaId);
        if (normalized.isEmpty()) {
            String before = this.secretKatanaId;
            this.secretKatanaId = "";
            assignSecretKatanaIfNeeded();
            LOGGER.warn("[MoonTrade] KATANA_ID_MIGRATE merchant={} legacyId={} newId={}",
                    this.getUuid(), before, this.secretKatanaId);
            return;
        }
        if (!normalized.equals(this.secretKatanaId)) {
            String before = this.secretKatanaId;
            this.secretKatanaId = normalized;
            LOGGER.warn("[MoonTrade] KATANA_ID_MIGRATE merchant={} legacyId={} newId={}",
                    this.getUuid(), before, this.secretKatanaId);
        }
    }

    /**
     * 尝试标记隐藏物品已售出（原子操作）
     * 
     * @return true 如果成功标记（之前未售出）
     */
    public synchronized boolean tryMarkSecretSold(String soldSecretId) {
        if (this.secretSold) {
            return false;
        }
        String normalizedSoldId = normalizeSecretKatanaId(soldSecretId);
        this.secretSold = true;
        if ((this.secretKatanaId == null || this.secretKatanaId.isEmpty())
                && !normalizedSoldId.isEmpty()) {
            this.secretKatanaId = normalizedSoldId;
        }
        LOGGER.info("[MoonTrade] SECRET_SOLD merchant={} katanaId={} soldSecretId={}",
                this.getUuid().toString().substring(0, 8), this.secretKatanaId, normalizedSoldId);
        return true;
    }

    public synchronized boolean tryMarkSecretSold() {
        return tryMarkSecretSold(this.secretKatanaId);
    }

    /**
     * P0-A FIX: refreshSigilOffers is now a no-op on entity level.
     * Seed is derived from (merchantUuid, playerUuid, refreshSeenCount).
     * The caller (TradeActionHandler) increments refreshSeenCount which changes the
     * derived seed.
     * Kept as public method to avoid breaking TradeActionHandler call site.
     */
    public void refreshSigilOffers() {
        // P0-A: No entity-level state to update; seed derived
        // per-(merchant,player,refreshSeenCount)
        LOGGER.debug("[MoonTrade] SIGIL_REFRESH player={} merchant={} note=seed_now_derived_per_player",
                getCurrentPlayerForLog(), this.getUuid());
    }

    private String pickKatanaTypeForMerchant() {
        // 仅使用稳定实体信息 seed，避免玩家维度影响
        long seed = deriveSecretKatanaSeed();
        return rollSecretKatanaType(seed, false).chosenType;
    }

    private SecretRollResult rollSecretKatanaType(long seed, boolean logRoll) {
        MerchantVariant variant = variantOf(this.getType());
        boolean debugBoost = false;
        Random random = new Random(seed);
        SecretRollResult rollResult = weightedPickSecretWithRoll(variant, random, debugBoost);
        if (logRoll && TradeConfig.TRADE_DEBUG) {
            LOGGER.info("[MoonTrade] MM_SECRET_ROLL variant={} seed={} roll={} chosenId={}",
                    getVariantKey(), seed, rollResult.roll, rollResult.chosenType);
        }
        return rollResult;
    }

    private String weightedPickSecret(MerchantVariant variant, Random rng, boolean debugBoost) {
        return weightedPickSecretWithRoll(variant, rng, debugBoost).chosenType;
    }

    private SecretRollResult weightedPickSecretWithRoll(MerchantVariant variant, Random rng, boolean debugBoost) {
        WeightedKatanaEntry[] pool = variant.weightedKatanaPool;
        int totalWeight = 0;
        int[] effectiveWeights = new int[pool.length];
        for (int i = 0; i < pool.length; i++) {
            int weight = Math.max(0, pool[i].weight);
            if (debugBoost && i > 0) {
                weight += 4;
            } else if (debugBoost && i == 0) {
                weight = Math.max(45, weight - 16);
            }
            effectiveWeights[i] = weight;
            totalWeight += weight;
        }
        if (totalWeight <= 0) {
            return new SecretRollResult("moonglow", -1);
        }
        int roll = rng.nextInt(totalWeight);
        int cursor = roll;
        for (int i = 0; i < pool.length; i++) {
            if (cursor < effectiveWeights[i]) {
                return new SecretRollResult(pool[i].katanaType, roll);
            }
            cursor -= effectiveWeights[i];
        }
        return new SecretRollResult(pool[pool.length - 1].katanaType, roll);
    }

    private void logSecretPick(String chosenId, long seed) {
        String[] candidates = KATANA_WHITELIST.keySet().toArray(new String[0]);
        Arrays.sort(candidates);
        Identifier typeId = Registries.ENTITY_TYPE.getId(this.getType());
        LOGGER.info(
                "[MoonTrade] action=SECRET_PICK merchantUuid={} entityTypeId={} seed={} candidatesSize={} candidates={} chosenId={} variant={}",
                this.getUuid(),
                typeId,
                seed,
                candidates.length,
                String.join("|", candidates),
                chosenId,
                getVariantKey());
    }

    public static boolean isSecretTradeOutput(ItemStack stack) {
        NbtComponent component = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (component == null) {
            return false;
        }
        return component.copyNbt().getBoolean(NBT_SECRET_MARKER);
    }

    public static boolean isKatana(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return KATANA_ID_BY_ITEM.containsKey(stack.getItem());
    }

    public static String getKatanaIdFromKatanaStack(ItemStack stack) {
        return KatanaIdUtil.extractCanonicalKatanaId(stack);
    }

    public static String getSecretTradeMarkerId(ItemStack stack) {
        return getKatanaIdFromKatanaStack(stack);
    }

    public static void clearSecretTradeMarker(ItemStack stack) {
        NbtComponent component = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (component == null) {
            return;
        }
        NbtCompound nbt = component.copyNbt();
        if (!nbt.contains(NBT_SECRET_MARKER) && !nbt.contains(NBT_SECRET_MARKER_ID)
                && !nbt.contains(NBT_MM_KATANA_ID)) {
            return;
        }
        nbt.remove(NBT_SECRET_MARKER);
        nbt.remove(NBT_SECRET_MARKER_ID);
        nbt.remove(NBT_MM_KATANA_ID);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
    }

    private static boolean isLegacyKatanaId(String id) {
        if (id == null || !id.startsWith("katana_")) {
            return false;
        }
        String suffix = id.substring("katana_".length());
        return suffix.length() == 8 && suffix.chars()
                .allMatch(ch -> (ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'f') || (ch >= 'A' && ch <= 'F'));
    }

    private String getCurrentPlayerForLog() {
        PlayerEntity customer = this.getCustomer();
        return customer == null ? "none" : customer.getUuid().toString();
    }
}
