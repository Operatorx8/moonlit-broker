package mod.test.mymodtest;

import mod.test.mymodtest.registry.ModEntities;
import net.fabricmc.api.ModInitializer;

public class Mymodtest implements ModInitializer {

    @Override
    public void onInitialize() {
        ModEntities.register();
    }
}
