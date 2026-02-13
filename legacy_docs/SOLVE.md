技术方案
┌─────────────────────────────────────────────────────────────┐
│                    数据持久化层级                            │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  世界级别 (PersistentState)         实体级别 (Entity NBT)   │
│  ─────────────────────────         ────────────────────     │
│  MerchantSpawnerState              MysteriousMerchantEntity │
│  ├── lastSpawnDay                  ├── spawnTick            │
│  ├── spawnCountToday               ├── hasEverTraded        │
│  ├── cooldownUntil                 ├── playerDataMap        │
│  ├── totalSpawnedCount             └── ...                  │
│  └── perPlayerCooldown (Map)                                │
│                                                             │
│  存储位置:                          存储位置:               │
│  world/data/mymodtest_spawner.dat   entity chunk data       │
│                                                             │
└─────────────────────────────────────────────────────────────┘

实现代码
1. 创建 SpawnerState 类
// src/main/java/mod/test/mymodtest/world/MerchantSpawnerState.java

package mod.test.mymodtest.world;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MerchantSpawnerState extends PersistentState {
    
    // 全局状态
    private long lastSpawnDay = -1;           // 上次生成的游戏天数
    private int spawnCountToday = 0;          // 今天已生成数量
    private long cooldownUntil = 0;           // 全局冷却结束时间（世界tick）
    private int totalSpawnedCount = 0;        // 历史总生成数（统计用）
    
    // 每玩家状态（如果需要）
    private Map<UUID, PlayerSpawnerData> perPlayerData = new HashMap<>();
    
    // 常量
    private static final String DATA_NAME = "mymodtest_merchant_spawner";
    
    // ========== 构造和工厂 ==========
    
    public MerchantSpawnerState() {}
    
    public static MerchantSpawnerState createFromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        MerchantSpawnerState state = new MerchantSpawnerState();
        
        state.lastSpawnDay = nbt.getLong("LastSpawnDay");
        state.spawnCountToday = nbt.getInt("SpawnCountToday");
        state.cooldownUntil = nbt.getLong("CooldownUntil");
        state.totalSpawnedCount = nbt.getInt("TotalSpawnedCount");
        
        // 读取玩家数据
        NbtCompound playerDataNbt = nbt.getCompound("PerPlayerData");
        for (String key : playerDataNbt.getKeys()) {
            UUID uuid = UUID.fromString(key);
            PlayerSpawnerData data = PlayerSpawnerData.fromNbt(playerDataNbt.getCompound(key));
            state.perPlayerData.put(uuid, data);
        }
        
        System.out.println("[Spawner] NBT_LOAD lastSpawnDay=" + state.lastSpawnDay 
            + ", spawnCountToday=" + state.spawnCountToday);
        
        return state;
    }
    
    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        nbt.putLong("LastSpawnDay", this.lastSpawnDay);
        nbt.putInt("SpawnCountToday", this.spawnCountToday);
        nbt.putLong("CooldownUntil", this.cooldownUntil);
        nbt.putInt("TotalSpawnedCount", this.totalSpawnedCount);
        
        // 保存玩家数据
        NbtCompound playerDataNbt = new NbtCompound();
        for (Map.Entry<UUID, PlayerSpawnerData> entry : perPlayerData.entrySet()) {
            playerDataNbt.put(entry.getKey().toString(), entry.getValue().toNbt());
        }
        nbt.put("PerPlayerData", playerDataNbt);
        
        System.out.println("[Spawner] NBT_SAVE lastSpawnDay=" + this.lastSpawnDay 
            + ", spawnCountToday=" + this.spawnCountToday);
        
        return nbt;
    }
    
    // ========== 获取实例（关键方法） ==========
    
    private static Type<MerchantSpawnerState> type = new Type<>(
        MerchantSpawnerState::new,
        MerchantSpawnerState::createFromNbt,
        null  // DataFixTypes, 可以为 null
    );
    
    public static MerchantSpawnerState getServerState(ServerWorld world) {
        // 使用 Overworld 的 PersistentStateManager，确保所有维度共享同一份数据
        ServerWorld overworld = world.getServer().getOverworld();
        PersistentStateManager manager = overworld.getPersistentStateManager();
        
        MerchantSpawnerState state = manager.getOrCreate(type, DATA_NAME);
        state.markDirty();  // 标记需要保存
        return state;
    }
    
    // ========== 业务逻辑方法 ==========
    
    /**
     * 检查今天是否可以生成（考虑日期重置和每日上限）
     */
    public boolean canSpawnToday(ServerWorld world, int dailyLimit) {
        long currentDay = world.getTimeOfDay() / 24000L;
        
        // 新的一天，重置计数
        if (currentDay != this.lastSpawnDay) {
            this.lastSpawnDay = currentDay;
            this.spawnCountToday = 0;
            this.markDirty();
            System.out.println("[Spawner] NEW_DAY reset, day=" + currentDay);
        }
        
        return this.spawnCountToday < dailyLimit;
    }
    
    /**
     * 检查全局冷却
     */
    public boolean isCooldownExpired(ServerWorld world) {
        return world.getTime() >= this.cooldownUntil;
    }
    
    /**
     * 记录一次成功生成
     */
    public void recordSpawn(ServerWorld world, long cooldownTicks) {
        this.spawnCountToday++;
        this.totalSpawnedCount++;
        this.cooldownUntil = world.getTime() + cooldownTicks;
        this.markDirty();
        
        System.out.println("[Spawner] RECORD_SPAWN count=" + this.spawnCountToday 
            + ", cooldownUntil=" + this.cooldownUntil);
    }
    
    /**
     * 获取或创建玩家数据
     */
    public PlayerSpawnerData getOrCreatePlayerData(UUID playerUUID) {
        return perPlayerData.computeIfAbsent(playerUUID, uuid -> {
            this.markDirty();
            return new PlayerSpawnerData();
        });
    }
    
    // ========== Getters ==========
    
    public int getSpawnCountToday() { return spawnCountToday; }
    public int getTotalSpawnedCount() { return totalSpawnedCount; }
    public long getCooldownUntil() { return cooldownUntil; }
    
    // ========== 玩家数据内部类 ==========
    
    public static class PlayerSpawnerData {
        private long lastInteractionTick = 0;
        private int encounterCount = 0;  // 该玩家遇到商人的次数
        
        public NbtCompound toNbt() {
            NbtCompound nbt = new NbtCompound();
            nbt.putLong("LastInteractionTick", lastInteractionTick);
            nbt.putInt("EncounterCount", encounterCount);
            return nbt;
        }
        
        public static PlayerSpawnerData fromNbt(NbtCompound nbt) {
            PlayerSpawnerData data = new PlayerSpawnerData();
            data.lastInteractionTick = nbt.getLong("LastInteractionTick");
            data.encounterCount = nbt.getInt("EncounterCount");
            return data;
        }
        
        public void recordEncounter(long currentTick) {
            this.lastInteractionTick = currentTick;
            this.encounterCount++;
        }
        
        public int getEncounterCount() { return encounterCount; }
    }
}

2. 修改 MysteriousMerchantSpawner
// 在 MysteriousMerchantSpawner.java 中使用

public class MysteriousMerchantSpawner {
    
    private static final int DAILY_LIMIT = 1;           // 每天最多生成1个
    private static final long COOLDOWN_TICKS = 24000;   // 冷却1天
    
    public void trySpawn(ServerWorld world) {
        // 1. 获取持久化状态
        MerchantSpawnerState state = MerchantSpawnerState.getServerState(world);
        
        // 2. 检查每日上限
        if (!state.canSpawnToday(world, DAILY_LIMIT)) {
            System.out.println("[Spawner] SKIP_DAILY_LIMIT count=" + state.getSpawnCountToday());
            return;
        }
        
        // 3. 检查全局冷却
        if (!state.isCooldownExpired(world)) {
            System.out.println("[Spawner] SKIP_COOLDOWN until=" + state.getCooldownUntil());
            return;
        }
        
        // 4. 检查世界中是否已有商人（防刷屏）
        if (merchantExistsInWorld(world)) {
            System.out.println("[Spawner] SKIP_EXISTING");
            return;
        }
        
        // 5. 概率检查
        if (world.random.nextFloat() > SPAWN_CHANCE) {
            System.out.println("[Spawner] CHANCE_FAILED");
            return;
        }
        
        // 6. 执行生成
        if (doSpawn(world)) {
            // 7. 记录到持久化状态
            state.recordSpawn(world, COOLDOWN_TICKS);
            System.out.println("[Spawner] SPAWN_SUCCESS total=" + state.getTotalSpawnedCount());
        }
    }
    
    private boolean merchantExistsInWorld(ServerWorld world) {
        return !world.getEntitiesByType(
            ModEntities.MYSTERIOUS_MERCHANT,
            entity -> true
        ).isEmpty();
    }
    
    private boolean doSpawn(ServerWorld world) {
        // 实际生成逻辑...
        return true;
    }
}
```

---

## 边界情况处理

| 场景 | 处理方式 |
|------|----------|
| 退档重进 | PersistentState 自动从文件恢复 |
| 多维度 | 使用 `getOverworld()` 确保所有维度共享同一份状态 |
| 区块未加载 | Spawner 状态是世界级别，不受区块影响 |
| 多人游戏 | PersistentState 是服务端数据，自动同步 |
| 商人消失后 | 根据 `cooldownUntil` 决定何时可再生成，而非仅靠"存在检查" |

---

## 数据流示意
```
trySpawn() 调用
    │
    ├── 1. MerchantSpawnerState.getServerState(world)
    │       └── 从 world/data/mymodtest_merchant_spawner.dat 加载
    │           （如果不存在则创建新的）
    │
    ├── 2. state.canSpawnToday() 
    │       └── 检查日期，必要时重置 spawnCountToday
    │
    ├── 3. state.isCooldownExpired()
    │       └── 检查 world.getTime() >= cooldownUntil
    │
    ├── 4. merchantExistsInWorld() 
    │       └── 额外保险，防止重复
    │
    ├── 5. 概率检查
    │
    ├── 6. doSpawn() 实际生成
    │
    └── 7. state.recordSpawn()
            ├── spawnCountToday++
            ├── cooldownUntil = now + 24000
            └── markDirty() → 触发保存