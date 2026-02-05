package mod.test.mymodtest.trade.loot;

import mod.test.mymodtest.registry.ModItems;
import mod.test.mymodtest.trade.TradeConfig;
import mod.test.mymodtest.trade.item.TradeScrollItem;
import mod.test.mymodtest.world.MerchantUnlockState;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

/**
 * 怪物掉落处理器
 * 处理交易卷轴和银币的怪物掉落
 */
public class MobDropHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(MobDropHandler.class);
    private static final Random RANDOM = new Random();

    /**
     * 注册怪物死亡事件
     */
    public static void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register(MobDropHandler::onMobDeath);
        LOGGER.info("[MoonTrade] 怪物掉落处理器已注册");
    }

    private static void onMobDeath(LivingEntity entity, DamageSource source) {
        // 只处理敌对生物
        if (!(entity instanceof HostileEntity)) {
            return;
        }

        // 只处理玩家击杀
        if (!(source.getAttacker() instanceof ServerPlayerEntity player)) {
            return;
        }

        ServerWorld world = (ServerWorld) entity.getWorld();

        // 检查掉落条件
        boolean shouldDrop = checkDropCondition(entity, world);
        
        if (shouldDrop) {
            // 尝试掉落交易卷轴
            tryDropScroll(entity, player, world);
            // 尝试掉落银币
            tryDropSilver(entity, player, world);
        }
    }

    /**
     * 检查掉落条件：
     * - (夜晚 + 主世界地表) OR (装备怪/精英怪)
     */
    private static boolean checkDropCondition(LivingEntity entity, ServerWorld world) {
        // 条件1：夜晚 + 主世界地表
        boolean isNight = world.isNight();
        boolean isOverworld = world.getRegistryKey() == World.OVERWORLD;
        boolean isSurface = entity.getY() > world.getSeaLevel() - 10 
            && world.isSkyVisible(entity.getBlockPos());
        
        if (isNight && isOverworld && isSurface) {
            return true;
        }

        // 条件2：装备怪（有装备的怪物）
        if (isEquippedMob(entity)) {
            return true;
        }

        return false;
    }

    /**
     * 检查是否为装备怪
     */
    private static boolean isEquippedMob(LivingEntity entity) {
        // 检查是否有武器或盔甲
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = entity.getEquippedStack(slot);
            if (!stack.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 尝试掉落交易卷轴
     */
    private static void tryDropScroll(LivingEntity entity, ServerPlayerEntity player, ServerWorld world) {
        if (RANDOM.nextFloat() >= TradeConfig.MOB_SCROLL_CHANCE) {
            return;
        }

        // 创建普通卷轴
        ItemStack scroll = new ItemStack(ModItems.TRADE_SCROLL, 1);
        TradeScrollItem.initialize(scroll, TradeConfig.GRADE_NORMAL);
        
        entity.dropStack(scroll);

        if (TradeConfig.TRADE_DEBUG) {
            LOGGER.debug("[MoonTrade] SCROLL_DROP player= mob={} pos={}", 
                player.getName().getString(),
                entity.getType().getUntranslatedName(),
                entity.getBlockPos());
        }
    }

    /**
     * 尝试掉落银币（带限流）
     */
    private static void tryDropSilver(LivingEntity entity, ServerPlayerEntity player, ServerWorld world) {
        // 银币掉率比卷轴高一些
        if (RANDOM.nextFloat() >= 0.05f) { // 5% 基础掉率
            return;
        }

        // 检查限流
        MerchantUnlockState state = MerchantUnlockState.getServerState(world);
        MerchantUnlockState.Progress progress = state.getOrCreateProgress(player.getUuid());
        
        long currentTime = world.getTime();
        if (!progress.tryRecordSilverDrop(currentTime, 
                TradeConfig.SILVER_CAP_WINDOW_TICKS, 
                TradeConfig.SILVER_MOB_DROP_CAP)) {
            if (TradeConfig.TRADE_DEBUG) {
                LOGGER.debug("[MoonTrade] SILVER_THROTTLED player={}", player.getName().getString());
            }
            return;
        }
        
        state.markDirty();

        // 掉落 1-3 个银币
        int amount = 1 + RANDOM.nextInt(3);
        ItemStack silver = new ItemStack(ModItems.SILVER_NOTE, amount);
        entity.dropStack(silver);

        if (TradeConfig.TRADE_DEBUG) {
            LOGGER.debug("[MoonTrade] SILVER_DROP player={} amount={} dropCount={}", 
                player.getName().getString(), amount, progress.getSilverDropCount());
        }
    }
}
