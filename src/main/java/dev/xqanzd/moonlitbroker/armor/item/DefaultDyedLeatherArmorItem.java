package dev.xqanzd.moonlitbroker.armor.item;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.registry.entry.RegistryEntry;

/**
 * Armor item backed by leather-style dye layers with a per-item default color.
 * The default color is stored in item default components so every new stack
 * (trade output, loot, command, creative tab) starts colored consistently.
 */
public class DefaultDyedLeatherArmorItem extends ArmorItem {
    public DefaultDyedLeatherArmorItem(
            RegistryEntry<ArmorMaterial> material,
            Type type,
            Settings settings,
            int defaultColor) {
        super(material, type, settings.component(
                DataComponentTypes.DYED_COLOR,
                new DyedColorComponent(defaultColor, true)
        ));
    }
}
