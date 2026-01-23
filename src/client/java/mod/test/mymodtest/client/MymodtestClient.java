package mod.test.mymodtest.client;

import mod.test.mymodtest.client.registry.ModEntityRenderers;
import net.fabricmc.api.ClientModInitializer;

public class MymodtestClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ModEntityRenderers.register();
    }
}
