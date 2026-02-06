package mod.test.mymodtest.armor.transitional;

import mod.test.mymodtest.armor.transitional.effect.CargoPantsHandler;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 过渡护甲子系统初始化入口
 */
public final class TransitionalArmorInit {

    private static final Logger LOGGER = LoggerFactory.getLogger("MoonTrace");

    private TransitionalArmorInit() {}

    public static void init() {
        LOGGER.info("[MoonTrace|TransArmor|BOOT] action=init_start");

        // 注册物品
        TransitionalArmorItems.register();

        // Cargo Pants CD 数据清理（防止长时间堆积）
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long nowTick = server.getTicks();
            if (nowTick % 600 == 0) {
                CargoPantsHandler.cleanupExpired(nowTick);
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                CargoPantsHandler.cleanupPlayer(handler.getPlayer().getUuid()));

        LOGGER.info("[MoonTrace|TransArmor|BOOT] action=init_complete result=OK items=6");
    }
}
