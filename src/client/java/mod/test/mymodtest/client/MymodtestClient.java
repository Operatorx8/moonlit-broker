package mod.test.mymodtest.client;

import mod.test.mymodtest.registry.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.entity.WanderingTraderEntityRenderer;

public class MymodtestClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(
                ModEntities.MYSTERIOUS_MERCHANT,
                WanderingTraderEntityRenderer::new
        );
    }
}
