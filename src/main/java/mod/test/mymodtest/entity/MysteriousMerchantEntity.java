package mod.test.mymodtest.entity;

import mod.test.mymodtest.entity.ai.DrinkPotionGoal;
import mod.test.mymodtest.entity.ai.EnhancedFleeGoal;
import mod.test.mymodtest.entity.ai.SeekLightGoal;
import mod.test.mymodtest.entity.data.PlayerTradeData;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.WanderingTraderEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;
import net.minecraft.village.TradedItem;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MysteriousMerchantEntity extends WanderingTraderEntity {

    // ========== Phase 3: AI 调试开关 ==========
    /** 设置为 true 以启用 AI 行为调试日志 */
    public static final boolean DEBUG_AI = true;

    // ========== Phase 3: AI 行为常量 ==========
    /** 基础移动速度 */
    public static final double BASE_MOVEMENT_SPEED = 0.5;

    // Phase 2.3: 玩家交易数据
    private final Map<UUID, PlayerTradeData> playerDataMap = new HashMap<>();
    private boolean hasEverTraded = false;

    public MysteriousMerchantEntity(EntityType<? extends WanderingTraderEntity> type, World world) {
        super(type, world);
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
            // TODO Phase 2+: 添加隐藏交易到列表
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
    }

    public static DefaultAttributeContainer.Builder createMerchantAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.MAX_HEALTH, 20.0)
                .add(EntityAttributes.MOVEMENT_SPEED, 0.5);
    }

    // Phase 2.5: NBT 持久化
    private static final String NBT_HAS_EVER_TRADED = "HasEverTraded";
    private static final String NBT_PLAYER_UUIDS = "PlayerUUIDs";
    private static final String NBT_PLAYER_PREFIX = "Player_";

    @Override
    protected void writeCustomData(WriteView nbt) {
        super.writeCustomData(nbt);

        // 保存 hasEverTraded
        nbt.putBoolean(NBT_HAS_EVER_TRADED, this.hasEverTraded);

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

        System.out.println("[MysteriousMerchant] NBT 已保存, 玩家数据条目: " + playerDataMap.size());
    }

    @Override
    protected void readCustomData(ReadView nbt) {
        super.readCustomData(nbt);

        // 读取 hasEverTraded
        this.hasEverTraded = nbt.getBoolean(NBT_HAS_EVER_TRADED, false);

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

        System.out.println("[MysteriousMerchant] NBT 已加载, hasEverTraded: " + hasEverTraded +
                ", 玩家数据条目: " + playerDataMap.size());
    }
}
