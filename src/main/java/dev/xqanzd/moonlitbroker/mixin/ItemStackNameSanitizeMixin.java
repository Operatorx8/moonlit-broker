package dev.xqanzd.moonlitbroker.mixin;

import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public class ItemStackNameSanitizeMixin {
    private static final String MOD_ID = "xqanzd_moonlit_broker";
    private static final String ITEM_KEY_PREFIX = "item." + MOD_ID + ".";
    private static final String BLOCK_KEY_PREFIX = "block." + MOD_ID + ".";

    @Inject(method = "getName", at = @At("RETURN"), cancellable = true)
    private void xqanzd_moonlit_broker$sanitizeUntranslatedName(CallbackInfoReturnable<Text> cir) {
        Text resolved = cir.getReturnValue();
        if (resolved == null) {
            return;
        }

        String text = resolved.getString();
        if (!text.startsWith(ITEM_KEY_PREFIX) && !text.startsWith(BLOCK_KEY_PREFIX)) {
            return;
        }

        ItemStack stack = (ItemStack) (Object) this;
        Identifier id = Registries.ITEM.getId(stack.getItem());
        if (id == null || !MOD_ID.equals(id.getNamespace())) {
            return;
        }

        String fallback = xqanzd_moonlit_broker$lastSegment(id.getPath());
        if (!fallback.isEmpty()) {
            cir.setReturnValue(Text.literal(fallback));
        }
    }

    @Unique
    private static String xqanzd_moonlit_broker$lastSegment(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        int slash = path.lastIndexOf('/');
        if (slash >= 0 && slash < path.length() - 1) {
            return path.substring(slash + 1);
        }
        return path;
    }
}
