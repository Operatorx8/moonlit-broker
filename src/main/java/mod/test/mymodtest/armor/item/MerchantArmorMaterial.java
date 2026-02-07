package mod.test.mymodtest.armor.item;

import mod.test.mymodtest.armor.ArmorConfig;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.ArmorMaterials;
import net.minecraft.recipe.Ingredient;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;

import java.util.List;
import java.util.Map;

/**
 * 神秘商人盔甲材质
 * 附魔系数按稀有度分档：
 * UNCOMMON -> IRON (9), RARE -> CHAIN (12), EPIC -> NETHERITE (15)
 *
 * Armor items (helmet/chest/legs/boots) all follow rarity-tier enchantability mapping
 */
public class MerchantArmorMaterial {

    public static final String MOD_ID = "mymodtest";

    /** 注册的盔甲材质（按稀有度分档） */
    public static RegistryEntry<ArmorMaterial> MERCHANT_UNCOMMON_ARMOR;
    public static RegistryEntry<ArmorMaterial> MERCHANT_RARE_ARMOR;
    public static RegistryEntry<ArmorMaterial> MERCHANT_EPIC_ARMOR;

    /**
     * 注册盔甲材质
     * 必须在物品注册前调用
     */
    public static void register() {
        MERCHANT_UNCOMMON_ARMOR = registerMaterial(
                "merchant_uncommon",
                ArmorMaterials.IRON.value().enchantability(),
                ArmorConfig.KNOCKBACK_RESISTANCE
        );
        MERCHANT_RARE_ARMOR = registerMaterial(
                "merchant_rare",
                ArmorMaterials.CHAIN.value().enchantability(),
                ArmorConfig.KNOCKBACK_RESISTANCE
        );
        MERCHANT_EPIC_ARMOR = registerMaterial(
                "merchant_epic",
                ArmorMaterials.NETHERITE.value().enchantability(),
                ArmorConfig.KNOCKBACK_RESISTANCE
        );
    }

    public static RegistryEntry<ArmorMaterial> byRarity(Rarity rarity) {
        return switch (rarity) {
            case UNCOMMON -> MERCHANT_UNCOMMON_ARMOR;
            case RARE -> MERCHANT_RARE_ARMOR;
            case EPIC -> MERCHANT_EPIC_ARMOR;
            default -> MERCHANT_UNCOMMON_ARMOR;
        };
    }

    private static RegistryEntry<ArmorMaterial> registerMaterial(String id, int enchantability, float knockbackResistance) {
        Map<ArmorItem.Type, Integer> defenseMap = Map.of(
                ArmorItem.Type.HELMET, ArmorConfig.HELMET_PROTECTION,
                ArmorItem.Type.CHESTPLATE, ArmorConfig.CHESTPLATE_PROTECTION,
                ArmorItem.Type.LEGGINGS, ArmorConfig.LEGGINGS_PROTECTION,
                ArmorItem.Type.BOOTS, 0
        );

        ArmorMaterial material = new ArmorMaterial(
                defenseMap,
                enchantability,
                SoundEvents.ITEM_ARMOR_EQUIP_CHAIN,
                () -> Ingredient.EMPTY,
                // Temporary fallback to vanilla iron armor layer to avoid missing-texture purple/black.
                List.of(new ArmorMaterial.Layer(Identifier.of("minecraft", "iron"))),
                ArmorConfig.TOUGHNESS,
                knockbackResistance
        );

        return Registry.registerReference(
                Registries.ARMOR_MATERIAL,
                Identifier.of(MOD_ID, id),
                material
        );
    }
}
