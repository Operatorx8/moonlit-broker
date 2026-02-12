package dev.xqanzd.moonlitbroker.weapon.transitional.item;

import dev.xqanzd.moonlitbroker.weapon.transitional.TransitionalWeaponConstants;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterials;
import net.minecraft.util.Rarity;

/**
 * Fatalis - 重击剑
 * 无特效，纯数值武器
 * 特点：高伤害 (10)，标准攻速 (1.8)
 */
public class FatalisItem extends SwordItem {

    public FatalisItem() {
        super(ToolMaterials.IRON, createSettings());
    }

    private static Item.Settings createSettings() {
        // 计算伤害加成：base_damage - 1 - material_damage
        // IRON material damage = 2, 所以 bonus = 10 - 1 - 2 = 7
        int damageBonus = TransitionalWeaponConstants.FATALIS_BASE_DAMAGE - 1 - 2;

        return new Item.Settings()
                .maxDamage(TransitionalWeaponConstants.FATALIS_DURABILITY)
                .rarity(Rarity.UNCOMMON)
                .fireproof()
                .attributeModifiers(SwordItem.createAttributeModifiers(
                        ToolMaterials.IRON,
                        damageBonus,
                        TransitionalWeaponConstants.FATALIS_ATTACK_SPEED
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

}
