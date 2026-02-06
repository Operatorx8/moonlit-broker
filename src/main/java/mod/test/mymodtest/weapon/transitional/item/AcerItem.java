package mod.test.mymodtest.weapon.transitional.item;

import mod.test.mymodtest.weapon.transitional.TransitionalWeaponConstants;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterials;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Rarity;

import java.util.List;

/**
 * Acer - 暴击强化剑
 * 特效：暴击伤害乘数从 1.5 提升至 1.7
 */
public class AcerItem extends SwordItem {

    public AcerItem() {
        super(ToolMaterials.IRON, createSettings());
    }

    private static Item.Settings createSettings() {
        // 计算伤害加成：base_damage - 1 - material_damage
        // IRON material damage = 2, 所以 bonus = 6 - 1 - 2 = 3
        int damageBonus = TransitionalWeaponConstants.ACER_BASE_DAMAGE - 1 - 2;

        return new Item.Settings()
                .maxDamage(TransitionalWeaponConstants.ACER_DURABILITY)
                .rarity(Rarity.UNCOMMON)
                .fireproof()
                .attributeModifiers(SwordItem.createAttributeModifiers(
                        ToolMaterials.IRON,
                        damageBonus,
                        TransitionalWeaponConstants.ACER_ATTACK_SPEED
                ));
    }

    @Override
    public boolean canRepair(ItemStack stack, ItemStack ingredient) {
        return false;
    }

    @Override
    public int getEnchantability() {
        return TransitionalWeaponConstants.KATANA_ENCHANTABILITY;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.mymodtest.acer.effect").formatted(Formatting.GOLD));
        tooltip.add(Text.translatable("item.mymodtest.acer.desc").formatted(Formatting.GRAY));
        super.appendTooltip(stack, context, tooltip, type);
    }
}
