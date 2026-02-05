package mod.test.mymodtest.armor.effect;

import mod.test.mymodtest.armor.ArmorConfig;
import mod.test.mymodtest.armor.item.ArmorItems;
import mod.test.mymodtest.armor.util.CooldownManager;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 商人的防风衣 - 防风（击退抗性）+ 低血机动爆发
 *
 * 机制：
 * - 击退抗性：+0.3（穿戴时自动应用）
 * - 低血速度：生命从 ≥50% 跌到 <50% 时，获得 Speed I 5s，CD 90s
 * - 边沿触发：触发后需回到 ≥60% 才允许下次触发
 */
public class WindbreakerHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("MoonTrace");

    /** 玩家上次检查 tick */
    private static final Map<Integer, Long> lastCheckTick = new ConcurrentHashMap<>();

    /** 玩家上一次是否在 50% 以上 */
    private static final Map<Integer, Boolean> wasAboveThreshold = new ConcurrentHashMap<>();

    /** 玩家是否已回到 60% 可重新触发 */
    private static final Map<Integer, Boolean> rearmReady = new ConcurrentHashMap<>();

    /** 玩家是否已应用击退抗性 */
    private static final Map<Integer, Boolean> kbModifierApplied = new ConcurrentHashMap<>();

    /**
     * 每 tick 调用（在 ServerTickEvent 中）
     */
    public static void tick(ServerWorld world, long currentTick) {
        for (var player : world.getPlayers()) {
            if (!(player instanceof ServerPlayerEntity serverPlayer)) continue;

            // 检查检查间隔
            Long lastTick = lastCheckTick.get(serverPlayer.getId());
            if (lastTick != null && currentTick - lastTick < ArmorConfig.WINDBREAKER_CHECK_INTERVAL) {
                continue;
            }
            lastCheckTick.put(serverPlayer.getId(), currentTick);

            // 检查是否穿戴该胸甲
            boolean wearing = isWearing(serverPlayer);
            boolean hadModifier = kbModifierApplied.getOrDefault(serverPlayer.getId(), false);

            if (!wearing) {
                // 脱下胸甲，移除击退抗性
                if (hadModifier) {
                    removeKnockbackModifier(serverPlayer);
                    kbModifierApplied.put(serverPlayer.getId(), false);
                    wasAboveThreshold.remove(serverPlayer.getId());
                    rearmReady.remove(serverPlayer.getId());

                    if (ArmorConfig.DEBUG) {
                        LOGGER.info("[MoonTrace|Armor|STATE] action=state_change result=OK state=windbreaker_kb enabled=false reason=not_wearing ctx{{p={}}}",
                                serverPlayer.getName().getString());
                    }
                }
                continue;
            }

            // 穿着胸甲，确保击退抗性已应用
            if (!hadModifier) {
                applyKnockbackModifier(serverPlayer);
                kbModifierApplied.put(serverPlayer.getId(), true);

                if (ArmorConfig.DEBUG) {
                    LOGGER.info("[MoonTrace|Armor|STATE] action=state_change result=OK state=windbreaker_kb enabled=true kb_resistance={} ctx{{p={}}}",
                            ArmorConfig.WINDBREAKER_KNOCKBACK_RESISTANCE, serverPlayer.getName().getString());
                }
            }

            // 计算血量百分比
            float healthRatio = serverPlayer.getHealth() / serverPlayer.getMaxHealth();

            // 检查是否需要重新武装
            if (healthRatio >= ArmorConfig.WINDBREAKER_HEALTH_REARM) {
                if (!rearmReady.getOrDefault(serverPlayer.getId(), true)) {
                    rearmReady.put(serverPlayer.getId(), true);
                    LOGGER.info("[MoonTrace|Armor|STATE] action=state_change result=OK state=rearm_ready enabled=true health_pct={} ctx{{p={}}}",
                            String.format("%.2f", healthRatio), serverPlayer.getName().getString());
                }
            }

            // 边沿检测：从 ≥50% 跌到 <50%
            boolean isAboveThreshold = healthRatio >= ArmorConfig.WINDBREAKER_HEALTH_TRIGGER;
            Boolean wasAbove = wasAboveThreshold.get(serverPlayer.getId());
            if (wasAbove == null) wasAbove = true;

            if (wasAbove && !isAboveThreshold) {
                // 边沿触发！检查条件
                boolean isRearmReady = rearmReady.getOrDefault(serverPlayer.getId(), true);
                boolean cdReady = CooldownManager.isReady(serverPlayer.getUuid(), ArmorConfig.WINDBREAKER_SPEED_EFFECT_ID, currentTick);

                if (isRearmReady && cdReady) {
                    // 触发！
                    serverPlayer.addStatusEffect(new StatusEffectInstance(
                            StatusEffects.SPEED,
                            ArmorConfig.WINDBREAKER_SPEED_DURATION,
                            ArmorConfig.WINDBREAKER_SPEED_AMPLIFIER,
                            false,  // ambient
                            true,   // showParticles
                            true    // showIcon
                    ));

                    // 设置状态
                    rearmReady.put(serverPlayer.getId(), false);
                    CooldownManager.setCooldown(serverPlayer.getUuid(), ArmorConfig.WINDBREAKER_SPEED_EFFECT_ID, currentTick, ArmorConfig.WINDBREAKER_SPEED_COOLDOWN);

                    // 日志
                    LOGGER.info("[MoonTrace|Armor|TRIGGER] action=trigger result=OK effect={} edge{{from={} to={}}} ctx{{p={}}}",
                            ArmorConfig.WINDBREAKER_SPEED_EFFECT_ID, String.format("%.2f", wasAbove ? 0.5f : healthRatio),
                            String.format("%.2f", healthRatio), serverPlayer.getName().getString());
                    LOGGER.info("[MoonTrace|Armor|APPLY] action=apply result=OK effect=speed final{{dur={} amp={}}} ctx{{p={}}}",
                            ArmorConfig.WINDBREAKER_SPEED_DURATION, ArmorConfig.WINDBREAKER_SPEED_AMPLIFIER,
                            serverPlayer.getName().getString());
                } else {
                    if (ArmorConfig.DEBUG) {
                        if (!isRearmReady) {
                            LOGGER.info("[MoonTrace|Armor|TRIGGER] action=check result=BLOCKED reason=not_rearmed ctx{{p={}}}",
                                    serverPlayer.getName().getString());
                        } else {
                            long cdLeft = CooldownManager.getRemainingTicks(serverPlayer.getUuid(), ArmorConfig.WINDBREAKER_SPEED_EFFECT_ID, currentTick);
                            LOGGER.info("[MoonTrace|Armor|TRIGGER] action=check result=BLOCKED reason=cd_hit cd_total={} cd_left={} ctx{{p={}}}",
                                    ArmorConfig.WINDBREAKER_SPEED_COOLDOWN, cdLeft, serverPlayer.getName().getString());
                        }
                    }
                }
            }

            // 更新状态
            wasAboveThreshold.put(serverPlayer.getId(), isAboveThreshold);
        }
    }

    /**
     * 应用击退抗性修改器
     */
    private static void applyKnockbackModifier(ServerPlayerEntity player) {
        EntityAttributeInstance instance = player.getAttributeInstance(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE);
        if (instance == null) return;

        // 移除旧的（如果存在）
        instance.removeModifier(ArmorConfig.WINDBREAKER_KB_MODIFIER_ID);

        // 添加新的
        EntityAttributeModifier modifier = new EntityAttributeModifier(
                ArmorConfig.WINDBREAKER_KB_MODIFIER_ID,
                ArmorConfig.WINDBREAKER_KNOCKBACK_RESISTANCE,
                EntityAttributeModifier.Operation.ADD_VALUE
        );
        instance.addTemporaryModifier(modifier);
    }

    /**
     * 移除击退抗性修改器
     */
    private static void removeKnockbackModifier(ServerPlayerEntity player) {
        EntityAttributeInstance instance = player.getAttributeInstance(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE);
        if (instance != null) {
            instance.removeModifier(ArmorConfig.WINDBREAKER_KB_MODIFIER_ID);
        }
    }

    private static boolean isWearing(ServerPlayerEntity player) {
        return player.getEquippedStack(EquipmentSlot.CHEST).isOf(ArmorItems.WINDBREAKER_CHESTPLATE);
    }

    /**
     * 玩家下线时清理状态
     */
    public static void onPlayerLogout(ServerPlayerEntity player) {
        lastCheckTick.remove(player.getId());
        wasAboveThreshold.remove(player.getId());
        rearmReady.remove(player.getId());
        kbModifierApplied.remove(player.getId());
        removeKnockbackModifier(player);
    }
}
