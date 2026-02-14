package dev.xqanzd.moonlitbroker.weapon.transitional.item;

import dev.xqanzd.moonlitbroker.weapon.transitional.TransitionalWeaponConstants;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterials;
import net.minecraft.util.Rarity;

/**
 * Velox - 快攻剑
 * 无特效，纯数值武器
 * 特点：高攻速 (2.4)，中高伤害 (7)
 */
public class VeloxItem extends SwordItem {

    public VeloxItem() {
        super(ToolMaterials.IRON, createSettings());
    }

    private static Item.Settings createSettings() {
        // 计算伤害加成：base_damage - 1 - material_damage
        // IRON material damage = 2, 所以 bonus = 7 - 1 - 2 = 4
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

}
