package mod.test.mymodtest;

import mod.test.mymodtest.armor.ArmorInit;
import mod.test.mymodtest.entity.spawn.MysteriousMerchantSpawner;
import mod.test.mymodtest.katana.KatanaInit;
import mod.test.mymodtest.registry.ModBlocks;
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
    public static final String MOD_ID = "mymodtest";
    private static final Logger LOGGER = LoggerFactory.getLogger(Mymodtest.class);

    // Phase 4: 每个世界维度一个生成器实例
    private static final Map<ServerWorld, MysteriousMerchantSpawner> spawners = new HashMap<>();

    @Override
    public void onInitialize() {
        LOGGER.info("[Mymodtest] Initializing Mysterious Merchant mod...");

        // 注册商人系统
        ModItems.register();
        ModBlocks.register();
        ModEntities.register();

        // 初始化 Katana 子系统
        KatanaInit.init();

        // 初始化 Armor 子系统
        ArmorInit.init();

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

        LOGGER.info("[Mymodtest] Mod initialization complete!");
    }
}
