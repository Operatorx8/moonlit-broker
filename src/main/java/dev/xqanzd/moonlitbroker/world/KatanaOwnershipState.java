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
