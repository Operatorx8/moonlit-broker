package mod.test.mymodtest.armor.item;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dyeable armor item with a per-item default dyed color.
 */
public class DefaultDyedLeatherArmorItem extends ArmorItem {
    private static final Logger LOGGER = LoggerFactory.getLogger("MoonTrace");
    private static final boolean DEBUG_DYE_DEFAULT = false;

    private final int defaultColor;

    public DefaultDyedLeatherArmorItem(
            RegistryEntry<ArmorMaterial> material,
            Type type,
            Settings settings,
            int defaultColor) {
        super(material, type, settings);
        this.defaultColor = defaultColor;
    }

    @Override
    public ItemStack getDefaultStack() {
        ItemStack stack = super.getDefaultStack();
        this.ensureDefaultColor(stack);
        return stack;
    }

    private void ensureDefaultColor(ItemStack stack) {
        if (stack.get(DataComponentTypes.DYED_COLOR) == null) {
            stack.set(DataComponentTypes.DYED_COLOR, new DyedColorComponent(this.defaultColor, true));
            if (DEBUG_DYE_DEFAULT && LOGGER.isDebugEnabled()) {
                LOGGER.debug("[MoonTrace|Armor|Dye] action=default_inject item={} color=#{}, trigger=getDefaultStack",
                        net.minecraft.registry.Registries.ITEM.getId(stack.getItem()),
                        String.format("%06X", this.defaultColor & 0xFFFFFF));
            }
        }
    }
}
