package dev.xqanzd.moonlitbroker.weapon.transitional;

import dev.xqanzd.moonlitbroker.weapon.transitional.item.TransitionalWeaponItems;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 过渡武器子系统初始化入口
 */
public final class TransitionalWeaponInit {

    private static final Logger LOGGER = LoggerFactory.getLogger("MoonTrace");

    private TransitionalWeaponInit() {}

    public static void init() {
        LOGGER.info("[MoonTrace|TransWeapon|BOOT] action=init_start");

        // 注册物品
        TransitionalWeaponItems.register();

        LOGGER.info("[MoonTrace|TransWeapon|BOOT] action=init_complete result=OK items=3");
    }
}
