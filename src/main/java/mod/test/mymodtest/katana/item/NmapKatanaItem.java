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

public class NmapKatanaItem extends SwordItem {

    public NmapKatanaItem(Settings settings) {
        super(ToolMaterials.NETHERITE, settings);
    }

    public static Settings createSettings() {
        return new Settings()
            .maxDamage(KatanaItems.KATANA_MAX_DURABILITY)
            .rarity(Rarity.EPIC)
            .attributeModifiers(createAttributeModifiers());
    }

    private static AttributeModifiersComponent createAttributeModifiers() {
        // NETHERITE 材质 attackDamage=4；+2 后总攻击力为 6
        return SwordItem.createAttributeModifiers(ToolMaterials.NETHERITE, 2, -2.2f);
    }

    @Override
    public boolean canRepair(ItemStack stack, ItemStack ingredient) {
        return false;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        // Lore subtitle
        tooltip.add(Text.literal("nmap -sV -O -A --script=vuln")
            .formatted(Formatting.DARK_GRAY, Formatting.ITALIC));

        tooltip.add(Text.empty());

        // Effect title
        tooltip.add(Text.translatable("item.mymodtest.nmap_katana.effect_name")
            .formatted(Formatting.GOLD, Formatting.BOLD));

        // Module list
        tooltip.add(Text.literal("  \u25b8 ")
            .append(Text.translatable("item.mymodtest.nmap_katana.module_discovery"))
            .formatted(Formatting.AQUA));
        tooltip.add(Text.literal("  \u25b8 ")
            .append(Text.translatable("item.mymodtest.nmap_katana.module_enum"))
            .formatted(Formatting.GREEN));
        tooltip.add(Text.literal("  \u25b8 ")
            .append(Text.translatable("item.mymodtest.nmap_katana.module_vuln"))
            .formatted(Formatting.RED));
        tooltip.add(Text.literal("  \u25b8 ")
            .append(Text.translatable("item.mymodtest.nmap_katana.module_firewall"))
            .formatted(Formatting.LIGHT_PURPLE));
    }

    @Override
    public float getMiningSpeed(ItemStack stack, BlockState state) {
        if (state.isOf(Blocks.COBWEB)) return 15.0f;
        return 1.0f;
    }
}
