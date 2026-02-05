package mod.test.mymodtest.armor.item;

import mod.test.mymodtest.armor.ArmorConfig;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 盔甲物品注册
 * 附魔系数按稀有度分档：
 * UNCOMMON -> IRON (9), RARE -> CHAIN (12), EPIC -> NETHERITE (15)
 */
public final class ArmorItems {
    private ArmorItems() {}

    public static final String MOD_ID = "mymodtest";
    private static final Logger LOGGER = LoggerFactory.getLogger("MoonTrace");

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

    /**
     * 注册所有盔甲物品
     */
    public static void register() {
        // 先注册材质
        MerchantArmorMaterial.register();

        // 注册 5 个头盔（按稀有度映射附魔系数）
        SENTINEL_HELMET = registerHelmet("sentinel_helmet", ArmorConfig.SENTINEL_HELMET_RARITY);
        SILENT_OATH_HELMET = registerHelmet("silent_oath_helmet", ArmorConfig.SILENT_OATH_HELMET_RARITY);
        EXILE_MASK_HELMET = registerHelmet("exile_mask_helmet", ArmorConfig.EXILE_MASK_HELMET_RARITY);
        RELIC_CIRCLET_HELMET = registerHelmet("relic_circlet_helmet", ArmorConfig.RELIC_CIRCLET_HELMET_RARITY);
        RETRACER_ORNAMENT_HELMET = registerHelmet("retracer_ornament_helmet", ArmorConfig.RETRACER_ORNAMENT_HELMET_RARITY);

        // 注册 5 个胸甲（按稀有度映射附魔系数）
        OLD_MARKET_CHESTPLATE = registerChestplate("old_market_chestplate", ArmorConfig.OLD_MARKET_CHESTPLATE_RARITY);
        BLOOD_PACT_CHESTPLATE = registerChestplate("blood_pact_chestplate", ArmorConfig.BLOOD_PACT_CHESTPLATE_RARITY);
        GHOST_GOD_CHESTPLATE = registerChestplate("ghost_god_chestplate", ArmorConfig.GHOST_GOD_CHESTPLATE_RARITY);
        WINDBREAKER_CHESTPLATE = registerChestplate("windbreaker_chestplate", ArmorConfig.WINDBREAKER_CHESTPLATE_RARITY);
        VOID_DEVOURER_CHESTPLATE = registerChestplate("void_devourer_chestplate", ArmorConfig.VOID_DEVOURER_CHESTPLATE_RARITY);

        LOGGER.info("[MoonTrace|Armor|BOOT] action=register result=OK helmets_loaded=5 chestplates_loaded=5");
    }

    /**
     * 注册单个头盔
     */
    private static Item registerHelmet(String name, Rarity rarity) {
        Item.Settings settings = new Item.Settings()
                .maxDamage(ArmorConfig.DURABILITY_BASE * 11)
                .rarity(rarity)
                .fireproof();

        Item helmet = new ArmorItem(
                MerchantArmorMaterial.byRarity(rarity),
                ArmorItem.Type.HELMET,
                settings
        );

        Registry.register(Registries.ITEM, Identifier.of(MOD_ID, name), helmet);
        LOGGER.info("[MoonTrace|Armor|BOOT] action=register result=OK item={}", name);

        return helmet;
    }

    /**
     * 注册单个胸甲
     * 胸甲耐久 = 25 × 16 = 400
     */
    private static Item registerChestplate(String name, Rarity rarity) {
        Item.Settings settings = new Item.Settings()
                .maxDamage(ArmorConfig.DURABILITY_BASE * 16)
                .rarity(rarity)
                .fireproof();

        Item chestplate = new ArmorItem(
                MerchantArmorMaterial.byRarity(rarity),
                ArmorItem.Type.CHESTPLATE,
                settings
        );

        Registry.register(Registries.ITEM, Identifier.of(MOD_ID, name), chestplate);
        LOGGER.info("[MoonTrace|Armor|BOOT] action=register result=OK item={}", name);

        return chestplate;
    }
}
