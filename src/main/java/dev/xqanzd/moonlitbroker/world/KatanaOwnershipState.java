package dev.xqanzd.moonlitbroker.world;

import dev.xqanzd.moonlitbroker.trade.KatanaIdUtil;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Global katana ownership state:
 * key = (playerUuid, katanaId)
 *
 * Also stores active contract instance IDs and reclaim cooldowns.
 */
public class KatanaOwnershipState extends PersistentState {
    private static final Logger LOGGER = LoggerFactory.getLogger(KatanaOwnershipState.class);
    private static final String DATA_NAME = "xqanzd_moonlit_broker_katana_ownership";
    private static final String NBT_PLAYERS = "Players";
    private static final String NBT_CONTRACTS = "Contracts";
    private static final String NBT_ACTIVE = "Active";
    private static final String NBT_RECLAIM_TICK = "ReclaimTick";

    private final Map<UUID, Set<String>> katanaIdsByPlayer = new HashMap<>();
    private final Map<UUID, Map<String, UUID>> activeInstanceByPlayer = new HashMap<>();
    private final Map<UUID, Map<String, Long>> lastReclaimTickByPlayer = new HashMap<>();

    // ========== P0-2 FIX: Transient pending claims (NOT persisted) ==========
    private static final long PENDING_TTL_TICKS = 600L; // 30 seconds — generous to survive lag spikes

    public record PendingClaim(String katanaId, long tickCreated) {}

    /** Transient: not saved to NBT. One pending claim per player at a time. */
    private final Map<UUID, PendingClaim> pendingClaims = new HashMap<>();

    private static final Type<KatanaOwnershipState> TYPE = new Type<>(
        KatanaOwnershipState::new,
        KatanaOwnershipState::fromNbt,
        null
    );

    public static KatanaOwnershipState getServerState(ServerWorld world) {
        MinecraftServer server = world.getServer();
        ServerWorld overworld = server.getWorld(World.OVERWORLD);
        assert overworld != null;
        return overworld.getPersistentStateManager().getOrCreate(TYPE, DATA_NAME);
    }

    /**
     * Canonical tick source for all pending TTL operations — always uses Overworld time
     * to avoid cross-dimension tick drift.
     */
    public static long getOverworldTick(ServerWorld world) {
        ServerWorld overworld = world.getServer().getWorld(World.OVERWORLD);
        assert overworld != null;
        return overworld.getTime();
    }

    // ========== Ownership (existing) ==========

    public boolean hasOwned(UUID playerUuid, String katanaId) {
        String normalized = normalizeKatanaId(katanaId);
        if (normalized.isEmpty()) {
            return false;
        }
        Set<String> owned = katanaIdsByPlayer.get(playerUuid);
        return owned != null && owned.contains(normalized);
    }

    public boolean addOwned(UUID playerUuid, String katanaId) {
        String normalized = normalizeKatanaId(katanaId);
        if (normalized.isEmpty()) {
            return false;
        }
        Set<String> owned = katanaIdsByPlayer.computeIfAbsent(playerUuid, id -> new HashSet<>());
        boolean added = owned.add(normalized);
        if (added) {
            markDirty();
        }
        return added;
    }

    /**
     * @deprecated Use {@link #hasOwned(UUID, String)}.
     */
    @Deprecated
    public boolean has(UUID playerUuid, String katanaId) {
        return hasOwned(playerUuid, katanaId);
    }

    /**
     * @deprecated Use {@link #addOwned(UUID, String)}.
     */
    @Deprecated
    public boolean add(UUID playerUuid, String katanaId) {
        return addOwned(playerUuid, katanaId);
    }

    // ========== Active Instance Contract ==========

    @Nullable
    public UUID getActiveInstanceId(UUID playerUuid, String katanaId) {
        String normalized = normalizeKatanaId(katanaId);
        if (normalized.isEmpty()) return null;
        Map<String, UUID> map = activeInstanceByPlayer.get(playerUuid);
        return map == null ? null : map.get(normalized);
    }

    public void setActiveInstanceId(UUID playerUuid, String katanaId, UUID instanceId) {
        String normalized = normalizeKatanaId(katanaId);
        if (normalized.isEmpty()) return;
        activeInstanceByPlayer.computeIfAbsent(playerUuid, id -> new HashMap<>())
                .put(normalized, instanceId);
        markDirty();
    }

    // ========== Reclaim Cooldown ==========

    public long getLastReclaimTick(UUID playerUuid, String katanaId) {
        String normalized = normalizeKatanaId(katanaId);
        if (normalized.isEmpty()) return 0L;
        Map<String, Long> map = lastReclaimTickByPlayer.get(playerUuid);
        return map == null ? 0L : map.getOrDefault(normalized, 0L);
    }

    public void setLastReclaimTick(UUID playerUuid, String katanaId, long tick) {
        String normalized = normalizeKatanaId(katanaId);
        if (normalized.isEmpty()) return;
        lastReclaimTickByPlayer.computeIfAbsent(playerUuid, id -> new HashMap<>())
                .put(normalized, tick);
        markDirty();
    }

    // ========== Pending Claims (P0-2 two-phase commit) ==========

    /**
     * Write a pending claim. Returns false if the player already has ANY live pending claim
     * (same or different type) — one pending per player at a time.
     */
    public boolean setPending(UUID playerUuid, String katanaId, long currentTick) {
        String normalized = normalizeKatanaId(katanaId);
        if (normalized.isEmpty()) return false;
        cleanExpiredPending(currentTick);
        PendingClaim existing = pendingClaims.get(playerUuid);
        if (existing != null) {
            if (existing.katanaId().equals(normalized)) {
                LOGGER.debug("[MoonTrade] PENDING_REJECT_SAME player={} katanaId={}", playerUuid, normalized);
            } else {
                LOGGER.info("[MoonTrade] PENDING_REJECT_DIFF player={} existing={} requested={}",
                        playerUuid, existing.katanaId(), normalized);
            }
            return false;
        }
        pendingClaims.put(playerUuid, new PendingClaim(normalized, currentTick));
        return true;
    }

    /**
     * Check if the player has a live pending claim for the given katanaId.
     */
    public boolean hasPending(UUID playerUuid, String katanaId, long currentTick) {
        String normalized = normalizeKatanaId(katanaId);
        if (normalized.isEmpty()) return false;
        PendingClaim claim = pendingClaims.get(playerUuid);
        if (claim == null) return false;
        if (currentTick - claim.tickCreated() > PENDING_TTL_TICKS) {
            pendingClaims.remove(playerUuid);
            return false;
        }
        return claim.katanaId().equals(normalized);
    }

    /**
     * Check if the player has ANY live pending claim (regardless of type).
     */
    public boolean hasAnyPending(UUID playerUuid, long currentTick) {
        PendingClaim claim = pendingClaims.get(playerUuid);
        if (claim == null) return false;
        if (currentTick - claim.tickCreated() > PENDING_TTL_TICKS) {
            pendingClaims.remove(playerUuid);
            return false;
        }
        return true;
    }

    /**
     * Commit a pending claim: write ownership and clear pending state.
     * Only commits if pending exists AND matches the given katanaId.
     * Returns true if ownership was newly added.
     */
    public boolean commitPending(UUID playerUuid, String katanaId) {
        String normalized = normalizeKatanaId(katanaId);
        if (normalized.isEmpty()) return false;

        PendingClaim claim = pendingClaims.get(playerUuid);
        if (claim == null) {
            return false;
        }
        if (!claim.katanaId().equals(normalized)) {
            // Mismatch: don't write wrong katanaId as owned. Clear stale pending.
            pendingClaims.remove(playerUuid);
            LOGGER.warn("[MoonTrade] PENDING_COMMIT_MISMATCH player={} pending={} commitArg={}",
                    playerUuid, claim.katanaId(), normalized);
            return false;
        }

        pendingClaims.remove(playerUuid);
        return addOwned(playerUuid, normalized);
    }

    /**
     * Roll back (clear) pending claim without writing ownership.
     */
    public void clearPending(UUID playerUuid) {
        pendingClaims.remove(playerUuid);
    }

    /**
     * Evict expired pending claims.
     */
    public void cleanExpiredPending(long currentTick) {
        pendingClaims.entrySet().removeIf(e -> currentTick - e.getValue().tickCreated() > PENDING_TTL_TICKS);
    }

    // ========== Helpers ==========

    public static String normalizeKatanaId(String katanaId) {
        return KatanaIdUtil.canonicalizeKatanaId(katanaId);
    }

    // ========== NBT Serialization ==========

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        // Players (ownership)
        NbtCompound playersNbt = new NbtCompound();
        for (Map.Entry<UUID, Set<String>> entry : katanaIdsByPlayer.entrySet()) {
            NbtList katanaList = new NbtList();
            entry.getValue().stream().sorted().forEach(id -> katanaList.add(NbtString.of(id)));
            playersNbt.put(entry.getKey().toString(), katanaList);
        }
        nbt.put(NBT_PLAYERS, playersNbt);

        // Contracts (activeInstanceId + reclaimTick)
        NbtCompound contractsNbt = new NbtCompound();
        Set<UUID> allPlayers = new HashSet<>();
        allPlayers.addAll(activeInstanceByPlayer.keySet());
        allPlayers.addAll(lastReclaimTickByPlayer.keySet());
        for (UUID playerUuid : allPlayers) {
            NbtCompound playerContract = new NbtCompound();

            Map<String, UUID> activeMap = activeInstanceByPlayer.get(playerUuid);
            if (activeMap != null && !activeMap.isEmpty()) {
                NbtCompound activeNbt = new NbtCompound();
                for (Map.Entry<String, UUID> e : activeMap.entrySet()) {
                    activeNbt.putString(e.getKey(), e.getValue().toString());
                }
                playerContract.put(NBT_ACTIVE, activeNbt);
            }

            Map<String, Long> reclaimMap = lastReclaimTickByPlayer.get(playerUuid);
            if (reclaimMap != null && !reclaimMap.isEmpty()) {
                NbtCompound reclaimNbt = new NbtCompound();
                for (Map.Entry<String, Long> e : reclaimMap.entrySet()) {
                    reclaimNbt.putLong(e.getKey(), e.getValue());
                }
                playerContract.put(NBT_RECLAIM_TICK, reclaimNbt);
            }

            if (!playerContract.isEmpty()) {
                contractsNbt.put(playerUuid.toString(), playerContract);
            }
        }
        nbt.put(NBT_CONTRACTS, contractsNbt);

        return nbt;
    }

    public static KatanaOwnershipState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        KatanaOwnershipState state = new KatanaOwnershipState();

        // Players (ownership)
        if (nbt.contains(NBT_PLAYERS, NbtElement.COMPOUND_TYPE)) {
            NbtCompound playersNbt = nbt.getCompound(NBT_PLAYERS);
            for (String playerUuidText : playersNbt.getKeys()) {
                try {
                    UUID playerUuid = UUID.fromString(playerUuidText);
                    NbtList katanaList = playersNbt.getList(playerUuidText, NbtElement.STRING_TYPE);
                    Set<String> owned = new HashSet<>();
                    for (int i = 0; i < katanaList.size(); i++) {
                        String normalized = normalizeKatanaId(katanaList.get(i).asString());
                        if (!normalized.isEmpty()) {
                            owned.add(normalized);
                        }
                    }
                    if (!owned.isEmpty()) {
                        state.katanaIdsByPlayer.put(playerUuid, owned);
                    }
                } catch (IllegalArgumentException e) {
                    LOGGER.warn("[MoonTrade] KATANA_OWNERSHIP_SKIP_INVALID_UUID uuid={}", playerUuidText);
                }
            }
        }

        // Contracts
        if (nbt.contains(NBT_CONTRACTS, NbtElement.COMPOUND_TYPE)) {
            NbtCompound contractsNbt = nbt.getCompound(NBT_CONTRACTS);
            for (String playerUuidText : contractsNbt.getKeys()) {
                try {
                    UUID playerUuid = UUID.fromString(playerUuidText);
                    NbtCompound playerContract = contractsNbt.getCompound(playerUuidText);

                    if (playerContract.contains(NBT_ACTIVE, NbtElement.COMPOUND_TYPE)) {
                        NbtCompound activeNbt = playerContract.getCompound(NBT_ACTIVE);
                        Map<String, UUID> activeMap = new HashMap<>();
                        for (String type : activeNbt.getKeys()) {
                            String normalized = normalizeKatanaId(type);
                            if (normalized.isEmpty()) continue;
                            try {
                                activeMap.put(normalized, UUID.fromString(activeNbt.getString(type)));
                            } catch (IllegalArgumentException ignored) {
                                LOGGER.warn("[MoonTrade] CONTRACT_SKIP_INVALID_INSTANCE_UUID player={} type={}", playerUuidText, type);
                            }
                        }
                        if (!activeMap.isEmpty()) {
                            state.activeInstanceByPlayer.put(playerUuid, activeMap);
                        }
                    }

                    if (playerContract.contains(NBT_RECLAIM_TICK, NbtElement.COMPOUND_TYPE)) {
                        NbtCompound reclaimNbt = playerContract.getCompound(NBT_RECLAIM_TICK);
                        Map<String, Long> reclaimMap = new HashMap<>();
                        for (String type : reclaimNbt.getKeys()) {
                            String normalized = normalizeKatanaId(type);
                            if (normalized.isEmpty()) continue;
                            reclaimMap.put(normalized, reclaimNbt.getLong(type));
                        }
                        if (!reclaimMap.isEmpty()) {
                            state.lastReclaimTickByPlayer.put(playerUuid, reclaimMap);
                        }
                    }
                } catch (IllegalArgumentException e) {
                    LOGGER.warn("[MoonTrade] CONTRACT_SKIP_INVALID_UUID uuid={}", playerUuidText);
                }
            }
        }

        return state;
    }
}
