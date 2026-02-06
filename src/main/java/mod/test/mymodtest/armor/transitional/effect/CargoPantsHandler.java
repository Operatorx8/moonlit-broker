package mod.test.mymodtest.armor.transitional.effect;

import mod.test.mymodtest.armor.transitional.TransitionalArmorConstants;
import mod.test.mymodtest.armor.transitional.TransitionalArmorItems;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 多袋工装裤 - Torch 返还逻辑
 *
 * 机制：
 * - 穿戴于 LEGS 槽位时，放置 minecraft:torch 成功后
 * - 有 15% 概率返还 1 个火把（抵消系统扣除）
 * - 每玩家 CD 200 ticks（10s）
 * - 创造模式不触发
 * - SERVER_ONLY, LEGS_ONLY
 */
public final class CargoPantsHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger("MoonTrace");

    /**
     * 每玩家的 CD 截止时间
     * key: 玩家 UUID, value: CD 结束的 server tick
     */
    private static final Map<UUID, Long> CARGO_CD_MAP = new ConcurrentHashMap<>();

    private CargoPantsHandler() {}

    /**
     * 处理火把放置成功事件
     * 由 BlockItemPlaceMixin 在放置成功后调用
     *
     * @param player    放置火把的玩家
     * @param handStack 玩家手持的物品栈（放置后 count 已被系统 -1）
     */
    public static void onTorchPlaced(PlayerEntity player, ItemStack handStack) {
        // 仅服务端
        if (player.getWorld().isClient()) {
            return;
        }

        // 创造模式不触发（创造模式下 decrement 可能未执行）
        if (player.isCreative()) {
            return;
        }

        // 检查 LEGS 槽位是否穿戴多袋工装裤
        if (!isWearing(player)) {
            return;
        }

        // 统一使用服务器 tick，避免跨维度 world time 差异影响 CD
        long now = player instanceof ServerPlayerEntity serverPlayer
                ? serverPlayer.getServer().getTicks()
                : player.getWorld().getTime();
        Long cdReady = CARGO_CD_MAP.get(player.getUuid());
        if (cdReady != null && now < cdReady) {
            // CD 中，不触发
            return;
        }

        // 概率判定
        float roll = player.getRandom().nextFloat();
        if (roll >= TransitionalArmorConstants.CARGO_TORCH_SAVE_CHANCE) {
            return;
        }

        // 返还火把
        int rawCount = handStack.getCount();

        // 边界情况：如果 stack count 为 0（放完最后一个），直接给玩家添加一个新的
        if (handStack.isEmpty() || rawCount == 0) {
            ItemStack refund = new ItemStack(Items.TORCH, 1);
            if (!player.getInventory().insertStack(refund)) {
                player.dropItem(refund, false);
            }
        } else {
            // 正常情况：increment(1) 等于"还回去"
            handStack.increment(1);
        }

        // 设置 CD
        CARGO_CD_MAP.put(player.getUuid(), now + TransitionalArmorConstants.CARGO_TORCH_CD_TICKS);

        if (TransitionalArmorConstants.DEBUG) {
            LOGGER.info("[TransArmor] item=cargo_pants player={} itemId=minecraft:torch rawCount={} outCount={} roll={} cdRemaining={}",
                    player.getUuidAsString().substring(0, 8),
                    rawCount,
                    handStack.isEmpty() ? 1 : handStack.getCount(),
                    String.format("%.3f", roll),
                    TransitionalArmorConstants.CARGO_TORCH_CD_TICKS);
        }
    }

    /**
     * 检查是否穿戴多袋工装裤
     */
    private static boolean isWearing(PlayerEntity player) {
        return player.getEquippedStack(EquipmentSlot.LEGS).isOf(TransitionalArmorItems.CARGO_PANTS);
    }

    /**
     * 清理玩家 CD 数据（玩家退出时调用）
     */
    public static void cleanupPlayer(UUID playerUuid) {
        CARGO_CD_MAP.remove(playerUuid);
    }

    /**
     * 清理所有过期的 CD 数据
     * 可以在服务器 tick 中定期调用
     */
    public static void cleanupExpired(long currentTime) {
        CARGO_CD_MAP.entrySet().removeIf(entry -> currentTime > entry.getValue() + 12000);
    }
}
