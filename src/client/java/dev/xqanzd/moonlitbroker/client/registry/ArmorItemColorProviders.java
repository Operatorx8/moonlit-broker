package dev.xqanzd.moonlitbroker.client.registry;

import dev.xqanzd.moonlitbroker.Mymodtest;
import dev.xqanzd.moonlitbroker.armor.item.DefaultDyedLeatherArmorItem;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemConvertible;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Registers item tint providers for all custom leather-like armor items.
 */
public final class ArmorItemColorProviders {

    private static final Logger LOGGER = LoggerFactory.getLogger("MoonTrace");
    private static final int VANILLA_LEATHER_FALLBACK_COLOR = 0xFFA06540;
    private static boolean registered;

    private ArmorItemColorProviders() {}

    public static void register() {
        if (registered) {
            return;
        }
        List<ItemConvertible> targets = new ArrayList<>();

        for (Item item : Registries.ITEM) {
            if (!(item instanceof DefaultDyedLeatherArmorItem)) {
                continue;
            }
            Identifier id = Registries.ITEM.getId(item);
            if (!Mymodtest.MOD_ID.equals(id.getNamespace())) {
                continue;
            }
            targets.add(item);
        }

        if (targets.isEmpty()) {
            LOGGER.warn("[MoonTrace|Armor|Dye] action=client_color_provider result=SKIP reason=no_targets");
            return;
        }

        ColorProviderRegistry.ITEM.register(
                (stack, tintIndex) -> tintIndex > 0
                        ? -1
                        : DyedColorComponent.getColor(stack, VANILLA_LEATHER_FALLBACK_COLOR),
                targets.toArray(ItemConvertible[]::new)
        );
        registered = true;

        LOGGER.info("[MoonTrace|Armor|Dye] action=client_color_provider result=OK targets={}", targets.size());
    }
}
