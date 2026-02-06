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

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.mymodtest.fatalis.effect").formatted(Formatting.DARK_RED));
        tooltip.add(Text.translatable("item.mymodtest.fatalis.desc").formatted(Formatting.GRAY));
        super.appendTooltip(stack, context, tooltip, type);
    }
}
