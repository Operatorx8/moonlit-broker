package mod.test.mymodtest.weapon.transitional.item;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/**
 * 过渡武器物品注册
 */
public final class TransitionalWeaponItems {

    public static final String MOD_ID = "mymodtest";

    // === 物品实例 ===
    public static final AcerItem ACER = new AcerItem();
    public static final VeloxItem VELOX = new VeloxItem();
    public static final FatalisItem FATALIS = new FatalisItem();

    private TransitionalWeaponItems() {}

    /**
     * 注册所有过渡武器物品
     */
    public static void register() {
        registerItem("acer", ACER);
        registerItem("velox", VELOX);
        registerItem("fatalis", FATALIS);

        // 添加到战斗物品组
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> {
            entries.add(ACER);
            entries.add(VELOX);
            entries.add(FATALIS);
        });
    }

    private static <T extends Item> T registerItem(String name, T item) {
        return Registry.register(Registries.ITEM, Identifier.of(MOD_ID, name), item);
    }
}
