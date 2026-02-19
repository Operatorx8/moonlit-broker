package dev.xqanzd.moonlitbroker.util;

import dev.xqanzd.moonlitbroker.trade.KatanaIdUtil;
import dev.xqanzd.moonlitbroker.trade.TradeConfig;
import dev.xqanzd.moonlitbroker.world.KatanaOwnershipState;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side utility for katana contract (active-instance) checks.
 * All effect entry points gate through {@link #isActiveContract} to prevent
 * duplicated / dormant katana instances from triggering abilities.
 */
public final class KatanaContractUtil {
    private KatanaContractUtil() {}

    public static final String NBT_OWNER_UUID = "mm_owner_uuid";
    public static final String NBT_INSTANCE_ID = "mm_instance_id";
    public static final String NBT_RECLAIM = "MM_RECLAIM";
    private static final Set<String> MYTHIC_TYPES = Set.of(
            "moonglow",
            "regret",
            "eclipse",
            "oblivion",
            "nmap");
    private static final Map<String, Long> LAST_HINT_TICK_BY_KEY = new ConcurrentHashMap<>();
    private static final long HINT_COOLDOWN_TICKS = 20L * 30L; // 30s
    private static final int HINT_CACHE_MAX_SIZE = 2048;

    // ========== ItemStack readers ==========

    @Nullable
    public static UUID getOwnerUuid(ItemStack stack) {
        NbtCompound nbt = getCustomNbt(stack);
        if (nbt == null || !nbt.contains(NBT_OWNER_UUID)) return null;
        try {
            return UUID.fromString(nbt.getString(NBT_OWNER_UUID));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Nullable
    public static UUID getInstanceId(ItemStack stack) {
        NbtCompound nbt = getCustomNbt(stack);
        if (nbt == null || !nbt.contains(NBT_INSTANCE_ID)) return null;
        try {
            return UUID.fromString(nbt.getString(NBT_INSTANCE_ID));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static boolean isReclaimOutput(ItemStack stack) {
        NbtCompound nbt = getCustomNbt(stack);
        return nbt != null && nbt.getBoolean(NBT_RECLAIM);
    }

    // ========== ItemStack writers ==========

    /**
     * Write contract fields (owner, type, instanceId) to the stack's custom data.
     * Also writes the existing MM_KATANA_ID field for backward compat.
     */
    public static void writeKatanaContract(ItemStack stack, UUID owner, String type, UUID instanceId) {
        NbtComponent component = stack.get(DataComponentTypes.CUSTOM_DATA);
        NbtCompound nbt = component == null ? new NbtCompound() : component.copyNbt();
        nbt.putString(NBT_OWNER_UUID, owner.toString());
        nbt.putString(NBT_INSTANCE_ID, instanceId.toString());
        nbt.putString(KatanaIdUtil.MM_KATANA_ID, type);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
    }

    // ========== Contract gate ==========

    /**
     * Returns true if the held katana should be allowed to trigger effects.
     * <p>
     * Rules:
     * <ol>
     *   <li>Not a katana → true (pass through, let non-katana items work normally)</li>
     *   <li>Player doesn't own this katana type → false</li>
     *   <li>No activeInstanceId in state (legacy, pre-contract) → true</li>
     *   <li>Stack has no instanceId but contract exists → false (legacy item, now dormant)</li>
     *   <li>Stack instanceId must equal activeInstanceId</li>
     * </ol>
     */
    public static boolean isActiveContract(ServerWorld world, PlayerEntity player, ItemStack stack) {
        String type = KatanaIdUtil.extractCanonicalKatanaId(stack);
        if (type.isEmpty()) return true; // not a katana
        if (TradeConfig.CONTRACT_ENFORCE_ONLY_MYTHIC && !MYTHIC_TYPES.contains(type)) return true;
        if (TradeConfig.CONTRACT_ALLOW_CREATIVE_BYPASS && player.getAbilities().creativeMode) return true;

        KatanaOwnershipState state = KatanaOwnershipState.getServerState(world);
        UUID playerUuid = player.getUuid();

        if (!state.hasOwned(playerUuid, type)) return false;

        UUID activeId = state.getActiveInstanceId(playerUuid, type);
        if (activeId == null) return true; // legacy mode

        UUID instanceId = getInstanceId(stack);
        if (instanceId == null) return false; // legacy item, contract exists
        return instanceId.equals(activeId);
    }

    /**
     * Thin wrapper for effect entry points so handlers can share one gate call.
     */
    public static boolean gateOrReturn(ServerWorld world, PlayerEntity player, ItemStack stack) {
        String type = KatanaIdUtil.extractCanonicalKatanaId(stack);
        if (type.isEmpty()) return true; // not a katana
        if (TradeConfig.CONTRACT_ENFORCE_ONLY_MYTHIC && !MYTHIC_TYPES.contains(type)) return true;
        if (TradeConfig.CONTRACT_ALLOW_CREATIVE_BYPASS && player.getAbilities().creativeMode) return true;

        KatanaOwnershipState state = KatanaOwnershipState.getServerState(world);
        UUID playerUuid = player.getUuid();

        if (!state.hasOwned(playerUuid, type)) {
            maybeSendGateHint(world, player, type, "unowned",
                    "msg.xqanzd_moonlit_broker.contract.unowned");
            return false;
        }

        UUID activeId = state.getActiveInstanceId(playerUuid, type);
        if (activeId == null) return true; // legacy mode

        UUID instanceId = getInstanceId(stack);
        if (instanceId == null || !instanceId.equals(activeId)) {
            maybeSendGateHint(world, player, type, "dormant",
                    "msg.xqanzd_moonlit_broker.contract.dormant");
            return false;
        }

        return true;
    }

    /**
     * Returns true if the katana is owned by this player but its instance is NOT the active one.
     * Used to reject dormant katanas from anvil repair.
     */
    public static boolean isDormant(ServerWorld world, PlayerEntity player, ItemStack stack) {
        String type = KatanaIdUtil.extractCanonicalKatanaId(stack);
        if (type.isEmpty()) return false;
        if (TradeConfig.CONTRACT_ENFORCE_ONLY_MYTHIC && !MYTHIC_TYPES.contains(type)) return false;
        if (TradeConfig.CONTRACT_ALLOW_CREATIVE_BYPASS && player.getAbilities().creativeMode) return false;

        KatanaOwnershipState state = KatanaOwnershipState.getServerState(world);
        UUID playerUuid = player.getUuid();

        if (!state.hasOwned(playerUuid, type)) return false;

        UUID activeId = state.getActiveInstanceId(playerUuid, type);
        if (activeId == null) return false; // legacy, not dormant

        UUID instanceId = getInstanceId(stack);
        if (instanceId == null) return true; // legacy item but contract exists → dormant
        return !instanceId.equals(activeId);
    }

    private static void maybeSendGateHint(
            ServerWorld world,
            PlayerEntity player,
            String type,
            String reason,
            String messageKey) {
        if (!TradeConfig.DORMANT_SHOW_ACTIONBAR_HINT) {
            return;
        }
        ServerWorld overworld = Objects.requireNonNull(
                world.getServer().getWorld(World.OVERWORLD),
                "Overworld is null (server world not available)");
        long now = overworld.getTime();
        String key = player.getUuid() + ":" + type + ":" + reason;
        Long last = LAST_HINT_TICK_BY_KEY.get(key);
        if (last != null && now - last < HINT_COOLDOWN_TICKS) {
            return;
        }
        if (LAST_HINT_TICK_BY_KEY.size() > HINT_CACHE_MAX_SIZE) {
            LAST_HINT_TICK_BY_KEY.clear();
        }
        LAST_HINT_TICK_BY_KEY.put(key, now);
        player.sendMessage(Text.translatable(messageKey).formatted(Formatting.GRAY), true);
    }

    // ========== Internal ==========

    @Nullable
    private static NbtCompound getCustomNbt(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        NbtComponent component = stack.get(DataComponentTypes.CUSTOM_DATA);
        return component == null ? null : component.copyNbt();
    }
}
