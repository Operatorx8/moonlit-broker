package mod.test.mymodtest.client;

import mod.test.mymodtest.registry.ModEntities;
import mod.test.mymodtest.registry.ModTooltips;
import mod.test.mymodtest.client.render.MysteriousMerchantRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public class MymodtestClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ModTooltips.init();
        // Phase 8: 注册所有 5 种商人变体的渲染器
        EntityRendererRegistry.register(ModEntities.MYSTERIOUS_MERCHANT, MysteriousMerchantRenderer::new);
        EntityRendererRegistry.register(ModEntities.MYSTERIOUS_MERCHANT_ARID, MysteriousMerchantRenderer::new);
        EntityRendererRegistry.register(ModEntities.MYSTERIOUS_MERCHANT_COLD, MysteriousMerchantRenderer::new);
        EntityRendererRegistry.register(ModEntities.MYSTERIOUS_MERCHANT_WET, MysteriousMerchantRenderer::new);
        EntityRendererRegistry.register(ModEntities.MYSTERIOUS_MERCHANT_EXOTIC, MysteriousMerchantRenderer::new);
    }
}
