package dev.xqanzd.moonlitbroker.katana.item;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterials;
import net.minecraft.util.Rarity;

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
    public float getMiningSpeed(ItemStack stack, BlockState state) {
        if (state.isOf(Blocks.COBWEB)) return 15.0f;
        return 1.0f;
    }
}
