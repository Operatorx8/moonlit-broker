package dev.xqanzd.moonlitbroker.katana.item;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KatanaItems {
    private static final Logger LOGGER = LoggerFactory.getLogger("Katana");

    private static final String MOD_ID = "xqanzd_moonlit_broker";
    public static final int KATANA_MAX_DURABILITY = 1765;

    public static final MoonGlowKatanaItem MOON_GLOW_KATANA = register(
        "moon_glow_katana",
        new MoonGlowKatanaItem(MoonGlowKatanaItem.createSettings())
    );

    public static final RegretBladeItem REGRET_BLADE = register(
        "regret_blade",
        new RegretBladeItem(RegretBladeItem.createSettings())
    );

    public static final EclipseBladeItem ECLIPSE_BLADE = register(
        "eclipse_blade",
        new EclipseBladeItem(EclipseBladeItem.createSettings())
    );

    public static final OblivionEdgeItem OBLIVION_EDGE = register(
        "oblivion_edge",
        new OblivionEdgeItem(OblivionEdgeItem.createSettings())
    );

    public static final NmapKatanaItem NMAP_KATANA = register(
        "nmap_katana",
        new NmapKatanaItem(NmapKatanaItem.createSettings())
    );

    private static <T extends Item> T register(String name, T item) {
        return Registry.register(Registries.ITEM, Identifier.of(MOD_ID, name), item);
    }

    public static void register() {
        LOGGER.info("[Katana] Registering items...");

        // 添加到战斗物品组
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> {
            entries.add(MOON_GLOW_KATANA);
            entries.add(REGRET_BLADE);
            entries.add(ECLIPSE_BLADE);
            entries.add(OBLIVION_EDGE);
            entries.add(NMAP_KATANA);
        });

        LOGGER.info("[Katana] Items registered: moon_glow_katana, regret_blade, eclipse_blade, oblivion_edge, nmap_katana");
    }

    public static boolean isKatana(ItemStack stack) {
        Item item = stack.getItem();
        return item == MOON_GLOW_KATANA
            || item == REGRET_BLADE
            || item == ECLIPSE_BLADE
            || item == OBLIVION_EDGE
            || item == NMAP_KATANA;
    }
}
