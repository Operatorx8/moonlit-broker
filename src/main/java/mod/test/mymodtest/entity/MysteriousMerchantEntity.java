package mod.test.mymodtest.entity;

import mod.test.mymodtest.entity.ai.DrinkPotionGoal;
import mod.test.mymodtest.entity.ai.EnhancedFleeGoal;
import mod.test.mymodtest.entity.ai.SeekLightGoal;
import mod.test.mymodtest.entity.data.PlayerTradeData;
import mod.test.mymodtest.registry.ModItems;
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
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;
import net.minecraft.village.TradedItem;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class MysteriousMerchantEntity extends WanderingTraderEntity {

    // ========== 调试开关 ==========
    /** 设置为 true 以启用 AI 行为调试日志 */
    public static final boolean DEBUG_AI = true;
    /** 设置为 true 以使用更短的 despawn 时间（用于测试） */
    public static final boolean DEBUG_DESPAWN = true;

    // ========== Phase 3: AI 行为常量 ==========
    /** 基础移动速度 */
    public static final double BASE_MOVEMENT_SPEED = 0.5;

    // ========== Phase 4: Despawn 常量 ==========
    /** 1 Minecraft 天 = 24000 ticks */
    private static final int TICKS_PER_DAY = 24000;
    /** 正常模式：7天后开始消失预警 */
    private static final int WARNING_TIME_NORMAL = 7 * TICKS_PER_DAY;  // 168000 ticks
    /** 正常模式：30天后强制消失 */
    private static final int DESPAWN_TIME_NORMAL = 30 * TICKS_PER_DAY; // 720000 ticks
    /** 调试模式：30秒后开始消失预警 */
    private static final int WARNING_TIME_DEBUG = 30 * 20;  // 600 ticks
    /** 调试模式：60秒后强制消失 */
    private static final int DESPAWN_TIME_DEBUG = 60 * 20;  // 1200 ticks
    /** 闪烁间隔（ticks）*/
    private static final int BLINK_INTERVAL = 10;

    // ========== Phase 5: 惩罚常量 ==========
    /** 攻击惩罚：失明持续时间（ticks）*/
    private static final int ATTACK_BLINDNESS_DURATION = 100;  // 5秒
    /** 攻击惩罚：反胃持续时间（ticks）*/
    private static final int ATTACK_NAUSEA_DURATION = 140;     // 7秒
    /** 击杀惩罚：效果持续时间倍率 */
    private static final int KILL_EFFECT_MULTIPLIER = 4;
    /** 击杀惩罚：额外的不幸效果持续时间（ticks）*/
    private static final int KILL_UNLUCK_DURATION = 24000;     // 20分钟

    // Phase 2.3: 玩家交易数据
    private final Map<UUID, PlayerTradeData> playerDataMap = new HashMap<>();
    private boolean hasEverTraded = false;

    // Phase 4: Despawn 数据
    private long spawnTick = -1;
    private boolean isInWarningPhase = false;

    // Phase 6: 隐藏交易和自定义名称
    private TradeOfferList secretOffers = null;
    private String merchantName = "";

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

        // 初始化 spawnTick（首次 tick 时记录）
        if (spawnTick < 0) {
            spawnTick = this.getEntityWorld().getTime();
            if (DEBUG_AI) {
                System.out.println("[MysteriousMerchant] 记录 spawnTick: " + spawnTick);
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
                if (DEBUG_AI) {
                    System.out.println("[MysteriousMerchant] 进入消失预警阶段，剩余时间: " +
                            (despawnTime - aliveTicks) / 20 + " 秒");
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
        if (DEBUG_AI) {
            System.out.println("[MysteriousMerchant] 执行消失！存活时间: " +
                    (this.getEntityWorld().getTime() - spawnTick) / 20 + " 秒");
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

    // ========== Phase 5: 攻击与击杀惩罚 ==========

    /**
     * 覆写伤害处理：当玩家攻击商人时施加惩罚
     */
    @Override
    public boolean damage(ServerWorld world, DamageSource source, float amount) {
        // 检查攻击者是否为玩家
        if (source.getAttacker() instanceof PlayerEntity player) {
            applyAttackPunishment(player);
        }
        return super.damage(world, source, amount);
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
            System.out.println("[MysteriousMerchant] 玩家 " + player.getName().getString() +
                    " 攻击了商人，施加惩罚效果");
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

        System.out.println("[MysteriousMerchant] 玩家 " + player.getName().getString() +
                " 击杀了商人，施加严重惩罚！");
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

        // 给予玩家特殊奖励
        PlayerTradeData playerData = getOrCreatePlayerData(player.getUuid());
        int bonusCount = 3;  // 相当于额外 3 次交易
        for (int i = 0; i < bonusCount; i++) {
            playerData.incrementTradeCount();
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

        // 检查是否解锁隐藏交易
        if (playerData.getTradeCount() >= 10 && !playerData.isSecretUnlocked()) {
            playerData.setSecretUnlocked(true);
            unlockSecretTrades(player);  // Phase 6: 添加隐藏交易
            if (player instanceof ServerPlayerEntity serverPlayer) {
                serverPlayer.sendMessage(
                        Text.literal("[神秘商人] 你的虔诚打动了我，隐藏交易已解锁！")
                                .formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD),
                        false
                );
            }
            System.out.println("[MysteriousMerchant] 玩家 " + player.getName().getString() +
                    " 通过神秘硬币解锁了隐藏交易！");
        }

        if (DEBUG_AI) {
            System.out.println("[MysteriousMerchant] 玩家 " + player.getName().getString() +
                    " 使用了神秘硬币，当前交易次数: " + playerData.getTradeCount());
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
            System.out.println("[MysteriousMerchant] AI Goals 已注册");
        }
    }

    // 获取或创建玩家交易数据
    public PlayerTradeData getOrCreatePlayerData(UUID playerUUID) {
        return playerDataMap.computeIfAbsent(playerUUID, PlayerTradeData::new);
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

        // 3. 更新玩家交易数据
        PlayerTradeData playerData = getOrCreatePlayerData(player.getUuid());
        playerData.incrementTradeCount();
        int count = playerData.getTradeCount();

        // 4. 给玩家正面效果 (100 ticks = 5秒)
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 100, 0));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 100, 0));

        // 5. 检查隐藏交易解锁
        if (count >= 10 && !playerData.isSecretUnlocked()) {
            playerData.setSecretUnlocked(true);
            unlockSecretTrades(player);  // Phase 6: 添加隐藏交易
            if (player instanceof ServerPlayerEntity serverPlayer) {
                serverPlayer.sendMessage(
                        Text.literal("[神秘商人] 你的忠诚令我感动，特殊商品已为你开放！")
                                .formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD),
                        false
                );
            }
            System.out.println("[MysteriousMerchant] 玩家 " + player.getName().getString() + " 解锁了隐藏交易!");
        }

        // 6. 调试日志
        System.out.println("[MysteriousMerchant] 玩家 " + player.getName().getString() +
                " 交易次数: " + count + ", hasEverTraded: " + hasEverTraded);
    }

    @Override
    protected void fillRecipes(ServerWorld world) {
        // Phase 2.1: 测试交易列表
        TradeOfferList offers = this.getOffers();

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

        // Phase 6: 添加神秘硬币交易（用绿宝石购买）
        offers.add(new TradeOffer(
                new TradedItem(Items.EMERALD, 32),
                new ItemStack(ModItems.MYSTERIOUS_COIN, 1),
                3,     // 限量供应
                15,
                0.1f
        ));
    }

    // ========== Phase 6: 隐藏交易系统 ==========

    /**
     * 创建隐藏交易列表
     */
    private TradeOfferList createSecretOffers() {
        TradeOfferList secrets = new TradeOfferList();

        // 隐藏交易 1: 神秘硬币 → 附魔金苹果
        secrets.add(new TradeOffer(
                new TradedItem(ModItems.MYSTERIOUS_COIN, 3),
                new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 1),
                1,     // 极其稀有
                50,
                0.0f   // 不涨价
        ));

        // 隐藏交易 2: 神秘硬币 + 绿宝石 → 不死图腾
        secrets.add(new TradeOffer(
                new TradedItem(ModItems.MYSTERIOUS_COIN, 5),
                Optional.of(new TradedItem(Items.EMERALD, 64)),
                new ItemStack(Items.TOTEM_OF_UNDYING, 1),
                1,
                100,
                0.0f
        ));

        // 隐藏交易 3: 神秘硬币 → 龙息
        secrets.add(new TradeOffer(
                new TradedItem(ModItems.MYSTERIOUS_COIN, 2),
                new ItemStack(Items.DRAGON_BREATH, 3),
                3,
                30,
                0.0f
        ));

        // 隐藏交易 4: 神秘硬币 + 钻石 → 下界之星
        secrets.add(new TradeOffer(
                new TradedItem(ModItems.MYSTERIOUS_COIN, 8),
                Optional.of(new TradedItem(Items.DIAMOND, 16)),
                new ItemStack(Items.NETHER_STAR, 1),
                1,
                200,
                0.0f
        ));

        // 隐藏交易 5: 神秘硬币 + 绿宝石 → 鞘翅
        secrets.add(new TradeOffer(
                new TradedItem(ModItems.MYSTERIOUS_COIN, 10),
                Optional.of(new TradedItem(Items.EMERALD, 64)),
                new ItemStack(Items.ELYTRA, 1),
                1,
                250,
                0.0f
        ));

        return secrets;
    }

    /**
     * 为玩家解锁隐藏交易
     */
    public void unlockSecretTrades(PlayerEntity player) {
        if (secretOffers == null) {
            secretOffers = createSecretOffers();
        }

        // 将隐藏交易添加到主交易列表
        TradeOfferList mainOffers = this.getOffers();
        for (TradeOffer offer : secretOffers) {
            // 检查是否已存在（避免重复添加）
            boolean exists = false;
            for (TradeOffer existing : mainOffers) {
                if (existing.getSellItem().getItem() == offer.getSellItem().getItem()) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                mainOffers.add(offer);
            }
        }

        if (DEBUG_AI) {
            System.out.println("[MysteriousMerchant] 隐藏交易已添加到列表，当前交易数: " + mainOffers.size());
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
                .add(EntityAttributes.MAX_HEALTH, 20.0)
                .add(EntityAttributes.MOVEMENT_SPEED, 0.5);
    }

    // Phase 2.5 & 4 & 6: NBT 持久化
    private static final String NBT_HAS_EVER_TRADED = "HasEverTraded";
    private static final String NBT_PLAYER_UUIDS = "PlayerUUIDs";
    private static final String NBT_PLAYER_PREFIX = "Player_";
    private static final String NBT_SPAWN_TICK = "SpawnTick";
    private static final String NBT_IN_WARNING_PHASE = "InWarningPhase";
    private static final String NBT_MERCHANT_NAME = "MerchantName";
    private static final String NBT_HAS_SECRET_OFFERS = "HasSecretOffers";

    @Override
    protected void writeCustomData(WriteView nbt) {
        super.writeCustomData(nbt);

        // 保存 hasEverTraded
        nbt.putBoolean(NBT_HAS_EVER_TRADED, this.hasEverTraded);

        // Phase 4: 保存 spawnTick
        nbt.putLong(NBT_SPAWN_TICK, this.spawnTick);
        nbt.putBoolean(NBT_IN_WARNING_PHASE, this.isInWarningPhase);

        // 保存玩家 UUID 列表（逗号分隔）
        StringBuilder uuidList = new StringBuilder();
        for (Map.Entry<UUID, PlayerTradeData> entry : playerDataMap.entrySet()) {
            String uuidStr = entry.getKey().toString();
            if (uuidList.length() > 0) uuidList.append(",");
            uuidList.append(uuidStr);

            // 保存每个玩家的数据
            WriteView playerNbt = nbt.get(NBT_PLAYER_PREFIX + uuidStr);
            playerNbt.putInt("TradeCount", entry.getValue().getTradeCount());
            playerNbt.putBoolean("SecretUnlocked", entry.getValue().isSecretUnlocked());
        }
        nbt.putString(NBT_PLAYER_UUIDS, uuidList.toString());

        // Phase 6: 保存商人名称和隐藏交易状态
        nbt.putString(NBT_MERCHANT_NAME, this.merchantName);
        nbt.putBoolean(NBT_HAS_SECRET_OFFERS, this.secretOffers != null);

        System.out.println("[MysteriousMerchant] NBT 已保存, spawnTick: " + spawnTick +
                ", merchantName: " + merchantName + ", 玩家数据条目: " + playerDataMap.size());
    }

    @Override
    protected void readCustomData(ReadView nbt) {
        super.readCustomData(nbt);

        // 读取 hasEverTraded
        this.hasEverTraded = nbt.getBoolean(NBT_HAS_EVER_TRADED, false);

        // Phase 4: 读取 spawnTick
        this.spawnTick = nbt.getLong(NBT_SPAWN_TICK, -1);
        this.isInWarningPhase = nbt.getBoolean(NBT_IN_WARNING_PHASE, false);

        // 读取玩家数据
        playerDataMap.clear();
        String uuidListStr = nbt.getString(NBT_PLAYER_UUIDS, "");
        if (!uuidListStr.isEmpty()) {
            for (String uuidStr : uuidListStr.split(",")) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    ReadView playerNbt = nbt.getReadView(NBT_PLAYER_PREFIX + uuidStr);
                    PlayerTradeData data = new PlayerTradeData(uuid);
                    data.setTradeCount(playerNbt.getInt("TradeCount", 0));
                    data.setSecretUnlocked(playerNbt.getBoolean("SecretUnlocked", false));
                    playerDataMap.put(uuid, data);
                } catch (IllegalArgumentException e) {
                    System.err.println("[MysteriousMerchant] 无效的 UUID: " + uuidStr);
                }
            }
        }

        // Phase 6: 读取商人名称
        this.merchantName = nbt.getString(NBT_MERCHANT_NAME, "");
        if (!this.merchantName.isEmpty()) {
            this.setCustomName(Text.literal(this.merchantName).formatted(Formatting.GOLD));
            this.setCustomNameVisible(true);
        }

        // Phase 6: 如果之前有隐藏交易，重新创建
        boolean hadSecretOffers = nbt.getBoolean(NBT_HAS_SECRET_OFFERS, false);
        if (hadSecretOffers) {
            this.secretOffers = createSecretOffers();
            // 注意：实际添加到交易列表会在玩家打开交易界面时根据玩家数据判断
        }

        System.out.println("[MysteriousMerchant] NBT 已加载, spawnTick: " + spawnTick +
                ", merchantName: " + merchantName + ", hasEverTraded: " + hasEverTraded +
                ", 玩家数据条目: " + playerDataMap.size());
    }
}
