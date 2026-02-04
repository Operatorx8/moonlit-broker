package mod.test.mymodtest.katana.effect.nmap;

import mod.test.mymodtest.katana.item.KatanaItems;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.Angerable;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

public class NmapScanHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("Nmap");

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (!isHoldingNmap(player)) continue;

                long currentTick = player.getServerWorld().getTime();
                NmapManager.NmapPlayerState state = NmapManager.getOrCreate(player);

                // Scan interval
                int interval = NmapManager.isScanOnCooldown(player, currentTick)
                    ? NmapConfig.COOLDOWN_SCAN_INTERVAL_TICKS
                    : NmapConfig.SCAN_INTERVAL_TICKS;

                if (currentTick - state.lastScanTick < interval) continue;
                state.lastScanTick = currentTick;

                performScan(player, currentTick);
            }
        });

        LOGGER.info("[Nmap] Scan handler registered");
    }

    public static boolean isHoldingNmap(PlayerEntity player) {
        return player.getMainHandStack().isOf(KatanaItems.NMAP_KATANA) ||
               player.getOffHandStack().isOf(KatanaItems.NMAP_KATANA);
    }

    private static void performScan(ServerPlayerEntity player, long currentTick) {
        ServerWorld world = player.getServerWorld();

        Box scanBox = new Box(
            player.getX() - NmapConfig.SCAN_RADIUS,
            player.getY() - NmapConfig.SCAN_RADIUS,
            player.getZ() - NmapConfig.SCAN_RADIUS,
            player.getX() + NmapConfig.SCAN_RADIUS,
            player.getY() + NmapConfig.SCAN_RADIUS,
            player.getZ() + NmapConfig.SCAN_RADIUS
        );

        List<Entity> entities = world.getOtherEntities(player, scanBox, EntityPredicates.VALID_LIVING_ENTITY);

        int hostileCount = 0;

        for (Entity entity : entities) {
            if (!(entity instanceof LivingEntity living)) continue;

            if (isHostile(living, player)) {
                hostileCount++;
                NmapManager.addScannedHostile(player, entity.getUuid(), currentTick);
            }
        }

        boolean onCooldown = NmapManager.isScanOnCooldown(player, currentTick);

        if (NmapConfig.DEBUG) {
            LOGGER.info("[Nmap] SCAN: {} hosts discovered (cooldown: {})", hostileCount, onCooldown);
        }

        if (onCooldown) {
            if (hostileCount > 0) {
                // Cooldown period light indication
                player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), 0.2f, 2.0f);
            }
        } else {
            if (hostileCount > 0) {
                activateShield(player);
            }
        }
    }

    private static boolean isHostile(LivingEntity entity, PlayerEntity player) {
        if (entity instanceof EnderDragonEntity || entity instanceof WitherEntity) return true;
        if (entity instanceof Monster) return true;

        if (entity instanceof Angerable angerable) {
            UUID angryAt = angerable.getAngryAt();
            return angryAt != null && angryAt.equals(player.getUuid());
        }

        return false;
    }

    private static void activateShield(ServerPlayerEntity player) {
        player.addStatusEffect(new StatusEffectInstance(
            StatusEffects.RESISTANCE,
            NmapConfig.RESISTANCE_DURATION_TICKS,
            NmapConfig.RESISTANCE_AMPLIFIER,
            false, true, true
        ));

        NmapManager.activateShield(player);

        if (NmapConfig.DEBUG) {
            LOGGER.info("[Nmap] SHIELD UP: Resistance V for 6s");
        }

        // Scan particles
        ServerWorld world = player.getServerWorld();
        for (int i = 0; i < 36; i++) {
            double angle = Math.toRadians(i * 10);
            double x = player.getX() + Math.cos(angle) * 3;
            double z = player.getZ() + Math.sin(angle) * 3;
            world.spawnParticles(ParticleTypes.END_ROD, x, player.getY() + 1, z, 1, 0, 0.5, 0, 0.02);
        }

        player.playSound(SoundEvents.BLOCK_BEACON_ACTIVATE, 0.5f, 1.5f);
    }

    /**
     * Called when player takes damage
     */
    public static void onPlayerDamaged(ServerPlayerEntity player, boolean fromHostile, long currentTick) {
        if (!isHoldingNmap(player)) return;
        if (!NmapManager.isShieldActive(player)) return;

        int cooldown = fromHostile
            ? NmapConfig.COOLDOWN_HOSTILE_HIT_TICKS
            : NmapConfig.COOLDOWN_OTHER_DAMAGE_TICKS;

        NmapManager.setScanCooldown(player, cooldown, currentTick);

        player.playSound(SoundEvents.BLOCK_GLASS_BREAK, 0.8f, 0.8f);
        player.getServerWorld().spawnParticles(
            ParticleTypes.CLOUD,
            player.getX(), player.getY() + 1, player.getZ(),
            30, 0.5, 0.5, 0.5, 0.1
        );
    }
}
