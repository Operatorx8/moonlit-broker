package dev.xqanzd.moonlitbroker;

import dev.xqanzd.moonlitbroker.armor.ArmorInit;
import dev.xqanzd.moonlitbroker.armor.transitional.TransitionalArmorInit;
import dev.xqanzd.moonlitbroker.entity.spawn.MysteriousMerchantSpawner;
import dev.xqanzd.moonlitbroker.katana.KatanaInit;
import dev.xqanzd.moonlitbroker.screen.ModScreenHandlers;
import dev.xqanzd.moonlitbroker.weapon.transitional.TransitionalWeaponInit;
import dev.xqanzd.moonlitbroker.registry.ModBlocks;
import dev.xqanzd.moonlitbroker.registry.ModEntities;
import dev.xqanzd.moonlitbroker.registry.ModItemGroups;
import dev.xqanzd.moonlitbroker.registry.ModItems;
import dev.xqanzd.moonlitbroker.trade.command.BountyContractCommand;
import dev.xqanzd.moonlitbroker.trade.command.BountySubmitCommand;
import dev.xqanzd.moonlitbroker.trade.loot.BountyDropHandler;
import dev.xqanzd.moonlitbroker.trade.loot.BountyProgressHandler;
import dev.xqanzd.moonlitbroker.trade.loot.LootTableInjector;
import dev.xqanzd.moonlitbroker.trade.loot.MobDropHandler;
import dev.xqanzd.moonlitbroker.trade.network.TradeNetworking;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class Mymodtest implements ModInitializer {
    public static final String MOD_ID = "xqanzd_moonlit_broker";
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
        ModScreenHandlers.register();

        // 初始化 Katana 子系统
        KatanaInit.init();

        // 初始化 Armor 子系统
        ArmorInit.init();

        // 初始化过渡武器子系统
        TransitionalWeaponInit.init();

        // 初始化过渡护甲子系统
        TransitionalArmorInit.init();

        // 注册创造模式物品分组
        ModItemGroups.init();

        // Trade System: 注册网络包
        TradeNetworking.registerServer();

        // Trade System: 注册战利品表注入
        LootTableInjector.register();

        // Trade System: 注册怪物掉落处理器
        MobDropHandler.register();

        // Trade System: 注册 Bounty 命令
        BountySubmitCommand.register();
        BountyContractCommand.register();

        // Trade System: 注册悬赏进度处理器
        BountyProgressHandler.register();

        // Trade System: 注册悬赏契约掉落处理器
        BountyDropHandler.register();

        // Phase 4: 注册世界 tick 事件，用于自然生成
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            // 只在主世界生成商人
            if (world.getRegistryKey() == World.OVERWORLD) {
                MysteriousMerchantSpawner spawner = spawners.computeIfAbsent(
                        world,
                        w -> new MysteriousMerchantSpawner());
                spawner.trySpawn(world);
            }
        });

        LOGGER.info("[Mymodtest] Mod initialization complete!");
    }
}
