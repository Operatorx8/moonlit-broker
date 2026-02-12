package dev.xqanzd.moonlitbroker.client.registry;

import dev.xqanzd.moonlitbroker.client.render.MysteriousMerchantRenderer;
import dev.xqanzd.moonlitbroker.registry.ModEntities;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public final class ModEntityRenderers {

    public static void register() {
        // Phase 8: 所有 5 种商人变体使用同一个自定义渲染器（按 EntityType 选材质）
        EntityRendererRegistry.register(ModEntities.MYSTERIOUS_MERCHANT, MysteriousMerchantRenderer::new);
        EntityRendererRegistry.register(ModEntities.MYSTERIOUS_MERCHANT_ARID, MysteriousMerchantRenderer::new);
        EntityRendererRegistry.register(ModEntities.MYSTERIOUS_MERCHANT_COLD, MysteriousMerchantRenderer::new);
        EntityRendererRegistry.register(ModEntities.MYSTERIOUS_MERCHANT_WET, MysteriousMerchantRenderer::new);
        EntityRendererRegistry.register(ModEntities.MYSTERIOUS_MERCHANT_EXOTIC, MysteriousMerchantRenderer::new);
    }

    private ModEntityRenderers() {}
}
