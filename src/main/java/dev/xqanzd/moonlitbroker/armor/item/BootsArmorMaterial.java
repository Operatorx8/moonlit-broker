package dev.xqanzd.moonlitbroker.armor.item;

import dev.xqanzd.moonlitbroker.armor.BootsEffectConstants;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.ArmorMaterials;
import net.minecraft.item.Items;
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
 * 靴子专用盔甲材质
 * 每个靴子有独立的护甲值，因此需要按 (稀有度, 护甲值) 组合注册材质
 * 韧性按护甲值分档，击退抗性 0
 */
public class BootsArmorMaterial {

    public static final String MOD_ID = "xqanzd_moonlit_broker";

    // 按 (rarity_protection) 组合注册
    public static RegistryEntry<ArmorMaterial> BOOTS_EPIC_3;      // Untraceable Treads
    public static RegistryEntry<ArmorMaterial> BOOTS_UNCOMMON_2;   // Boundary Walker, Marching Boots
    public static RegistryEntry<ArmorMaterial> BOOTS_RARE_1;       // Ghost Step
    public static RegistryEntry<ArmorMaterial> BOOTS_RARE_2;       // Gossamer Boots

    public static void register() {
        if (BOOTS_UNCOMMON_2 != null) {
            return;
        }
        BOOTS_EPIC_3 = registerBootsMaterial(
                "boots_epic_3",
                ArmorMaterials.NETHERITE.value().enchantability(),
                3,
                Ingredient.ofItems(Items.NETHERITE_INGOT)
        );
        BOOTS_UNCOMMON_2 = registerBootsMaterial(
                "boots_uncommon_2",
                ArmorMaterials.IRON.value().enchantability(),
                2,
                Ingredient.ofItems(Items.IRON_INGOT)
        );
        BOOTS_RARE_1 = registerBootsMaterial(
                "boots_rare_1",
                ArmorMaterials.CHAIN.value().enchantability(),
                1,
                Ingredient.ofItems(Items.GOLD_INGOT)
        );
        BOOTS_RARE_2 = registerBootsMaterial(
                "boots_rare_2",
                ArmorMaterials.CHAIN.value().enchantability(),
                2,
                Ingredient.ofItems(Items.GOLD_INGOT)
        );
    }

    /**
     * 根据稀有度和护甲值获取对应材质
     */
    public static RegistryEntry<ArmorMaterial> byRarityAndProtection(Rarity rarity, int protection) {
        if (rarity == Rarity.EPIC && protection == 3) return BOOTS_EPIC_3;
        if (rarity == Rarity.UNCOMMON && protection == 2) return BOOTS_UNCOMMON_2;
        if (rarity == Rarity.RARE && protection == 1) return BOOTS_RARE_1;
        if (rarity == Rarity.RARE && protection == 2) return BOOTS_RARE_2;
        // fallback
        return BOOTS_UNCOMMON_2;
    }

    private static RegistryEntry<ArmorMaterial> registerBootsMaterial(
            String id,
            int enchantability,
            int bootsProtection,
            Ingredient repairIngredient) {
        Map<ArmorItem.Type, Integer> defenseMap = Map.of(
                ArmorItem.Type.HELMET, 0,
                ArmorItem.Type.CHESTPLATE, 0,
                ArmorItem.Type.LEGGINGS, 0,
                ArmorItem.Type.BOOTS, bootsProtection
        );

        ArmorMaterial material = new ArmorMaterial(
                defenseMap,
                enchantability,
                SoundEvents.ITEM_ARMOR_EQUIP_CHAIN,
                () -> repairIngredient,
                dyedLeatherLayers(),
                BootsEffectConstants.toughnessByProtection(bootsProtection),
                BootsEffectConstants.BOOTS_KNOCKBACK_RESISTANCE
        );

        return Registry.registerReference(
                Registries.ARMOR_MATERIAL,
                Identifier.of(MOD_ID, id),
                material
        );
    }

    private static List<ArmorMaterial.Layer> dyedLeatherLayers() {
        return List.of(
                new ArmorMaterial.Layer(Identifier.of("minecraft", "leather"), "", true),
                new ArmorMaterial.Layer(Identifier.of("minecraft", "leather"), "_overlay", false)
        );
    }
}
