package dev.xqanzd.moonlitbroker.screen;

import dev.xqanzd.moonlitbroker.Mymodtest;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;

public class ModScreenHandlers {
    public static final ScreenHandlerType<MoonlitMerchantScreenHandler> MOONLIT_MERCHANT =
        Registry.register(
            Registries.SCREEN_HANDLER,
            Identifier.of(Mymodtest.MOD_ID, "moonlit_merchant"),
            new ScreenHandlerType<>(MoonlitMerchantScreenHandler::new, FeatureFlags.VANILLA_FEATURES)
        );

    public static void register() {
        // Static initializer triggers registration
    }
}
