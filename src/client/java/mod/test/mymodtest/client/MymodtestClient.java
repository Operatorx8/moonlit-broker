package mod.test.mymodtest.client;

import mod.test.mymodtest.registry.ModEntities;
import mod.test.mymodtest.registry.ModTooltips;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.entity.WanderingTraderEntityRenderer;

public class MymodtestClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ModTooltips.init();
        EntityRendererRegistry.register(
                ModEntities.MYSTERIOUS_MERCHANT,
                WanderingTraderEntityRenderer::new
        );
    }
}
