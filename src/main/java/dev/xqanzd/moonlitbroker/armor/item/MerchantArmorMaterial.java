package dev.xqanzd.moonlitbroker.armor.item;

import dev.xqanzd.moonlitbroker.armor.ArmorConfig;
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

import java.util.HashMap;
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

    public static final String MOD_ID = "xqanzd_moonlit_broker";

    /** 注册的胸甲材质（按稀有度分档，兼容旧字段） */
    public static RegistryEntry<ArmorMaterial> MERCHANT_UNCOMMON_ARMOR;
    public static RegistryEntry<ArmorMaterial> MERCHANT_RARE_ARMOR;
    public static RegistryEntry<ArmorMaterial> MERCHANT_EPIC_ARMOR;

    /** 注册的头盔材质（按稀有度分档） */
    public static RegistryEntry<ArmorMaterial> MERCHANT_HELMET_UNCOMMON_ARMOR;
    public static RegistryEntry<ArmorMaterial> MERCHANT_HELMET_RARE_ARMOR;
    public static RegistryEntry<ArmorMaterial> MERCHANT_HELMET_EPIC_ARMOR;

    /** 注册的护腿材质（按稀有度分档） */
    public static RegistryEntry<ArmorMaterial> MERCHANT_LEGGINGS_UNCOMMON_ARMOR;
    public static RegistryEntry<ArmorMaterial> MERCHANT_LEGGINGS_RARE_ARMOR;
    public static RegistryEntry<ArmorMaterial> MERCHANT_LEGGINGS_EPIC_ARMOR;

    /**
     * 注册盔甲材质
     * 必须在物品注册前调用
     */
    public static void register() {
        if (MERCHANT_UNCOMMON_ARMOR != null) {
            return;
        }
        MERCHANT_HELMET_UNCOMMON_ARMOR = registerMaterial(
                "merchant_helmet_uncommon",
                ArmorMaterials.IRON.value().enchantability(),
                ArmorItem.Type.HELMET,
                ArmorConfig.HELMET_PROTECTION,
                ArmorConfig.HELMET_TOUGHNESS,
                ArmorConfig.KNOCKBACK_RESISTANCE,
                Ingredient.ofItems(Items.IRON_INGOT)
        );
        MERCHANT_HELMET_RARE_ARMOR = registerMaterial(
                "merchant_helmet_rare",
                ArmorMaterials.CHAIN.value().enchantability(),
                ArmorItem.Type.HELMET,
                ArmorConfig.HELMET_PROTECTION,
                ArmorConfig.HELMET_TOUGHNESS,
                ArmorConfig.KNOCKBACK_RESISTANCE,
                Ingredient.ofItems(Items.GOLD_INGOT)
        );
        MERCHANT_HELMET_EPIC_ARMOR = registerMaterial(
                "merchant_helmet_epic",
                ArmorMaterials.NETHERITE.value().enchantability(),
                ArmorItem.Type.HELMET,
                ArmorConfig.HELMET_PROTECTION,
                ArmorConfig.HELMET_TOUGHNESS,
                ArmorConfig.KNOCKBACK_RESISTANCE,
                Ingredient.ofItems(Items.NETHERITE_INGOT)
        );

        MERCHANT_UNCOMMON_ARMOR = registerMaterial(
                "merchant_uncommon",
                ArmorMaterials.IRON.value().enchantability(),
                ArmorItem.Type.CHESTPLATE,
                ArmorConfig.CHESTPLATE_PROTECTION,
                ArmorConfig.CHESTPLATE_TOUGHNESS,
                ArmorConfig.KNOCKBACK_RESISTANCE,
                Ingredient.ofItems(Items.IRON_INGOT)
        );
        MERCHANT_RARE_ARMOR = registerMaterial(
                "merchant_rare",
                ArmorMaterials.CHAIN.value().enchantability(),
                ArmorItem.Type.CHESTPLATE,
                ArmorConfig.CHESTPLATE_PROTECTION,
                ArmorConfig.CHESTPLATE_TOUGHNESS,
                ArmorConfig.KNOCKBACK_RESISTANCE,
                Ingredient.ofItems(Items.GOLD_INGOT)
        );
        MERCHANT_EPIC_ARMOR = registerMaterial(
                "merchant_epic",
                ArmorMaterials.NETHERITE.value().enchantability(),
                ArmorItem.Type.CHESTPLATE,
                ArmorConfig.CHESTPLATE_PROTECTION,
                ArmorConfig.CHESTPLATE_TOUGHNESS,
                ArmorConfig.KNOCKBACK_RESISTANCE,
                Ingredient.ofItems(Items.NETHERITE_INGOT)
        );

        MERCHANT_LEGGINGS_UNCOMMON_ARMOR = registerMaterial(
                "merchant_leggings_uncommon",
                ArmorMaterials.IRON.value().enchantability(),
                ArmorItem.Type.LEGGINGS,
                ArmorConfig.LEGGINGS_PROTECTION,
                ArmorConfig.LEGGINGS_TOUGHNESS,
                ArmorConfig.KNOCKBACK_RESISTANCE,
                Ingredient.ofItems(Items.IRON_INGOT)
        );
        MERCHANT_LEGGINGS_RARE_ARMOR = registerMaterial(
                "merchant_leggings_rare",
                ArmorMaterials.CHAIN.value().enchantability(),
                ArmorItem.Type.LEGGINGS,
                ArmorConfig.LEGGINGS_PROTECTION,
                ArmorConfig.LEGGINGS_TOUGHNESS,
                ArmorConfig.KNOCKBACK_RESISTANCE,
                Ingredient.ofItems(Items.GOLD_INGOT)
        );
        MERCHANT_LEGGINGS_EPIC_ARMOR = registerMaterial(
                "merchant_leggings_epic",
                ArmorMaterials.NETHERITE.value().enchantability(),
                ArmorItem.Type.LEGGINGS,
                ArmorConfig.LEGGINGS_PROTECTION,
                ArmorConfig.LEGGINGS_TOUGHNESS,
                ArmorConfig.KNOCKBACK_RESISTANCE,
                Ingredient.ofItems(Items.NETHERITE_INGOT)
        );
    }

    public static RegistryEntry<ArmorMaterial> byRarityAndType(Rarity rarity, ArmorItem.Type type) {
        return switch (type) {
            case HELMET -> byRarity(rarity, MERCHANT_HELMET_UNCOMMON_ARMOR, MERCHANT_HELMET_RARE_ARMOR, MERCHANT_HELMET_EPIC_ARMOR);
            case LEGGINGS -> byRarity(rarity, MERCHANT_LEGGINGS_UNCOMMON_ARMOR, MERCHANT_LEGGINGS_RARE_ARMOR, MERCHANT_LEGGINGS_EPIC_ARMOR);
            case CHESTPLATE -> byRarity(rarity, MERCHANT_UNCOMMON_ARMOR, MERCHANT_RARE_ARMOR, MERCHANT_EPIC_ARMOR);
            default -> byRarity(rarity, MERCHANT_UNCOMMON_ARMOR, MERCHANT_RARE_ARMOR, MERCHANT_EPIC_ARMOR);
        };
    }

    public static RegistryEntry<ArmorMaterial> byRarity(Rarity rarity) {
        return byRarity(rarity, MERCHANT_UNCOMMON_ARMOR, MERCHANT_RARE_ARMOR, MERCHANT_EPIC_ARMOR);
    }

    private static RegistryEntry<ArmorMaterial> byRarity(
            Rarity rarity,
            RegistryEntry<ArmorMaterial> uncommon,
            RegistryEntry<ArmorMaterial> rare,
            RegistryEntry<ArmorMaterial> epic) {
        return switch (rarity) {
            case UNCOMMON -> uncommon;
            case RARE -> rare;
            case EPIC -> epic;
            default -> uncommon;
        };
    }

    private static RegistryEntry<ArmorMaterial> registerMaterial(
            String id,
            int enchantability,
            ArmorItem.Type armorType,
            int protection,
            float toughness,
            float knockbackResistance,
            Ingredient repairIngredient) {
        Map<ArmorItem.Type, Integer> defenseMap = switch (armorType) {
            case HELMET -> Map.of(
                    ArmorItem.Type.HELMET, protection,
                    ArmorItem.Type.CHESTPLATE, 0,
                    ArmorItem.Type.LEGGINGS, 0,
                    ArmorItem.Type.BOOTS, 0
            );
            case CHESTPLATE -> Map.of(
                    ArmorItem.Type.HELMET, 0,
                    ArmorItem.Type.CHESTPLATE, protection,
                    ArmorItem.Type.LEGGINGS, 0,
                    ArmorItem.Type.BOOTS, 0
            );
            case LEGGINGS -> Map.of(
                    ArmorItem.Type.HELMET, 0,
                    ArmorItem.Type.CHESTPLATE, 0,
                    ArmorItem.Type.LEGGINGS, protection,
                    ArmorItem.Type.BOOTS, 0
            );
            default -> Map.of(
                    ArmorItem.Type.HELMET, 0,
                    ArmorItem.Type.CHESTPLATE, 0,
                    ArmorItem.Type.LEGGINGS, 0,
                    ArmorItem.Type.BOOTS, 0
            );
        };

        ArmorMaterial material = new ArmorMaterial(
                defenseMap,
                enchantability,
                SoundEvents.ITEM_ARMOR_EQUIP_CHAIN,
                () -> repairIngredient,
                dyedLeatherLayers(),
                toughness,
                knockbackResistance
        );

        return Registry.registerReference(
                Registries.ARMOR_MATERIAL,
                Identifier.of(MOD_ID, id),
                material
        );
    }

    /** 单件专属材质缓存：key = itemPath, value = 已注册的材质。防止重复注册。 */
    private static final Map<String, RegistryEntry<ArmorMaterial>> ITEM_SPECIFIC_CACHE = new HashMap<>();

    /**
     * 为单件装备创建专属材质（用于 ArmorSpecs 覆写场景）。
     * 仅在该装备有数值覆写时调用，其他装备继续共享 rarity 材质。
     * 同一 itemPath 只会注册一次，后续调用返回缓存实例。
     */
    public static RegistryEntry<ArmorMaterial> registerItemSpecificMaterial(
            String itemPath,
            int enchantability,
            ArmorItem.Type armorType,
            int protection,
            float toughness,
            float knockbackResistance,
            Ingredient repairIngredient) {
        return ITEM_SPECIFIC_CACHE.computeIfAbsent(itemPath, key ->
                registerMaterial(
                        "item_" + key,
                        enchantability,
                        armorType,
                        protection,
                        toughness,
                        knockbackResistance,
                        repairIngredient
                )
        );
    }

    private static List<ArmorMaterial.Layer> dyedLeatherLayers() {
        return List.of(
                new ArmorMaterial.Layer(Identifier.of("minecraft", "leather"), "", true),
                new ArmorMaterial.Layer(Identifier.of("minecraft", "leather"), "_overlay", false)
        );
    }
}
