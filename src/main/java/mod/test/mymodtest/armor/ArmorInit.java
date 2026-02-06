package mod.test.mymodtest.armor;

import mod.test.mymodtest.armor.effect.BloodPactHandler;
import mod.test.mymodtest.armor.effect.ClearLedgerHandler;
import mod.test.mymodtest.armor.effect.ExileMaskHandler;
import mod.test.mymodtest.armor.effect.OldMarketHandler;
import mod.test.mymodtest.armor.effect.RelicCircletHandler;
import mod.test.mymodtest.armor.effect.SentinelHandler;
import mod.test.mymodtest.armor.effect.SmugglerPouchHandler;
import mod.test.mymodtest.armor.effect.StealthShinHandler;
import mod.test.mymodtest.armor.effect.WindbreakerHandler;
import mod.test.mymodtest.armor.effect.boots.BootsTickHandler;
import mod.test.mymodtest.armor.item.ArmorItems;
import mod.test.mymodtest.armor.util.CooldownManager;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 盔甲子系统初始化入口
 * 负责注册所有盔甲物品、效果处理器和 tick 事件
 */
public class ArmorInit {
    private static final Logger LOGGER = LoggerFactory.getLogger("MoonTrace");

    /** tick 计数器，用于低频清理 */
    private static long tickCounter = 0;

    public static void init() {
        LOGGER.info("[MoonTrace|Armor|BOOT] action=init_start");

        // 注册盔甲物品
        ArmorItems.register();

        // 注册 tick 事件
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            if (!(world instanceof ServerWorld serverWorld)) return;

            MinecraftServer server = serverWorld.getServer();
            long currentTick = world.getTime();

            // ===== 头盔效果 =====
            // 哨兵的最后瞭望 - 黑暗侦测
            SentinelHandler.tick(serverWorld, currentTick);

            // 流亡者的面甲 - 低血增伤
            ExileMaskHandler.tick(serverWorld, currentTick);

            // 遗世之环 - 愤怒检测
            RelicCircletHandler.tick(serverWorld, currentTick);

            // ===== 胸甲效果 =====
            // 商人的防风衣 - 低血速度边沿触发
            WindbreakerHandler.tick(serverWorld, currentTick);

            // ===== 护腿效果（使用 server.getTicks() 统一时钟） =====
            long serverTick = server.getTicks();

            // 走私者的暗袋 - 磁吸掉落物
            SmugglerPouchHandler.tick(serverWorld, serverTick);

            // 潜行之胫 - 充能检测
            StealthShinHandler.tick(serverWorld, serverTick);

            // 清账步态 - 过期检查
            ClearLedgerHandler.tick(serverWorld, serverTick);

            // ===== 靴子效果 =====
            BootsTickHandler.tick(serverWorld, serverTick);

            // 低频清理（每 600 ticks = 30秒）
            tickCounter++;
            if (tickCounter % 600 == 0) {
                CooldownManager.cleanupExpired(currentTick);
                RelicCircletHandler.cleanupDeadMobs(serverWorld);
            }
        });

        // 玩家下线时清理状态
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            var player = handler.getPlayer();
            CooldownManager.clearAllCooldowns(player.getUuid());
            // 头盔效果清理
            ExileMaskHandler.onPlayerLogout(player);
            RelicCircletHandler.onPlayerLogout(player);
            // 胸甲效果清理
            BloodPactHandler.onPlayerLogout(player);
            WindbreakerHandler.onPlayerLogout(player);
            OldMarketHandler.clearPlayerTrades(player.getUuid());
            // 护腿效果清理
            SmugglerPouchHandler.onPlayerLogout(player);
            StealthShinHandler.onPlayerLogout(player);
            ClearLedgerHandler.onPlayerLogout(player);
            // 靴子效果清理
            BootsTickHandler.onPlayerLogout(player);
        });

        // 玩家重生时清理状态（死亡后冷却重置）
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            // 清除冷却
            CooldownManager.clearAllCooldowns(newPlayer.getUuid());

            // 护腿效果 respawn 清理
            SmugglerPouchHandler.onPlayerRespawn(newPlayer);
            StealthShinHandler.onPlayerRespawn(newPlayer);
            ClearLedgerHandler.onPlayerRespawn(newPlayer);
            // 靴子效果 respawn 清理
            BootsTickHandler.onPlayerRespawn(newPlayer);

            if (ArmorConfig.DEBUG) {
                LOGGER.info("[MoonTrace|Armor|STATE] action=player_respawn result=OK ctx{{p={}}}",
                        newPlayer.getName().getString());
            }
        });

        LOGGER.info("[MoonTrace|Armor|BOOT] action=init_complete result=OK debug={}", ArmorConfig.DEBUG);
    }
}
