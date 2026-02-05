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

/**
 * 残念之刃 - 处刑型太刀
 * 特效：削减目标当前血量的 30%，但无法击杀
 */
public class RegretBladeItem extends SwordItem {

    public RegretBladeItem(Settings settings) {
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
        if (state.isOf(Blocks.COBWEB)) return 15.0f;
        return 1.0f;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.empty());

        // 特效名称 - 暗红色
        tooltip.add(Text.translatable("item.mymodtest.regret_blade.effect_name")
            .formatted(Formatting.DARK_RED));

        // 特效说明
        tooltip.add(Text.literal("  ")
            .append(Text.translatable("item.mymodtest.regret_blade.effect_desc1"))
            .formatted(Formatting.GRAY));
        // 护甲穿透 - 金色高亮
        tooltip.add(Text.literal("  ")
            .append(Text.translatable("item.mymodtest.regret_blade.effect_desc2"))
            .formatted(Formatting.GOLD));
        tooltip.add(Text.literal("  ")
            .append(Text.translatable("item.mymodtest.regret_blade.effect_desc3"))
            .formatted(Formatting.GRAY));
        // 风味文字
        tooltip.add(Text.literal("  ")
            .append(Text.translatable("item.mymodtest.regret_blade.effect_desc4"))
            .formatted(Formatting.DARK_GRAY, Formatting.ITALIC));
    }
}
