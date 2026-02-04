package mod.test.mymodtest.armor.effect;

import mod.test.mymodtest.armor.ArmorConfig;
import mod.test.mymodtest.armor.item.ArmorItems;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 流亡者的面甲 - 低血增伤（实时计算、无叠加 bug）
 *
 * 机制：
 * - 生命值 < 50% 时激活
 * - 每损失 1.5 心 (7.5%) -> 攻击力 +0.5 心 (1.0 damage)
 * - 上限：+2 心 (4.0 damage)
 * - 每 20 ticks 更新一次
 * - 使用固定 UUID 的 AttributeModifier 覆盖式更新
 */
public class ExileMaskHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("MoonTrace");

    /** 玩家上次更新 tick */
    private static final Map<Integer, Long> lastUpdateTick = new ConcurrentHashMap<>();

    /** 玩家当前是否处于低血状态（用于状态变化日志） */
    private static final Map<Integer, Boolean> isActiveState = new ConcurrentHashMap<>();

    /** 玩家当前的增伤值（用于检测变化） */
    private static final Map<Integer, Float> currentBonusMap = new ConcurrentHashMap<>();

    /**
     * 每 tick 调用（在 ServerTickEvent 中）
     */
    public static void tick(ServerWorld world, long currentTick) {
        for (var player : world.getPlayers()) {
            if (!(player instanceof ServerPlayerEntity serverPlayer)) continue;

            // 检查更新间隔
            Long lastTick = lastUpdateTick.get(serverPlayer.getId());
            if (lastTick != null && currentTick - lastTick < ArmorConfig.EXILE_UPDATE_INTERVAL) {
                continue;
            }
            lastUpdateTick.put(serverPlayer.getId(), currentTick);

            // 检查是否穿戴该头盔
            boolean wearing = isWearing(serverPlayer);
            boolean wasActive = isActiveState.getOrDefault(serverPlayer.getId(), false);

            if (!wearing) {
                // 脱下头盔，移除增伤
                if (wasActive) {
                    removeModifier(serverPlayer);
                    isActiveState.put(serverPlayer.getId(), false);
                    currentBonusMap.remove(serverPlayer.getId());

                    LOGGER.info("[MoonTrace|Armor|STATE] action=state_change result=OK state=exile_mask_active enabled=false reason=not_wearing ctx{{p={}}}",
                            serverPlayer.getName().getString());
                }
                continue;
            }

            // 计算血量百分比
            float healthPercent = serverPlayer.getHealth() / serverPlayer.getMaxHealth();

            if (healthPercent >= ArmorConfig.EXILE_HEALTH_THRESHOLD) {
                // 血量 >= 50%，移除增伤
                if (wasActive) {
                    removeModifier(serverPlayer);
                    isActiveState.put(serverPlayer.getId(), false);
                    currentBonusMap.remove(serverPlayer.getId());

                    LOGGER.info("[MoonTrace|Armor|STATE] action=state_change result=OK state=exile_mask_active enabled=false health_pct={} ctx{{p={}}}",
                            String.format("%.2f", healthPercent), serverPlayer.getName().getString());
                }
            } else {
                // 血量 < 50%，计算增伤
                float lostPercent = ArmorConfig.EXILE_HEALTH_THRESHOLD - healthPercent;
                int stacks = (int) Math.floor(lostPercent / ArmorConfig.EXILE_STACK_THRESHOLD);
                float damageBonus = Math.min(stacks * ArmorConfig.EXILE_DAMAGE_PER_STACK, ArmorConfig.EXILE_MAX_DAMAGE_BONUS);

                Float previousBonus = currentBonusMap.get(serverPlayer.getId());

                // 更新 AttributeModifier
                updateModifier(serverPlayer, damageBonus);
                currentBonusMap.put(serverPlayer.getId(), damageBonus);

                // 状态变化日志
                if (!wasActive) {
                    isActiveState.put(serverPlayer.getId(), true);
                    LOGGER.info("[MoonTrace|Armor|STATE] action=state_change result=OK state=exile_mask_active enabled=true health_pct={} damage_bonus={} ctx{{p={}}}",
                            String.format("%.2f", healthPercent), damageBonus, serverPlayer.getName().getString());
                } else if (previousBonus == null || Math.abs(previousBonus - damageBonus) > 0.01f) {
                    // 增伤值变化时才打印
                    if (ArmorConfig.DEBUG) {
                        LOGGER.info("[MoonTrace|Armor|APPLY] action=attribute_update result=OK attr=generic.attack_damage value={} ctx{{p={}}}",
                                damageBonus, serverPlayer.getName().getString());
                    }
                }
            }
        }
    }

    /**
     * 更新攻击力修改器（覆盖式）
     */
    private static void updateModifier(ServerPlayerEntity player, float value) {
        EntityAttributeInstance instance = player.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        if (instance == null) return;

        // 移除旧的修改器
        instance.removeModifier(ArmorConfig.EXILE_MODIFIER_ID);

        // 添加新的修改器（如果 value > 0）
        if (value > 0) {
            EntityAttributeModifier modifier = new EntityAttributeModifier(
                    ArmorConfig.EXILE_MODIFIER_ID,
                    value,
                    EntityAttributeModifier.Operation.ADD_VALUE
            );
            instance.addTemporaryModifier(modifier);
        }
    }

    /**
     * 移除攻击力修改器
     */
    private static void removeModifier(ServerPlayerEntity player) {
        EntityAttributeInstance instance = player.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        if (instance != null) {
            instance.removeModifier(ArmorConfig.EXILE_MODIFIER_ID);
        }
    }

    private static boolean isWearing(ServerPlayerEntity player) {
        return player.getEquippedStack(EquipmentSlot.HEAD).isOf(ArmorItems.EXILE_MASK_HELMET);
    }

    /**
     * 玩家下线时清理状态
     */
    public static void onPlayerLogout(ServerPlayerEntity player) {
        lastUpdateTick.remove(player.getId());
        isActiveState.remove(player.getId());
        currentBonusMap.remove(player.getId());
        removeModifier(player);
    }
}
