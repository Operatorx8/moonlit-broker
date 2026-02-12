package dev.xqanzd.moonlitbroker.mixin;

import dev.xqanzd.moonlitbroker.entity.MysteriousMerchantEntity;
import dev.xqanzd.moonlitbroker.trade.network.TradeActionHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.village.Merchant;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * AUDIT FIX: Wire session cleanup when merchant UI is closed.
 * Calls TradeActionHandler.onSessionClosed to clear page/cooldown state.
 */
@Mixin(MerchantScreenHandler.class)
public class MerchantScreenHandlerMixin {

    @Shadow
    @Final
    private Merchant merchant;

    @Inject(method = "onClosed", at = @At("HEAD"))
    private void onClosedInject(PlayerEntity player, CallbackInfo ci) {
        if (player instanceof ServerPlayerEntity serverPlayer 
                && this.merchant instanceof MysteriousMerchantEntity merchantEntity) {
            TradeActionHandler.onSessionClosed(serverPlayer, merchantEntity);
        }
    }
}
