package mod.test.mymodtest.weapon.transitional.item;

import mod.test.mymodtest.weapon.transitional.TransitionalWeaponConstants;
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
 * Velox - 快攻剑
 * 无特效，纯数值武器
 * 特点：高攻速 (2.2)，低伤害 (5)
 */
public class VeloxItem extends SwordItem {

    public VeloxItem() {
        super(ToolMaterials.IRON, createSettings());
    }

    private static Item.Settings createSettings() {
        // 计算伤害加成：base_damage - 1 - material_damage
        // IRON material damage = 2, 所以 bonus = 5 - 1 - 2 = 2
        int damageBonus = TransitionalWeaponConstants.VELOX_BASE_DAMAGE - 1 - 2;

        return new Item.Settings()
                .maxDamage(TransitionalWeaponConstants.VELOX_DURABILITY)
                .rarity(Rarity.COMMON)
                .fireproof()
                .attributeModifiers(SwordItem.createAttributeModifiers(
                        ToolMaterials.IRON,
                        damageBonus,
                        TransitionalWeaponConstants.VELOX_ATTACK_SPEED
                ));
    }

    @Override
    public boolean canRepair(ItemStack stack, ItemStack ingredient) {
        return false;
    }

    @Override
    public int getEnchantability() {
        return TransitionalWeaponConstants.SWORD_ENCHANTABILITY;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.mymodtest.velox.effect").formatted(Formatting.AQUA));
        tooltip.add(Text.translatable("item.mymodtest.velox.desc").formatted(Formatting.GRAY));
        super.appendTooltip(stack, context, tooltip, type);
    }
}
