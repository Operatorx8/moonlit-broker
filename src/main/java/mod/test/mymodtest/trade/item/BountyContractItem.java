package mod.test.mymodtest.trade.item;

import mod.test.mymodtest.registry.ModItems;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.List;

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

    // ========== Tooltip ==========

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        super.appendTooltip(stack, context, tooltip, type);

        String target = getTarget(stack);
        if (target.isEmpty()) {
            tooltip.add(Text.literal("空白契约").formatted(Formatting.GRAY));
            return;
        }

        // 目标名称
        Identifier targetId = Identifier.tryParse(target);
        String targetName = target;
        if (targetId != null) {
            EntityType<?> entityType = Registries.ENTITY_TYPE.get(targetId);
            if (entityType != null) {
                targetName = entityType.getName().getString();
            }
        }
        tooltip.add(Text.literal("目标: " + targetName).formatted(Formatting.YELLOW));

        // 进度
        int progress = getProgress(stack);
        int required = getRequired(stack);
        tooltip.add(Text.literal("进度: " + progress + "/" + required)
                .formatted(progress >= required ? Formatting.GREEN : Formatting.GRAY));

        // 完成状态
        if (isCompleted(stack)) {
            tooltip.add(Text.literal("✓ 已完成 — 右键商人提交").formatted(Formatting.GREEN, Formatting.BOLD));
        } else {
            tooltip.add(Text.literal("✗ 未完成").formatted(Formatting.RED));
        }
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
