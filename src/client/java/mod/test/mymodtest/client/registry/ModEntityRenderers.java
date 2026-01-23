package mod.test.mymodtest.client.registry;

import mod.test.mymodtest.registry.ModEntities;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.entity.WanderingTraderEntityRenderer;

public final class ModEntityRenderers {

    public static void register() {
        EntityRendererRegistry.register(ModEntities.MYSTERIOUS_MERCHANT, WanderingTraderEntityRenderer::new);
    }

    private ModEntityRenderers() {}
}
