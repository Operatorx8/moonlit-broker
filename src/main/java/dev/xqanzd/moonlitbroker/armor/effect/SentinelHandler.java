package dev.xqanzd.moonlitbroker.armor.effect;

import dev.xqanzd.moonlitbroker.armor.ArmorConfig;
import dev.xqanzd.moonlitbroker.armor.item.ArmorItems;
import dev.xqanzd.moonlitbroker.armor.util.CooldownManager;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.Monster;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.world.LightType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 哨兵的最后瞭望 - Echo Pulse 回声测距
 *
 * 机制：
 * - 仅在光照 <= 7 时可触发
 * - 低频扫描（每 20 ticks 检查一次）
 * - 触发时仅给玩家提供线索：Speed I + 提示音
 * - 冷却：40s (800 ticks)
 */
public class SentinelHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("MoonTrace");

    /** 玩家上次扫描 tick */
    private static final Map<Integer, Long> lastScanTick = new ConcurrentHashMap<>();

    /**
     * 每 tick 调用（在 ServerTickEvent 中）
     */
    public static void tick(ServerWorld world, long currentTick) {
        for (var player : world.getPlayers()) {
            if (!(player instanceof ServerPlayerEntity serverPlayer)) continue;

            // 检查是否穿戴该头盔
            if (!isWearing(serverPlayer)) continue;

            // 检查扫描间隔
            Long lastTick = lastScanTick.get(serverPlayer.getId());
            if (lastTick != null && currentTick - lastTick < ArmorConfig.SENTINEL_SCAN_INTERVAL) {
                continue;
            }
            lastScanTick.put(serverPlayer.getId(), currentTick);

            // 检查光照条件
            int lightLevel = world.getLightLevel(LightType.BLOCK, serverPlayer.getBlockPos());
            int skyLight = world.getLightLevel(LightType.SKY, serverPlayer.getBlockPos());
            int combinedLight = Math.max(lightLevel, skyLight);

            if (combinedLight > ArmorConfig.SENTINEL_LIGHT_THRESHOLD) {
                if (ArmorConfig.DEBUG) {
                    LOGGER.info("[MoonTrace|Armor|TRIGGER] action=check result=BLOCKED reason=not_dark light={} threshold={} ctx{{p={}}}",
                            combinedLight, ArmorConfig.SENTINEL_LIGHT_THRESHOLD, serverPlayer.getName().getString());
                }
                continue;
            }

            // 检查冷却
            if (!CooldownManager.isReady(serverPlayer.getUuid(), ArmorConfig.SENTINEL_EFFECT_ID, currentTick)) {
                if (ArmorConfig.DEBUG) {
                    long cdLeft = CooldownManager.getRemainingTicks(serverPlayer.getUuid(), ArmorConfig.SENTINEL_EFFECT_ID, currentTick);
                    LOGGER.info("[MoonTrace|Armor|TRIGGER] action=check result=BLOCKED reason=cd_hit cd_total={} cd_left={} ctx{{p={}}}",
                            ArmorConfig.SENTINEL_COOLDOWN, cdLeft, serverPlayer.getName().getString());
                }
                continue;
            }

            // 扫描范围内敌对生物
            float range = ArmorConfig.SENTINEL_SCAN_RANGE;
            Box scanBox = new Box(
                    serverPlayer.getX() - range, serverPlayer.getY() - range, serverPlayer.getZ() - range,
                    serverPlayer.getX() + range, serverPlayer.getY() + range, serverPlayer.getZ() + range
            );

            List<LivingEntity> hostiles = world.getEntitiesByClass(
                    LivingEntity.class,
                    scanBox,
                    entity -> entity instanceof Monster && entity.isAlive()
            );

            if (hostiles.isEmpty()) {
                // 没有敌对生物，不触发也不进入 CD
                continue;
            }

            // Echo Pulse：仅给玩家短时速度线索
            serverPlayer.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.SPEED,
                    ArmorConfig.SENTINEL_SPEED_DURATION,
                    ArmorConfig.SENTINEL_SPEED_AMPLIFIER,
                    false,  // ambient
                    false,  // showParticles
                    true    // showIcon
            ));

            SoundEvent pulseSound = resolvePulseSound();
            if (pulseSound != null) {
                world.playSound(
                        null,
                        serverPlayer.getX(),
                        serverPlayer.getY(),
                        serverPlayer.getZ(),
                        pulseSound,
                        SoundCategory.PLAYERS,
                        ArmorConfig.SENTINEL_SOUND_VOLUME,
                        ArmorConfig.SENTINEL_SOUND_PITCH
                );
            }

            // 进入冷却
            CooldownManager.setCooldown(serverPlayer.getUuid(), ArmorConfig.SENTINEL_EFFECT_ID, currentTick, ArmorConfig.SENTINEL_COOLDOWN);

            // 日志
            LOGGER.info("[MoonTrace|Armor|TRIGGER] action=trigger result=OK effect={} targets={} range={} ctx{{p={} dim={} light={}}}",
                    ArmorConfig.SENTINEL_EFFECT_ID, hostiles.size(), (int) range,
                    serverPlayer.getName().getString(), world.getRegistryKey().getValue().getPath(), combinedLight);
            LOGGER.info("[MoonTrace|Armor|APPLY] action=apply result=OK effect=speed final{{dur={} amp={}}} sound={} ctx{{p={}}}",
                    ArmorConfig.SENTINEL_SPEED_DURATION, ArmorConfig.SENTINEL_SPEED_AMPLIFIER,
                    ArmorConfig.SENTINEL_SOUND_ID, serverPlayer.getName().getString());
        }
    }

    private static SoundEvent resolvePulseSound() {
        return Registries.SOUND_EVENT.getOrEmpty(Identifier.of(ArmorConfig.SENTINEL_SOUND_ID)).orElse(null);
    }

    private static boolean isWearing(ServerPlayerEntity player) {
        return player.getEquippedStack(EquipmentSlot.HEAD).isOf(ArmorItems.SENTINEL_HELMET);
    }
}
