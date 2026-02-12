package dev.xqanzd.moonlitbroker.mixin;

import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.village.Merchant;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * AUDIT FIX: Accessor to get merchant from MerchantScreenHandler for identity cross-check.
 */
@Mixin(MerchantScreenHandler.class)
public interface MerchantScreenHandlerAccessor {
    @Accessor("merchant")
    Merchant getMerchant();
}
