package mod.test.mymodtest;

import mod.test.mymodtest.entity.spawn.MysteriousMerchantSpawner;
import mod.test.mymodtest.registry.ModEntities;
import mod.test.mymodtest.registry.ModItems;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class Mymodtest implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(Mymodtest.class);

    // Phase 4: 每个世界维度一个生成器实例
    private static final Map<ServerWorld, MysteriousMerchantSpawner> spawners = new HashMap<>();

    @Override
    public void onInitialize() {
        ModItems.register();
        ModEntities.register();

        // Phase 4: 注册世界 tick 事件，用于自然生成
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            // 只在主世界生成商人
            if (world.getRegistryKey() == World.OVERWORLD) {
                MysteriousMerchantSpawner spawner = spawners.computeIfAbsent(
                        world,
                        w -> new MysteriousMerchantSpawner()
                );
                spawner.trySpawn(world);
            }
        });

        LOGGER.debug("[Mymodtest] 模组已加载，神秘商人生成器已注册");
    }
}
