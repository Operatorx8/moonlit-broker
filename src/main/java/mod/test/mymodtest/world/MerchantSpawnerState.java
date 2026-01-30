package mod.test.mymodtest.world;

import mod.test.mymodtest.entity.spawn.MysteriousMerchantSpawner;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;

import java.util.UUID;

/**
 * 神秘商人生成器的持久化状态
 * 存储位置: world/data/mymodtest_merchant_spawner.dat
 *
 * 使用 PersistentState.Type 进行序列化（Fabric 1.21.1 API）
 */
public class MerchantSpawnerState extends PersistentState {

    // 全局状态
    private long lastSpawnDay;              // 上次生成的游戏天数
    private int spawnCountToday;            // 今天已生成数量
    private long cooldownUntil;             // 全局冷却结束时间（世界tick，绝对值）
    private int totalSpawnedCount;          // 历史总生成数（统计用）

    // 活跃商人追踪（防重复生成核心）
    private UUID activeMerchantUuid;        // 当前活跃商人的 UUID（null 表示无活跃商人）
    private long activeMerchantExpireAt;    // 活跃商人预期过期时间（保险机制）

    // 常量
    private static final String DATA_NAME = "mymodtest_merchant_spawner";

    // ========== 构造函数 ==========

    public MerchantSpawnerState() {
        this.lastSpawnDay = -1;
        this.spawnCountToday = 0;
        this.cooldownUntil = 0;
        this.totalSpawnedCount = 0;
        this.activeMerchantUuid = null;
        this.activeMerchantExpireAt = 0;
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

    // ========== 业务逻辑方法 ==========

    /**
     * 检查今天是否可以生成（考虑日期重置和每日上限）
     * 时间基准：getTimeOfDay() / 24000（游戏内天数）
     */
    public boolean canSpawnToday(ServerWorld world, int dailyLimit) {
        long currentDay = world.getTimeOfDay() / 24000L;

        // 新的一天，重置计数
        if (currentDay != this.lastSpawnDay) {
            if (MysteriousMerchantSpawner.DEBUG) {
                System.out.println("[SpawnerState] NEW_DAY previousDay=" + this.lastSpawnDay +
                    " currentDay=" + currentDay + " resetting spawnCountToday from " + this.spawnCountToday + " to 0");
            }
            this.lastSpawnDay = currentDay;
            this.spawnCountToday = 0;
            this.markDirty();
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
            System.out.println("[SpawnerState][WARN] ACTIVE_MERCHANT_EXPIRED uuid=" +
                this.activeMerchantUuid.toString().substring(0, 8) + "... auto-clearing");
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
        this.spawnCountToday++;
        this.totalSpawnedCount++;
        this.cooldownUntil = world.getTime() + cooldownTicks;
        this.activeMerchantUuid = merchantUuid;
        // 保险机制：设置预期过期时间为存活时间的 1.5 倍
        this.activeMerchantExpireAt = world.getTime() + (long)(expectedLifetime * 1.5);
        this.markDirty();

        if (MysteriousMerchantSpawner.DEBUG) {
            System.out.println("[SpawnerState] RECORD_SPAWN spawnCountToday=" + this.spawnCountToday +
                " totalSpawned=" + this.totalSpawnedCount +
                " cooldownUntil=" + this.cooldownUntil +
                " activeMerchantUuid=" + merchantUuid.toString().substring(0, 8) + "..." +
                " expireAt=" + this.activeMerchantExpireAt);
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
            System.out.println("[SpawnerState] CLEAR_ACTIVE_MERCHANT previousUuid=" +
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
            " expireAt=" + activeMerchantExpireAt;
    }
}
