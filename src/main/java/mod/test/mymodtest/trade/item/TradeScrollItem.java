package mod.test.mymodtest.trade.item;

import mod.test.mymodtest.trade.TradeConfig;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;

/**
 * 交易卷轴物品
 * NBT: uses (int), grade (NORMAL/SEALED)
 */
public class TradeScrollItem extends Item {

    public TradeScrollItem(Settings settings) {
        super(settings);
    }

    /**
     * 获取卷轴剩余使用次数
     */
    public static int getUses(ItemStack stack) {
        NbtComponent component = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (component == null) return 0;
        NbtCompound nbt = component.copyNbt();
        return nbt.contains(TradeConfig.NBT_SCROLL_USES) ? nbt.getInt(TradeConfig.NBT_SCROLL_USES) : 0;
    }

    /**
     * 设置卷轴使用次数
     */
    public static void setUses(ItemStack stack, int uses) {
        NbtCompound nbt = getOrCreateNbt(stack);
        nbt.putInt(TradeConfig.NBT_SCROLL_USES, Math.max(0, uses));
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
    }

    /**
     * 获取卷轴等级
     */
    public static String getGrade(ItemStack stack) {
        NbtComponent component = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (component == null) return TradeConfig.GRADE_NORMAL;
        NbtCompound nbt = component.copyNbt();
        return nbt.contains(TradeConfig.NBT_SCROLL_GRADE) ? nbt.getString(TradeConfig.NBT_SCROLL_GRADE) : TradeConfig.GRADE_NORMAL;
    }

    /**
     * 设置卷轴等级
     */
    public static void setGrade(ItemStack stack, String grade) {
        NbtCompound nbt = getOrCreateNbt(stack);
        nbt.putString(TradeConfig.NBT_SCROLL_GRADE, grade);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
    }

    /**
     * 检查是否为封印卷轴
     */
    public static boolean isSealed(ItemStack stack) {
        return TradeConfig.GRADE_SEALED.equals(getGrade(stack));
    }

    /**
     * 尝试消耗指定次数，成功返回true
     * 原子操作：如果次数不足则不消耗
     */
    public static boolean tryConsume(ItemStack stack, int amount) {
        int current = getUses(stack);
        if (current < amount) {
            return false;
        }
        setUses(stack, current - amount);
        return true;
    }

    /**
     * 初始化新卷轴
     */
    public static void initialize(ItemStack stack, String grade) {
        int uses = TradeConfig.GRADE_SEALED.equals(grade) 
            ? TradeConfig.SCROLL_USES_SEALED 
            : TradeConfig.SCROLL_USES_NORMAL;
        setGrade(stack, grade);
        setUses(stack, uses);
    }

    private static NbtCompound getOrCreateNbt(ItemStack stack) {
        NbtComponent component = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (component != null) {
            return component.copyNbt();
        }
        return new NbtCompound();
    }

}
