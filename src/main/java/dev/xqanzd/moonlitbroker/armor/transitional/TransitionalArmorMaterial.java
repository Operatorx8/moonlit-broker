package dev.xqanzd.moonlitbroker.armor.transitional;

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

import java.util.List;
import java.util.Map;

/**
 * 过渡护甲材质
 * 每件护甲有独立的防御值和韧性，按具体规格注册
 */
public final class TransitionalArmorMaterial {

    public static final String MOD_ID = "xqanzd_moonlit_broker";

    private TransitionalArmorMaterial() {}

    // HEAD 材质
    public static RegistryEntry<ArmorMaterial> SCAVENGER_GOGGLES;  // def=3, tough=1.0, UNCOMMON
    public static RegistryEntry<ArmorMaterial> CAST_IRON_SALLET;   // def=3, tough=1.0, UNCOMMON
    public static RegistryEntry<ArmorMaterial> SANCTIFIED_HOOD;    // def=3, tough=1.0, RARE

    // CHEST 材质
    public static RegistryEntry<ArmorMaterial> REACTIVE_BUG_PLATE; // def=6, tough=1, RARE
    public static RegistryEntry<ArmorMaterial> PATCHWORK_COAT;     // def=5, tough=0, UNCOMMON
    public static RegistryEntry<ArmorMaterial> RITUAL_ROBE;        // def=5, tough=1, UNCOMMON

    // LEGS 材质
    public static RegistryEntry<ArmorMaterial> WRAPPED_LEGGINGS;      // def=6, tough=1.2, UNCOMMON
    public static RegistryEntry<ArmorMaterial> REINFORCED_GREAVES;    // def=6, tough=1.2, UNCOMMON
    public static RegistryEntry<ArmorMaterial> CARGO_PANTS;           // def=6, tough=1.2, RARE

    // FEET 材质
    public static RegistryEntry<ArmorMaterial> PENITENT_BOOTS;        // def=3, tough=0.8, UNCOMMON
    public static RegistryEntry<ArmorMaterial> STANDARD_IRON_BOOTS;   // def=3, tough=0.8, UNCOMMON
    public static RegistryEntry<ArmorMaterial> CUSHION_HIKING_BOOTS;  // def=3, tough=0.8, RARE

    public static void register() {
        if (SCAVENGER_GOGGLES != null) {
            return;
        }
        // HEAD
        SCAVENGER_GOGGLES = registerHelmetMaterial(
                "trans_scavenger_goggles",
                getEnchantabilityByRarity(TransitionalArmorConstants.SCAVENGER_GOGGLES_RARITY),
                TransitionalArmorConstants.SCAVENGER_GOGGLES_DEFENSE,
                TransitionalArmorConstants.SCAVENGER_GOGGLES_TOUGHNESS,
                getRepairIngredientByRarity(TransitionalArmorConstants.SCAVENGER_GOGGLES_RARITY)
        );
        CAST_IRON_SALLET = registerHelmetMaterial(
                "trans_cast_iron_sallet",
                getEnchantabilityByRarity(TransitionalArmorConstants.CAST_IRON_SALLET_RARITY),
                (int) TransitionalArmorConstants.CAST_IRON_SALLET_DEFENSE,
                TransitionalArmorConstants.CAST_IRON_SALLET_TOUGHNESS,
                getRepairIngredientByRarity(TransitionalArmorConstants.CAST_IRON_SALLET_RARITY)
        );
        SANCTIFIED_HOOD = registerHelmetMaterial(
                "trans_sanctified_hood",
                getEnchantabilityByRarity(TransitionalArmorConstants.SANCTIFIED_HOOD_RARITY),
                TransitionalArmorConstants.SANCTIFIED_HOOD_DEFENSE,
                TransitionalArmorConstants.SANCTIFIED_HOOD_TOUGHNESS,
                getRepairIngredientByRarity(TransitionalArmorConstants.SANCTIFIED_HOOD_RARITY)
        );

        // CHEST
        REACTIVE_BUG_PLATE = registerChestplateMaterial(
                "trans_reactive_bug_plate",
                getEnchantabilityByRarity(TransitionalArmorConstants.REACTIVE_BUG_PLATE_RARITY),
                TransitionalArmorConstants.REACTIVE_BUG_PLATE_DEFENSE,
                TransitionalArmorConstants.REACTIVE_BUG_PLATE_TOUGHNESS,
                getRepairIngredientByRarity(TransitionalArmorConstants.REACTIVE_BUG_PLATE_RARITY)
        );
        PATCHWORK_COAT = registerChestplateMaterial(
                "trans_patchwork_coat",
                getEnchantabilityByRarity(TransitionalArmorConstants.PATCHWORK_COAT_RARITY),
                TransitionalArmorConstants.PATCHWORK_COAT_DEFENSE,
                TransitionalArmorConstants.PATCHWORK_COAT_TOUGHNESS,
                getRepairIngredientByRarity(TransitionalArmorConstants.PATCHWORK_COAT_RARITY)
        );
        RITUAL_ROBE = registerChestplateMaterial(
                "trans_ritual_robe",
                getEnchantabilityByRarity(TransitionalArmorConstants.RITUAL_ROBE_RARITY),
                TransitionalArmorConstants.RITUAL_ROBE_DEFENSE,
                TransitionalArmorConstants.RITUAL_ROBE_TOUGHNESS,
                getRepairIngredientByRarity(TransitionalArmorConstants.RITUAL_ROBE_RARITY)
        );

        // LEGS
        WRAPPED_LEGGINGS = registerLeggingsMaterial(
                "trans_wrapped_leggings",
                getEnchantabilityByRarity(TransitionalArmorConstants.WRAPPED_LEGGINGS_RARITY),
                (int) TransitionalArmorConstants.WRAPPED_LEGGINGS_DEFENSE,
                TransitionalArmorConstants.WRAPPED_LEGGINGS_TOUGHNESS,
                getRepairIngredientByRarity(TransitionalArmorConstants.WRAPPED_LEGGINGS_RARITY)
        );
        REINFORCED_GREAVES = registerLeggingsMaterial(
                "trans_reinforced_greaves",
                getEnchantabilityByRarity(TransitionalArmorConstants.REINFORCED_GREAVES_RARITY),
                TransitionalArmorConstants.REINFORCED_GREAVES_DEFENSE,
                TransitionalArmorConstants.REINFORCED_GREAVES_TOUGHNESS,
                getRepairIngredientByRarity(TransitionalArmorConstants.REINFORCED_GREAVES_RARITY)
        );
        CARGO_PANTS = registerLeggingsMaterial(
                "trans_cargo_pants",
                getEnchantabilityByRarity(TransitionalArmorConstants.CARGO_PANTS_RARITY),
                (int) TransitionalArmorConstants.CARGO_PANTS_DEFENSE,
                TransitionalArmorConstants.CARGO_PANTS_TOUGHNESS,
                getRepairIngredientByRarity(TransitionalArmorConstants.CARGO_PANTS_RARITY)
        );

        // FEET
        PENITENT_BOOTS = registerBootsMaterial(
                "trans_penitent_boots",
                getEnchantabilityByRarity(TransitionalArmorConstants.PENITENT_BOOTS_RARITY),
                TransitionalArmorConstants.PENITENT_BOOTS_DEFENSE,
                TransitionalArmorConstants.PENITENT_BOOTS_TOUGHNESS,
                getRepairIngredientByRarity(TransitionalArmorConstants.PENITENT_BOOTS_RARITY)
        );
        STANDARD_IRON_BOOTS = registerBootsMaterial(
                "trans_standard_iron_boots",
                getEnchantabilityByRarity(TransitionalArmorConstants.STANDARD_IRON_BOOTS_RARITY),
                TransitionalArmorConstants.STANDARD_IRON_BOOTS_DEFENSE,
                TransitionalArmorConstants.STANDARD_IRON_BOOTS_TOUGHNESS,
                getRepairIngredientByRarity(TransitionalArmorConstants.STANDARD_IRON_BOOTS_RARITY)
        );
        CUSHION_HIKING_BOOTS = registerBootsMaterial(
                "trans_cushion_hiking_boots",
                getEnchantabilityByRarity(TransitionalArmorConstants.CUSHION_HIKING_BOOTS_RARITY),
                (int) TransitionalArmorConstants.CUSHION_HIKING_BOOTS_DEFENSE,
                TransitionalArmorConstants.CUSHION_HIKING_BOOTS_TOUGHNESS,
                getRepairIngredientByRarity(TransitionalArmorConstants.CUSHION_HIKING_BOOTS_RARITY)
        );
    }

    /**
     * 根据稀有度返回附魔系数
     * UNCOMMON -> IRON (9), RARE -> CHAIN (12), EPIC -> NETHERITE (15)
     */
    private static int getEnchantabilityByRarity(net.minecraft.util.Rarity rarity) {
        return switch (rarity) {
            case UNCOMMON -> ArmorMaterials.IRON.value().enchantability();
            case RARE -> ArmorMaterials.CHAIN.value().enchantability();
            case EPIC -> ArmorMaterials.NETHERITE.value().enchantability();
            default -> ArmorMaterials.IRON.value().enchantability();
        };
    }

    private static Ingredient getRepairIngredientByRarity(net.minecraft.util.Rarity rarity) {
        return switch (rarity) {
            case UNCOMMON -> Ingredient.ofItems(Items.IRON_INGOT);
            case RARE -> Ingredient.ofItems(Items.GOLD_INGOT);
            case EPIC -> Ingredient.ofItems(Items.NETHERITE_INGOT);
            default -> Ingredient.ofItems(Items.IRON_INGOT);
        };
    }

    private static RegistryEntry<ArmorMaterial> registerHelmetMaterial(
            String id,
            int enchantability,
            int helmetDefense,
            float toughness,
            Ingredient repairIngredient) {
        Map<ArmorItem.Type, Integer> defenseMap = Map.of(
                ArmorItem.Type.HELMET, helmetDefense,
                ArmorItem.Type.CHESTPLATE, 0,
                ArmorItem.Type.LEGGINGS, 0,
                ArmorItem.Type.BOOTS, 0
        );

        ArmorMaterial material = new ArmorMaterial(
                defenseMap,
                enchantability,
                SoundEvents.ITEM_ARMOR_EQUIP_LEATHER,
                () -> repairIngredient,
                dyedLeatherLayers(),
                toughness,
                TransitionalArmorConstants.KNOCKBACK_RESISTANCE
        );

        return Registry.registerReference(
                Registries.ARMOR_MATERIAL,
                Identifier.of(MOD_ID, id),
                material
        );
    }

    private static RegistryEntry<ArmorMaterial> registerChestplateMaterial(
            String id,
            int enchantability,
            int chestplateDefense,
            float toughness,
            Ingredient repairIngredient) {
        Map<ArmorItem.Type, Integer> defenseMap = Map.of(
                ArmorItem.Type.HELMET, 0,
                ArmorItem.Type.CHESTPLATE, chestplateDefense,
                ArmorItem.Type.LEGGINGS, 0,
                ArmorItem.Type.BOOTS, 0
        );

        ArmorMaterial material = new ArmorMaterial(
                defenseMap,
                enchantability,
                SoundEvents.ITEM_ARMOR_EQUIP_LEATHER,
                () -> repairIngredient,
                dyedLeatherLayers(),
                toughness,
                TransitionalArmorConstants.KNOCKBACK_RESISTANCE
        );

        return Registry.registerReference(
                Registries.ARMOR_MATERIAL,
                Identifier.of(MOD_ID, id),
                material
        );
    }

    private static RegistryEntry<ArmorMaterial> registerLeggingsMaterial(
            String id,
            int enchantability,
            int leggingsDefense,
            float toughness,
            Ingredient repairIngredient) {
        Map<ArmorItem.Type, Integer> defenseMap = Map.of(
                ArmorItem.Type.HELMET, 0,
                ArmorItem.Type.CHESTPLATE, 0,
                ArmorItem.Type.LEGGINGS, leggingsDefense,
                ArmorItem.Type.BOOTS, 0
        );

        ArmorMaterial material = new ArmorMaterial(
                defenseMap,
                enchantability,
                SoundEvents.ITEM_ARMOR_EQUIP_LEATHER,
                () -> repairIngredient,
                dyedLeatherLayers(),
                toughness,
                TransitionalArmorConstants.KNOCKBACK_RESISTANCE
        );

        return Registry.registerReference(
                Registries.ARMOR_MATERIAL,
                Identifier.of(MOD_ID, id),
                material
        );
    }

    private static RegistryEntry<ArmorMaterial> registerBootsMaterial(
            String id,
            int enchantability,
            int bootsDefense,
            float toughness,
            Ingredient repairIngredient) {
        Map<ArmorItem.Type, Integer> defenseMap = Map.of(
                ArmorItem.Type.HELMET, 0,
                ArmorItem.Type.CHESTPLATE, 0,
                ArmorItem.Type.LEGGINGS, 0,
                ArmorItem.Type.BOOTS, bootsDefense
        );

        ArmorMaterial material = new ArmorMaterial(
                defenseMap,
                enchantability,
                SoundEvents.ITEM_ARMOR_EQUIP_LEATHER,
                () -> repairIngredient,
                dyedLeatherLayers(),
                toughness,
                TransitionalArmorConstants.KNOCKBACK_RESISTANCE
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
