package mod.test.mymodtest.entity;

import mod.test.mymodtest.entity.ai.DrinkPotionGoal;
import mod.test.mymodtest.entity.ai.EnhancedFleeGoal;
import mod.test.mymodtest.entity.ai.SeekLightGoal;
import mod.test.mymodtest.registry.ModItems;
import mod.test.mymodtest.world.MerchantSpawnerState;
import mod.test.mymodtest.world.MerchantUnlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.WanderingTraderEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
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
import java.util.Collections;
import java.util.Random;
import java.util.Optional;

public class MysteriousMerchantEntity extends WanderingTraderEntity {
    private static final Logger LOGGER = LoggerFactory.getLogger(MysteriousMerchantEntity.class);

    // ========== 调试开关 ==========
    /** 发布版默认关闭；开启后启用 AI 行为调试日志 */
    public static final boolean DEBUG_AI = false;
    /** 发布版默认关闭；开启后使用更短的 despawn 时间（用于测试） */
    public static final boolean DEBUG_DESPAWN = false;

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

    // Phase 8: 解封系统交易
    private TradeOfferList katanaHiddenOffers = null;
    private String merchantName = "";

    private static final int ELIGIBLE_TRADE_COUNT = 15;
    private static final int REFRESH_GUARANTEE_COUNT = 3;
    private static final Identifier KATANA_ITEM_ID = Identifier.of(ModItems.MOD_ID, "katana");

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
        try {
            MerchantSpawnerState state = MerchantSpawnerState.getServerState(serverWorld);
            boolean cleared = state.clearActiveMerchantIfMatch(this.getUuid());
            if (DEBUG_DESPAWN) {
                LOGGER.debug("[Merchant] NOTIFY_STATE_CLEAR reason={} uuid={}... cleared={}",
                    reason, this.getUuid().toString().substring(0, 8), cleared);
            }
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
     * 覆写交互：检测神秘硬币
     */
    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        ItemStack heldItem = player.getStackInHand(hand);

        // 检查是否手持神秘硬币
        if (heldItem.getItem() == ModItems.MYSTERIOUS_COIN) {
            return handleMysteriousCoinInteraction(player, heldItem, hand);
        }

        if (!this.getEntityWorld().isClient() && player instanceof ServerPlayerEntity serverPlayer) {
            rebuildOffersForPlayer(serverPlayer);
        }

        // 默认交互（打开交易界面）
        return super.interactMob(player, hand);
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
        addBaseOffers(offers);
    }

    private void addBaseOffers(TradeOfferList offers) {
        // 5 绿宝石 → 1 钻石
        offers.add(new TradeOffer(
                new TradedItem(Items.EMERALD, 5),
                new ItemStack(Items.DIAMOND, 1),
                12,    // maxUses
                10,    // merchantExperience
                0.05f  // priceMultiplier
        ));

        // 10 绿宝石 → 1 金苹果
        offers.add(new TradeOffer(
                new TradedItem(Items.EMERALD, 10),
                new ItemStack(Items.GOLDEN_APPLE, 1),
                12,
                10,
                0.05f
        ));

        // 1 钻石 → 16 绿宝石
        offers.add(new TradeOffer(
                new TradedItem(Items.DIAMOND, 1),
                new ItemStack(Items.EMERALD, 16),
                12,
                10,
                0.05f
        ));

        // 添加神秘硬币交易（用绿宝石购买）
        offers.add(new TradeOffer(
                new TradedItem(Items.EMERALD, 32),
                new ItemStack(ModItems.MYSTERIOUS_COIN, 1),
                3,     // 限量供应
                15,
                0.1f
        ));
    }

    private void rebuildOffersForPlayer(ServerPlayerEntity player) {
        TradeOfferList offers = this.getOffers();
        offers.clear();
        addBaseOffers(offers);

        if (!(this.getEntityWorld() instanceof ServerWorld serverWorld)) {
            return;
        }

        MerchantUnlockState state = MerchantUnlockState.getServerState(serverWorld);
        MerchantUnlockState.Progress progress = state.getOrCreateProgress(player.getUuid());
        boolean eligible = progress.getTradeCount() >= ELIGIBLE_TRADE_COUNT;

        if (eligible) {
            offers.add(createSealedLedgerOffer());
        }

        if (eligible && !progress.isUnlockedKatanaHidden()) {
            progress.setRefreshSeenCount(progress.getRefreshSeenCount() + 1);
            state.markDirty();
            offers.add(createUnsealOffer());
            addSigilOffers(offers, progress);
        }

        if (progress.isUnlockedKatanaHidden()) {
            addKatanaHiddenOffers(offers);
        }
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

    private void addSigilOffers(TradeOfferList offers, MerchantUnlockState.Progress progress) {
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

        int targetCount = 3 + this.random.nextInt(3);
        boolean guaranteeB1 = progress.getRefreshSeenCount() >= REFRESH_GUARANTEE_COUNT;
        int maxA = guaranteeB1 ? 0 : 1;

        ArrayList<SigilOfferEntry> chosen = new ArrayList<>();

        if (guaranteeB1) {
            SigilOfferEntry b1 = extractById(bList, "B1");
            if (b1 != null) {
                chosen.add(b1);
            }
        } else {
            SigilOfferEntry firstBc = extractRandomFrom(bList, cList);
            if (firstBc != null) {
                chosen.add(firstBc);
            }
        }

        if (maxA > 0 && !aList.isEmpty() && this.random.nextBoolean() && chosen.size() < targetCount) {
            chosen.add(pickRandomEntry(aList));
        }

        ArrayList<SigilOfferEntry> remaining = new ArrayList<>();
        remaining.addAll(bList);
        remaining.addAll(cList);
        if (maxA > 0) {
            remaining.addAll(aList);
        }
        Collections.shuffle(remaining, new Random(this.random.nextLong()));

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
        if (katanaHiddenOffers == null) {
            katanaHiddenOffers = createKatanaHiddenOffers();
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

    private ItemStack resolveKatanaStack() {
        if (Registries.ITEM.containsId(KATANA_ITEM_ID)) {
            return new ItemStack(Registries.ITEM.get(KATANA_ITEM_ID), 1);
        }
        LOGGER.warn("[MysteriousMerchant] katana 物品未注册，使用下界合金剑作为占位");
        return new ItemStack(Items.NETHERITE_SWORD, 1);
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

    private SigilOfferEntry extractRandomFrom(ArrayList<SigilOfferEntry> first, ArrayList<SigilOfferEntry> second) {
        ArrayList<SigilOfferEntry> combined = new ArrayList<>();
        combined.addAll(first);
        combined.addAll(second);
        if (combined.isEmpty()) {
            return null;
        }
        SigilOfferEntry pick = combined.get(this.random.nextInt(combined.size()));
        first.remove(pick);
        second.remove(pick);
        return pick;
    }

    private SigilOfferEntry pickRandomEntry(ArrayList<SigilOfferEntry> list) {
        SigilOfferEntry entry = list.remove(this.random.nextInt(list.size()));
        return entry;
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

        if (DEBUG_DESPAWN) {
            LOGGER.debug("[Merchant] NBT_SAVE spawnTick={} isInWarningPhase={} hasEverTraded={}",
                spawnTick, isInWarningPhase, hasEverTraded);
        }
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);

        // 读取 hasEverTraded
        this.hasEverTraded = nbt.getBoolean(NBT_HAS_EVER_TRADED);

        // Phase 4: 读取 spawnTick
        this.spawnTick = nbt.getLong(NBT_SPAWN_TICK);
        if (this.spawnTick == 0) this.spawnTick = -1;  // 兼容旧数据
        this.isInWarningPhase = nbt.getBoolean(NBT_IN_WARNING_PHASE);

        // Phase 6: 读取商人名称
        this.merchantName = nbt.getString(NBT_MERCHANT_NAME);
        if (!this.merchantName.isEmpty()) {
            this.setCustomName(Text.literal(this.merchantName).formatted(Formatting.GOLD));
            this.setCustomNameVisible(true);
        }

        if (DEBUG_DESPAWN) {
            LOGGER.debug("[Merchant] NBT_LOAD spawnTick={} isInWarningPhase={} hasEverTraded={}",
                spawnTick, isInWarningPhase, hasEverTraded);
        }
    }
}
