package mod.test.mymodtest.armor.item;

import mod.test.mymodtest.armor.ArmorConfig;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
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

    /**
     * 注册所有盔甲物品
     */
    public static void register() {
        // 先注册材质
        MerchantArmorMaterial.register();

        // 通用物品设置
        Item.Settings baseSettings = new Item.Settings()
                .maxDamage(ArmorConfig.DURABILITY_BASE * 11)  // 头盔耐久 = 25 × 11 = 275
                .rarity(Rarity.EPIC)
                .fireproof();

        // 注册 5 个头盔
        SENTINEL_HELMET = registerHelmet("sentinel_helmet", baseSettings);
        SILENT_OATH_HELMET = registerHelmet("silent_oath_helmet", baseSettings);
        EXILE_MASK_HELMET = registerHelmet("exile_mask_helmet", baseSettings);
        RELIC_CIRCLET_HELMET = registerHelmet("relic_circlet_helmet", baseSettings);
        RETRACER_ORNAMENT_HELMET = registerHelmet("retracer_ornament_helmet", baseSettings);

        LOGGER.info("[MoonTrace|Armor|BOOT] action=register result=OK helmets_loaded=5");
    }

    /**
     * 注册单个头盔
     */
    private static Item registerHelmet(String name, Item.Settings baseSettings) {
        // 克隆设置以避免共享状态问题
        Item.Settings settings = new Item.Settings()
                .maxDamage(ArmorConfig.DURABILITY_BASE * 11)
                .rarity(Rarity.EPIC)
                .fireproof();

        Item helmet = new ArmorItem(
                MerchantArmorMaterial.MERCHANT_ARMOR,
                ArmorItem.Type.HELMET,
                settings
        );

        Registry.register(Registries.ITEM, Identifier.of(MOD_ID, name), helmet);
        LOGGER.info("[MoonTrace|Armor|BOOT] action=register result=OK item={}", name);

        return helmet;
    }
}
