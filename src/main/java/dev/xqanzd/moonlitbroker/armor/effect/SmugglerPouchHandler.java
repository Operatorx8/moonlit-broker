package dev.xqanzd.moonlitbroker.armor.effect;

import dev.xqanzd.moonlitbroker.armor.ArmorConfig;
import dev.xqanzd.moonlitbroker.armor.item.ArmorItems;
import dev.xqanzd.moonlitbroker.armor.util.CooldownManager;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 走私者的暗袋 - 暗袋吸附
 *
 * 机制：
 * - 触发源：ServerTick 低频扫描（每 20 ticks）
 * - 吸附半径：6 格
 * - 激活持续：5s（100 ticks）
 * - 冷却：35s（700 ticks）
 * - 限制：仅吸附 ItemEntity，不吸经验球；速度为自然牵引，不瞬移
 * - 激活期内也仅每 20 ticks 拉取一次（非每 tick）
 */
public class SmugglerPouchHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("MoonTrace");

    /** 玩家上次检查 tick（用于触发扫描） */
    private static final Map<UUID, Long> lastCheckTick = new ConcurrentHashMap<>();

    /** 玩家磁吸激活结束 tick */
    private static final Map<UUID, Long> magnetEndTick = new ConcurrentHashMap<>();

    /** 玩家上次拉取 tick（激活期内也受 20t 间隔限制） */
    private static final Map<UUID, Long> lastPullTick = new ConcurrentHashMap<>();

    /**
     * 每 tick 调用（在 ServerTickEvent 中）
     */
    public static void tick(ServerWorld world, long nowTick) {
        for (var player : world.getPlayers()) {
            if (!(player instanceof ServerPlayerEntity serverPlayer)) continue;

            UUID playerId = serverPlayer.getUuid();

            // 检查是否穿戴该护腿
            boolean wearing = isWearing(serverPlayer);
            if (!wearing) {
                // 穿脱护腿清理：立即清理所有状态
                if (lastCheckTick.containsKey(playerId) || magnetEndTick.containsKey(playerId) || lastPullTick.containsKey(playerId)) {
                    lastCheckTick.remove(playerId);
                    magnetEndTick.remove(playerId);
                    lastPullTick.remove(playerId);
                    // 同时清理冷却
                    CooldownManager.clearCooldown(playerId, ArmorConfig.SMUGGLER_POUCH_EFFECT_ID);
                    if (ArmorConfig.DEBUG) {
                        LOGGER.info("[MoonTrace|Armor|STATE] action=state_change result=OK state=smuggler_pouch_removed reason=not_wearing ctx{{p={}}}",
                                serverPlayer.getName().getString());
                    }
                }
                continue;
            }

            // 检查是否在激活状态（持续吸附）
            Long endTick = magnetEndTick.get(playerId);
            if (endTick != null && nowTick <= endTick) {
                // 激活中，但也受 20t 间隔限制
                Long lastPull = lastPullTick.get(playerId);
                if (lastPull == null || nowTick - lastPull >= ArmorConfig.SMUGGLER_POUCH_SCAN_INTERVAL) {
                    pullNearbyItems(serverPlayer, nowTick);
                    lastPullTick.put(playerId, nowTick);

                    if (ArmorConfig.DEBUG) {
                        long remaining = endTick - nowTick;
                        LOGGER.info("[MoonTrace|Armor|STATE] action=state_change result=OK state=magnet_active remaining={} ctx{{p={}}}",
                                remaining, serverPlayer.getName().getString());
                    }
                }

                // 检查是否到期
                if (nowTick >= endTick) {
                    magnetEndTick.remove(playerId);
                    lastPullTick.remove(playerId);
                    LOGGER.info("[MoonTrace|Armor|STATE] action=state_change result=OK state=magnet_expired ctx{{p={}}}",
                            serverPlayer.getName().getString());
                }
                continue;
            }

            // 检查检查间隔
            Long lastTick = lastCheckTick.get(playerId);
            if (lastTick != null && nowTick - lastTick < ArmorConfig.SMUGGLER_POUCH_SCAN_INTERVAL) {
                continue;
            }
            lastCheckTick.put(playerId, nowTick);

            // 检查冷却
            if (!CooldownManager.isReady(playerId, ArmorConfig.SMUGGLER_POUCH_EFFECT_ID, nowTick)) {
                if (ArmorConfig.DEBUG) {
                    long cdLeft = CooldownManager.getRemainingTicks(playerId, ArmorConfig.SMUGGLER_POUCH_EFFECT_ID, nowTick);
                    LOGGER.info("[MoonTrace|Armor|TRIGGER] action=check result=BLOCKED reason=cd_hit cd_total={} cd_left={} ctx{{p={}}}",
                            ArmorConfig.SMUGGLER_POUCH_COOLDOWN, cdLeft, serverPlayer.getName().getString());
                }
                continue;
            }

            // 检查附近是否有可拾取的 ItemEntity
            int itemCount = countNearbyItems(serverPlayer);
            if (itemCount == 0) {
                continue;
            }

            // 触发磁吸激活
            magnetEndTick.put(playerId, nowTick + ArmorConfig.SMUGGLER_POUCH_DURATION);
            lastPullTick.put(playerId, nowTick); // 首次拉取
            CooldownManager.setCooldown(playerId, ArmorConfig.SMUGGLER_POUCH_EFFECT_ID, nowTick, ArmorConfig.SMUGGLER_POUCH_COOLDOWN);

            LOGGER.info("[MoonTrace|Armor|TRIGGER] action=trigger result=OK effect={} duration={} ctx{{p={} items_nearby={}}}",
                    ArmorConfig.SMUGGLER_POUCH_EFFECT_ID, ArmorConfig.SMUGGLER_POUCH_DURATION,
                    serverPlayer.getName().getString(), itemCount);

            // 首次立即拉取
            pullNearbyItems(serverPlayer, nowTick);
        }
    }

    /**
     * 统计附近可拾取的物品数量
     */
    private static int countNearbyItems(ServerPlayerEntity player) {
        Box box = new Box(
                player.getX() - ArmorConfig.SMUGGLER_POUCH_RADIUS,
                player.getY() - ArmorConfig.SMUGGLER_POUCH_RADIUS,
                player.getZ() - ArmorConfig.SMUGGLER_POUCH_RADIUS,
                player.getX() + ArmorConfig.SMUGGLER_POUCH_RADIUS,
                player.getY() + ArmorConfig.SMUGGLER_POUCH_RADIUS,
                player.getZ() + ArmorConfig.SMUGGLER_POUCH_RADIUS
        );

        List<ItemEntity> items = player.getWorld().getEntitiesByClass(ItemEntity.class, box,
                item -> !item.cannotPickup() && item.isAlive());
        return items.size();
    }

    /**
     * 牵引附近的物品
     */
    private static void pullNearbyItems(ServerPlayerEntity player, long nowTick) {
        Box box = new Box(
                player.getX() - ArmorConfig.SMUGGLER_POUCH_RADIUS,
                player.getY() - ArmorConfig.SMUGGLER_POUCH_RADIUS,
                player.getZ() - ArmorConfig.SMUGGLER_POUCH_RADIUS,
                player.getX() + ArmorConfig.SMUGGLER_POUCH_RADIUS,
                player.getY() + ArmorConfig.SMUGGLER_POUCH_RADIUS,
                player.getZ() + ArmorConfig.SMUGGLER_POUCH_RADIUS
        );

        List<ItemEntity> items = player.getWorld().getEntitiesByClass(ItemEntity.class, box,
                item -> !item.cannotPickup() && item.isAlive());

        int pulledCount = 0;
        for (ItemEntity item : items) {
            // 计算方向向量
            Vec3d direction = player.getPos().subtract(item.getPos()).normalize();

            // 设置速度（自然牵引，不瞬移）
            double vx = direction.x * ArmorConfig.SMUGGLER_POUCH_PULL_SPEED;
            double vy = direction.y * ArmorConfig.SMUGGLER_POUCH_PULL_SPEED + ArmorConfig.SMUGGLER_POUCH_PULL_SPEED_Y;
            double vz = direction.z * ArmorConfig.SMUGGLER_POUCH_PULL_SPEED;

            item.setVelocity(vx, vy, vz);
            item.velocityModified = true;
            pulledCount++;
        }

        if (ArmorConfig.DEBUG && pulledCount > 0) {
            LOGGER.info("[MoonTrace|Armor|STATE] action=state_change result=OK state=magnet_pull items_pulled={} ctx{{p={}}}",
                    pulledCount, player.getName().getString());
        }
    }

    private static boolean isWearing(ServerPlayerEntity player) {
        return player.getEquippedStack(EquipmentSlot.LEGS).isOf(ArmorItems.SMUGGLER_POUCH_LEGGINGS);
    }

    /**
     * 玩家下线时清理状态
     */
    public static void onPlayerLogout(ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        lastCheckTick.remove(playerId);
        magnetEndTick.remove(playerId);
        lastPullTick.remove(playerId);
    }

    /**
     * 玩家死亡/重生时清理状态
     */
    public static void onPlayerRespawn(ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        lastCheckTick.remove(playerId);
        magnetEndTick.remove(playerId);
        lastPullTick.remove(playerId);
    }
}
