package mod.test.mymodtest.armor.item;

import mod.test.mymodtest.armor.ArmorConfig;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.recipe.Ingredient;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 神秘商人盔甲材质
 * 所有 5 件头盔共用此材质
 */
public class MerchantArmorMaterial {

    public static final String MOD_ID = "mymodtest";

    /** 注册的盔甲材质 */
    public static RegistryEntry<ArmorMaterial> MERCHANT_ARMOR;

    /**
     * 注册盔甲材质
     * 必须在物品注册前调用
     */
    public static void register() {
        // 防护值映射（头、胸、腿、靴）
        Map<ArmorItem.Type, Integer> defenseMap = Map.of(
                ArmorItem.Type.HELMET, ArmorConfig.PROTECTION,
                ArmorItem.Type.CHESTPLATE, 0,  // 暂不实现
                ArmorItem.Type.LEGGINGS, 0,    // 暂不实现
                ArmorItem.Type.BOOTS, 0        // 暂不实现
        );

        // 创建盔甲材质
        ArmorMaterial material = new ArmorMaterial(
                defenseMap,
                ArmorConfig.ENCHANTABILITY,
                SoundEvents.ITEM_ARMOR_EQUIP_CHAIN,
                () -> Ingredient.EMPTY,  // 不可修复
                List.of(new ArmorMaterial.Layer(Identifier.of(MOD_ID, "merchant"))),
                ArmorConfig.TOUGHNESS,
                ArmorConfig.KNOCKBACK_RESISTANCE
        );

        // 注册到 Registry
        MERCHANT_ARMOR = Registry.registerReference(
                Registries.ARMOR_MATERIAL,
                Identifier.of(MOD_ID, "merchant"),
                material
        );
    }
}
