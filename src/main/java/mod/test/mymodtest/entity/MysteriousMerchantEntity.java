package mod.test.mymodtest.entity;

import mod.test.mymodtest.entity.ai.DrinkPotionGoal;
import mod.test.mymodtest.entity.ai.EnhancedFleeGoal;
import mod.test.mymodtest.entity.ai.SeekLightGoal;
import mod.test.mymodtest.katana.item.KatanaItems;
import mod.test.mymodtest.registry.ModItems;
import mod.test.mymodtest.world.MerchantSpawnerState;
import mod.test.mymodtest.world.MerchantUnlockState;
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
    /** Base 交易闭环防护：禁止双向货币互兑形成永动机 */
    private static final Set<Item> LOOP_GUARD_CURRENCIES = Set.of(
        Items.EMERALD,
        Items.DIAMOND,
        Items.GOLD_INGOT
    );

    // ========== Phase 3: AI 行为常量 ==========
    /** 基础移动速度 */
    public static final double BASE_MOVEMENT_SPEED = 0.5;

    // ========== Phase 4: Despawn 常量 ==========
    /** 1 Minecraft 天 = 24000 ticks */
    private static final int TICKS_PER_DAY = 24000;
    /** 正常模式：2天后开始消失预警（事件 NPC 模式） */
    private static final int WARNING_TIME_NORMAL = 2 * TICKS_PER_DAY;  // 48000 ticks
    /** 正常模式：5天后强制消失（事件 NPC 模式） */
    private static final int DESPAWN_TIME_NORMAL = 5 * TICKS_PER_DAY;  // 120000 ticks
    /** 调试模式：30秒后开始消失预警 */
    private static final int WARNING_TIME_DEBUG = 30 * 20;  // 600 ticks
    /** 调试模式：60秒后强制消失 */
    private static final int DESPAWN_TIME_DEBUG = 60 * 20;  // 1200 ticks
    /** 闪烁间隔（ticks）- 20 ticks = 1秒 */
    private static final int BLINK_INTERVAL = 20;

    // ========== Phase 5: 惩罚常量 ==========
    /** 攻击惩罚：失明持续时间（ticks）*/
    private static final int ATTACK_BLINDNESS_DURATION = 100;  // 5秒
    /** 攻击惩罚：反胃持续时间（ticks）*/
    private static final int ATTACK_NAUSEA_DURATION = 140;     // 7秒
    /** 击杀惩罚：效果持续时间倍率 */
    private static final int KILL_EFFECT_MULTIPLIER = 4;
    /** 击杀惩罚：额外的不幸效果持续时间（ticks）*/
    private static final int KILL_UNLUCK_DURATION = 24000;     // 20分钟

    // Phase 2.3: 交易统计
    private boolean hasEverTraded = false;

    // Phase 4: Despawn 数据
    private long spawnTick = -1;
    private boolean isInWarningPhase = false;
    /** P0-1: 是否已通知 SpawnerState 清除（防 discard 重入） */
    private boolean stateClearNotified = false;

    // Phase 8: 解封系统交易
    private TradeOfferList katanaHiddenOffers = null;
    private String katanaHiddenOffersCacheId = "";
    private String merchantName = "";

    // ========== Trade System: 隐藏交易限制 ==========
    /** 是否已售出隐藏物品 */
    private boolean secretSold = false;
    /** 隐藏物品ID（每个商人实体唯一） */
    private String secretKatanaId = "";

    // P0-A FIX: entity-level sigil seed DEPRECATED - seed is now derived per-(merchant,player)
    // Kept only for NBT backward-compat read; never written to new saves.
    @SuppressWarnings("unused")
    private long sigilRollSeed_DEPRECATED = 0;
    @SuppressWarnings("unused")
    private boolean sigilRollInitialized_DEPRECATED = false;

    private static final int ELIGIBLE_TRADE_COUNT = 15;
    private static final int REFRESH_GUARANTEE_COUNT = 3;
    private static final int TRADE_PAGE_SIZE = 7;

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
                    int remainingSec = (int)((despawnTime - aliveTicks) / 20);
                    LOGGER.debug("[Merchant] WARNING_ENTER side=SERVER remaining={}s aliveTicks={} spawnTick={} worldTime={}",
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
                    10,  // count
                    0.3, 0.5, 0.3,  // offset
                    0.02  // speed
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
                    1.0f, 1.0f
            );

            // 生成大量传送门粒子
            serverWorld.spawnParticles(
                    ParticleTypes.PORTAL,
                    this.getX(), this.getY() + 1.0, this.getZ(),
                    50,  // count
                    0.5, 1.0, 0.5,  // offset
                    0.1  // speed
            );

            // 生成烟雾粒子
            serverWorld.spawnParticles(
                    ParticleTypes.LARGE_SMOKE,
                    this.getX(), this.getY() + 0.5, this.getZ(),
                    20,
                    0.3, 0.5, 0.3,
                    0.05
            );

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
        if (spawnTick < 0) return -1;
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
     * @param serverWorld 服务端世界
     * @param reason 清除原因（DESPAWN / DEATH）
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
        if (player.isCreative()) return;

        // 失明效果
        player.addStatusEffect(new StatusEffectInstance(
                StatusEffects.BLINDNESS,
                ATTACK_BLINDNESS_DURATION,
                0,    // amplifier
                false, // ambient
                true,  // showParticles
                true   // showIcon
        ));

        // 反胃效果
        player.addStatusEffect(new StatusEffectInstance(
                StatusEffects.NAUSEA,
                ATTACK_NAUSEA_DURATION,
                0,
                false,
                true,
                true
        ));

        // 播放不祥音效
        if (this.getEntityWorld() instanceof ServerWorld serverWorld) {
            serverWorld.playSound(
                    null,
                    player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE,
                    SoundCategory.HOSTILE,
                    0.5f, 1.5f
            );
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
        if (player.isCreative()) return;

        // 失明效果（翻倍时长）
        player.addStatusEffect(new StatusEffectInstance(
                StatusEffects.BLINDNESS,
                ATTACK_BLINDNESS_DURATION * KILL_EFFECT_MULTIPLIER,
                1,    // amplifier 提升
                false,
                true,
                true
        ));

        // 反胃效果（翻倍时长）
        player.addStatusEffect(new StatusEffectInstance(
                StatusEffects.NAUSEA,
                ATTACK_NAUSEA_DURATION * KILL_EFFECT_MULTIPLIER,
                1,
                false,
                true,
                true
        ));

        // 不幸效果（长时间）
        player.addStatusEffect(new StatusEffectInstance(
                StatusEffects.UNLUCK,
                KILL_UNLUCK_DURATION,
                1,
                false,
                true,
                true
        ));

        // 缓慢效果
        player.addStatusEffect(new StatusEffectInstance(
                StatusEffects.SLOWNESS,
                ATTACK_BLINDNESS_DURATION * KILL_EFFECT_MULTIPLIER,
                1,
                false,
                true,
                true
        ));

        // 发送警告消息
        if (player instanceof ServerPlayerEntity serverPlayer) {
            serverPlayer.sendMessage(
                    Text.literal("神秘商人的诅咒降临于你...")
                            .formatted(Formatting.DARK_RED, Formatting.BOLD),
                    false
            );
            serverPlayer.sendMessage(
                    Text.literal("你感受到一股不祥的力量...")
                            .formatted(Formatting.DARK_PURPLE, Formatting.ITALIC),
                    true  // actionBar
            );
        }

        // 播放恐怖音效
        if (this.getEntityWorld() instanceof ServerWorld serverWorld) {
            serverWorld.playSound(
                    null,
                    player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENTITY_WITHER_SPAWN,
                    SoundCategory.HOSTILE,
                    0.8f, 0.5f
            );

            // 生成不祥粒子
            serverWorld.spawnParticles(
                    ParticleTypes.WITCH,
                    player.getX(), player.getY() + 1.0, player.getZ(),
                    30,
                    0.5, 1.0, 0.5,
                    0.1
            );
        }

        if (DEBUG_AI) {
            LOGGER.info("[MysteriousMerchant] 玩家 {} 击杀了商人，施加严重惩罚！",
                player.getName().getString());
        }
    }

    // ========== Phase 5: 特殊交互 ==========

    /**
     * 覆写交互：检测神秘硬币、首次见面赠送指南
     */
    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        ItemStack heldItem = player.getStackInHand(hand);

        // 检查是否手持神秘硬币
        if (heldItem.getItem() == ModItems.MYSTERIOUS_COIN) {
            return handleMysteriousCoinInteraction(player, heldItem, hand);
        }

        // 保留原版前置约束，避免与刷怪蛋/无效状态冲突
        if (heldItem.isOf(Items.VILLAGER_SPAWN_EGG) || !this.isAlive() || this.hasCustomer() || this.isBaby()) {
            return super.interactMob(player, hand);
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

        grantFirstMeetGuideIfNeeded(serverPlayer);
        initSecretKatanaIdIfNeeded();

        OfferBuildAudit audit = rebuildOffersForPlayer(serverPlayer, OfferBuildSource.INTERACT_PREOPEN);
        if (audit.offersTotal() <= 0) {
            LOGGER.warn("[MoonTrade] action=OPEN_UI side=S player={} merchant={} source={} cache={} unlock={} offersTotal={} base={} sigil={} hidden={} offersHash={} seed={} refreshSeenCount={} durationMs={} pageSize={} reason=no_offers",
                playerTag(serverPlayer), merchantTag(), audit.source(), audit.cache(), audit.unlock(),
                audit.offersTotal(), audit.baseCount(), audit.sigilCount(), audit.hiddenCount(),
                Integer.toHexString(audit.offersHash()), audit.seed(), audit.refreshSeenCount(), audit.durationMs(), TRADE_PAGE_SIZE);
            return ActionResult.CONSUME;
        }

        this.setCustomer(serverPlayer);
        this.sendOffers(serverPlayer, this.getDisplayName(), this.getExperience());

        LOGGER.info("[MoonTrade] action=OPEN_UI side=S player={} merchant={} source={} cache={} unlock={} offersTotal={} base={} sigil={} hidden={} offersHash={} seed={} refreshSeenCount={} durationMs={} pageSize={}",
            playerTag(serverPlayer), merchantTag(), audit.source(), audit.cache(), audit.unlock(),
            audit.offersTotal(), audit.baseCount(), audit.sigilCount(), audit.hiddenCount(),
            Integer.toHexString(audit.offersHash()), audit.seed(), audit.refreshSeenCount(), audit.durationMs(), TRADE_PAGE_SIZE);

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
            if (!player.giveItemStack(guideScroll)) {
                // 背包满了，掉落在地上
                player.dropItem(guideScroll, false);
            }
            
            // 4 FIX: 赠送商人印记并绑定到玩家
            ItemStack merchantMark = new ItemStack(ModItems.MERCHANT_MARK, 1);
            mod.test.mymodtest.trade.item.MerchantMarkItem.bindToPlayer(merchantMark, player);
            if (!player.giveItemStack(merchantMark)) {
                player.dropItem(merchantMark, false);
            }
            
            // 发送消息
            player.sendMessage(
                Text.literal("[神秘商人] 初次见面，送你一份指南和印记。")
                    .formatted(Formatting.GOLD),
                false
            );
            
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
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.LUCK, 6000, 1));        // 5分钟幸运 II
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 1200, 1));       // 1分钟速度 II
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 600, 1)); // 30秒再生 II
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.HERO_OF_THE_VILLAGE, 12000, 0)); // 10分钟村庄英雄

        // 发送消息
        if (player instanceof ServerPlayerEntity serverPlayer) {
            serverPlayer.sendMessage(
                    Text.literal("神秘商人接受了你的供奉...")
                            .formatted(Formatting.GOLD),
                    false
            );
            serverPlayer.sendMessage(
                    Text.literal("你感受到一股神秘的祝福！")
                            .formatted(Formatting.YELLOW, Formatting.ITALIC),
                    true
            );
        }

        // 播放神秘音效和粒子
        if (this.getEntityWorld() instanceof ServerWorld serverWorld) {
            serverWorld.playSound(
                    null,
                    this.getX(), this.getY(), this.getZ(),
                    SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE,
                    SoundCategory.NEUTRAL,
                    1.0f, 1.0f
            );

            serverWorld.spawnParticles(
                    ParticleTypes.ENCHANT,
                    this.getX(), this.getY() + 1.5, this.getZ(),
                    50,
                    0.5, 0.5, 0.5,
                    0.5
            );

            serverWorld.spawnParticles(
                    ParticleTypes.HAPPY_VILLAGER,
                    player.getX(), player.getY() + 1.0, player.getZ(),
                    20,
                    0.3, 0.5, 0.3,
                    0.1
            );
        }

        if (DEBUG_AI) {
            LOGGER.debug("[MysteriousMerchant] 玩家 {} 使用了神秘硬币，获得祝福效果",
                    player.getName().getString());
        }

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

        MerchantUnlockState state = MerchantUnlockState.getServerState(serverWorld);
        MerchantUnlockState.Progress progress = state.getOrCreateProgress(player.getUuid());

        // 3. 更新玩家交易数据
        progress.setTradeCount(progress.getTradeCount() + 1);
        int count = progress.getTradeCount();
        state.markDirty();

        // 4. 给玩家正面效果 (100 ticks = 5秒)
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 100, 0));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 100, 0));

        // 5. 达到解封资格提示（仅一次）
        if (count >= ELIGIBLE_TRADE_COUNT && !progress.isEligibleNotified()) {
            progress.setEligibleNotified(true);
            state.markDirty();
            if (player instanceof ServerPlayerEntity serverPlayer) {
                serverPlayer.sendMessage(
                        Text.literal("[神秘商人] 你已获得解封资格。")
                                .formatted(Formatting.GOLD, Formatting.BOLD),
                        false
                );
            }
            // P0-9 修复：关键业务日志使用 info 级别
            LOGGER.info("[MerchantUnlock] ELIGIBLE player={} tradeCount={}",
                    player.getName().getString(), count);
        }

        // 6. 识别解封交易
        if (isUnsealOffer(offer)) {
            if (!progress.isUnlockedKatanaHidden()) {
                progress.setUnlockedKatanaHidden(true);
                state.markDirty();
                LOGGER.info("[MerchantUnlock] UNLOCK player={} uuid={}",
                        player.getName().getString(), player.getUuid().toString().substring(0, 8));
            }
            if (!progress.isUnlockedNotified()) {
                progress.setUnlockedNotified(true);
                state.markDirty();
                if (player instanceof ServerPlayerEntity serverPlayer) {
                    serverPlayer.sendMessage(
                            Text.literal("[神秘商人] 你解封了卷轴，隐藏交易已开启。")
                                    .formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD),
                            false
                    );
                    // P0-6 修复：提示玩家重新打开交易界面以查看隐藏交易
                    serverPlayer.sendMessage(
                            Text.literal("[神秘商人] 请关闭并重新打开交易界面查看隐藏交易。")
                                    .formatted(Formatting.GRAY, Formatting.ITALIC),
                            false
                    );
                }
            }
        }

        // 6. 调试日志（仅在 DEBUG_AI 开启时输出详细信息）
        if (DEBUG_AI) {
            LOGGER.debug("[MysteriousMerchant] TRADE_COMPLETE player={} count={} unlocked={}",
                    player.getName().getString(), count, progress.isUnlockedKatanaHidden());
        }
    }

    @Override
    protected void fillRecipes() {
        TradeOfferList offers = this.getOffers();
        int offersBefore = offers.size();
        if (offersBefore > 0 || hasHiddenOffers(offers)) {
            LOGGER.info("[MoonTrade] action=FILL_RECIPES_CALLED side=S merchant={} offersBefore={} offersAfter={} reason=already_built_noop",
                merchantTag(), offersBefore, offersBefore);
            return;
        }
        addBaseOffers(offers);
        LOGGER.info("[MoonTrade] action=FILL_RECIPES_CALLED side=S merchant={} offersBefore={} offersAfter={} reason=empty_fallback_add_base",
            merchantTag(), offersBefore, offers.size());
    }

    private void addBaseOffers(TradeOfferList offers) {
        // 5 绿宝石 → 1 钻石
        addBaseOfferWithLoopGuard(offers, new TradeOffer(
                new TradedItem(Items.EMERALD, 5),
                new ItemStack(Items.DIAMOND, 1),
                12,    // maxUses
                10,    // merchantExperience
                0.05f  // priceMultiplier
        ), "5_emerald_to_1_diamond");

        // 10 绿宝石 → 1 金苹果
        addBaseOfferWithLoopGuard(offers, new TradeOffer(
                new TradedItem(Items.EMERALD, 10),
                new ItemStack(Items.GOLDEN_APPLE, 1),
                12,
                10,
                0.05f
        ), "10_emerald_to_1_golden_apple");

        // 添加神秘硬币交易（用绿宝石购买）
        addBaseOfferWithLoopGuard(offers, new TradeOffer(
                new TradedItem(Items.EMERALD, 32),
                new ItemStack(ModItems.MYSTERIOUS_COIN, 1),
                3,     // 限量供应
                15,
                0.1f
        ), "32_emerald_to_1_mysterious_coin");
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
        offers.add(new TradeOffer(new TradedItem(Items.COBBLESTONE, 16), new ItemStack(Items.CLAY_BALL, 2), 64, 1, 0.0f));
        offers.add(new TradeOffer(new TradedItem(Items.DIRT, 16), new ItemStack(Items.BRICK, 1), 64, 1, 0.0f));
        offers.add(new TradeOffer(new TradedItem(Items.SAND, 8), new ItemStack(Items.GLASS, 2), 64, 1, 0.0f));
        offers.add(new TradeOffer(new TradedItem(Items.GRAVEL, 8), new ItemStack(Items.CLAY_BALL, 1), 64, 1, 0.0f));
        offers.add(new TradeOffer(new TradedItem(Items.KELP, 8), new ItemStack(Items.DRIED_KELP, 2), 64, 1, 0.0f));
        offers.add(new TradeOffer(new TradedItem(Items.BONE, 1), new ItemStack(Items.BONE_MEAL, 3), 64, 1, 0.0f));
        return offers.size() - before;
    }

    /**
     * DEBUG only: Append 10 visually distinct trades after a REFRESH_NORMAL rebuild.
     * Each trade has a unique displayName "DBG REFRESH=<refreshSeenCount> #<i>" so you can
     * confirm in-game that refresh truly rebuilt the offer list with a new refreshSeenCount.
     * Uses different result items (paper, stick, dirt, etc.) for easy visual distinction.
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
                    .formatted(Formatting.RED, Formatting.BOLD)
            );
            offers.add(new TradeOffer(
                new TradedItem(Items.WHEAT_SEEDS, 1),
                sellStack,
                64, 1, 0.0f
            ));
        }
        return offers.size() - before;
    }

    public OfferBuildAudit rebuildOffersForPlayer(ServerPlayerEntity player, OfferBuildSource source) {
        long startNanos = System.nanoTime();
        TradeOfferList offers = this.getOffers();
        boolean eligible = false;
        boolean unlocked = false;
        long seedForLog = -1L;
        int refreshForThisMerchant = -1;
        MerchantUnlockState state = null;
        MerchantUnlockState.Progress progress = null;
        String cacheResult = "BYPASS";

        if (this.getEntityWorld() instanceof ServerWorld serverWorld) {
            state = MerchantUnlockState.getServerState(serverWorld);
            progress = state.getOrCreateProgress(player.getUuid());
            eligible = progress.getTradeCount() >= ELIGIBLE_TRADE_COUNT;
            unlocked = progress.isUnlockedKatanaHidden();

            MerchantUnlockState.Progress.RefreshCountReadResult refreshRead = progress.readSigilRefreshSeen(this.getUuid());
            refreshForThisMerchant = Math.max(0, refreshRead.count());
            seedForLog = deriveSigilSeed(this.getUuid(), player.getUuid(), refreshForThisMerchant);
        }

        offers.clear();
        addBaseOffers(offers);

        if (state != null && progress != null) {
            if (eligible) {
                offers.add(createSealedLedgerOffer());
            }

            if (eligible && !unlocked) {
                offers.add(createUnsealOffer());
                addSigilOffers(offers, seedForLog, refreshForThisMerchant);
            }

            if (unlocked) {
                addSigilOffers(offers, seedForLog, refreshForThisMerchant);
                addKatanaHiddenOffers(offers);
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

        OfferCounters counters = classifyOffers(offers);
        int offersHash = computeOffersHash(offers);
        String unlockState = toUnlockState(eligible, unlocked);
        if (state != null && progress != null) {
            int cacheFingerprint = computeCacheFingerprint(
                offersHash,
                player.getUuid(),
                unlockState,
                refreshForThisMerchant,
                seedForLog
            );
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
            durationMs
        );

        LOGGER.info("[MoonTrade] action=REBUILD_DONE side=S player={} merchant={} source={} cache={} unlock={} offersTotal={} base={} sigil={} hidden={} offersHash={} seed={} refreshSeenCount={} durationMs={} pageSize={} debugAdded={}",
            playerTag(player), merchantTag(), audit.source(), audit.cache(), audit.unlock(),
            audit.offersTotal(), audit.baseCount(), audit.sigilCount(), audit.hiddenCount(),
            Integer.toHexString(audit.offersHash()), audit.seed(), audit.refreshSeenCount(), audit.durationMs(), TRADE_PAGE_SIZE, debugRefreshAdded);
        return audit;
    }

    /**
     * Rebuild secret page offers for player (public for TradeActionHandler)
     * 3.1 FIX: Respects secretSold flag - does not include katana if already sold
     */
    public void rebuildSecretOffersForPlayer(ServerPlayerEntity player) {
        TradeOfferList offers = this.getOffers();
        offers.clear();
        
        if (!(this.getEntityWorld() instanceof ServerWorld serverWorld)) {
            return;
        }
        MerchantUnlockState state = MerchantUnlockState.getServerState(serverWorld);
        MerchantUnlockState.Progress progress = state.getOrCreateProgress(player.getUuid());
        boolean unlocked = progress.isUnlockedKatanaHidden();
        
        // Add secret page specific offers
        // 3.1 FIX: Only add katana offer if not already sold
        if (!this.secretSold) {
            addKatanaHiddenOffers(offers);
        } else {
            // Add a "SOLD" placeholder or alternative offers
            LOGGER.info("[MoonTrade] SECRET_ALREADY_SOLD merchant={} player={}", 
                this.getUuid().toString().substring(0, 8), player.getName().getString());
        }

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
        String skip = this.secretSold ? "sold" : (offers.isEmpty() ? "empty_or_resolve_failed" : "none");
        LOGGER.info("[MoonTrade] HIDDEN_BUILD player={} merchant={} secretSold={} katanaId={} resolved={} offersCount={} skip={}",
            player.getUuid(), this.getUuid(), this.secretSold ? 1 : 0,
            this.secretKatanaId, resolvedItem, offers.size(), skip);
    }

    private TradeOffer createSealedLedgerOffer() {
        return new TradeOffer(
                new TradedItem(ModItems.MYSTERIOUS_COIN, 1),
                new ItemStack(ModItems.SEALED_LEDGER, 1),
                1,
                25,
                0.0f
        );
    }

    private TradeOffer createUnsealOffer() {
        return new TradeOffer(
                new TradedItem(ModItems.SEALED_LEDGER, 1),
                Optional.of(new TradedItem(ModItems.SIGIL, 1)),
                new ItemStack(ModItems.ARCANE_LEDGER, 1),
                1,
                50,
                0.0f
        );
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

    private void addKatanaHiddenOffers(TradeOfferList offers) {
        // P0-3: 确保 ID 已初始化
        initSecretKatanaIdIfNeeded();

        // secretKatanaId changed => invalidate cached offers to avoid stale output.
        if (katanaHiddenOffers != null && !this.secretKatanaId.equals(katanaHiddenOffersCacheId)) {
            katanaHiddenOffers = null;
            katanaHiddenOffersCacheId = "";
        }

        // P0-3: 仅在 ID 有效时生成并缓存；无效 ID 不缓存（允许后续重试）
        if (katanaHiddenOffers == null) {
            if (this.secretKatanaId == null || this.secretKatanaId.isEmpty()) {
                LOGGER.warn("[MoonTrade] KATANA_CACHE_SKIP player={} merchant={} secretKatanaId={} reason=id_still_empty",
                    getCurrentPlayerForLog(), this.getUuid(), this.secretKatanaId);
                return;
            }
            katanaHiddenOffers = createKatanaHiddenOffers();
            // 如果 resolve 失败返回空列表，不缓存（允许下次重试）
            if (katanaHiddenOffers.isEmpty()) {
                LOGGER.warn("[MoonTrade] KATANA_CACHE_SKIP player={} merchant={} secretKatanaId={} reason=resolve_failed",
                    getCurrentPlayerForLog(), this.getUuid(), this.secretKatanaId);
                katanaHiddenOffers = null;
                return;
            }
            katanaHiddenOffersCacheId = this.secretKatanaId;
        }

        for (TradeOffer offer : katanaHiddenOffers) {
            offers.add(offer);
        }
    }

    private TradeOfferList createKatanaHiddenOffers() {
        TradeOfferList list = new TradeOfferList();
        ItemStack katanaStack = resolveKatanaStack();
        if (katanaStack.isEmpty()) {
            return list;
        }
        markSecretTradeOutput(katanaStack);

        list.add(new TradeOffer(
                new TradedItem(ModItems.ARCANE_LEDGER, 1),
                Optional.of(new TradedItem(Items.EMERALD, 32)),
                katanaStack,
                1,
                80,
                0.0f
        ));

        return list;
    }

    /**
     * P0-C FIX: Whitelist of valid katana IDs -> Item mappings.
     * Only IDs in this map can be resolved to actual items.
     * Invariant: id must be live - unknown/invalid IDs produce EMPTY.
     */
    private static final Map<String, net.minecraft.item.Item> KATANA_WHITELIST = Map.of(
        "moon_glow_katana", KatanaItems.MOON_GLOW_KATANA,
        "regret_blade", KatanaItems.REGRET_BLADE,
        "eclipse_blade", KatanaItems.ECLIPSE_BLADE,
        "oblivion_edge", KatanaItems.OBLIVION_EDGE,
        "nmap_katana", KatanaItems.NMAP_KATANA
    );

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

        // P0-C: Strip "katana_" prefix + UUID suffix to get the base katana type
        // secretKatanaId format: "katana_<uuid8>" - we need to map this to actual katana
        // Since the ID doesn't encode which katana type, derive from merchant UUID deterministically
        String katanaType = resolveKatanaType();
        if (katanaType == null) {
            LOGGER.warn("[MoonTrade] KATANA_RESOLVE_FAIL player={} merchant={} secretKatanaId={} reason=unknown_or_legacy_id",
                getCurrentPlayerForLog(), this.getUuid(), this.secretKatanaId);
            return ItemStack.EMPTY;
        }

        net.minecraft.item.Item item = KATANA_WHITELIST.get(katanaType);
        if (item == null) {
            LOGGER.warn("[MoonTrade] KATANA_RESOLVE_FAIL player={} merchant={} secretKatanaId={} katanaType={} reason=not_in_whitelist",
                getCurrentPlayerForLog(), this.getUuid(), this.secretKatanaId, katanaType);
            return ItemStack.EMPTY;
        }

        return new ItemStack(item, 1);
    }

    private void markSecretTradeOutput(ItemStack stack) {
        NbtComponent component = stack.get(DataComponentTypes.CUSTOM_DATA);
        NbtCompound nbt = component == null ? new NbtCompound() : component.copyNbt();
        nbt.putBoolean(NBT_SECRET_MARKER, true);
        nbt.putString(NBT_SECRET_MARKER_ID, this.secretKatanaId);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
    }

    /**
     * P0-C: Derive which katana type this merchant offers, based on merchant UUID.
     * Deterministic: same merchant always offers the same katana type.
     */
    private String resolveKatanaType() {
        if (this.secretKatanaId == null || this.secretKatanaId.isEmpty()) {
            return null;
        }
        // New format: katana:<type>:<merchant8>
        if (this.secretKatanaId.startsWith("katana:")) {
            String[] parts = this.secretKatanaId.split(":");
            if (parts.length >= 2) {
                String type = parts[1];
                if (KATANA_WHITELIST.containsKey(type)) {
                    return type;
                }
            }
            return null;
        }
        // Compatible format: katana_<type>
        if (this.secretKatanaId.startsWith("katana_")) {
            // Legacy format "katana_<uuid8>" must never be parsed as type name.
            if (isLegacyKatanaId(this.secretKatanaId)) {
                return pickKatanaTypeForMerchant();
            }
            String maybeType = this.secretKatanaId.substring("katana_".length());
            if (KATANA_WHITELIST.containsKey(maybeType)) {
                return maybeType;
            }
        }
        return null;
    }

    private ArrayList<SigilOfferEntry> createSigilOfferPool() {
        ArrayList<SigilOfferEntry> pool = new ArrayList<>();

        pool.add(new SigilOfferEntry("A1", SigilTier.A, new TradeOffer(
                new TradedItem(Items.EMERALD, 64),
                new ItemStack(ModItems.SIGIL, 1),
                1, 40, 0.0f
        )));
        pool.add(new SigilOfferEntry("A2", SigilTier.A, new TradeOffer(
                new TradedItem(Items.DIAMOND, 8),
                new ItemStack(ModItems.SIGIL, 1),
                1, 40, 0.0f
        )));
        pool.add(new SigilOfferEntry("A3", SigilTier.A, new TradeOffer(
                new TradedItem(Items.NETHERITE_INGOT, 1),
                new ItemStack(ModItems.SIGIL, 1),
                1, 60, 0.0f
        )));

        pool.add(new SigilOfferEntry("B1", SigilTier.B, new TradeOffer(
                new TradedItem(Items.BLAZE_ROD, 2),
                new ItemStack(ModItems.SIGIL, 1),
                2, 30, 0.0f
        )));
        pool.add(new SigilOfferEntry("B2", SigilTier.B, new TradeOffer(
                new TradedItem(Items.GOLD_INGOT, 12),
                new ItemStack(ModItems.SIGIL, 1),
                2, 25, 0.0f
        )));
        pool.add(new SigilOfferEntry("B3", SigilTier.B, new TradeOffer(
                new TradedItem(Items.ENDER_PEARL, 6),
                new ItemStack(ModItems.SIGIL, 1),
                2, 25, 0.0f
        )));
        pool.add(new SigilOfferEntry("B4", SigilTier.B, new TradeOffer(
                new TradedItem(Items.GHAST_TEAR, 2),
                new ItemStack(ModItems.SIGIL, 1),
                2, 30, 0.0f
        )));

        pool.add(new SigilOfferEntry("C1", SigilTier.C, new TradeOffer(
                new TradedItem(Items.EMERALD, 24),
                new ItemStack(ModItems.SIGIL, 1),
                3, 15, 0.0f
        )));
        pool.add(new SigilOfferEntry("C2", SigilTier.C, new TradeOffer(
                new TradedItem(Items.IRON_INGOT, 16),
                new ItemStack(ModItems.SIGIL, 1),
                3, 15, 0.0f
        )));
        pool.add(new SigilOfferEntry("C3", SigilTier.C, new TradeOffer(
                new TradedItem(Items.REDSTONE, 24),
                new ItemStack(ModItems.SIGIL, 1),
                3, 15, 0.0f
        )));

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

    private SigilOfferEntry extractRandomFrom(ArrayList<SigilOfferEntry> first, ArrayList<SigilOfferEntry> second, Random rng) {
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
     * P0-A FIX: Derive sigil seed deterministically from (merchant, player, refreshSeenCount).
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
            || (first.isOf(Items.EMERALD) && first.getCount() == 10 && sell.isOf(Items.GOLDEN_APPLE) && sell.getCount() == 1)
            || (first.isOf(Items.EMERALD) && first.getCount() == 32 && sell.isOf(ModItems.MYSTERIOUS_COIN) && sell.getCount() == 1);
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
    public static final String NBT_SECRET_MARKER = "MoonTradeSecret";
    public static final String NBT_SECRET_MARKER_ID = "MoonTradeSecretId";
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

        // P0-A FIX: sigil seed no longer stored on entity (deprecated, write zeros for compat)
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
            this.spawnTick = -1;  // 兼容旧数据：无此 key 视为未初始化
        }
        this.isInWarningPhase = nbt.getBoolean(NBT_IN_WARNING_PHASE);

        // Phase 6: 读取商人名称
        this.merchantName = nbt.getString(NBT_MERCHANT_NAME);
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
        this.secretKatanaId = secretKatanaId;
    }

    /**
     * 初始化隐藏物品ID（仅在首次调用时生成）
     */
    public void initSecretKatanaIdIfNeeded() {
        if (this.secretKatanaId == null || this.secretKatanaId.isEmpty()) {
            String type = pickKatanaTypeForMerchant();
            this.secretKatanaId = "katana:" + type + ":" + this.getUuid().toString().substring(0, 8);
            logSecretPick(type);
            return;
        }
        // Migrate legacy id "katana_<uuid8>" -> "katana:<type>:<uuid8>"
        if (isLegacyKatanaId(this.secretKatanaId)) {
            String type = pickKatanaTypeForMerchant();
            String suffix = this.secretKatanaId.substring("katana_".length());
            this.secretKatanaId = "katana:" + type + ":" + suffix;
            logSecretPick(type);
            LOGGER.warn("[MoonTrade] KATANA_ID_MIGRATE merchant={} legacyId={} newId={}",
                this.getUuid(), "katana_" + suffix, this.secretKatanaId);
        }
    }

    /**
     * 尝试标记隐藏物品已售出（原子操作）
     * @return true 如果成功标记（之前未售出）
     */
    public synchronized boolean tryMarkSecretSold(String soldSecretId) {
        if (this.secretSold) {
            return false;
        }
        this.secretSold = true;
        if ((this.secretKatanaId == null || this.secretKatanaId.isEmpty())
            && soldSecretId != null && !soldSecretId.isEmpty()) {
            this.secretKatanaId = soldSecretId;
        }
        LOGGER.info("[MoonTrade] SECRET_SOLD merchant={} katanaId={} soldSecretId={}",
            this.getUuid().toString().substring(0, 8), this.secretKatanaId, soldSecretId);
        return true;
    }

    public synchronized boolean tryMarkSecretSold() {
        return tryMarkSecretSold(this.secretKatanaId);
    }

    /**
     * P0-A FIX: refreshSigilOffers is now a no-op on entity level.
     * Seed is derived from (merchantUuid, playerUuid, refreshSeenCount).
     * The caller (TradeActionHandler) increments refreshSeenCount which changes the derived seed.
     * Kept as public method to avoid breaking TradeActionHandler call site.
     */
    public void refreshSigilOffers() {
        // P0-A: No entity-level state to update; seed derived per-(merchant,player,refreshSeenCount)
        LOGGER.debug("[MoonTrade] SIGIL_REFRESH player={} merchant={} note=seed_now_derived_per_player",
            getCurrentPlayerForLog(), this.getUuid());
    }

    private String pickKatanaTypeForMerchant() {
        String[] katanaTypes = KATANA_WHITELIST.keySet().toArray(new String[0]);
        Arrays.sort(katanaTypes);
        long seed = this.getUuid().getMostSignificantBits() ^ Long.rotateLeft(this.getUuid().getLeastSignificantBits(), 17);
        int index = Math.floorMod((int) (seed ^ (seed >>> 32)), katanaTypes.length);
        return katanaTypes[index];
    }

    private void logSecretPick(String chosenId) {
        String[] candidates = KATANA_WHITELIST.keySet().toArray(new String[0]);
        Arrays.sort(candidates);
        long seed = this.getUuid().getMostSignificantBits() ^ Long.rotateLeft(this.getUuid().getLeastSignificantBits(), 17);
        Identifier typeId = Registries.ENTITY_TYPE.getId(this.getType());
        LOGGER.info(
            "[MoonTrade] action=SECRET_PICK merchantUuid={} entityTypeId={} playerUuid={} seed={} candidatesSize={} candidates={} chosenId={}",
            this.getUuid(),
            typeId,
            getCurrentPlayerForLog(),
            seed,
            candidates.length,
            String.join("|", candidates),
            chosenId
        );
    }

    public static boolean isSecretTradeOutput(ItemStack stack) {
        NbtComponent component = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (component == null) {
            return false;
        }
        return component.copyNbt().getBoolean(NBT_SECRET_MARKER);
    }

    public static String getSecretTradeMarkerId(ItemStack stack) {
        NbtComponent component = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (component == null) {
            return "";
        }
        NbtCompound nbt = component.copyNbt();
        if (!nbt.getBoolean(NBT_SECRET_MARKER)) {
            return "";
        }
        return nbt.getString(NBT_SECRET_MARKER_ID);
    }

    public static void clearSecretTradeMarker(ItemStack stack) {
        NbtComponent component = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (component == null) {
            return;
        }
        NbtCompound nbt = component.copyNbt();
        if (!nbt.contains(NBT_SECRET_MARKER) && !nbt.contains(NBT_SECRET_MARKER_ID)) {
            return;
        }
        nbt.remove(NBT_SECRET_MARKER);
        nbt.remove(NBT_SECRET_MARKER_ID);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
    }

    private static boolean isLegacyKatanaId(String id) {
        if (id == null || !id.startsWith("katana_")) {
            return false;
        }
        String suffix = id.substring("katana_".length());
        return suffix.length() == 8 && suffix.chars().allMatch(ch ->
            (ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'f') || (ch >= 'A' && ch <= 'F'));
    }

    private String getCurrentPlayerForLog() {
        PlayerEntity customer = this.getCustomer();
        return customer == null ? "none" : customer.getUuid().toString();
    }
}
