package mod.test.mymodtest.world;

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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Global katana ownership state:
 * key = (playerUuid, katanaId)
 */
public class KatanaOwnershipState extends PersistentState {
    private static final Logger LOGGER = LoggerFactory.getLogger(KatanaOwnershipState.class);
    private static final String DATA_NAME = "mymodtest_katana_ownership";
    private static final String NBT_PLAYERS = "Players";
    private static final Map<String, String> LEGACY_KATANA_ID_ALIAS = Map.of(
        "moon_glow_katana", "moonglow",
        "regret_blade", "regret",
        "eclipse_blade", "eclipse",
        "oblivion_edge", "oblivion",
        "nmap_katana", "nmap"
    );

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

    public boolean has(UUID playerUuid, String katanaId) {
        String normalized = normalizeKatanaId(katanaId);
        if (normalized.isEmpty()) {
            return false;
        }
        Set<String> owned = katanaIdsByPlayer.get(playerUuid);
        return owned != null && owned.contains(normalized);
    }

    public boolean add(UUID playerUuid, String katanaId) {
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

    public static String normalizeKatanaId(String katanaId) {
        if (katanaId == null) {
            return "";
        }
        String id = katanaId.trim();
        if (id.isEmpty()) {
            return "";
        }

        String candidate = id;
        if (candidate.startsWith("katana:")) {
            candidate = candidate.substring("katana:".length());
            int nextColon = candidate.indexOf(':');
            if (nextColon > 0) {
                candidate = candidate.substring(0, nextColon);
            }
        } else if (candidate.startsWith("katana_")) {
            candidate = candidate.substring("katana_".length());
            if (candidate.matches("[0-9a-fA-F]{8}")) {
                return "";
            }
        } else if (candidate.contains(":")) {
            candidate = candidate.substring(candidate.indexOf(':') + 1);
        }

        if (candidate.isEmpty()) {
            return "";
        }
        String lowered = candidate.toLowerCase(Locale.ROOT);
        String aliased = LEGACY_KATANA_ID_ALIAS.getOrDefault(lowered, lowered);
        if ("moonglow".equals(aliased)
            || "regret".equals(aliased)
            || "eclipse".equals(aliased)
            || "oblivion".equals(aliased)
            || "nmap".equals(aliased)) {
            return aliased;
        }
        return "";
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
