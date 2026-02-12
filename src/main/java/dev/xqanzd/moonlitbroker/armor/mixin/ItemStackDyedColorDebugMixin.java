package dev.xqanzd.moonlitbroker.armor.mixin;

import net.minecraft.component.ComponentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Objects;

/**
 * Minimal debug logging for dye/clean actions on this mod's armor items.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackDyedColorDebugMixin {
    @Unique
    private static final Logger MYMODTEST$LOGGER = LoggerFactory.getLogger("MoonTrace");

    @Inject(method = "set", at = @At("HEAD"))
    private void xqanzd_moonlit_broker$logDyedColorSet(ComponentType<?> type, Object value, CallbackInfoReturnable<Object> cir) {
        if (type != DataComponentTypes.DYED_COLOR) {
            return;
        }

        ItemStack stack = (ItemStack) (Object) this;
        Identifier itemId = Registries.ITEM.getId(stack.getItem());
        if (!"xqanzd_moonlit_broker".equals(itemId.getNamespace())) {
            return;
        }

        DyedColorComponent oldColor = stack.get(DataComponentTypes.DYED_COLOR);
        DyedColorComponent newColor = value instanceof DyedColorComponent dyed ? dyed : null;
        if (Objects.equals(oldColor, newColor)) {
            return;
        }

        MYMODTEST$LOGGER.debug(
                "[MoonTrace|Armor|Dye] action=set item={} old={} new={}",
                itemId,
                xqanzd_moonlit_broker$formatColor(oldColor),
                xqanzd_moonlit_broker$formatColor(newColor)
        );
    }

    @Inject(method = "remove", at = @At("HEAD"))
    private void xqanzd_moonlit_broker$logDyedColorRemove(ComponentType<?> type, CallbackInfoReturnable<Object> cir) {
        if (type != DataComponentTypes.DYED_COLOR) {
            return;
        }

        ItemStack stack = (ItemStack) (Object) this;
        Identifier itemId = Registries.ITEM.getId(stack.getItem());
        if (!"xqanzd_moonlit_broker".equals(itemId.getNamespace())) {
            return;
        }

        DyedColorComponent oldColor = stack.get(DataComponentTypes.DYED_COLOR);
        if (oldColor == null) {
            return;
        }

        MYMODTEST$LOGGER.debug(
                "[MoonTrace|Armor|Dye] action=remove item={} old={} new=none",
                itemId,
                xqanzd_moonlit_broker$formatColor(oldColor)
        );
    }

    @Unique
    private static String xqanzd_moonlit_broker$formatColor(DyedColorComponent component) {
        if (component == null) {
            return "none";
        }
        return String.format("#%06X", component.rgb() & 0xFFFFFF);
    }
}
