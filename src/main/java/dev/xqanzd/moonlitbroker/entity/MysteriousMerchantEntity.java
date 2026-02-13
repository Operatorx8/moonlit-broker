package dev.xqanzd.moonlitbroker.entity;

import dev.xqanzd.moonlitbroker.entity.ai.DrinkPotionGoal;
import dev.xqanzd.moonlitbroker.entity.ai.EnhancedFleeGoal;
import dev.xqanzd.moonlitbroker.entity.ai.SeekLightGoal;
import dev.xqanzd.moonlitbroker.trade.item.BountyContractItem;
import dev.xqanzd.moonlitbroker.armor.transitional.TransitionalArmorItems;
import dev.xqanzd.moonlitbroker.katana.item.KatanaItems;
import dev.xqanzd.moonlitbroker.registry.ModEntities;
import dev.xqanzd.moonlitbroker.registry.ModItems;
import dev.xqanzd.moonlitbroker.trade.KatanaIdUtil;
import dev.xqanzd.moonlitbroker.trade.TradeConfig;
import dev.xqanzd.moonlitbroker.util.KatanaContractUtil;
import dev.xqanzd.moonlitbroker.world.KatanaOwnershipState;
import dev.xqanzd.moonlitbroker.world.MerchantSpawnerState;
import dev.xqanzd.moonlitbroker.world.MerchantUnlockState;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.Entity;
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
import java.util.Collections;
import java.util.Map;
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
    private static final boolean DEBUG_REFRESH_INJECT = true;
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

    // Phase 4: Despawn 数据
    private long spawnTick = -1;
    private boolean isInWarningPhase = false;
    /** P0-1: 是否已通知 SpawnerState 清除（防 discard 重入） */
    private boolean stateClearNotified = false;

    // Phase 8: 解封系统交易
    private String merchantName = "";

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

    private static final int ELIGIBLE_TRADE_COUNT = 15;
    private static final int REFRESH_GUARANTEE_COUNT = 3;
    private static final int TRADE_PAGE_SIZE = 7;
    private static final int KATANA_OFFER_MAX_USES = 1;
    private static final int RECLAIM_OFFER_MAX_USES = 1;

    public MysteriousMerchantEntity(EntityType<? extends WanderingTraderEntity> type, World world) {
        super(type, world);
    }

    // ========== Phase 4: Despawn 逻辑 ==========

    @Override
    public void tick() {
        super.tick();

        // 只在服务端处理 despawn 逻辑
        if (this.getEntityWorld().isClient()) {
            return;
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
                    Text.literal("神秘商人的诅咒降临于你...")
                            .formatted(Formatting.DARK_RED, Formatting.BOLD),
                    false);
            serverPlayer.sendMessage(
                    Text.literal("你感受到一股不祥的力量...")
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
            if (!offers.isEmpty()) {
                serverPlayer.sendTradeOffers(
                    syncId.getAsInt(), offers, levelProgress,
                    this.getExperience(), this.isLeveledMerchant(), this.canRefreshTrades()
                );
            }
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
        if (this.hasCustomer()) {
            return super.interactMob(player, hand);
        }

        // 检查是否手持神秘硬币
        if (heldItem.getItem() == ModItems.MYSTERIOUS_COIN) {
            return handleMysteriousCoinInteraction(player, heldItem, hand);
        }

        // Bounty v1: 非潜行且无人占用时提交；潜行拿契约按正常 UI 流程走
        if (heldItem.getItem() == ModItems.BOUNTY_CONTRACT && !player.isSneaking() && !this.hasCustomer()) {
            return handleBountyContractSubmit(player, heldItem, hand);
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
        grantFirstMeetGuideIfNeeded(serverPlayer);
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
     * 首次见面赠送指南卷轴和商人印记
     */
    private void grantFirstMeetGuideIfNeeded(ServerPlayerEntity player) {
        if (!(this.getEntityWorld() instanceof ServerWorld serverWorld)) {
            return;
        }

        MerchantUnlockState state = MerchantUnlockState.getServerState(serverWorld);
        MerchantUnlockState.Progress progress = state.getOrCreateProgress(player.getUuid());

        if (!progress.isFirstMeetGuideGiven()) {
            progress.setFirstMeetGuideGiven(true);
            state.markDirty();

            // 赠送指南卷轴
            ItemStack guideScroll = new ItemStack(ModItems.GUIDE_SCROLL, 1);
            dev.xqanzd.moonlitbroker.trade.item.GuideScrollItem.ensureGuideContent(guideScroll);
            if (!player.giveItemStack(guideScroll)) {
                // 背包满了，掉落在地上
                player.dropItem(guideScroll, false);
            }

            // 4 FIX: 赠送商人印记并绑定到玩家
            ItemStack merchantMark = new ItemStack(ModItems.MERCHANT_MARK, 1);
            dev.xqanzd.moonlitbroker.trade.item.MerchantMarkItem.bindToPlayer(merchantMark, player);
            if (!player.giveItemStack(merchantMark)) {
                player.dropItem(merchantMark, false);
            }

            // 发送消息
            player.sendMessage(
                    Text.literal("[神秘商人] 初次见面，送你一份指南和印记。")
                            .formatted(Formatting.GOLD),
                    false);

            LOGGER.info("[MoonTrade] FIRST_MEET_GUIDE player={}", player.getName().getString());
        }
    }

    /**
     * 处理神秘硬币交互
     */
    private ActionResult handleMysteriousCoinInteraction(PlayerEntity player, ItemStack coinStack, Hand hand) {
        if (this.getEntityWorld().isClient()) {
            return ActionResult.SUCCESS;
        }

        // 消耗一个硬币
        if (!player.isCreative()) {
            coinStack.decrement(1);
        }

        // 给予强化的正面效果
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.LUCK, 6000, 1)); // 5分钟幸运 II
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 1200, 1)); // 1分钟速度 II
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 600, 1)); // 30秒再生 II
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.HERO_OF_THE_VILLAGE, 12000, 0)); // 10分钟村庄英雄

        // 发送消息
        if (player instanceof ServerPlayerEntity serverPlayer) {
            serverPlayer.sendMessage(
                    Text.literal("神秘商人接受了你的供奉...")
                            .formatted(Formatting.GOLD),
                    false);
            serverPlayer.sendMessage(
                    Text.literal("你感受到一股神秘的祝福！")
                            .formatted(Formatting.YELLOW, Formatting.ITALIC),
                    true);
        }

        // 播放神秘音效和粒子
        if (this.getEntityWorld() instanceof ServerWorld serverWorld) {
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
        }

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
                    Text.literal("这不是有效的悬赏契约").formatted(Formatting.RED), true);
            LOGGER.info("[MoonTrade] action=BOUNTY_SUBMIT_REJECT reason=INVALID side=S player={}",
                    serverPlayer.getName().getString());
            return ActionResult.CONSUME;
        }

        // 2) 严格完成判定：boolean + progress >= required
        if (!BountyContractItem.isCompletedStrict(contractStack)) {
            int progress = BountyContractItem.getProgress(contractStack);
            int required = BountyContractItem.getRequired(contractStack);
            String target = BountyContractItem.getTarget(contractStack);
            serverPlayer.sendMessage(
                    Text.literal("悬赏未完成！进度: " + progress + "/" + required)
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
            dev.xqanzd.moonlitbroker.trade.loot.BountyHandler.grantRewards(serverPlayer);
            contractStack.decrement(1);
        } catch (Exception e) {
            LOGGER.error("[MoonTrade] action=BOUNTY_SUBMIT_ERROR side=S player={} target={} error={}",
                    serverPlayer.getName().getString(), target, e.getMessage(), e);
            serverPlayer.sendMessage(
                    Text.literal("悬赏提交失败，请查看日志").formatted(Formatting.RED), false);
            return ActionResult.CONSUME;
        }

        LOGGER.info("[MoonTrade] action=BOUNTY_SUBMIT_ACCEPT side=S player={} target={} progress={}/{} rewardScroll=1 rewardSilver={}",
                serverPlayer.getName().getString(), target, progress, required,
                dev.xqanzd.moonlitbroker.trade.TradeConfig.BOUNTY_SILVER_REWARD);

        serverPlayer.sendMessage(
                Text.literal("悬赏已提交！获得交易卷轴和银币")
                        .formatted(Formatting.GREEN),
                false);

        return ActionResult.SUCCESS;
    }

    // ========== Phase 3: 注册自定义 AI Goals ==========
    @Override
    protected void initGoals() {
        super.initGoals();

        // 优先级说明：数字越小优先级越高
        // 原版 WanderingTrader 的 goals:
        // - 0: SwimGoal
        // - 1: EscapeDangerGoal (panic)
        // - 1: LookAtCustomerGoal
        // - 2: WanderTowardTargetGoal
        // - 4: MoveTowardsRestrictionGoal
        // - 8: WanderAroundFarGoal
        // - 9: StopAndLookAtEntityGoal
        // - 10: LookAtEntityGoal

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
                }
                LOGGER.info("[MoonTrade] CONTRACT_ACTIVATE player={} katanaId={} instanceId={} reclaim={} merchant={}",
                        player.getUuid(), soldKatanaId, instanceId, isReclaim, this.getUuid());
            }
        }

        MerchantUnlockState state = MerchantUnlockState.getServerState(serverWorld);
        String variantKey = getVariantKey();
        MerchantUnlockState.Progress progress = state.getOrCreateProgress(player.getUuid(), variantKey);

        // 3. 更新玩家交易数据
        progress.setTradeCount(variantKey, progress.getTradeCount(variantKey) + 1);
        int count = progress.getTradeCount(variantKey);
        state.markDirty();

        // 4. 给玩家正面效果 (100 ticks = 5秒)
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 100, 0));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 100, 0));

        // 5. 达到解封资格提示（仅一次）
        if (count >= ELIGIBLE_TRADE_COUNT && !progress.isEligibleNotified(variantKey)) {
            progress.setEligibleNotified(variantKey, true);
            state.markDirty();
            if (player instanceof ServerPlayerEntity serverPlayer) {
                serverPlayer.sendMessage(
                        Text.literal("[神秘商人] 你已获得解封资格。")
                                .formatted(Formatting.GOLD, Formatting.BOLD),
                        false);
            }
            // P0-9 修复：关键业务日志使用 info 级别
            LOGGER.info("[MerchantUnlock] ELIGIBLE player={} tradeCount={}",
                    player.getName().getString(), count);
        }

        // 6. 识别解封交易
        if (isUnsealOffer(offer)) {
            if (!progress.isUnlockedKatanaHidden(variantKey)) {
                progress.setUnlockedKatanaHidden(variantKey, true);
                state.markDirty();
                LOGGER.info("[MerchantUnlock] UNLOCK player={} uuid={} variant={}",
                        player.getName().getString(), player.getUuid().toString().substring(0, 8), variantKey);
            }
            if (!progress.isUnlockedNotified(variantKey)) {
                progress.setUnlockedNotified(variantKey, true);
                state.markDirty();
                if (player instanceof ServerPlayerEntity serverPlayer) {
                    serverPlayer.sendMessage(
                            Text.literal("[神秘商人] 你解封了卷轴，隐藏交易已开启。")
                                    .formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD),
                            false);
                    // P0-6 修复：提示玩家重新打开交易界面以查看隐藏交易
                    serverPlayer.sendMessage(
                            Text.literal("[神秘商人] 请关闭并重新打开交易界面查看隐藏交易。")
                                    .formatted(Formatting.GRAY, Formatting.ITALIC),
                            false);
                }
            }
        }

        // Task F: afterUsing unlock scope audit log
        LOGGER.info(
                "[MoonTrade] UNLOCK_SCOPE_AUDIT player={} variant={} eligible={} unlocked={} tradeCount={} scope=per_variant",
                player.getName().getString(), variantKey,
                count >= ELIGIBLE_TRADE_COUNT, progress.isUnlockedKatanaHidden(variantKey), count);

        // 6. 调试日志（仅在 DEBUG_AI 开启时输出详细信息）
        if (DEBUG_AI) {
            LOGGER.debug("[MysteriousMerchant] TRADE_COMPLETE player={} count={} unlocked={}",
                    player.getName().getString(), count, progress.isUnlockedKatanaHidden(variantKey));
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
        LOGGER.info(
                "[MoonTrade] action=FILL_RECIPES_CALLED side=S merchant={} offersBefore={} offersAfter={} reason=empty_fallback_add_base",
                merchantTag(), offersBefore, offers.size());
    }

    private void addBaseOffers(TradeOfferList offers) {
        // 5 绿宝石 → 1 钻石
        addBaseOfferWithLoopGuard(offers, new TradeOffer(
                new TradedItem(Items.EMERALD, 5),
                new ItemStack(Items.DIAMOND, 1),
                12, // maxUses
                10, // merchantExperience
                0.05f // priceMultiplier
        ), "5_emerald_to_1_diamond");

        // 10 绿宝石 → 1 金苹果
        addBaseOfferWithLoopGuard(offers, new TradeOffer(
                new TradedItem(Items.EMERALD, 10),
                new ItemStack(Items.GOLDEN_APPLE, 1),
                12,
                10,
                0.05f), "10_emerald_to_1_golden_apple");

        // 添加神秘硬币交易（用绿宝石购买）
        addBaseOfferWithLoopGuard(offers, new TradeOffer(
                new TradedItem(Items.EMERALD, 32),
                new ItemStack(ModItems.MYSTERIOUS_COIN, 1),
                3, // 限量供应
                15,
                0.1f), "32_emerald_to_1_mysterious_coin");

        // Task C: Silver Note → Trade Scroll (NORMAL grade, NBT 由 TradeOutputSlotMixin
        // 初始化)
        addBaseOfferWithLoopGuard(offers, new TradeOffer(
                new TradedItem(ModItems.SILVER_NOTE, TradeConfig.SILVER_TO_SCROLL_COST),
                new ItemStack(ModItems.TRADE_SCROLL, 1),
                TradeConfig.SILVER_TO_SCROLL_MAX_USES,
                10,
                0.05f), "silver_to_trade_scroll");

        // Task E: 变体特色交易（过渡装备 + 特色物品）
        addVariantSpecialtyOffers(offers);

        MerchantVariant variant = variantOf(this.getType());
        LOGGER.info("[MoonTrade] BASE_BUILD variant={} offersCount={}",
                variant != null ? variant.typeKey : "UNKNOWN", offers.size());
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
                        new TradedItem(ModItems.SILVER_NOTE, 4),
                        new ItemStack(Items.CLOCK, 1), 5, 5, 0.05f), "specialty_clock");
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

    private void addBaseOfferWithLoopGuard(TradeOfferList offers, TradeOffer offer, String tradeDesc) {
        Item buyItem = offer.getOriginalFirstBuyItem().getItem();
        Item sellItem = offer.getSellItem().getItem();
        if (LOOP_GUARD_CURRENCIES.contains(buyItem)
                && LOOP_GUARD_CURRENCIES.contains(sellItem)
                && hasReverseCurrencyOffer(offers, buyItem, sellItem)) {
            LOGGER.warn("[MoonTrade] MM_TRADE_LOOP_GUARD removed={} reason=reverse_currency_pair_exists",
                    tradeDesc);
            return;
        }
        offers.add(offer);
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
        offers.add(new TradeOffer(new TradedItem(Items.STICK, 1), new ItemStack(Items.STRING, 1), 64, 1, 0.0f));
        offers.add(new TradeOffer(new TradedItem(Items.ROTTEN_FLESH, 4), new ItemStack(Items.PAPER, 1), 64, 1, 0.0f));
        offers.add(new TradeOffer(new TradedItem(Items.FLINT, 1), new ItemStack(Items.TORCH, 4), 64, 1, 0.0f));
        offers.add(new TradeOffer(new TradedItem(Items.WHEAT_SEEDS, 8), new ItemStack(Items.APPLE, 1), 64, 1, 0.0f));
        offers.add(
                new TradeOffer(new TradedItem(Items.COBBLESTONE, 16), new ItemStack(Items.CLAY_BALL, 2), 64, 1, 0.0f));
        offers.add(new TradeOffer(new TradedItem(Items.DIRT, 16), new ItemStack(Items.BRICK, 1), 64, 1, 0.0f));
        offers.add(new TradeOffer(new TradedItem(Items.SAND, 8), new ItemStack(Items.GLASS, 2), 64, 1, 0.0f));
        offers.add(new TradeOffer(new TradedItem(Items.GRAVEL, 8), new ItemStack(Items.CLAY_BALL, 1), 64, 1, 0.0f));
        offers.add(new TradeOffer(new TradedItem(Items.KELP, 8), new ItemStack(Items.DRIED_KELP, 2), 64, 1, 0.0f));
        offers.add(new TradeOffer(new TradedItem(Items.BONE, 1), new ItemStack(Items.BONE_MEAL, 3), 64, 1, 0.0f));
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
                    64, 1, 0.0f));
        }
        return offers.size() - before;
    }

    public OfferBuildAudit rebuildOffersForPlayer(ServerPlayerEntity player, OfferBuildSource source) {
        long startNanos = System.nanoTime();
        ensureVariantIdentityIfNeeded();
        String variantKey = getVariantKey();
        TradeOfferList offers = this.getOffers();
        boolean eligible = false;
        boolean unlocked = false;
        long seedForLog = -1L;
        int refreshForThisMerchant = -1;
        MerchantUnlockState state = null;
        MerchantUnlockState.Progress progress = null;
        KatanaOwnershipState ownershipState = null;
        String cacheResult = "BYPASS";

        if (this.getEntityWorld() instanceof ServerWorld serverWorld) {
            state = MerchantUnlockState.getServerState(serverWorld);
            progress = state.getOrCreateProgress(player.getUuid(), variantKey);
            ownershipState = KatanaOwnershipState.getServerState(serverWorld);
            eligible = progress.getTradeCount(variantKey) >= ELIGIBLE_TRADE_COUNT;
            unlocked = progress.isUnlockedKatanaHidden(variantKey);
            initSecretKatanaIdIfNeeded();

            MerchantUnlockState.Progress.RefreshCountReadResult refreshRead = progress
                    .readSigilRefreshSeen(this.getUuid());
            refreshForThisMerchant = Math.max(0, refreshRead.count());
            seedForLog = deriveSigilSeed(this.getUuid(), player.getUuid(), refreshForThisMerchant);
        }

        offers.clear();
        addBaseOffers(offers);

        if (state != null && progress != null) {
            if (eligible) {
                int normalStartSize = offers.size();
                offers.add(createSealedLedgerOffer());
                offers.add(createUnsealOffer());
                addSigilOffers(offers, seedForLog, refreshForThisMerchant);
                int chosenSigilTrades = offers.size() - normalStartSize - 2; // subtract N1 + N2
                LOGGER.info("[MoonTrade] NORMAL_BUILD chosenSigilTrades={} totalNormalOffers={}",
                        chosenSigilTrades, offers.size() - normalStartSize);
            }

            if (unlocked) {
                addKatanaHiddenOffers(offers, player);
            }
        }

        if (ownershipState != null) {
            applyKatanaOwnershipSoldOut(offers, player.getUuid(), ownershipState);
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

        OfferCounters counters = classifyOffers(offers);
        int offersHash = computeOffersHash(offers);
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
        return audit;
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
        addKatanaHiddenOffers(offers, player);

        applyKatanaOwnershipSoldOut(offers, player.getUuid(), ownershipState);

        int injectedCount = 0;
        if (DEBUG_SCROLL_INJECT && unlocked) {
            injectedCount = appendDebugScrollOffers(offers);
        }
        LOGGER.debug("[MoonTrade] MM_SCROLL_INJECT enabled={} added={}",
                DEBUG_SCROLL_INJECT, injectedCount);

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
                25,
                0.0f);
    }

    private TradeOffer createUnsealOffer() {
        return new TradeOffer(
                new TradedItem(ModItems.SEALED_LEDGER, 1),
                Optional.of(new TradedItem(ModItems.SIGIL, TradeConfig.UNSEAL_SIGIL_COST)),
                new ItemStack(ModItems.ARCANE_LEDGER, 1),
                1,
                50,
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

    private void addKatanaHiddenOffers(TradeOfferList offers, ServerPlayerEntity player) {
        // P0-3: 确保 ID 已初始化
        initSecretKatanaIdIfNeeded();

        if (this.secretKatanaId == null || this.secretKatanaId.isEmpty()) {
            LOGGER.warn("[MoonTrade] KATANA_BUILD_SKIP player={} merchant={} secretKatanaId={} reason=id_still_empty",
                    getCurrentPlayerForLog(), this.getUuid(), this.secretKatanaId);
            return;
        }

        TradeOffer offer = createKatanaOffer(player);
        if (offer == null) {
            LOGGER.warn("[MoonTrade] KATANA_BUILD_SKIP player={} merchant={} secretKatanaId={} reason=resolve_failed",
                    getCurrentPlayerForLog(), this.getUuid(), this.secretKatanaId);
            return;
        }
        offers.add(offer);

        // Add reclaim offer if eligible
        addReclaimOffer(offers, player);

        // ---- Variant Anchors: B 招牌锚点（固定必出） + 随机层 1 件 B ----
        addVariantBAnchorAndRandom(offers, player);
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
        offers.add(new TradeOffer(
                new TradedItem(ModItems.SILVER_NOTE, TradeConfig.B_ARMOR_SILVER_COST),
                Optional.of(new TradedItem(Items.EMERALD, TradeConfig.B_ARMOR_EMERALD_TAX)),
                new ItemStack(bAnchorItem),
                TradeConfig.B_ARMOR_MAX_USES, 20, 0.05f));
        LOGGER.info("[MoonTrade] B_ANCHOR_ADD variant={} item={}", variantKey,
                Registries.ITEM.getId(bAnchorItem));

        // ---- 随机层：Draw 1 ----
        java.util.List<Item> pool = TradeConfig.variantBRandomPool().get(variantKey);
        if (pool == null || pool.isEmpty()) {
            LOGGER.info("[MoonTrade] B_RANDOM_SKIP variant={} reason=empty_pool", variantKey);
            return;
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
        String lastB = TradeConfig.getLastRandomB(player.getUuid(), variantKey);
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
        TradeConfig.setLastRandomB(player.getUuid(), variantKey, drawnId);

        offers.add(new TradeOffer(
                new TradedItem(ModItems.SILVER_NOTE, TradeConfig.B_ARMOR_SILVER_COST),
                Optional.of(new TradedItem(Items.EMERALD, TradeConfig.B_ARMOR_EMERALD_TAX)),
                new ItemStack(drawn),
                TradeConfig.B_ARMOR_MAX_USES, 20, 0.05f));
        LOGGER.info("[MoonTrade] B_RANDOM_ADD variant={} item={} anchorEpic={}", variantKey, drawnId, anchorIsEpic);
    }

    /**
     * Add a reclaim trade offer if the player has previously owned this merchant variant's katana,
     * has arcane access, and the reclaim cooldown has passed.
     */
    private void addReclaimOffer(TradeOfferList offers, ServerPlayerEntity player) {
        if (!(this.getEntityWorld() instanceof ServerWorld serverWorld)) return;

        String katanaType = resolveKatanaType();
        if (katanaType == null) return;

        KatanaOwnershipState ownershipState = KatanaOwnershipState.getServerState(serverWorld);
        UUID playerUuid = player.getUuid();

        // Must have owned this katana type before
        if (!ownershipState.hasOwned(playerUuid, katanaType)) return;

        // Must have arcane unlock for this variant
        MerchantUnlockState unlockState = MerchantUnlockState.getServerState(serverWorld);
        String variantKey = getVariantKey();
        MerchantUnlockState.Progress progress = unlockState.getOrCreateProgress(playerUuid, variantKey);
        if (!progress.isUnlockedKatanaHidden(variantKey)) return;

        // Check reclaim cooldown
        long lastReclaim = ownershipState.getLastReclaimTick(playerUuid, katanaType);
        long nowTick = serverWorld.getTime();
        if (lastReclaim > 0 && (nowTick - lastReclaim) < TradeConfig.RECLAIM_CD_TICKS) {
            LOGGER.info("[MoonTrade] RECLAIM_COOLDOWN player={} type={} remaining={}",
                    playerUuid, katanaType, TradeConfig.RECLAIM_CD_TICKS - (nowTick - lastReclaim));
            return;
        }

        // Resolve the katana item
        Item katanaItem = KATANA_WHITELIST.get(katanaType);
        if (katanaItem == null) return;

        ItemStack reclaimStack = new ItemStack(katanaItem, 1);
        // Write contract data + reclaim marker
        UUID instanceId = UUID.randomUUID();
        KatanaContractUtil.writeKatanaContract(reclaimStack, playerUuid, katanaType, instanceId);
        // Also write legacy markers for compat
        NbtComponent component = reclaimStack.get(DataComponentTypes.CUSTOM_DATA);
        NbtCompound nbt = component == null ? new NbtCompound() : component.copyNbt();
        nbt.putBoolean(NBT_SECRET_MARKER, true);
        nbt.putString(NBT_SECRET_MARKER_ID, katanaType);
        nbt.putBoolean(KatanaContractUtil.NBT_RECLAIM, true);
        reclaimStack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));

        TradeOffer reclaimOffer = new TradeOffer(
                new TradedItem(Items.NETHERITE_INGOT, TradeConfig.RECLAIM_COST_NETHERITE),
                Optional.of(new TradedItem(Items.DIAMOND, TradeConfig.RECLAIM_COST_DIAMONDS)),
                reclaimStack,
                RECLAIM_OFFER_MAX_USES,
                80,
                0.0f);
        offers.add(reclaimOffer);

        LOGGER.info("[MoonTrade] RECLAIM_OFFER_ADDED player={} type={} instanceId={} merchant={}",
                playerUuid, katanaType, instanceId, this.getUuid());
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

    private TradeOffer createKatanaOffer(ServerPlayerEntity player) {
        ItemStack katanaStack = resolveKatanaStack();
        if (katanaStack.isEmpty()) {
            return null;
        }
        markSecretTradeOutput(katanaStack, player);

        // Task D: Per-variant secondary cost
        MerchantVariant variant = variantOf(this.getType());
        Item secondaryCost;
        int secondaryCount;
        switch (variant) {
            case ARID -> {
                secondaryCost = Items.BLAZE_POWDER;
                secondaryCount = 8;
            }
            case COLD -> {
                secondaryCost = Items.PACKED_ICE;
                secondaryCount = 16;
            }
            case WET -> {
                secondaryCost = Items.NAUTILUS_SHELL;
                secondaryCount = 4;
            }
            case EXOTIC -> {
                secondaryCost = Items.ECHO_SHARD;
                secondaryCount = 4;
            }
            default -> {
                secondaryCost = Items.EMERALD;
                secondaryCount = 32;
            }
        }
        LOGGER.info("[MoonTrade] KATANA_OFFER_BUILD variant={} secondaryCost={} secondaryCount={} katanaId={}",
                variant.typeKey, Registries.ITEM.getId(secondaryCost), secondaryCount, this.secretKatanaId);

        TradeOffer offer = new TradeOffer(
                new TradedItem(ModItems.ARCANE_LEDGER, 1),
                Optional.of(new TradedItem(secondaryCost, secondaryCount)),
                katanaStack,
                KATANA_OFFER_MAX_USES,
                80,
                0.0f);
        return offer;
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
                new TradedItem(Items.EMERALD, 64),
                new ItemStack(ModItems.SIGIL, 1),
                1, 40, 0.0f)));
        pool.add(new SigilOfferEntry("A2", SigilTier.A, new TradeOffer(
                new TradedItem(Items.DIAMOND, 8),
                new ItemStack(ModItems.SIGIL, 1),
                1, 40, 0.0f)));
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
        ItemStack first = offer.getOriginalFirstBuyItem();
        ItemStack sell = offer.getSellItem();
        return (first.isOf(Items.EMERALD) && first.getCount() == 5 && sell.isOf(Items.DIAMOND) && sell.getCount() == 1)
                || (first.isOf(Items.EMERALD) && first.getCount() == 10 && sell.isOf(Items.GOLDEN_APPLE)
                        && sell.getCount() == 1)
                || (first.isOf(Items.EMERALD) && first.getCount() == 32 && sell.isOf(ModItems.MYSTERIOUS_COIN)
                        && sell.getCount() == 1);
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
        hash = 31 * hash + stack.getComponents().hashCode();
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
