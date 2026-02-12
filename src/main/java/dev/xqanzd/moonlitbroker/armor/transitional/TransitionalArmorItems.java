package dev.xqanzd.moonlitbroker.armor.transitional;

import dev.xqanzd.moonlitbroker.armor.item.ArmorColorPalette;
import dev.xqanzd.moonlitbroker.armor.item.DefaultDyedLeatherArmorItem;
import dev.xqanzd.moonlitbroker.armor.item.MMArmorMaterials;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 过渡护甲物品注册
 */
public final class TransitionalArmorItems {

    private TransitionalArmorItems() {}

    public static final String MOD_ID = "xqanzd_moonlit_broker";
    private static final Logger LOGGER = LoggerFactory.getLogger("MoonTrace");

    // ==================== 头盔物品 ====================
    public static Item SCAVENGER_GOGGLES;
    public static Item CAST_IRON_SALLET;
    public static Item SANCTIFIED_HOOD;

    // ==================== 胸甲物品 ====================
    public static Item REACTIVE_BUG_PLATE;
    public static Item PATCHWORK_COAT;
    public static Item RITUAL_ROBE;

    // ==================== 护腿物品 ====================
    public static Item WRAPPED_LEGGINGS;
    public static Item REINFORCED_GREAVES;
    public static Item CARGO_PANTS;

    // ==================== 靴子物品 ====================
    public static Item PENITENT_BOOTS;
    public static Item STANDARD_IRON_BOOTS;
    public static Item CUSHION_HIKING_BOOTS;

    /**
     * 注册所有过渡护甲物品
     */
    public static void register() {
        MMArmorMaterials.register();

        // 头盔
        SCAVENGER_GOGGLES = registerHelmet(
                "scavenger_goggles",
                TransitionalArmorConstants.SCAVENGER_GOGGLES_DURABILITY,
                TransitionalArmorConstants.SCAVENGER_GOGGLES_RARITY
        );
        CAST_IRON_SALLET = registerHelmet(
                "cast_iron_sallet",
                TransitionalArmorConstants.CAST_IRON_SALLET_DURABILITY,
                TransitionalArmorConstants.CAST_IRON_SALLET_RARITY
        );
        SANCTIFIED_HOOD = registerHelmet(
                "sanctified_hood",
                TransitionalArmorConstants.SANCTIFIED_HOOD_DURABILITY,
                TransitionalArmorConstants.SANCTIFIED_HOOD_RARITY
        );

        // 胸甲
        REACTIVE_BUG_PLATE = registerChestplate(
                "reactive_bug_plate",
                TransitionalArmorConstants.REACTIVE_BUG_PLATE_DURABILITY,
                TransitionalArmorConstants.REACTIVE_BUG_PLATE_RARITY
        );
        PATCHWORK_COAT = registerChestplate(
                "patchwork_coat",
                TransitionalArmorConstants.PATCHWORK_COAT_DURABILITY,
                TransitionalArmorConstants.PATCHWORK_COAT_RARITY
        );
        RITUAL_ROBE = registerChestplate(
                "ritual_robe",
                TransitionalArmorConstants.RITUAL_ROBE_DURABILITY,
                TransitionalArmorConstants.RITUAL_ROBE_RARITY
        );

        // 护腿
        WRAPPED_LEGGINGS = registerLeggings(
                "wrapped_leggings",
                TransitionalArmorConstants.WRAPPED_LEGGINGS_DURABILITY,
                TransitionalArmorConstants.WRAPPED_LEGGINGS_RARITY
        );
        REINFORCED_GREAVES = registerLeggings(
                "reinforced_greaves",
                TransitionalArmorConstants.REINFORCED_GREAVES_DURABILITY,
                TransitionalArmorConstants.REINFORCED_GREAVES_RARITY
        );
        CARGO_PANTS = registerLeggings(
                "cargo_pants",
                TransitionalArmorConstants.CARGO_PANTS_DURABILITY,
                TransitionalArmorConstants.CARGO_PANTS_RARITY
        );

        // 靴子
        PENITENT_BOOTS = registerBoots(
                "penitent_boots",
                TransitionalArmorConstants.PENITENT_BOOTS_DURABILITY,
                TransitionalArmorConstants.PENITENT_BOOTS_RARITY
        );
        STANDARD_IRON_BOOTS = registerBoots(
                "standard_iron_boots",
                TransitionalArmorConstants.STANDARD_IRON_BOOTS_DURABILITY,
                TransitionalArmorConstants.STANDARD_IRON_BOOTS_RARITY
        );
        CUSHION_HIKING_BOOTS = registerBoots(
                "cushion_hiking_boots",
                TransitionalArmorConstants.CUSHION_HIKING_BOOTS_DURABILITY,
                TransitionalArmorConstants.CUSHION_HIKING_BOOTS_RARITY
        );

        LOGGER.info("[MoonTrace|TransArmor|BOOT] action=register result=OK helmets=3 chestplates=3 leggings=3 boots=3");
    }

    private static Item registerHelmet(
            String name,
            int durability,
            Rarity rarity) {
        int defaultColor = ArmorColorPalette.colorFor(name);
        Item.Settings settings = new Item.Settings()
                .maxDamage(durability)
                .rarity(rarity)
                .fireproof();

        RegistryEntry<ArmorMaterial> material = MMArmorMaterials.transitionalByItemId(name);
        Item helmet = new DefaultDyedLeatherArmorItem(material, ArmorItem.Type.HELMET, settings, defaultColor);
        Registry.register(Registries.ITEM, Identifier.of(MOD_ID, name), helmet);
        LOGGER.info("[MoonTrace|TransArmor|BOOT] action=register result=OK item={}", name);
        return helmet;
    }

    private static Item registerChestplate(
            String name,
            int durability,
            Rarity rarity) {
        int defaultColor = ArmorColorPalette.colorFor(name);
        Item.Settings settings = new Item.Settings()
                .maxDamage(durability)
                .rarity(rarity)
                .fireproof();

        RegistryEntry<ArmorMaterial> material = MMArmorMaterials.transitionalByItemId(name);
        Item chestplate = new DefaultDyedLeatherArmorItem(material, ArmorItem.Type.CHESTPLATE, settings, defaultColor);
        Registry.register(Registries.ITEM, Identifier.of(MOD_ID, name), chestplate);
        LOGGER.info("[MoonTrace|TransArmor|BOOT] action=register result=OK item={}", name);
        return chestplate;
    }

    private static Item registerLeggings(
            String name,
            int durability,
            Rarity rarity) {
        int defaultColor = ArmorColorPalette.colorFor(name);
        Item.Settings settings = new Item.Settings()
                .maxDamage(durability)
                .rarity(rarity)
                .fireproof();

        RegistryEntry<ArmorMaterial> material = MMArmorMaterials.transitionalByItemId(name);
        Item leggings = new DefaultDyedLeatherArmorItem(material, ArmorItem.Type.LEGGINGS, settings, defaultColor);
        Registry.register(Registries.ITEM, Identifier.of(MOD_ID, name), leggings);
        LOGGER.info("[MoonTrace|TransArmor|BOOT] action=register result=OK item={}", name);
        return leggings;
    }

    private static Item registerBoots(
            String name,
            int durability,
            Rarity rarity) {
        int defaultColor = ArmorColorPalette.colorFor(name);
        Item.Settings settings = new Item.Settings()
                .maxDamage(durability)
                .rarity(rarity)
                .fireproof();

        RegistryEntry<ArmorMaterial> material = MMArmorMaterials.transitionalByItemId(name);
        Item boots = new DefaultDyedLeatherArmorItem(material, ArmorItem.Type.BOOTS, settings, defaultColor);
        Registry.register(Registries.ITEM, Identifier.of(MOD_ID, name), boots);
        LOGGER.info("[MoonTrace|TransArmor|BOOT] action=register result=OK item={}", name);
        return boots;
    }
}
