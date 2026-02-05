package mod.test.mymodtest.trade.item;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * 指南卷轴物品
 * 首次见面赠送，仅提供信息
 */
public class GuideScrollItem extends Item {

    public GuideScrollItem(Settings settings) {
        super(settings);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        super.appendTooltip(stack, context, tooltip, type);
        
        tooltip.add(Text.literal("神秘商人的指南").formatted(Formatting.GOLD));
        tooltip.add(Text.empty());
        tooltip.add(Text.literal("与商人交易可获得声望").formatted(Formatting.GRAY));
        tooltip.add(Text.literal("声望达到15后可进入隐藏交易").formatted(Formatting.GRAY));
        tooltip.add(Text.literal("需要商人印记和封印卷轴").formatted(Formatting.GRAY));
    }
}
