package dev.xqanzd.moonlitbroker.armor.effect;

import dev.xqanzd.moonlitbroker.armor.ArmorConfig;
import dev.xqanzd.moonlitbroker.armor.item.ArmorItems;
import dev.xqanzd.moonlitbroker.armor.util.CooldownManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.Angerable;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 遗世之环 - 被盯上时获得短暂吸收护盾
 *
 * 机制：
 * - 边沿触发：怪物首次将玩家设为目标时触发
 * - 给予 Absorption I，持续 3s (60 ticks)
 * - 冷却：30s (600 ticks)
 * - 末影人/猪灵/僵尸猪人：检测愤怒状态变化（每 20 ticks 低频检测）
 */
public class RelicCircletHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("MoonTrace");

    /** 玩家 -> 已触发过的怪物集合（避免重复触发） */
    private static final Map<UUID, Set<Integer>> triggeredMobs = new ConcurrentHashMap<>();

    /** 玩家上次愤怒检测 tick */
    private static final Map<Integer, Long> lastAngeredCheckTick = new ConcurrentHashMap<>();

    /**
     * 被怪物设为目标时调用（在 EntityTargetEvent 或低频扫描中）
     * @return true 如果触发了效果
     */
    public static boolean onTargeted(ServerPlayerEntity player, LivingEntity mob, long currentTick) {
        // 检查是否穿戴该头盔
        if (!isWearing(player)) {
            return false;
        }

        // 检查是否已触发过（边沿触发）
        Set<Integer> triggered = triggeredMobs.computeIfAbsent(player.getUuid(), k -> new HashSet<>());
        if (triggered.contains(mob.getId())) {
            if (ArmorConfig.DEBUG) {
                LOGGER.info("[MoonTrace|Armor|TRIGGER] action=check result=BLOCKED reason=already_triggered ctx{{p={} mob={}}}",
                        player.getName().getString(), mob.getType().getName().getString());
            }
            return false;
        }

        // 检查冷却
        if (!CooldownManager.isReady(player.getUuid(), ArmorConfig.RELIC_CIRCLET_EFFECT_ID, currentTick)) {
            if (ArmorConfig.DEBUG) {
                long cdLeft = CooldownManager.getRemainingTicks(player.getUuid(), ArmorConfig.RELIC_CIRCLET_EFFECT_ID, currentTick);
                LOGGER.info("[MoonTrace|Armor|TRIGGER] action=check result=BLOCKED reason=cd_hit cd_total={} cd_left={} ctx{{p={}}}",
                        ArmorConfig.RELIC_CIRCLET_COOLDOWN, cdLeft, player.getName().getString());
            }
            // 即使 CD 中也标记为已触发，避免 CD 结束后立即触发
            triggered.add(mob.getId());
            return false;
        }

        // 标记为已触发
        triggered.add(mob.getId());

        // 应用 Absorption 效果
        player.addStatusEffect(new StatusEffectInstance(
                StatusEffects.ABSORPTION,
                ArmorConfig.RELIC_ABSORPTION_DURATION,
                ArmorConfig.RELIC_ABSORPTION_AMPLIFIER,
                false,  // ambient
                true,   // showParticles
                true    // showIcon
        ));

        // 进入冷却
        CooldownManager.setCooldown(player.getUuid(), ArmorConfig.RELIC_CIRCLET_EFFECT_ID, currentTick, ArmorConfig.RELIC_CIRCLET_COOLDOWN);

        // 日志
        LOGGER.info("[MoonTrace|Armor|TRIGGER] action=trigger result=OK effect={} first_target=true ctx{{p={} mob={}}}",
                ArmorConfig.RELIC_CIRCLET_EFFECT_ID, player.getName().getString(), mob.getType().getName().getString());
        LOGGER.info("[MoonTrace|Armor|APPLY] action=apply result=OK effect=absorption final{{dur={} amp={}}} ctx{{p={}}}",
                ArmorConfig.RELIC_ABSORPTION_DURATION, ArmorConfig.RELIC_ABSORPTION_AMPLIFIER, player.getName().getString());

        return true;
    }

    /**
     * 每 tick 调用 - 用于检测末影人/猪灵/僵尸猪人的愤怒状态
     */
    public static void tick(ServerWorld world, long currentTick) {
        for (var player : world.getPlayers()) {
            if (!(player instanceof ServerPlayerEntity serverPlayer)) continue;

            // 检查是否穿戴该头盔
            if (!isWearing(serverPlayer)) continue;

            // 检查扫描间隔
            Long lastTick = lastAngeredCheckTick.get(serverPlayer.getId());
            if (lastTick != null && currentTick - lastTick < ArmorConfig.RELIC_ANGERED_CHECK_INTERVAL) {
                continue;
            }
            lastAngeredCheckTick.put(serverPlayer.getId(), currentTick);

            // 扫描附近的愤怒生物
            float range = 16.0f;
            Box scanBox = new Box(
                    serverPlayer.getX() - range, serverPlayer.getY() - range, serverPlayer.getZ() - range,
                    serverPlayer.getX() + range, serverPlayer.getY() + range, serverPlayer.getZ() + range
            );

            var angeredMobs = world.getEntitiesByClass(
                    LivingEntity.class,
                    scanBox,
                    entity -> isAngeredAtPlayer(entity, serverPlayer)
            );

            for (var mob : angeredMobs) {
                onTargeted(serverPlayer, mob, currentTick);
            }
        }
    }

    /**
     * 检查实体是否正在愤怒地攻击玩家
     */
    private static boolean isAngeredAtPlayer(LivingEntity entity, ServerPlayerEntity player) {
        // 检查普通敌对生物的目标 (MobEntity has getTarget())
        if (entity instanceof MobEntity mobEntity) {
            LivingEntity target = mobEntity.getTarget();
            return target != null && target.getUuid().equals(player.getUuid());
        }

        // 检查 Angerable 生物（末影人、猪灵、僵尸猪人）
        if (entity instanceof Angerable angerable) {
            UUID angryAt = angerable.getAngryAt();
            return angryAt != null && angryAt.equals(player.getUuid());
        }

        return false;
    }

    /**
     * 清理死亡/脱战的怪物记录
     */
    public static void cleanupDeadMobs(ServerWorld world) {
        for (var entry : triggeredMobs.entrySet()) {
            entry.getValue().removeIf(mobId -> {
                Entity entity = world.getEntityById(mobId);
                if (entity == null || !entity.isAlive()) {
                    return true;  // 移除
                }
                // 检查是否已脱战（无目标）
                if (entity instanceof MobEntity mobEntity && mobEntity.getTarget() == null) {
                    return true;
                }
                return false;
            });
        }
    }

    /**
     * 玩家下线时清理状态
     */
    public static void onPlayerLogout(ServerPlayerEntity player) {
        triggeredMobs.remove(player.getUuid());
        lastAngeredCheckTick.remove(player.getId());
    }

    private static boolean isWearing(ServerPlayerEntity player) {
        return player.getEquippedStack(EquipmentSlot.HEAD).isOf(ArmorItems.RELIC_CIRCLET_HELMET);
    }
}
