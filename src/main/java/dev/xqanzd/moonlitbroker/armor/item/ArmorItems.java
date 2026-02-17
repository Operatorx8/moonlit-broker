package dev.xqanzd.moonlitbroker.armor.item;

import dev.xqanzd.moonlitbroker.armor.ArmorConfig;
import dev.xqanzd.moonlitbroker.armor.ArmorSpec;
import dev.xqanzd.moonlitbroker.armor.ArmorSpecs;
import dev.xqanzd.moonlitbroker.armor.BootsEffectConstants;
import dev.xqanzd.moonlitbroker.util.ModLog;
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
 * 盔甲物品注册
 * 数值由自定义材质提供，外观走可染色皮革层；默认颜色见 ArmorColorPalette。
 */
public final class ArmorItems {
    private ArmorItems() {}

    public static final String MOD_ID = "xqanzd_moonlit_broker";
    private static final Logger LOGGER = LoggerFactory.getLogger(ModLog.MOD_TAG);

    // ==================== 头盔物品 ====================

    public static Item SENTINEL_HELMET;
    public static Item SILENT_OATH_HELMET;
    public static Item EXILE_MASK_HELMET;
    public static Item RELIC_CIRCLET_HELMET;
    public static Item RETRACER_ORNAMENT_HELMET;

    // ==================== 胸甲物品 ====================

    public static Item OLD_MARKET_CHESTPLATE;
    public static Item BLOOD_PACT_CHESTPLATE;
    public static Item GHOST_GOD_CHESTPLATE;
    public static Item WINDBREAKER_CHESTPLATE;
    public static Item VOID_DEVOURER_CHESTPLATE;

    // ==================== 护腿物品 ====================

    public static Item SMUGGLER_SHIN_LEGGINGS;
    public static Item SMUGGLER_POUCH_LEGGINGS;
    public static Item GRAZE_GUARD_LEGGINGS;
    public static Item STEALTH_SHIN_LEGGINGS;
    public static Item CLEAR_LEDGER_LEGGINGS;

    // ==================== 靴子物品 ====================

    public static Item UNTRACEABLE_TREADS_BOOTS;
    public static Item BOUNDARY_WALKER_BOOTS;
    public static Item GHOST_STEP_BOOTS;
    public static Item MARCHING_BOOTS;
    public static Item GOSSAMER_BOOTS;

    /**
     * 注册所有盔甲物品
     */
    public static void register() {
        MMArmorMaterials.register();

        // 注册 5 个头盔（自定义材质 + 染色）
        SENTINEL_HELMET = registerHelmet("sentinel_helmet", ArmorConfig.SENTINEL_HELMET_RARITY);
        SILENT_OATH_HELMET = registerHelmet("silent_oath_helmet", ArmorConfig.SILENT_OATH_HELMET_RARITY);
        EXILE_MASK_HELMET = registerHelmet("exile_mask_helmet", ArmorConfig.EXILE_MASK_HELMET_RARITY);
        RELIC_CIRCLET_HELMET = registerHelmet("relic_circlet_helmet", ArmorConfig.RELIC_CIRCLET_HELMET_RARITY);
        RETRACER_ORNAMENT_HELMET = registerHelmet("retracer_ornament_helmet", ArmorConfig.RETRACER_ORNAMENT_HELMET_RARITY);

        // 注册 5 个胸甲（自定义材质 + 染色）
        OLD_MARKET_CHESTPLATE = registerChestplate("old_market_chestplate", ArmorConfig.OLD_MARKET_CHESTPLATE_RARITY);
        BLOOD_PACT_CHESTPLATE = registerChestplate("blood_pact_chestplate", ArmorConfig.BLOOD_PACT_CHESTPLATE_RARITY);
        GHOST_GOD_CHESTPLATE = registerChestplate("ghost_god_chestplate", ArmorConfig.GHOST_GOD_CHESTPLATE_RARITY);
        WINDBREAKER_CHESTPLATE = registerChestplate("windbreaker_chestplate", ArmorConfig.WINDBREAKER_CHESTPLATE_RARITY);
        VOID_DEVOURER_CHESTPLATE = registerChestplate("void_devourer_chestplate", ArmorConfig.VOID_DEVOURER_CHESTPLATE_RARITY);

        // 注册 5 个护腿（自定义材质 + 染色）
        SMUGGLER_SHIN_LEGGINGS = registerLeggings("smuggler_shin_leggings", ArmorConfig.SMUGGLER_SHIN_LEGGINGS_RARITY);
        SMUGGLER_POUCH_LEGGINGS = registerLeggings("smuggler_pouch_leggings", ArmorConfig.SMUGGLER_POUCH_LEGGINGS_RARITY);
        GRAZE_GUARD_LEGGINGS = registerLeggings("graze_guard_leggings", ArmorConfig.GRAZE_GUARD_LEGGINGS_RARITY);
        STEALTH_SHIN_LEGGINGS = registerLeggings("stealth_shin_leggings", ArmorConfig.STEALTH_SHIN_LEGGINGS_RARITY);
        CLEAR_LEDGER_LEGGINGS = registerLeggings("clear_ledger_leggings", ArmorConfig.CLEAR_LEDGER_LEGGINGS_RARITY);

        // 注册 5 个靴子（自定义材质 + 染色）
        UNTRACEABLE_TREADS_BOOTS = registerBoots("untraceable_treads_boots",
                BootsEffectConstants.UNTRACEABLE_TREADS_RARITY,
                BootsEffectConstants.UNTRACEABLE_TREADS_DURABILITY,
                BootsEffectConstants.UNTRACEABLE_TREADS_PROTECTION);
        BOUNDARY_WALKER_BOOTS = registerBoots("boundary_walker_boots",
                BootsEffectConstants.BOUNDARY_WALKER_RARITY,
                BootsEffectConstants.BOUNDARY_WALKER_DURABILITY,
                BootsEffectConstants.BOUNDARY_WALKER_PROTECTION);
        GHOST_STEP_BOOTS = registerBoots("ghost_step_boots",
                BootsEffectConstants.GHOST_STEP_RARITY,
                BootsEffectConstants.GHOST_STEP_DURABILITY,
                BootsEffectConstants.GHOST_STEP_PROTECTION);
        MARCHING_BOOTS = registerBoots("marching_boots",
                BootsEffectConstants.MARCHING_BOOTS_RARITY,
                BootsEffectConstants.MARCHING_BOOTS_DURABILITY,
                BootsEffectConstants.MARCHING_BOOTS_PROTECTION);
        GOSSAMER_BOOTS = registerBoots("gossamer_boots",
                BootsEffectConstants.GOSSAMER_BOOTS_RARITY,
                BootsEffectConstants.GOSSAMER_BOOTS_DURABILITY,
                BootsEffectConstants.GOSSAMER_BOOTS_PROTECTION);

        LOGGER.info(ModLog.armorBootPrefix() + " action=register result=OK helmets=5 chestplates=5 leggings=5 boots=5");
    }

    /**
     * 注册单个头盔
     * 若 ArmorSpecs 中存在该 item 的覆写，则创建专属材质。
     */
    private static Item registerHelmet(String name, Rarity rarity) {
        int defaultColor = ArmorColorPalette.colorFor(name);
        Item.Settings settings = new Item.Settings()
                .maxDamage(ArmorConfig.DURABILITY_BASE * 11)
                .rarity(rarity)
                .fireproof();

        ArmorSpec spec = ArmorSpecs.forItemPath(name);
        RegistryEntry<ArmorMaterial> material;
        if (spec != null && spec.hasAnyOverride()) {
            int protection = spec.protectionOr(ArmorConfig.HELMET_PROTECTION);
            float toughness = spec.toughnessOr(ArmorConfig.HELMET_TOUGHNESS);
            float kbResist = spec.knockbackResistanceOr(ArmorConfig.KNOCKBACK_RESISTANCE);
            material = MerchantArmorMaterial.registerItemSpecificMaterial(
                    name,
                    net.minecraft.item.ArmorMaterials.IRON.value().enchantability(),
                    ArmorItem.Type.HELMET,
                    protection, toughness, kbResist,
                    net.minecraft.recipe.Ingredient.ofItems(net.minecraft.item.Items.IRON_INGOT)
            );
            LOGGER.info(ModLog.armorBootPrefix()
                            + " action=register_override item={} toughness={} protection={} kbResist={}",
                    name, toughness, protection, kbResist);
        } else {
            material = MMArmorMaterials.merchantByRarityAndType(rarity, ArmorItem.Type.HELMET);
        }

        Item helmet = new DefaultDyedLeatherArmorItem(
                material,
                ArmorItem.Type.HELMET,
                settings,
                defaultColor
        );

        Registry.register(Registries.ITEM, Identifier.of(MOD_ID, name), helmet);
        LOGGER.info(ModLog.armorBootPrefix() + " action=register result=OK item={}", name);

        return helmet;
    }

    /**
     * 注册单个胸甲
     * 胸甲耐久 = 25 × 16 = 400
     * 若 ArmorSpecs 中存在该 item 的覆写，则创建专属材质。
     */
    private static Item registerChestplate(String name, Rarity rarity) {
        int defaultColor = ArmorColorPalette.colorFor(name);
        Item.Settings settings = new Item.Settings()
                .maxDamage(ArmorConfig.DURABILITY_BASE * 16)
                .rarity(rarity)
                .fireproof();

        // 查询 ArmorSpecs 覆写
        ArmorSpec spec = ArmorSpecs.forItemPath(name);
        RegistryEntry<ArmorMaterial> material;
        if (spec != null && spec.hasAnyOverride()) {
            // 有覆写：创建该装备的专属材质
            int protection = spec.protectionOr(ArmorConfig.CHESTPLATE_PROTECTION);
            float toughness = spec.toughnessOr(ArmorConfig.CHESTPLATE_TOUGHNESS);
            float kbResist = spec.knockbackResistanceOr(ArmorConfig.KNOCKBACK_RESISTANCE);
            material = MerchantArmorMaterial.registerItemSpecificMaterial(
                    name,
                    net.minecraft.item.ArmorMaterials.IRON.value().enchantability(),
                    ArmorItem.Type.CHESTPLATE,
                    protection,
                    toughness,
                    kbResist,
                    net.minecraft.recipe.Ingredient.ofItems(net.minecraft.item.Items.IRON_INGOT)
            );
            LOGGER.info(ModLog.armorBootPrefix()
                            + " action=register_override item={} toughness={} protection={} kbResist={}",
                    name, toughness, protection, kbResist);
        } else {
            // 无覆写：使用共享的 rarity 材质
            material = MMArmorMaterials.merchantByRarityAndType(rarity, ArmorItem.Type.CHESTPLATE);
        }

        Item chestplate = new DefaultDyedLeatherArmorItem(
                material,
                ArmorItem.Type.CHESTPLATE,
                settings,
                defaultColor
        );

        Registry.register(Registries.ITEM, Identifier.of(MOD_ID, name), chestplate);
        LOGGER.info(ModLog.armorBootPrefix() + " action=register result=OK item={}", name);

        return chestplate;
    }

    /**
     * 注册单个护腿
     * 护腿耐久 = 25 × 15 = 375
     * 若 ArmorSpecs 中存在该 item 的覆写，则创建专属材质。
     */
    private static Item registerLeggings(String name, Rarity rarity) {
        int defaultColor = ArmorColorPalette.colorFor(name);
        Item.Settings settings = new Item.Settings()
                .maxDamage(ArmorConfig.DURABILITY_BASE * 15)
                .rarity(rarity)
                .fireproof();

        ArmorSpec spec = ArmorSpecs.forItemPath(name);
        RegistryEntry<ArmorMaterial> material;
        if (spec != null && spec.hasAnyOverride()) {
            int protection = spec.protectionOr(ArmorConfig.LEGGINGS_PROTECTION);
            float toughness = spec.toughnessOr(ArmorConfig.LEGGINGS_TOUGHNESS);
            float kbResist = spec.knockbackResistanceOr(ArmorConfig.KNOCKBACK_RESISTANCE);
            material = MerchantArmorMaterial.registerItemSpecificMaterial(
                    name,
                    net.minecraft.item.ArmorMaterials.IRON.value().enchantability(),
                    ArmorItem.Type.LEGGINGS,
                    protection, toughness, kbResist,
                    net.minecraft.recipe.Ingredient.ofItems(net.minecraft.item.Items.IRON_INGOT)
            );
            LOGGER.info(ModLog.armorBootPrefix()
                            + " action=register_override item={} toughness={} protection={} kbResist={}",
                    name, toughness, protection, kbResist);
        } else {
            material = MMArmorMaterials.merchantByRarityAndType(rarity, ArmorItem.Type.LEGGINGS);
        }

        Item leggings = new DefaultDyedLeatherArmorItem(
                material,
                ArmorItem.Type.LEGGINGS,
                settings,
                defaultColor
        );

        Registry.register(Registries.ITEM, Identifier.of(MOD_ID, name), leggings);
        LOGGER.info(ModLog.armorBootPrefix() + " action=register result=OK item={}", name);

        return leggings;
    }

    /**
     * 注册单个靴子
     * 靴子有独立耐久与护甲值（通过自定义材质）
     */
    private static Item registerBoots(String name, Rarity rarity, int durability, int protection) {
        int defaultColor = ArmorColorPalette.colorFor(name);
        Item.Settings settings = new Item.Settings()
                .maxDamage(durability)
                .rarity(rarity)
                .fireproof();

        RegistryEntry<ArmorMaterial> material = MMArmorMaterials.bootsByRarityAndProtection(rarity, protection);
        Item boots = new DefaultDyedLeatherArmorItem(
                material,
                ArmorItem.Type.BOOTS,
                settings,
                defaultColor
        );

        Registry.register(Registries.ITEM, Identifier.of(MOD_ID, name), boots);
        LOGGER.info(ModLog.armorBootPrefix() + " action=register result=OK item={}", name);

        return boots;
    }
}
