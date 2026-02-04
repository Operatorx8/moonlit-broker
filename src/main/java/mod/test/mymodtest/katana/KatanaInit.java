package mod.test.mymodtest.katana;

import mod.test.mymodtest.katana.effect.EclipseHandler;
import mod.test.mymodtest.katana.effect.EclipseManager;
import mod.test.mymodtest.katana.effect.LifeCutHandler;
import mod.test.mymodtest.katana.effect.MoonTraceHandler;
import mod.test.mymodtest.katana.effect.MoonTraceManager;
import mod.test.mymodtest.katana.effect.OblivionHandler;
import mod.test.mymodtest.katana.effect.OblivionManager;
import mod.test.mymodtest.katana.effect.nmap.NmapAttackHandler;
import mod.test.mymodtest.katana.effect.nmap.NmapManager;
import mod.test.mymodtest.katana.effect.nmap.NmapScanHandler;
import mod.test.mymodtest.katana.item.KatanaItems;
import mod.test.mymodtest.katana.sound.ModSounds;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Katana 子系统初始化入口
 * 负责注册所有太刀物品、效果处理器和 tick 事件
 */
public class KatanaInit {
    private static final Logger LOGGER = LoggerFactory.getLogger("Katana");

    public static void init() {
        LOGGER.info("[Katana] Initializing katana subsystem...");

        // 注册物品
        KatanaItems.register();

        // 注册攻击事件处理
        MoonTraceHandler.register();
        LifeCutHandler.register();
        EclipseHandler.register();
        OblivionHandler.register();
        NmapScanHandler.register();
        NmapAttackHandler.register();

        // 注册 tick 事件
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            server.getWorlds().forEach(world -> {
                long currentTick = world.getTime();

                // 清理过期的月痕标记
                MoonTraceManager.tickCleanup(currentTick);

                // 清理过期的月蚀标记
                EclipseManager.tickCleanup(currentTick);

                // 清理过期的窃念标记
                OblivionManager.tickCleanup(currentTick);

                // 清理过期的 Nmap 状态
                NmapManager.tickCleanup(currentTick);

                // 清理过期的冷却记录
                MoonTraceHandler.cleanupCooldowns(currentTick);

                // 处理延迟魔法伤害（月之光芒）
                MoonTraceHandler.tickDelayedMagic(world);

                // 处理延迟伤害（残念之刃）
                LifeCutHandler.tickDelayedDamage(world);

                // 清理过期的 LifeCut 冷却记录
                LifeCutHandler.cleanupCooldowns(currentTick);

                // 检查并刷新 Speed buff
                MoonTraceHandler.tickSpeedBuff(world);
            });
        });

        // 初始化音效
        ModSounds.init();

        LOGGER.info("[Katana] Katana subsystem initialized!");
    }
}
