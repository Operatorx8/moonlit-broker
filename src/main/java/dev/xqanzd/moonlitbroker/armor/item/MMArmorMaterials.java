package dev.xqanzd.moonlitbroker.armor.item;

import dev.xqanzd.moonlitbroker.armor.transitional.TransitionalArmorMaterial;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Rarity;

/**
 * Central entry for custom dyeable armor materials.
 * Keeps dyeability and armor stats decoupled from vanilla LEATHER material constants.
 */
public final class MMArmorMaterials {

    public static RegistryEntry<ArmorMaterial> MM_LEATHERLIKE_A;
    public static RegistryEntry<ArmorMaterial> MM_LEATHERLIKE_B;

    private static boolean registered;

    private MMArmorMaterials() {}

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;

        MerchantArmorMaterial.register();
        BootsArmorMaterial.register();
        TransitionalArmorMaterial.register();

        MM_LEATHERLIKE_A = MerchantArmorMaterial.MERCHANT_UNCOMMON_ARMOR;
        MM_LEATHERLIKE_B = TransitionalArmorMaterial.PATCHWORK_COAT;
    }

    public static RegistryEntry<ArmorMaterial> merchantByRarityAndType(Rarity rarity, ArmorItem.Type type) {
        register();
        return MerchantArmorMaterial.byRarityAndType(rarity, type);
    }

    public static RegistryEntry<ArmorMaterial> bootsByRarityAndProtection(Rarity rarity, int protection) {
        register();
        return BootsArmorMaterial.byRarityAndProtection(rarity, protection);
    }

    public static RegistryEntry<ArmorMaterial> transitionalByItemId(String itemId) {
        register();
        return switch (itemId) {
            case "scavenger_goggles" -> TransitionalArmorMaterial.SCAVENGER_GOGGLES;
            case "cast_iron_sallet" -> TransitionalArmorMaterial.CAST_IRON_SALLET;
            case "sanctified_hood" -> TransitionalArmorMaterial.SANCTIFIED_HOOD;

            case "reactive_bug_plate" -> TransitionalArmorMaterial.REACTIVE_BUG_PLATE;
            case "patchwork_coat" -> TransitionalArmorMaterial.PATCHWORK_COAT;
            case "ritual_robe" -> TransitionalArmorMaterial.RITUAL_ROBE;

            case "wrapped_leggings" -> TransitionalArmorMaterial.WRAPPED_LEGGINGS;
            case "reinforced_greaves" -> TransitionalArmorMaterial.REINFORCED_GREAVES;
            case "cargo_pants" -> TransitionalArmorMaterial.CARGO_PANTS;

            case "penitent_boots" -> TransitionalArmorMaterial.PENITENT_BOOTS;
            case "standard_iron_boots" -> TransitionalArmorMaterial.STANDARD_IRON_BOOTS;
            case "cushion_hiking_boots" -> TransitionalArmorMaterial.CUSHION_HIKING_BOOTS;
            default -> MM_LEATHERLIKE_B != null ? MM_LEATHERLIKE_B : MM_LEATHERLIKE_A;
        };
    }
}
