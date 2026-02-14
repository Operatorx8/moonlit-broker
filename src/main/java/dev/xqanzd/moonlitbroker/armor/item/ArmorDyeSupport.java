package dev.xqanzd.moonlitbroker.armor.item;

import net.minecraft.block.cauldron.CauldronBehavior;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Shared registration for dye-related behavior on custom leather-like armor.
 */
public final class ArmorDyeSupport {

    private static final Logger LOGGER = LoggerFactory.getLogger("MoonTrace");

    private ArmorDyeSupport() {}

    public static void registerCauldronCleaningBehavior() {
        Map<Item, CauldronBehavior> waterMap = CauldronBehavior.WATER_CAULDRON_BEHAVIOR.map();
        int registered = 0;

        for (Item item : Registries.ITEM) {
            if (!(item instanceof DefaultDyedLeatherArmorItem)) {
                continue;
            }

            Identifier id = Registries.ITEM.getId(item);
            if (!ArmorItems.MOD_ID.equals(id.getNamespace())) {
                continue;
            }

            waterMap.put(item, CauldronBehavior.CLEAN_DYEABLE_ITEM);
            registered++;
        }

        LOGGER.info("[MoonTrace|Armor|Dye] action=cauldron_behavior_bind result=OK targets={}", registered);
    }
}
