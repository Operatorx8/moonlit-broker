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
 */
public class KatanaOwnershipState extends PersistentState {
    private static final Logger LOGGER = LoggerFactory.getLogger(KatanaOwnershipState.class);
    private static final String DATA_NAME = "xqanzd_moonlit_broker_katana_ownership";
    private static final String NBT_PLAYERS = "Players";

    private final Map<UUID, Set<String>> katanaIdsByPlayer = new HashMap<>();

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

    public static String normalizeKatanaId(String katanaId) {
        return KatanaIdUtil.canonicalizeKatanaId(katanaId);
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        NbtCompound playersNbt = new NbtCompound();
        for (Map.Entry<UUID, Set<String>> entry : katanaIdsByPlayer.entrySet()) {
            NbtList katanaList = new NbtList();
            entry.getValue().stream().sorted().forEach(id -> katanaList.add(NbtString.of(id)));
            playersNbt.put(entry.getKey().toString(), katanaList);
        }
        nbt.put(NBT_PLAYERS, playersNbt);
        return nbt;
    }

    public static KatanaOwnershipState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        KatanaOwnershipState state = new KatanaOwnershipState();
        if (!nbt.contains(NBT_PLAYERS, NbtElement.COMPOUND_TYPE)) {
            return state;
        }
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
        return state;
    }
}
