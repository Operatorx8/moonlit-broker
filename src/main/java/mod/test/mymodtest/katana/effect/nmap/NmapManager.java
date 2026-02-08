package mod.test.mymodtest.katana.effect.nmap;

import net.minecraft.entity.player.PlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class NmapManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("Nmap");

    private static final Map<UUID, NmapPlayerState> playerStates = new ConcurrentHashMap<>();

    public static class NmapPlayerState {
        // Host Discovery
        public long nextScanTick = 0;
        public long cooldownUntilTick = 0;
        public int chainCount = 0;
        public long buffActiveUntilTick = 0;
        public boolean shieldActive = false;

        // Port Enumeration
        public Set<UUID> scannedHostiles = new HashSet<>();
        public long enumWindowStart = 0;
        public float currentPenetration = 0f;

        // Vulnerability Scan
        public long vulnCritCooldownUntil = 0;

        // Firewall
        public long firewallDebuffCooldownUntil = 0;
        public long firewallProjCooldownUntil = 0;
    }

    public static NmapPlayerState getOrCreate(PlayerEntity player) {
        return playerStates.computeIfAbsent(player.getUuid(), k -> new NmapPlayerState());
    }

    // ========== Host Discovery ==========

    public static boolean isScanOnCooldown(PlayerEntity player, long currentTick) {
        return currentTick < getOrCreate(player).cooldownUntilTick;
    }

    public static void setScanCooldown(PlayerEntity player, int cooldownTicks, long currentTick) {
        NmapPlayerState state = getOrCreate(player);
        state.cooldownUntilTick = currentTick + cooldownTicks;
        state.chainCount = 0;
        state.buffActiveUntilTick = 0;
        state.shieldActive = false;
    }

    public static void activateShield(PlayerEntity player, long currentTick) {
        NmapPlayerState state = getOrCreate(player);
        state.shieldActive = true;
        state.buffActiveUntilTick = currentTick + NmapConfig.RESISTANCE_DURATION_TICKS;
    }

    public static boolean isShieldActive(PlayerEntity player, long currentTick) {
        NmapPlayerState state = getOrCreate(player);
        return state.shieldActive && currentTick <= state.buffActiveUntilTick;
    }

    public static void setNextScanTick(PlayerEntity player, long nextScanTick) {
        getOrCreate(player).nextScanTick = nextScanTick;
    }

    public static long getNextScanTick(PlayerEntity player) {
        return getOrCreate(player).nextScanTick;
    }

    public static void incrementChain(PlayerEntity player) {
        getOrCreate(player).chainCount++;
    }

    public static int getChainCount(PlayerEntity player) {
        return getOrCreate(player).chainCount;
    }

    public static long getBuffActiveUntilTick(PlayerEntity player) {
        return getOrCreate(player).buffActiveUntilTick;
    }

    public static void cancelShieldAndEnterCooldown(PlayerEntity player, long currentTick) {
        setScanCooldown(player, NmapConfig.COOLDOWN_TICKS, currentTick);
    }

    // ========== Port Enumeration ==========

    public static void addScannedHostile(PlayerEntity player, UUID hostileUuid, long currentTick) {
        NmapPlayerState state = getOrCreate(player);

        // Window expiration check
        if (currentTick - state.enumWindowStart > NmapConfig.ENUM_WINDOW_TICKS) {
            state.scannedHostiles.clear();
            state.enumWindowStart = currentTick;
        }

        boolean isNew = state.scannedHostiles.add(hostileUuid);

        // Calculate penetration
        int count = Math.min(state.scannedHostiles.size(), NmapConfig.PENETRATION_MAX_COUNT);
        state.currentPenetration = Math.min(
            count * NmapConfig.PENETRATION_PER_HOSTILE,
            NmapConfig.PENETRATION_CAP
        );

        if (NmapConfig.DEBUG && isNew && count <= NmapConfig.PENETRATION_MAX_COUNT) {
            LOGGER.info("[Nmap] ENUM: {} targets -> {}% penetration",
                count, (int)(state.currentPenetration * 100));
        }
    }

    public static float getCurrentPenetration(PlayerEntity player, long currentTick) {
        NmapPlayerState state = getOrCreate(player);

        if (currentTick - state.enumWindowStart > NmapConfig.ENUM_WINDOW_TICKS) {
            state.scannedHostiles.clear();
            state.currentPenetration = 0f;
            state.enumWindowStart = currentTick;
        }

        return state.currentPenetration;
    }

    // ========== Vulnerability Scan ==========

    public static boolean canVulnCrit(PlayerEntity player, long currentTick) {
        return currentTick >= getOrCreate(player).vulnCritCooldownUntil;
    }

    public static void setVulnCritCooldown(PlayerEntity player, long currentTick) {
        getOrCreate(player).vulnCritCooldownUntil = currentTick + NmapConfig.VULN_CRIT_COOLDOWN_TICKS;
    }

    // ========== Firewall ==========

    public static boolean canFirewallDebuff(PlayerEntity player, long currentTick) {
        return currentTick >= getOrCreate(player).firewallDebuffCooldownUntil;
    }

    public static void setFirewallDebuffCooldown(PlayerEntity player, long currentTick) {
        getOrCreate(player).firewallDebuffCooldownUntil = currentTick + NmapConfig.FIREWALL_DEBUFF_COOLDOWN_TICKS;
    }

    public static boolean canFirewallProj(PlayerEntity player, long currentTick) {
        return currentTick >= getOrCreate(player).firewallProjCooldownUntil;
    }

    public static void setFirewallProjCooldown(PlayerEntity player, long currentTick) {
        getOrCreate(player).firewallProjCooldownUntil = currentTick + NmapConfig.FIREWALL_PROJ_COOLDOWN_TICKS;
    }

    // ========== Cleanup ==========

    public static void cleanup(UUID playerUuid) {
        playerStates.remove(playerUuid);
    }

    public static void tickCleanup(long currentTick) {
        // Periodic cleanup of expired states (every ~30 seconds)
        if (currentTick % 600 == 0) {
            playerStates.entrySet().removeIf(entry -> {
                NmapPlayerState state = entry.getValue();
                // Remove if all cooldowns expired and no active state
                return currentTick > state.cooldownUntilTick + 1200
                    && currentTick > state.buffActiveUntilTick + 1200
                    && state.scannedHostiles.isEmpty();
            });
        }
    }
}
