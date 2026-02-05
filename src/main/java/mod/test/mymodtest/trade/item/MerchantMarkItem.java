package mod.test.mymodtest.trade.item;

import mod.test.mymodtest.trade.TradeConfig;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.UUID;

/**
 * 商人印记物品
 * 绑定到玩家UUID，作为会员标识
 */
public class MerchantMarkItem extends Item {

    public MerchantMarkItem(Settings settings) {
        super(settings);
    }

    /**
     * 获取绑定的玩家UUID
     */
    public static UUID getOwnerUUID(ItemStack stack) {
        NbtComponent component = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (component == null) return null;
        NbtCompound nbt = component.copyNbt();
        if (!nbt.containsUuid(TradeConfig.NBT_MARK_OWNER_UUID)) return null;
        return nbt.getUuid(TradeConfig.NBT_MARK_OWNER_UUID);
    }

    /**
     * 绑定到玩家
     */
    public static void bindToPlayer(ItemStack stack, PlayerEntity player) {
        NbtCompound nbt = getOrCreateNbt(stack);
        nbt.putUuid(TradeConfig.NBT_MARK_OWNER_UUID, player.getUuid());
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
    }

    /**
     * 检查是否已绑定
     */
    public static boolean isBound(ItemStack stack) {
        return getOwnerUUID(stack) != null;
    }

    /**
     * 检查是否绑定到指定玩家
     */
    public static boolean isBoundTo(ItemStack stack, PlayerEntity player) {
        UUID owner = getOwnerUUID(stack);
        return owner != null && owner.equals(player.getUuid());
    }

    /**
     * 检查玩家是否持有有效的印记（绑定到自己的）
     */
    public static boolean playerHasValidMark(PlayerEntity player) {
        // 检查主手
        ItemStack mainHand = player.getMainHandStack();
        if (mainHand.getItem() instanceof MerchantMarkItem && isBoundTo(mainHand, player)) {
            return true;
        }
        // 检查副手
        ItemStack offHand = player.getOffHandStack();
        if (offHand.getItem() instanceof MerchantMarkItem && isBoundTo(offHand, player)) {
            return true;
        }
        // 检查背包
        for (ItemStack stack : player.getInventory().main) {
            if (stack.getItem() instanceof MerchantMarkItem && isBoundTo(stack, player)) {
                return true;
            }
        }
        return false;
    }

    private static NbtCompound getOrCreateNbt(ItemStack stack) {
        NbtComponent component = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (component != null) {
            return component.copyNbt();
        }
        return new NbtCompound();
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        super.appendTooltip(stack, context, tooltip, type);
        
        UUID owner = getOwnerUUID(stack);
        if (owner != null) {
            tooltip.add(Text.literal("已绑定").formatted(Formatting.GREEN));
            tooltip.add(Text.literal("UUID: " + owner.toString().substring(0, 8) + "...").formatted(Formatting.DARK_GRAY));
        } else {
            tooltip.add(Text.literal("未绑定 - 右键商人绑定").formatted(Formatting.YELLOW));
        }
    }
}
