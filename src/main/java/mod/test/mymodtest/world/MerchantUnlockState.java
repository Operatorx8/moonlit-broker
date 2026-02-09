package mod.test.mymodtest.world;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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

    public Progress getOrCreateProgress(UUID playerUuid, String variantKey) {
        Progress progress = getOrCreateProgress(playerUuid);
        progress.ensureVariantUnlockScope(variantKey);
        return progress;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        NbtCompound playersNbt = new NbtCompound();
        for (Map.Entry<UUID, Progress> entry : progressByPlayer.entrySet()) {
            playersNbt.put(entry.getKey().toString(), entry.getValue().toNbt(entry.getKey()));
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
        private static final String NBT_REFRESH_LEGACY = "RefreshSeenCount";
        private static final String NBT_REFRESH_MAP = "SigilRefreshByMerchant";
        private static final String NBT_LAST_SIGIL_HASH_LEGACY = "LastSigilOffersHash";
        private static final String NBT_LAST_SIGIL_HASH_MAP = "LastSigilOffersHashByMerchant";
        private static final String NBT_VARIANT_UNLOCK_MAP = "VariantUnlockByKey";
        private static final String NBT_PURCHASED_SECRET_IDS = "PurchasedSecretKatanaIds";
        // Cap is intentionally bounded to avoid unbounded save growth.
        // When exceeded, oldest UUID-ordered tail entries are truncated by design.
        private static final int MAX_PER_MERCHANT_ENTRIES = 2048;
        private static final int MAX_VARIANT_ENTRIES = 16;
        private static final int MAX_PURCHASED_SECRET_IDS = 1024;
        // Global de-noise: deprecated API warning should emit once per JVM lifetime.
        private static boolean deprecatedRefreshApiWarned = false;

        private int tradeCount;
        private boolean eligibleNotified;
        private boolean unlockedKatanaHidden;
        private boolean unlockedNotified;
        /** Variant-scoped unlock state: key=(playerUuid,variantKey). */
        private final Map<String, VariantUnlockProgress> unlockByVariant = new HashMap<>();
        // ========== Per-merchant refresh count (replaces global refreshSeenCount) ==========
        /** Per-(player,merchant) refresh seen count map. Key = merchantUuid */
        private final Map<UUID, Integer> sigilRefreshSeenByMerchant = new HashMap<>();
        /** Legacy global refreshSeenCount - used as fallback for old saves migration */
        private int legacyRefreshSeenCount = -1; // -1 = no legacy data
        
        // ========== Trade System 新增字段 ==========
        /** 声望值 - 仅在成功完成交易时增加 */
        private int reputation;
        /** 是否已赠送首次见面指南 */
        private boolean firstMeetGuideGiven;
        /** 银币掉落窗口开始时间 */
        private long silverWindowStart;
        /** 当前窗口内银币掉落计数 */
        private int silverDropCount;

        // ========== P0-A FIX: per-player sigil offers hash (跨玩家隔离) ==========
        /** Per-(player,merchant) 上次 sigil offers hash */
        private final Map<UUID, Integer> lastSigilOffersHashByMerchant = new HashMap<>();
        /** Legacy global hash fallback for migration */
        private int legacyLastSigilOffersHash = 0;
        /** Per-player purchased secret IDs; used to render global sold-out across merchants. */
        private final Set<String> purchasedSecretKatanaIds = new HashSet<>();
        private boolean invalidRefreshUuidWarned = false;
        private boolean invalidHashUuidWarned = false;
        private boolean refreshCapWarned = false;
        private boolean hashCapWarned = false;
        private boolean variantCapWarned = false;
        private boolean purchasedCapWarned = false;

        private static final class VariantUnlockProgress {
            private int tradeCount;
            private boolean eligibleNotified;
            private boolean unlockedKatanaHidden;
            private boolean unlockedNotified;
        }

        private static String normalizeVariantKey(String variantKey) {
            if (variantKey == null) {
                return "STANDARD";
            }
            String key = variantKey.trim();
            if (key.isEmpty()) {
                return "STANDARD";
            }
            return key.toUpperCase(Locale.ROOT);
        }

        private boolean hasLegacyUnlockState() {
            return this.tradeCount > 0
                || this.eligibleNotified
                || this.unlockedKatanaHidden
                || this.unlockedNotified;
        }

        private VariantUnlockProgress toVariantSnapshot() {
            VariantUnlockProgress snapshot = new VariantUnlockProgress();
            snapshot.tradeCount = this.tradeCount;
            snapshot.eligibleNotified = this.eligibleNotified;
            snapshot.unlockedKatanaHidden = this.unlockedKatanaHidden;
            snapshot.unlockedNotified = this.unlockedNotified;
            return snapshot;
        }

        private VariantUnlockProgress getVariantUnlockView(String variantKey) {
            String key = normalizeVariantKey(variantKey);
            VariantUnlockProgress scoped = this.unlockByVariant.get(key);
            if (scoped != null) {
                return scoped;
            }
            if (this.unlockByVariant.isEmpty() && hasLegacyUnlockState()) {
                return toVariantSnapshot();
            }
            return new VariantUnlockProgress();
        }

        private VariantUnlockProgress getOrCreateVariantUnlock(String variantKey) {
            String key = normalizeVariantKey(variantKey);
            VariantUnlockProgress scoped = this.unlockByVariant.get(key);
            if (scoped != null) {
                return scoped;
            }
            scoped = this.unlockByVariant.isEmpty() && hasLegacyUnlockState()
                ? toVariantSnapshot()
                : new VariantUnlockProgress();
            this.unlockByVariant.put(key, scoped);
            return scoped;
        }

        public void ensureVariantUnlockScope(String variantKey) {
            getOrCreateVariantUnlock(variantKey);
        }

        public int getTradeCount(String variantKey) {
            return getVariantUnlockView(variantKey).tradeCount;
        }

        public void setTradeCount(String variantKey, int tradeCount) {
            getOrCreateVariantUnlock(variantKey).tradeCount = Math.max(0, tradeCount);
        }

        public boolean isEligibleNotified(String variantKey) {
            return getVariantUnlockView(variantKey).eligibleNotified;
        }

        public void setEligibleNotified(String variantKey, boolean eligibleNotified) {
            getOrCreateVariantUnlock(variantKey).eligibleNotified = eligibleNotified;
        }

        public boolean isUnlockedKatanaHidden(String variantKey) {
            return getVariantUnlockView(variantKey).unlockedKatanaHidden;
        }

        public void setUnlockedKatanaHidden(String variantKey, boolean unlockedKatanaHidden) {
            getOrCreateVariantUnlock(variantKey).unlockedKatanaHidden = unlockedKatanaHidden;
        }

        public boolean isUnlockedNotified(String variantKey) {
            return getVariantUnlockView(variantKey).unlockedNotified;
        }

        public void setUnlockedNotified(String variantKey, boolean unlockedNotified) {
            getOrCreateVariantUnlock(variantKey).unlockedNotified = unlockedNotified;
        }

        public boolean hasPurchasedSecretKatana(String secretKatanaId) {
            if (secretKatanaId == null || secretKatanaId.isEmpty()) {
                return false;
            }
            return this.purchasedSecretKatanaIds.contains(secretKatanaId);
        }

        public boolean markSecretKatanaPurchased(String secretKatanaId) {
            if (secretKatanaId == null || secretKatanaId.isEmpty()) {
                return false;
            }
            return this.purchasedSecretKatanaIds.add(secretKatanaId);
        }

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

        /**
         * @deprecated Use getSigilRefreshSeen(merchantUuid) instead. Kept for compile-compat during migration.
         * Returns the legacy global value or 0.
         */
        @Deprecated
        public int getRefreshSeenCount() {
            if (!deprecatedRefreshApiWarned) {
                deprecatedRefreshApiWarned = true;
                LOGGER.warn("[MoonTrade] DEPRECATED_API method=getRefreshSeenCount note=use_getSigilRefreshSeen_by_merchant");
            }
            return legacyRefreshSeenCount > 0 ? legacyRefreshSeenCount : 0;
        }

        /**
         * @deprecated Use incSigilRefreshSeen(merchantUuid) instead.
         */
        @Deprecated
        public void setRefreshSeenCount(int refreshSeenCount) {
            if (!deprecatedRefreshApiWarned) {
                deprecatedRefreshApiWarned = true;
                LOGGER.warn("[MoonTrade] DEPRECATED_API method=setRefreshSeenCount note=legacy_value_written_only");
            }
            this.legacyRefreshSeenCount = Math.max(0, refreshSeenCount);
        }

        /**
         * Get the refresh-seen count for a specific merchant.
         * Migration: if no per-merchant entry exists, falls back to legacy global value (or 0).
         */
        public record RefreshCountReadResult(int count, String source) {}

        public RefreshCountReadResult readSigilRefreshSeen(UUID merchantUuid) {
            if (merchantUuid == null) {
                if (!invalidRefreshUuidWarned) {
                    invalidRefreshUuidWarned = true;
                    LOGGER.warn("[MoonTrade] REFRESH_COUNT_INVALID_UUID playerUuid=unknown merchantUuid=null page=UNKNOWN before=-1 after=-1 source=invalid_uuid action=read");
                }
                if (legacyRefreshSeenCount > 0) {
                    return new RefreshCountReadResult(legacyRefreshSeenCount, "invalid_uuid_legacy");
                }
                return new RefreshCountReadResult(0, "invalid_uuid_init");
            }
            Integer count = sigilRefreshSeenByMerchant.get(merchantUuid);
            if (count != null) {
                return new RefreshCountReadResult(count, "map");
            }
            // Migration fallback
            if (legacyRefreshSeenCount > 0) {
                return new RefreshCountReadResult(legacyRefreshSeenCount, "legacy");
            }
            return new RefreshCountReadResult(0, "init");
        }

        public int getSigilRefreshSeen(UUID merchantUuid) {
            return readSigilRefreshSeen(merchantUuid).count();
        }

        /**
         * Increment the refresh-seen count for a specific merchant.
         * If no entry exists, initializes from legacy value (or 0) before incrementing.
         * @return the new count after increment
         */
        public int incSigilRefreshSeen(UUID merchantUuid) {
            if (merchantUuid == null) {
                if (!invalidRefreshUuidWarned) {
                    invalidRefreshUuidWarned = true;
                    LOGGER.warn("[MoonTrade] REFRESH_COUNT_INVALID_UUID playerUuid=unknown merchantUuid=null page=UNKNOWN before=-1 after=-1 source=invalid_uuid action=inc");
                }
                return getSigilRefreshSeen(null);
            }
            int before = getSigilRefreshSeen(merchantUuid);
            int after = before + 1;
            sigilRefreshSeenByMerchant.put(merchantUuid, after);
            return after;
        }

        /**
         * Rollback the refresh-seen count for a specific merchant (used when refresh produces no change).
         */
        public void setSigilRefreshSeen(UUID merchantUuid, int value) {
            if (merchantUuid == null) {
                if (!invalidRefreshUuidWarned) {
                    invalidRefreshUuidWarned = true;
                    LOGGER.warn("[MoonTrade] REFRESH_COUNT_INVALID_UUID playerUuid=unknown merchantUuid=null page=UNKNOWN before=-1 after=-1 source=invalid_uuid action=set");
                }
                return;
            }
            sigilRefreshSeenByMerchant.put(merchantUuid, Math.max(0, value));
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

        // ========== P0-A FIX: sigil hash getter/setter ==========
        public int getLastSigilOffersHash(UUID merchantUuid) {
            if (merchantUuid == null) {
                if (!invalidHashUuidWarned) {
                    invalidHashUuidWarned = true;
                    LOGGER.warn("[MoonTrade] SIGIL_HASH_INVALID_UUID playerUuid=unknown merchantUuid=null page=UNKNOWN source=invalid_uuid action=read");
                }
                return legacyLastSigilOffersHash;
            }
            Integer hash = lastSigilOffersHashByMerchant.get(merchantUuid);
            if (hash != null) {
                return hash;
            }
            return legacyLastSigilOffersHash;
        }

        public void setLastSigilOffersHash(UUID merchantUuid, int hash) {
            if (merchantUuid == null) {
                if (!invalidHashUuidWarned) {
                    invalidHashUuidWarned = true;
                    LOGGER.warn("[MoonTrade] SIGIL_HASH_INVALID_UUID playerUuid=unknown merchantUuid=null page=UNKNOWN source=invalid_uuid action=write");
                }
                return;
            }
            this.lastSigilOffersHashByMerchant.put(merchantUuid, hash);
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

        public NbtCompound toNbt(UUID playerUuid) {
            NbtCompound nbt = new NbtCompound();
            nbt.putInt("TradeCount", this.tradeCount);
            nbt.putBoolean("EligibleNotified", this.eligibleNotified);
            nbt.putBoolean("UnlockedKatanaHidden", this.unlockedKatanaHidden);
            nbt.putBoolean("UnlockedNotified", this.unlockedNotified);
            int variantDrop = Math.max(0, this.unlockByVariant.size() - MAX_VARIANT_ENTRIES);
            if (variantDrop > 0 && !variantCapWarned) {
                variantCapWarned = true;
                LOGGER.warn("[MoonTrade] VARIANT_UNLOCK_MAP_CAP_TRUNCATE playerUuid={} cap={} drop={} source=save",
                    playerUuid, MAX_VARIANT_ENTRIES, variantDrop);
            }
            NbtList variantList = new NbtList();
            this.unlockByVariant.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .limit(MAX_VARIANT_ENTRIES)
                .forEach(entry -> {
                    VariantUnlockProgress scoped = entry.getValue();
                    NbtCompound entryNbt = new NbtCompound();
                    entryNbt.putString("variant", entry.getKey());
                    entryNbt.putInt("tradeCount", scoped.tradeCount);
                    entryNbt.putBoolean("eligibleNotified", scoped.eligibleNotified);
                    entryNbt.putBoolean("unlockedKatanaHidden", scoped.unlockedKatanaHidden);
                    entryNbt.putBoolean("unlockedNotified", scoped.unlockedNotified);
                    variantList.add(entryNbt);
                });
            nbt.put(NBT_VARIANT_UNLOCK_MAP, variantList);
            nbt.putInt(NBT_REFRESH_LEGACY, this.legacyRefreshSeenCount > 0 ? this.legacyRefreshSeenCount : 0);
            // Per-merchant refresh seen counts
            int refreshDrop = Math.max(0, this.sigilRefreshSeenByMerchant.size() - MAX_PER_MERCHANT_ENTRIES);
            if (refreshDrop > 0 && !refreshCapWarned) {
                refreshCapWarned = true;
                LOGGER.warn("[MoonTrade] REFRESH_MAP_CAP_TRUNCATE playerUuid={} cap={} drop={} source=save",
                    playerUuid, MAX_PER_MERCHANT_ENTRIES, refreshDrop);
            }
            NbtList refreshList = new NbtList();
            this.sigilRefreshSeenByMerchant.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .limit(MAX_PER_MERCHANT_ENTRIES)
                .forEach(entry -> {
                NbtCompound entryNbt = new NbtCompound();
                entryNbt.putString("merchant", entry.getKey().toString());
                entryNbt.putInt("count", entry.getValue());
                refreshList.add(entryNbt);
                });
            nbt.put(NBT_REFRESH_MAP, refreshList);
            // Trade System 新增字段
            nbt.putInt("Reputation", this.reputation);
            nbt.putBoolean("FirstMeetGuideGiven", this.firstMeetGuideGiven);
            nbt.putLong("SilverWindowStart", this.silverWindowStart);
            nbt.putInt("SilverDropCount", this.silverDropCount);
            int purchasedDrop = Math.max(0, this.purchasedSecretKatanaIds.size() - MAX_PURCHASED_SECRET_IDS);
            if (purchasedDrop > 0 && !purchasedCapWarned) {
                purchasedCapWarned = true;
                LOGGER.warn("[MoonTrade] SECRET_PURCHASED_SET_CAP_TRUNCATE playerUuid={} cap={} drop={} source=save",
                    playerUuid, MAX_PURCHASED_SECRET_IDS, purchasedDrop);
            }
            NbtList purchasedList = new NbtList();
            this.purchasedSecretKatanaIds.stream()
                .sorted()
                .limit(MAX_PURCHASED_SECRET_IDS)
                .forEach(secretId -> purchasedList.add(NbtString.of(secretId)));
            nbt.put(NBT_PURCHASED_SECRET_IDS, purchasedList);
            // Sigil hash migration + per-merchant map
            nbt.putInt(NBT_LAST_SIGIL_HASH_LEGACY, this.legacyLastSigilOffersHash);
            int hashDrop = Math.max(0, this.lastSigilOffersHashByMerchant.size() - MAX_PER_MERCHANT_ENTRIES);
            if (hashDrop > 0 && !hashCapWarned) {
                hashCapWarned = true;
                LOGGER.warn("[MoonTrade] SIGIL_HASH_MAP_CAP_TRUNCATE playerUuid={} cap={} drop={} source=save",
                    playerUuid, MAX_PER_MERCHANT_ENTRIES, hashDrop);
            }
            NbtList hashList = new NbtList();
            this.lastSigilOffersHashByMerchant.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .limit(MAX_PER_MERCHANT_ENTRIES)
                .forEach(entry -> {
                    NbtCompound entryNbt = new NbtCompound();
                    entryNbt.putString("merchant", entry.getKey().toString());
                    entryNbt.putInt("hash", entry.getValue());
                    hashList.add(entryNbt);
                });
            nbt.put(NBT_LAST_SIGIL_HASH_MAP, hashList);
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
            if (nbt.contains(NBT_VARIANT_UNLOCK_MAP, NbtElement.LIST_TYPE)) {
                NbtList variantList = nbt.getList(NBT_VARIANT_UNLOCK_MAP, NbtElement.COMPOUND_TYPE);
                for (int i = 0; i < variantList.size(); i++) {
                    NbtCompound entryNbt = variantList.getCompound(i);
                    String variant = normalizeVariantKey(entryNbt.getString("variant"));
                    VariantUnlockProgress scoped = new VariantUnlockProgress();
                    scoped.tradeCount = Math.max(0, entryNbt.getInt("tradeCount"));
                    scoped.eligibleNotified = entryNbt.getBoolean("eligibleNotified");
                    scoped.unlockedKatanaHidden = entryNbt.getBoolean("unlockedKatanaHidden");
                    scoped.unlockedNotified = entryNbt.getBoolean("unlockedNotified");
                    progress.unlockByVariant.put(variant, scoped);
                }
            }
            if (nbt.contains(NBT_REFRESH_LEGACY)) {
                progress.legacyRefreshSeenCount = nbt.getInt(NBT_REFRESH_LEGACY);
            }
            // Per-merchant refresh seen counts
            if (nbt.contains(NBT_REFRESH_MAP, NbtElement.LIST_TYPE)) {
                NbtList refreshList = nbt.getList(NBT_REFRESH_MAP, NbtElement.COMPOUND_TYPE);
                for (int i = 0; i < refreshList.size(); i++) {
                    NbtCompound entryNbt = refreshList.getCompound(i);
                    try {
                        UUID merchantUuid = UUID.fromString(entryNbt.getString("merchant"));
                        int count = Math.max(0, entryNbt.getInt("count"));
                        progress.sigilRefreshSeenByMerchant.put(merchantUuid, count);
                    } catch (IllegalArgumentException e) {
                        LOGGER.warn("[MerchantUnlockState] Invalid merchant UUID in SigilRefreshByMerchant: {}", entryNbt.getString("merchant"));
                    }
                }
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
            if (nbt.contains(NBT_PURCHASED_SECRET_IDS, NbtElement.LIST_TYPE)) {
                NbtList purchasedList = nbt.getList(NBT_PURCHASED_SECRET_IDS, NbtElement.STRING_TYPE);
                for (int i = 0; i < purchasedList.size(); i++) {
                    String secretId = purchasedList.get(i).asString();
                    if (!secretId.isEmpty()) {
                        progress.purchasedSecretKatanaIds.add(secretId);
                    }
                }
            }
            // Sigil hash migration + per-merchant map
            if (nbt.contains(NBT_LAST_SIGIL_HASH_LEGACY)) {
                progress.legacyLastSigilOffersHash = nbt.getInt(NBT_LAST_SIGIL_HASH_LEGACY);
            }
            if (nbt.contains(NBT_LAST_SIGIL_HASH_MAP, NbtElement.LIST_TYPE)) {
                NbtList hashList = nbt.getList(NBT_LAST_SIGIL_HASH_MAP, NbtElement.COMPOUND_TYPE);
                for (int i = 0; i < hashList.size(); i++) {
                    NbtCompound entryNbt = hashList.getCompound(i);
                    try {
                        UUID merchantUuid = UUID.fromString(entryNbt.getString("merchant"));
                        int hash = entryNbt.getInt("hash");
                        progress.lastSigilOffersHashByMerchant.put(merchantUuid, hash);
                    } catch (IllegalArgumentException e) {
                        LOGGER.warn("[MerchantUnlockState] Invalid merchant UUID in LastSigilOffersHashByMerchant: {}", entryNbt.getString("merchant"));
                    }
                }
            }
            return progress;
        }
    }
}
