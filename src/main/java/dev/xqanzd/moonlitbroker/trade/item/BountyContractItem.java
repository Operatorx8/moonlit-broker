package dev.xqanzd.moonlitbroker.trade.item;

import dev.xqanzd.moonlitbroker.registry.ModItems;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/**
 * 悬赏契约物品
 * NBT: BountyTarget, BountyRequired, BountyProgress, BountyCompleted
 */
public class BountyContractItem extends Item {

    private static final String NBT_TARGET = "BountyTarget";
    private static final String NBT_REQUIRED = "BountyRequired";
    private static final String NBT_PROGRESS = "BountyProgress";
    private static final String NBT_COMPLETED = "BountyCompleted";

    public BountyContractItem(Settings settings) {
        super(settings);
    }

    // ========== NBT Helpers ==========

    public static String getTarget(ItemStack stack) {
        NbtCompound nbt = getNbt(stack);
        return nbt != null && nbt.contains(NBT_TARGET) ? nbt.getString(NBT_TARGET) : "";
    }

    public static int getRequired(ItemStack stack) {
        NbtCompound nbt = getNbt(stack);
        return nbt != null && nbt.contains(NBT_REQUIRED) ? nbt.getInt(NBT_REQUIRED) : 0;
    }

    public static int getProgress(ItemStack stack) {
        NbtCompound nbt = getNbt(stack);
        return nbt != null && nbt.contains(NBT_PROGRESS) ? nbt.getInt(NBT_PROGRESS) : 0;
    }

    public static boolean isCompleted(ItemStack stack) {
        NbtCompound nbt = getNbt(stack);
        return nbt != null && nbt.contains(NBT_COMPLETED) && nbt.getBoolean(NBT_COMPLETED);
    }

    /**
     * 严格完成判定：NBT boolean + progress >= required + required > 0
     */
    public static boolean isCompletedStrict(ItemStack stack) {
        NbtCompound nbt = getNbt(stack);
        if (nbt == null) return false;
        boolean flag = nbt.contains(NBT_COMPLETED) && nbt.getBoolean(NBT_COMPLETED);
        int progress = nbt.contains(NBT_PROGRESS) ? nbt.getInt(NBT_PROGRESS) : 0;
        int required = nbt.contains(NBT_REQUIRED) ? nbt.getInt(NBT_REQUIRED) : 0;
        return flag && required > 0 && progress >= required;
    }

    /**
     * 增加进度，若达到 required 则标记 completed
     * 
     * @return true if newly completed
     */
    public static boolean incrementProgress(ItemStack stack) {
        NbtCompound nbt = getOrCreateNbt(stack);
        int progress = nbt.contains(NBT_PROGRESS) ? nbt.getInt(NBT_PROGRESS) : 0;
        int required = nbt.contains(NBT_REQUIRED) ? nbt.getInt(NBT_REQUIRED) : 0;
        if (progress >= required) {
            return false; // already done
        }
        progress++;
        nbt.putInt(NBT_PROGRESS, progress);
        boolean completed = progress >= required;
        if (completed) {
            nbt.putBoolean(NBT_COMPLETED, true);
        }
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
        return completed;
    }

    /**
     * 初始化一张新契约
     */
    public static void initialize(ItemStack stack, String targetEntityId, int required) {
        NbtCompound nbt = new NbtCompound();
        nbt.putString(NBT_TARGET, targetEntityId);
        nbt.putInt(NBT_REQUIRED, required);
        nbt.putInt(NBT_PROGRESS, 0);
        nbt.putBoolean(NBT_COMPLETED, false);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
    }

    /**
     * 检查被击杀的实体是否匹配此契约目标
     */
    public static boolean matchesTarget(ItemStack stack, EntityType<?> killedType) {
        String target = getTarget(stack);
        if (target.isEmpty())
            return false;
        Identifier targetId = Identifier.tryParse(target);
        if (targetId == null)
            return false;
        Identifier killedId = Registries.ENTITY_TYPE.getId(killedType);
        return targetId.equals(killedId);
    }

    /**
     * 判断是否为有效的悬赏契约
     */
    public static boolean isValidContract(ItemStack stack) {
        return stack.isOf(ModItems.BOUNTY_CONTRACT) && !getTarget(stack).isEmpty();
    }

    // ========== Internal ==========

    private static NbtCompound getNbt(ItemStack stack) {
        NbtComponent component = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (component == null)
            return null;
        return component.copyNbt();
    }

    private static NbtCompound getOrCreateNbt(ItemStack stack) {
        NbtComponent component = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (component != null) {
            return component.copyNbt();
        }
        return new NbtCompound();
    }
}
