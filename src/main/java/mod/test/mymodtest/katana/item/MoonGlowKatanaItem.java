package mod.test.mymodtest.katana.item;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterials;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Rarity;

import java.util.List;

public class MoonGlowKatanaItem extends SwordItem {

    public MoonGlowKatanaItem(Settings settings) {
        super(ToolMaterials.NETHERITE, settings);
    }

    public static Settings createSettings() {
        return new Settings()
            .maxDamage(KatanaItems.KATANA_MAX_DURABILITY)
            .rarity(Rarity.EPIC)
            .attributeModifiers(createAttributeModifiers());
    }

    private static AttributeModifiersComponent createAttributeModifiers() {
        // NETHERITE 材质 attackDamage=4；+3 后总攻击力为 7
        return SwordItem.createAttributeModifiers(ToolMaterials.NETHERITE, 3, -2.2f);
    }

    @Override
    public boolean canRepair(ItemStack stack, ItemStack ingredient) {
        return false;
    }

    @Override
    public float getMiningSpeed(ItemStack stack, BlockState state) {
        // 太刀可快速破坏蜘蛛网
        if (state.isOf(Blocks.COBWEB)) return 15.0f;
        return 1.0f;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        // 空行分隔
        tooltip.add(Text.empty());

        // 特效名称 - 蓝色
        tooltip.add(Text.translatable("item.mymodtest.moon_glow_katana.effect_name")
            .formatted(Formatting.BLUE));

        // 特效说明 - 灰色，带缩进
        tooltip.add(Text.literal("  ")
            .append(Text.translatable("item.mymodtest.moon_glow_katana.effect_desc1"))
            .formatted(Formatting.GRAY));
        tooltip.add(Text.literal("  ")
            .append(Text.translatable("item.mymodtest.moon_glow_katana.effect_desc2"))
            .formatted(Formatting.GRAY));
    }
}
