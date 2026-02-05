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
        
        // ========== Trade System 新增字段 ==========
        /** 声望值 - 仅在成功完成交易时增加 */
        private int reputation;
        /** 是否已赠送首次见面指南 */
        private boolean firstMeetGuideGiven;
        /** 银币掉落窗口开始时间 */
        private long silverWindowStart;
        /** 当前窗口内银币掉落计数 */
        private int silverDropCount;

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

        // ========== Trade System 新增方法 ==========
        
        public int getReputation() {
            return reputation;
        }

        public void setReputation(int reputation) {
            this.reputation = Math.max(0, reputation);
        }

        public void incrementReputation() {
            this.reputation++;
        }

        public boolean isFirstMeetGuideGiven() {
            return firstMeetGuideGiven;
        }

        public void setFirstMeetGuideGiven(boolean firstMeetGuideGiven) {
            this.firstMeetGuideGiven = firstMeetGuideGiven;
        }

        public long getSilverWindowStart() {
            return silverWindowStart;
        }

        public void setSilverWindowStart(long silverWindowStart) {
            this.silverWindowStart = silverWindowStart;
        }

        public int getSilverDropCount() {
            return silverDropCount;
        }

        public void setSilverDropCount(int silverDropCount) {
            this.silverDropCount = silverDropCount;
        }

        /**
         * 检查并尝试记录银币掉落
         * @param currentTime 当前世界时间
         * @param windowTicks 窗口时长
         * @param maxDrops 窗口内最大掉落数
         * @return true 如果允许掉落
         */
        public boolean tryRecordSilverDrop(long currentTime, long windowTicks, int maxDrops) {
            // 检查是否需要重置窗口
            if (currentTime - silverWindowStart >= windowTicks) {
                silverWindowStart = currentTime;
                silverDropCount = 0;
            }
            // 检查是否超过限制
            if (silverDropCount >= maxDrops) {
                return false;
            }
            silverDropCount++;
            return true;
        }

        public NbtCompound toNbt() {
            NbtCompound nbt = new NbtCompound();
            nbt.putInt("TradeCount", this.tradeCount);
            nbt.putBoolean("EligibleNotified", this.eligibleNotified);
            nbt.putBoolean("UnlockedKatanaHidden", this.unlockedKatanaHidden);
            nbt.putBoolean("UnlockedNotified", this.unlockedNotified);
            nbt.putInt("RefreshSeenCount", this.refreshSeenCount);
            // Trade System 新增字段
            nbt.putInt("Reputation", this.reputation);
            nbt.putBoolean("FirstMeetGuideGiven", this.firstMeetGuideGiven);
            nbt.putLong("SilverWindowStart", this.silverWindowStart);
            nbt.putInt("SilverDropCount", this.silverDropCount);
            return nbt;
        }

        public static Progress fromNbt(NbtCompound nbt) {
            Progress progress = new Progress();
            // 硬化补丁：防御性读取，缺失字段使用默认值（int=0, boolean=false）
            if (nbt.contains("TradeCount")) {
                progress.tradeCount = nbt.getInt("TradeCount");
            }
            if (nbt.contains("EligibleNotified")) {
                progress.eligibleNotified = nbt.getBoolean("EligibleNotified");
            }
            if (nbt.contains("UnlockedKatanaHidden")) {
                progress.unlockedKatanaHidden = nbt.getBoolean("UnlockedKatanaHidden");
            }
            if (nbt.contains("UnlockedNotified")) {
                progress.unlockedNotified = nbt.getBoolean("UnlockedNotified");
            }
            if (nbt.contains("RefreshSeenCount")) {
                progress.refreshSeenCount = nbt.getInt("RefreshSeenCount");
            }
            // Trade System 新增字段
            if (nbt.contains("Reputation")) {
                progress.reputation = nbt.getInt("Reputation");
            }
            if (nbt.contains("FirstMeetGuideGiven")) {
                progress.firstMeetGuideGiven = nbt.getBoolean("FirstMeetGuideGiven");
            }
            if (nbt.contains("SilverWindowStart")) {
                progress.silverWindowStart = nbt.getLong("SilverWindowStart");
            }
            if (nbt.contains("SilverDropCount")) {
                progress.silverDropCount = nbt.getInt("SilverDropCount");
            }
            return progress;
        }
    }
}
