package mod.test.mymodtest.world;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 神秘商人解封系统的玩家进度持久化状态
 * 存储位置: world/data/mymodtest_merchant_unlock.dat
 */
public class MerchantUnlockState extends PersistentState {
    private static final Logger LOGGER = LoggerFactory.getLogger(MerchantUnlockState.class);

    private static final String DATA_NAME = "mymodtest_merchant_unlock";
    private static final String NBT_PLAYERS = "Players";

    private final Map<UUID, Progress> progressByPlayer = new HashMap<>();

    public MerchantUnlockState() {
    }

    private static final Type<MerchantUnlockState> TYPE = new Type<>(
            MerchantUnlockState::new,
            MerchantUnlockState::fromNbt,
            null
    );

    public static MerchantUnlockState getServerState(ServerWorld world) {
        MinecraftServer server = world.getServer();
        ServerWorld overworld = server.getWorld(World.OVERWORLD);
        assert overworld != null;
        return overworld.getPersistentStateManager().getOrCreate(TYPE, DATA_NAME);
    }

    public Progress getOrCreateProgress(UUID playerUuid) {
        return progressByPlayer.computeIfAbsent(playerUuid, key -> new Progress());
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        NbtCompound playersNbt = new NbtCompound();
        for (Map.Entry<UUID, Progress> entry : progressByPlayer.entrySet()) {
            playersNbt.put(entry.getKey().toString(), entry.getValue().toNbt());
        }
        nbt.put(NBT_PLAYERS, playersNbt);
        return nbt;
    }

    public static MerchantUnlockState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        MerchantUnlockState state = new MerchantUnlockState();
        if (nbt.contains(NBT_PLAYERS)) {
            NbtCompound playersNbt = nbt.getCompound(NBT_PLAYERS);
            for (String key : playersNbt.getKeys()) {
                try {
                    UUID uuid = UUID.fromString(key);
                    NbtCompound progressNbt = playersNbt.getCompound(key);
                    state.progressByPlayer.put(uuid, Progress.fromNbt(progressNbt));
                } catch (IllegalArgumentException e) {
                    LOGGER.warn("[MerchantUnlockState] 无效 UUID: {}", key);
                }
            }
        }
        return state;
    }

    public static class Progress {
        private int tradeCount;
        private boolean eligibleNotified;
        private boolean unlockedKatanaHidden;
        private boolean unlockedNotified;
        private int refreshSeenCount;

        public int getTradeCount() {
            return tradeCount;
        }

        public void setTradeCount(int tradeCount) {
            this.tradeCount = tradeCount;
        }

        public boolean isEligibleNotified() {
            return eligibleNotified;
        }

        public void setEligibleNotified(boolean eligibleNotified) {
            this.eligibleNotified = eligibleNotified;
        }

        public boolean isUnlockedKatanaHidden() {
            return unlockedKatanaHidden;
        }

        public void setUnlockedKatanaHidden(boolean unlockedKatanaHidden) {
            this.unlockedKatanaHidden = unlockedKatanaHidden;
        }

        public boolean isUnlockedNotified() {
            return unlockedNotified;
        }

        public void setUnlockedNotified(boolean unlockedNotified) {
            this.unlockedNotified = unlockedNotified;
        }

        public int getRefreshSeenCount() {
            return refreshSeenCount;
        }

        public void setRefreshSeenCount(int refreshSeenCount) {
            this.refreshSeenCount = refreshSeenCount;
        }

        public NbtCompound toNbt() {
            NbtCompound nbt = new NbtCompound();
            nbt.putInt("TradeCount", this.tradeCount);
            nbt.putBoolean("EligibleNotified", this.eligibleNotified);
            nbt.putBoolean("UnlockedKatanaHidden", this.unlockedKatanaHidden);
            nbt.putBoolean("UnlockedNotified", this.unlockedNotified);
            nbt.putInt("RefreshSeenCount", this.refreshSeenCount);
            return nbt;
        }

        public static Progress fromNbt(NbtCompound nbt) {
            Progress progress = new Progress();
            progress.tradeCount = nbt.getInt("TradeCount");
            progress.eligibleNotified = nbt.getBoolean("EligibleNotified");
            progress.unlockedKatanaHidden = nbt.getBoolean("UnlockedKatanaHidden");
            progress.unlockedNotified = nbt.getBoolean("UnlockedNotified");
            progress.refreshSeenCount = nbt.getInt("RefreshSeenCount");
            return progress;
        }
    }
}
