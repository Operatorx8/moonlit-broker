package dev.xqanzd.moonlitbroker.katana.effect.nmap;

import dev.xqanzd.moonlitbroker.katana.item.KatanaItems;
import dev.xqanzd.moonlitbroker.util.KatanaContractUtil;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class NmapScanHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("Nmap");

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                long currentTick = player.getServerWorld().getTime();

                if (!isHoldingNmap(player)) continue;
                // Contract gate: dormant nmap → no scan
                ItemStack nmapStack = player.getMainHandStack().isOf(KatanaItems.NMAP_KATANA)
                        ? player.getMainHandStack() : player.getOffHandStack();
                if (!KatanaContractUtil.isActiveContract(player.getServerWorld(), player, nmapStack)) continue;
                if (currentTick < NmapManager.getNextScanTick(player)) continue;
                NmapManager.setNextScanTick(player, currentTick + NmapConfig.SCAN_INTERVAL_TICKS);

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
        if (NmapManager.isScanOnCooldown(player, currentTick)) {
            if (NmapConfig.DEBUG) {
                LOGGER.info("[Nmap] cooldown active, skip scan");
            }
            return;
        }

        int threatCount = countThreats(player, currentTick);
        if (threatCount > 0) {
            int chainCount = NmapManager.getChainCount(player);
            if (chainCount >= NmapConfig.MAX_CHAIN_REFRESHES) {
                forceCooldown(player, currentTick, "[Nmap] uptime cap reached -> forced cooldown 12s");
                return;
            }
            NmapManager.incrementChain(player);
            int newChain = NmapManager.getChainCount(player);
            activateShield(player, currentTick, threatCount, newChain);
            return;
        }

        forceCooldown(player, currentTick, "[Nmap] scan miss -> cooldown 12s");
    }

    private static int countThreats(ServerPlayerEntity player, long currentTick) {
        ServerWorld world = player.getServerWorld();
        Box scanBox = new Box(
            player.getX() - NmapConfig.SCAN_RADIUS,
            player.getY() - NmapConfig.SCAN_RADIUS,
            player.getZ() - NmapConfig.SCAN_RADIUS,
            player.getX() + NmapConfig.SCAN_RADIUS,
            player.getY() + NmapConfig.SCAN_RADIUS,
            player.getZ() + NmapConfig.SCAN_RADIUS
        );

        List<MobEntity> mobs = world.getEntitiesByClass(
            MobEntity.class,
            scanBox,
            mob -> mob.isAlive() && !mob.isRemoved()
        );
        int threats = 0;
        for (MobEntity mob : mobs) {
            if (Math.abs(mob.getY() - player.getY()) > NmapConfig.ABS_DY_MAX) {
                continue;
            }
            if (!isThreateningPlayer(mob, player)) {
                continue;
            }
            threats++;
            NmapManager.addScannedHostile(player, mob.getUuid(), currentTick);
        }
        return threats;
    }

    private static boolean isThreateningPlayer(MobEntity mob, ServerPlayerEntity player) {
        if (mob.getTarget() == player) {
            return true;
        }
        return isFallbackThreat(mob, player);
    }

    private static boolean isFallbackThreat(MobEntity mob, ServerPlayerEntity player) {
        double maxDistSq = NmapConfig.THREAT_FALLBACK_DISTANCE * NmapConfig.THREAT_FALLBACK_DISTANCE;
        if (mob.squaredDistanceTo(player) > maxDistSq) {
            return false;
        }
        if (!mob.canSee(player)) {
            return false;
        }
        Vec3d velocity = mob.getVelocity();
        if (velocity.lengthSquared() < NmapConfig.THREAT_MOVE_SPEED_SQ) {
            return false;
        }
        Vec3d towardPlayer = player.getPos().subtract(mob.getPos());
        return velocity.dotProduct(towardPlayer) > NmapConfig.THREAT_MOVE_DOT_MIN;
    }

    private static void activateShield(ServerPlayerEntity player, long currentTick, int threatCount, int chainCount) {
        player.addStatusEffect(new StatusEffectInstance(
            StatusEffects.RESISTANCE,
            NmapConfig.RESISTANCE_DURATION_TICKS,
            NmapConfig.RESISTANCE_AMPLIFIER,
            false, true, true
        ));
        NmapManager.activateShield(player, currentTick);

        if (NmapConfig.DEBUG) {
            LOGGER.info("[Nmap] scan hit threats={} -> RESISTANCE V 2s (chain={}/{})",
                threatCount, chainCount, NmapConfig.MAX_CHAIN_REFRESHES);
        }
        player.playSound(SoundEvents.BLOCK_BEACON_ACTIVATE, 0.5f, 1.5f);
    }

    private static void forceCooldown(ServerPlayerEntity player, long currentTick, String logMessage) {
        player.removeStatusEffect(StatusEffects.RESISTANCE);
        NmapManager.setScanCooldown(player, NmapConfig.COOLDOWN_TICKS, currentTick);
        if (NmapConfig.DEBUG) {
            LOGGER.info(logMessage);
        }
    }

    /**
     * Called when player takes damage
     */
    public static void onPlayerDamaged(ServerPlayerEntity player, boolean fromHostile, long currentTick) {
        if (!isHoldingNmap(player)) return;
        if (!NmapManager.isShieldActive(player, currentTick)) return;
        player.removeStatusEffect(StatusEffects.RESISTANCE);
        NmapManager.cancelShieldAndEnterCooldown(player, currentTick);
        if (NmapConfig.DEBUG) {
            LOGGER.info("[Nmap] damaged -> cancel + cooldown 12s");
        }
        player.playSound(SoundEvents.BLOCK_GLASS_BREAK, 0.8f, 0.8f);
    }
}
