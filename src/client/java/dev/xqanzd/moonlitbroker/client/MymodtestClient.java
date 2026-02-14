package dev.xqanzd.moonlitbroker.client;

import dev.xqanzd.moonlitbroker.client.screen.MoonlitMerchantScreen;
import dev.xqanzd.moonlitbroker.registry.ModEntities;
import dev.xqanzd.moonlitbroker.registry.ModTooltips;
import dev.xqanzd.moonlitbroker.client.render.MysteriousMerchantRenderer;
import dev.xqanzd.moonlitbroker.client.registry.ArmorItemColorProviders;
import dev.xqanzd.moonlitbroker.screen.ModScreenHandlers;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.gui.screen.ingame.HandledScreens;

public class MymodtestClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ModTooltips.init();
        ArmorItemColorProviders.register();
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> ArmorItemColorProviders.register());

        // Book UI v2: 注册自定义商人交易界面
        HandledScreens.register(ModScreenHandlers.MOONLIT_MERCHANT, MoonlitMerchantScreen::new);

        // Phase 8: 注册所有 5 种商人变体的渲染器
        EntityRendererRegistry.register(ModEntities.MYSTERIOUS_MERCHANT, MysteriousMerchantRenderer::new);
        EntityRendererRegistry.register(ModEntities.MYSTERIOUS_MERCHANT_ARID, MysteriousMerchantRenderer::new);
        EntityRendererRegistry.register(ModEntities.MYSTERIOUS_MERCHANT_COLD, MysteriousMerchantRenderer::new);
        EntityRendererRegistry.register(ModEntities.MYSTERIOUS_MERCHANT_WET, MysteriousMerchantRenderer::new);
        EntityRendererRegistry.register(ModEntities.MYSTERIOUS_MERCHANT_EXOTIC, MysteriousMerchantRenderer::new);
    }
}
