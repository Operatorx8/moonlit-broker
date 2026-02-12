package dev.xqanzd.moonlitbroker.world;

import dev.xqanzd.moonlitbroker.entity.MysteriousMerchantEntity;
import dev.xqanzd.moonlitbroker.entity.spawn.MysteriousMerchantSpawner;
import dev.xqanzd.moonlitbroker.registry.ModEntities;
import dev.xqanzd.moonlitbroker.trade.TradeConfig;
import net.minecraft.entity.EntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.village.VillagerType;
import net.minecraft.world.Heightmap;
import net.minecraft.world.PersistentState;
import net.minecraft.world.SpawnHelper;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * 神秘商人生成器的持久化状态
 * 存储位置: world/data/xqanzd_moonlit_broker_merchant_spawner.dat
 *
 * 使用 PersistentState.Type 进行序列化（Fabric 1.21.1 API）
 */
public class MerchantSpawnerState extends PersistentState {
    private static final Logger LOGGER = LoggerFactory.getLogger(MerchantSpawnerState.class);

    // 全局状态
    private long lastSpawnDay;              // 上次生成的游戏天数
    private int spawnCountToday;            // 今天已生成数量
    private long cooldownUntil;             // 全局冷却结束时间（世界tick，绝对值）
    private int totalSpawnedCount;          // 历史总生成数（统计用）

    // 活跃商人追踪（防重复生成核心）
    private UUID activeMerchantUuid;        // 当前活跃商人的 UUID（null 表示无活跃商人）
    private long activeMerchantExpireAt;    // 活跃商人预期过期时间（保险机制）

    // Bell 召唤预约（同一时刻仅保留一个全局请求）
    private SummonRequest summonRequest;

    // 常量
    private static final String DATA_NAME = "xqanzd_moonlit_broker_merchant_spawner";
    private static final String NBT_SUMMON_REQUEST = "summonRequest";

    private boolean processingSummonTick = false;

    private static final class SummonRequest {
        private final UUID playerUuid;
        private final BlockPos bellPos;
        private final long scheduledTick;

        private SummonRequest(UUID playerUuid, BlockPos bellPos, long scheduledTick) {
            this.playerUuid = playerUuid;
            this.bellPos = bellPos.toImmutable();
            this.scheduledTick = Math.max(0L, scheduledTick);
        }

        private UUID playerUuid() {
            return playerUuid;
        }

        private BlockPos bellPos() {
            return bellPos;
        }

        private long scheduledTick() {
            return scheduledTick;
        }

        private SummonRequest withScheduledTick(long nextTick) {
            return new SummonRequest(this.playerUuid, this.bellPos, nextTick);
        }

        private NbtCompound toNbt() {
            NbtCompound nbt = new NbtCompound();
            nbt.putUuid("playerUuid", this.playerUuid);
            nbt.putLong("bellPos", this.bellPos.asLong());
            nbt.putLong("scheduledTick", this.scheduledTick);
            return nbt;
        }

        private static SummonRequest fromNbt(NbtCompound nbt) {
            if (!nbt.containsUuid("playerUuid") || !nbt.contains("bellPos") || !nbt.contains("scheduledTick")) {
                return null;
            }
            UUID playerUuid = nbt.getUuid("playerUuid");
            BlockPos bellPos = BlockPos.fromLong(nbt.getLong("bellPos"));
            long scheduledTick = nbt.getLong("scheduledTick");
            return new SummonRequest(playerUuid, bellPos, scheduledTick);
        }
    }

    // ========== 构造函数 ==========

    public MerchantSpawnerState() {
        this.lastSpawnDay = -1;
        this.spawnCountToday = 0;
        this.cooldownUntil = 0;
        this.totalSpawnedCount = 0;
        this.activeMerchantUuid = null;
        this.activeMerchantExpireAt = 0;
        this.summonRequest = null;
    }

    // ========== PersistentState.Type ==========

    private static final Type<MerchantSpawnerState> TYPE = new Type<>(
            MerchantSpawnerState::new,
            MerchantSpawnerState::fromNbt,
            null  // DataFixTypes
    );

    // ========== NBT 序列化 ==========

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        nbt.putLong("lastSpawnDay", this.lastSpawnDay);
        nbt.putInt("spawnCountToday", this.spawnCountToday);
        nbt.putLong("cooldownUntil", this.cooldownUntil);
        nbt.putInt("totalSpawnedCount", this.totalSpawnedCount);
        nbt.putLong("activeMerchantExpireAt", this.activeMerchantExpireAt);

        if (this.activeMerchantUuid != null) {
            nbt.putUuid("activeMerchantUuid", this.activeMerchantUuid);
        }
        if (this.summonRequest != null) {
            nbt.put(NBT_SUMMON_REQUEST, this.summonRequest.toNbt());
        }

        return nbt;
    }

    public static MerchantSpawnerState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        MerchantSpawnerState state = new MerchantSpawnerState();
        state.lastSpawnDay = nbt.getLong("lastSpawnDay");
        state.spawnCountToday = nbt.getInt("spawnCountToday");
        state.cooldownUntil = nbt.getLong("cooldownUntil");
        state.totalSpawnedCount = nbt.getInt("totalSpawnedCount");
        state.activeMerchantExpireAt = nbt.getLong("activeMerchantExpireAt");

        if (nbt.containsUuid("activeMerchantUuid")) {
            state.activeMerchantUuid = nbt.getUuid("activeMerchantUuid");
        }
        if (nbt.contains(NBT_SUMMON_REQUEST)) {
            SummonRequest request = SummonRequest.fromNbt(nbt.getCompound(NBT_SUMMON_REQUEST));
            if (request != null) {
                state.summonRequest = request;
            }
        }

        return state;
    }

    // ========== 获取实例（关键方法） ==========

    /**
     * 获取服务端状态实例
     * 使用 Overworld 的 PersistentStateManager，确保所有维度共享同一份数据
     */
    public static MerchantSpawnerState getServerState(ServerWorld world) {
        MinecraftServer server = world.getServer();
        ServerWorld overworld = server.getWorld(World.OVERWORLD);
        assert overworld != null;

        MerchantSpawnerState state = overworld.getPersistentStateManager().getOrCreate(TYPE, DATA_NAME);
        return state;
    }

    private static String shortUuid(UUID uuid) {
        return uuid == null ? "null" : uuid.toString().substring(0, 8);
    }

    private void refreshDayCounter(ServerWorld world) {
        long currentDay = world.getTimeOfDay() / 24000L;

        if (currentDay != this.lastSpawnDay) {
            if (MysteriousMerchantSpawner.DEBUG) {
                LOGGER.debug("[SpawnerState] NEW_DAY previousDay={} currentDay={} resetting spawnCountToday from {} to 0",
                    this.lastSpawnDay, currentDay, this.spawnCountToday);
            }
            this.lastSpawnDay = currentDay;
            this.spawnCountToday = 0;
            this.markDirty();
        }
    }

    private BlockPos findSummonSpawnPosition(ServerWorld world, BlockPos center, Random random) {
        int range = TradeConfig.SUMMON_SPAWN_RANGE;
        for (int attempt = 0; attempt < TradeConfig.SUMMON_SPAWN_ATTEMPTS; attempt++) {
            int x = center.getX() + random.nextInt(range * 2 + 1) - range;
            int z = center.getZ() + random.nextInt(range * 2 + 1) - range;

            int y = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z);
            BlockPos testPos = new BlockPos(x, y, z);

            if (isValidSummonSpawnPosition(world, testPos)) {
                return testPos;
            }
        }
        return null;
    }

    private boolean isValidSummonSpawnPosition(ServerWorld world, BlockPos pos) {
        if (!world.getBlockState(pos.down()).isSolidBlock(world, pos.down())) {
            return false;
        }

        if (!world.getBlockState(pos).isAir()) {
            return false;
        }
        if (!world.getBlockState(pos.up()).isAir()) {
            return false;
        }

        return SpawnHelper.isClearForSpawn(
            world,
            pos,
            world.getBlockState(pos),
            world.getFluidState(pos),
            ModEntities.MYSTERIOUS_MERCHANT
        );
    }

    /**
     * 预约到点检查：到时强制执行 SUMMON 生成。
     * 失败策略：保留请求并延后重试；成功后清理请求。
     */
    public void tick(ServerWorld world) {
        if (this.processingSummonTick) {
            return;
        }
        if (world.getRegistryKey() != World.OVERWORLD) {
            return;
        }
        SummonRequest request = this.summonRequest;
        if (request == null) {
            return;
        }

        long currentTick = world.getTime();
        if (currentTick < request.scheduledTick()) {
            return;
        }

        this.processingSummonTick = true;
        try {
            if (request.playerUuid() == null) {
                LOGGER.warn("SUMMON_BLOCKED(reason=INVALID_REQUEST action=CLEAR)");
                this.summonRequest = null;
                this.markDirty();
                return;
            }

            if (hasActiveMerchant(world)) {
                long retryTick = currentTick + TradeConfig.SUMMON_RETRY_DELAY_TICKS;
                this.summonRequest = request.withScheduledTick(retryTick);
                this.markDirty();
                LOGGER.info("SUMMON_BLOCKED(reason=ACTIVE_MERCHANT_LOCK action=RETRY retryTick={} activeMerchant={})",
                    retryTick, shortUuid(this.activeMerchantUuid));
                return;
            }

            BlockPos spawnPos = findSummonSpawnPosition(world, request.bellPos(), world.getRandom());
            if (spawnPos == null) {
                long retryTick = currentTick + TradeConfig.SUMMON_RETRY_DELAY_TICKS;
                this.summonRequest = request.withScheduledTick(retryTick);
                this.markDirty();
                LOGGER.info("SUMMON_BLOCKED(reason=NO_VALID_SPAWN_POS action=RETRY bellPos={} retryTick={})",
                    request.bellPos().toShortString(), retryTick);
                return;
            }

            Random random = world.getRandom();
            RegistryEntry<Biome> biomeEntry = world.getBiome(spawnPos);
            VillagerType villagerType = VillagerType.forBiome(biomeEntry);
            MysteriousMerchantSpawner.VariantRollResult variantRoll =
                MysteriousMerchantSpawner.chooseMerchantVariantForVillagerType(villagerType, random, false);
            EntityType<MysteriousMerchantEntity> chosenType =
                MysteriousMerchantSpawner.merchantTypeOfVariant(variantRoll.rolledVariant());

            MysteriousMerchantEntity merchant = chosenType.create(world);
            if (merchant == null) {
                long retryTick = currentTick + TradeConfig.SUMMON_RETRY_DELAY_TICKS;
                this.summonRequest = request.withScheduledTick(retryTick);
                this.markDirty();
                LOGGER.warn("SUMMON_BLOCKED(reason=ENTITY_CREATE_FAILED action=RETRY retryTick={})", retryTick);
                return;
            }

            merchant.refreshPositionAndAngles(
                spawnPos.getX() + 0.5,
                spawnPos.getY(),
                spawnPos.getZ() + 0.5,
                random.nextFloat() * 360.0f,
                0.0f
            );

            if (!world.spawnEntity(merchant)) {
                long retryTick = currentTick + TradeConfig.SUMMON_RETRY_DELAY_TICKS;
                this.summonRequest = request.withScheduledTick(retryTick);
                this.markDirty();
                LOGGER.warn("SUMMON_BLOCKED(reason=SPAWN_ENTITY_FAILED action=RETRY retryTick={})", retryTick);
                return;
            }

            recordSpawn(world, 0L, merchant.getUuid(), TradeConfig.SUMMON_EXPECTED_LIFETIME_TICKS);
            this.summonRequest = null;
            this.markDirty();

            LOGGER.info("SUMMON_SPAWNED(spawnSource=SUMMON) player={} bellPos={} spawnPos={} uuid={} variant={} roll={}/{}",
                shortUuid(request.playerUuid()),
                request.bellPos().toShortString(),
                spawnPos.toShortString(),
                shortUuid(merchant.getUuid()),
                variantRoll.rolledVariant(),
                variantRoll.roll(),
                variantRoll.totalWeight());
        } finally {
            this.processingSummonTick = false;
        }
    }

    /**
     * 创建 Bell 召唤请求。
     */
    public void summonRequest(UUID playerUuid, BlockPos bellPos, long scheduledTick) {
        this.summonRequest = new SummonRequest(playerUuid, bellPos, scheduledTick);
        this.markDirty();
        LOGGER.info("SUMMON_REQUEST_CREATED player={} bellPos={} scheduledTick={}",
            shortUuid(playerUuid), bellPos.toShortString(), scheduledTick);
    }

    public boolean hasSummonRequest() {
        return this.summonRequest != null;
    }

    public long getSummonScheduledTick() {
        return this.summonRequest != null ? this.summonRequest.scheduledTick() : -1L;
    }

    // ========== 业务逻辑方法 ==========

    /**
     * 检查今天是否可以生成（考虑日期重置和每日上限）
     * 时间基准：getTimeOfDay() / 24000（游戏内天数）
     */
    public boolean canSpawnToday(ServerWorld world, int dailyLimit) {
        tick(world);
        refreshDayCounter(world);

        // 预约存在时，跳过自然生成，避免随机流程抢占预约。
        if (this.summonRequest != null) {
            return false;
        }

        return this.spawnCountToday < dailyLimit;
    }

    /**
     * 检查全局冷却是否已过期
     * 时间基准：getTime()（绝对世界 tick）
     */
    public boolean isCooldownExpired(ServerWorld world) {
        return world.getTime() >= this.cooldownUntil;
    }

    /**
     * 获取剩余冷却时间（ticks）
     */
    public long getRemainingCooldown(ServerWorld world) {
        return Math.max(0, this.cooldownUntil - world.getTime());
    }

    /**
     * 检查是否有活跃商人（防重复生成核心）
     * @param world 世界
     * @return true 表示有活跃商人，不应生成新的
     */
    public boolean hasActiveMerchant(ServerWorld world) {
        if (this.activeMerchantUuid == null) {
            return false;
        }

        // 保险机制：如果超过预期过期时间，自动清除（异常情况，保留日志）
        if (this.activeMerchantExpireAt > 0 && world.getTime() > this.activeMerchantExpireAt) {
            LOGGER.warn("[SpawnerState] ACTIVE_MERCHANT_EXPIRED uuid={}... auto-clearing",
                this.activeMerchantUuid.toString().substring(0, 8));
            clearActiveMerchant();
            return false;
        }

        return true;
    }

    /**
     * 获取活跃商人 UUID
     */
    public UUID getActiveMerchantUuid() {
        return this.activeMerchantUuid;
    }

    /**
     * 记录一次成功生成
     * @param world 世界
     * @param cooldownTicks 冷却时间
     * @param merchantUuid 生成的商人 UUID
     * @param expectedLifetime 预期存活时间（用于保险机制）
     */
    public void recordSpawn(ServerWorld world, long cooldownTicks, UUID merchantUuid, long expectedLifetime) {
        refreshDayCounter(world);
        this.spawnCountToday++;
        this.totalSpawnedCount++;
        this.cooldownUntil = world.getTime() + cooldownTicks;
        this.activeMerchantUuid = merchantUuid;
        // 保险机制：设置预期过期时间为存活时间的 1.5 倍
        this.activeMerchantExpireAt = world.getTime() + (long)(expectedLifetime * 1.5);
        this.markDirty();

        if (MysteriousMerchantSpawner.DEBUG) {
            LOGGER.debug("[SpawnerState] RECORD_SPAWN spawnCountToday={} totalSpawned={} cooldownUntil={} activeMerchantUuid={}... expireAt={}",
                this.spawnCountToday, this.totalSpawnedCount, this.cooldownUntil,
                merchantUuid.toString().substring(0, 8), this.activeMerchantExpireAt);
        }
    }

    /**
     * 清除活跃商人（商人消失/死亡时调用）
     */
    public void clearActiveMerchant() {
        UUID previousUuid = this.activeMerchantUuid;
        this.activeMerchantUuid = null;
        this.activeMerchantExpireAt = 0;
        this.markDirty();

        if (MysteriousMerchantSpawner.DEBUG) {
            LOGGER.debug("[SpawnerState] CLEAR_ACTIVE_MERCHANT previousUuid={}",
                (previousUuid != null ? previousUuid.toString().substring(0, 8) + "..." : "null"));
        }
    }

    /**
     * 验证并清除活跃商人（如果 UUID 匹配）
     * @param uuid 要清除的商人 UUID
     * @return true 如果成功清除
     */
    public boolean clearActiveMerchantIfMatch(UUID uuid) {
        if (this.activeMerchantUuid != null && this.activeMerchantUuid.equals(uuid)) {
            clearActiveMerchant();
            return true;
        }
        return false;
    }

    // ========== Getters ==========

    public long getLastSpawnDay() { return lastSpawnDay; }
    public int getSpawnCountToday() { return spawnCountToday; }
    public int getTotalSpawnedCount() { return totalSpawnedCount; }
    public long getCooldownUntil() { return cooldownUntil; }
    public long getActiveMerchantExpireAt() { return activeMerchantExpireAt; }

    /**
     * 输出当前状态摘要（用于调试日志）
     */
    public String toDebugString() {
        return "lastSpawnDay=" + lastSpawnDay +
            " spawnCountToday=" + spawnCountToday +
            " cooldownUntil=" + cooldownUntil +
            " totalSpawned=" + totalSpawnedCount +
            " activeMerchant=" + (activeMerchantUuid != null ? activeMerchantUuid.toString().substring(0, 8) + "..." : "null") +
            " expireAt=" + activeMerchantExpireAt +
            " summonRequest=" + (summonRequest != null ? shortUuid(summonRequest.playerUuid()) + "@" + summonRequest.scheduledTick() : "null");
    }
}
