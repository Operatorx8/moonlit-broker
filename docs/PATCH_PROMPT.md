# Patch Prompt: P0/P1 修复批次

**目标文件**: `src/main/java/dev/xqanzd/moonlitbroker/entity/MysteriousMerchantEntity.java`
**构建验证**: 每个 patch 完成后 `./gradlew build` 必须通过

---

## P0-1: 禁用原版 WanderingTrader despawn，防止绕过清理

### 问题
`MysteriousMerchantEntity extends WanderingTraderEntity`。原版有自己的 `despawnDelay` 倒计时，归零时直接 `this.discard()`，不经过我们的 `performDespawn()`，导致 `MerchantSpawnerState.activeMerchantUuid` 变成僵尸引用，新商人永远不会生成。

### 改动

**A. 新增实例字段（防重入标记）**

在 `// Phase 4: Despawn 数据` 区域，紧挨 `isInWarningPhase` 下方添加：

```java
/** 是否已通知 SpawnerState 清除（防 discard 重入） */
private boolean stateClearNotified = false;
```

**B. 覆写 `setDespawnDelay`，钳死原版计时器**

在 despawn 逻辑区域（`performDespawn()` 方法附近）添加：

```java
@Override
public void setDespawnDelay(int delay) {
    // 禁用原版 WanderingTrader 的 despawn 计时器
    // 使用自定义 spawnTick-based 逻辑替代
    super.setDespawnDelay(Integer.MAX_VALUE);
}
```

**C. 覆写 `discard()`，作为终极清理保险**

```java
@Override
public void discard() {
    // 不管谁调的 discard（原版倒计时、/kill、其他 mod），都确保清 state
    if (!stateClearNotified && this.getEntityWorld() instanceof ServerWorld serverWorld) {
        notifySpawnerStateClear(serverWorld, "DISCARD");
    }
    super.discard();
}
```

**D. 修改已有 `notifySpawnerStateClear`，设置防重入标记**

在 `notifySpawnerStateClear` 方法体开头加：

```java
this.stateClearNotified = true;
```

**E. NBT 持久化 `stateClearNotified`：不需要。** 此字段仅在运行时有效，reload 后重置为 false 是正确行为。

### 必须出现的日志
- 原版 despawn 路径触发时：`[Merchant] NOTIFY_STATE_CLEAR reason=DISCARD uuid=xxxxx... cleared=true`
- 自定义 `performDespawn()` 触发时：`reason=DESPAWN`（已有）

### 不可破坏的行为
- `performDespawn()` 的现有粒子/音效/discard 流程不变
- `onDeath()` 的清理流程不变

---

## P0-2: Sigil offers 缓存 + 禁止 open 刷新 + refreshSeenCount 语义修正

### 问题
`interactMob` 每次调用 `rebuildOffersForPlayer` → `addSigilOffers` 随机重掷。玩家开关 UI 即可无限免费刷新 sigil 列表。`refreshSeenCount` 在 open 时 ++，3 次后保底 B1，变成"开关 UI 3 次必出"。

### 设计决策（写入代码注释）
```
// 设计约定：Open 只展示当前缓存的列表；Refresh（显式 action + 扣费）才重掷。
```

### 改动

**A. 新增实例字段：sigil 随机种子**

在 Trade System 字段区域添加：

```java
/** Sigil 列表随机种子（首次 open 时生成，Refresh 时更新，NBT 持久化） */
private long sigilRollSeed = 0;
/** Sigil 列表是否已初始化 */
private boolean sigilRollInitialized = false;
```

**B. 修改 `addSigilOffers` 方法，使用确定性种子**

将现有方法签名改为：

```java
private void addSigilOffers(TradeOfferList offers, MerchantUnlockState.Progress progress, long seed)
```

方法体内，把所有 `this.random` 替换为用 seed 创建的局部 Random：

```java
Random rollRng = new Random(seed);
int targetCount = 3 + rollRng.nextInt(3);
// ... 后续所有随机调用都用 rollRng
```

同理 `Collections.shuffle(remaining, new Random(this.random.nextLong()))` 改为 `Collections.shuffle(remaining, new Random(rollRng.nextLong()))`。

**C. 修改 `rebuildOffersForPlayer`**

在调用 `addSigilOffers` 之前：

```java
if (eligible && !progress.isUnlockedKatanaHidden()) {
    // 首次 open 时初始化种子，后续 open 复用
    if (!sigilRollInitialized) {
        sigilRollSeed = this.getUuid().getLeastSignificantBits() ^ player.getUuid().getMostSignificantBits();
        sigilRollInitialized = true;
    }
    // 删除原有的 refreshSeenCount++ 行（这行挪到显式 Refresh action 里）
    // progress.setRefreshSeenCount(progress.getRefreshSeenCount() + 1);  // ← 删除
    // state.markDirty();  // ← 对应删除
    
    offers.add(createUnsealOffer());
    addSigilOffers(offers, progress, sigilRollSeed);
}
```

**D. 新增公开方法供 TradeActionHandler 调用（显式刷新）**

```java
/**
 * 显式刷新 Sigil 列表（由 TradeActionHandler 在 REFRESH action 时调用）
 * 调用前必须已扣费
 */
public void refreshSigilOffers() {
    this.sigilRollSeed = this.random.nextLong();
    this.sigilRollInitialized = true;
    LOGGER.info("[MoonTrade] SIGIL_REFRESH merchant={} newSeed={}",
        this.getUuid().toString().substring(0, 8), this.sigilRollSeed);
}
```

**E. NBT 持久化种子**

新增常量：
```java
private static final String NBT_SIGIL_ROLL_SEED = "SigilRollSeed";
private static final String NBT_SIGIL_ROLL_INIT = "SigilRollInitialized";
```

`writeCustomDataToNbt` 追加：
```java
nbt.putLong(NBT_SIGIL_ROLL_SEED, this.sigilRollSeed);
nbt.putBoolean(NBT_SIGIL_ROLL_INIT, this.sigilRollInitialized);
```

`readCustomDataFromNbt` 追加：
```java
this.sigilRollSeed = nbt.getLong(NBT_SIGIL_ROLL_SEED);
this.sigilRollInitialized = nbt.getBoolean(NBT_SIGIL_ROLL_INIT);
```

### 必须出现的日志
- 显式刷新时：`[MoonTrade] SIGIL_REFRESH merchant=xxx newSeed=xxx`

### 不可破坏的行为
- 保底 B1 逻辑不变（refreshSeenCount >= 3 时保底），只是触发时机从 open 改为 refresh
- 未达到 eligible 的玩家看不到 sigil offers（不变）

---

## P0-3: katanaHiddenOffers 缓存时序修复

### 问题
`katanaHiddenOffers` 首次创建时若 `secretKatanaId` 为空，缓存空列表，后续永远不再生成。

### 改动

修改 `addKatanaHiddenOffers` 方法：

```java
private void addKatanaHiddenOffers(TradeOfferList offers) {
    // 确保 ID 已初始化
    initSecretKatanaIdIfNeeded();
    
    // 仅在 ID 有效时生成并缓存；无效 ID 不缓存（允许后续重试）
    if (katanaHiddenOffers == null) {
        if (this.secretKatanaId == null || this.secretKatanaId.isEmpty()) {
            LOGGER.warn("[MoonTrade] KATANA_CACHE_SKIP merchant={} reason=id_still_empty",
                this.getUuid().toString().substring(0, 8));
            return;
        }
        katanaHiddenOffers = createKatanaHiddenOffers();
        // 如果 resolve 失败返回空列表，不缓存
        if (katanaHiddenOffers.isEmpty()) {
            LOGGER.warn("[MoonTrade] KATANA_CACHE_SKIP merchant={} reason=resolve_failed",
                this.getUuid().toString().substring(0, 8));
            katanaHiddenOffers = null;  // 允许下次重试
            return;
        }
    }
    
    for (TradeOffer offer : katanaHiddenOffers) {
        offers.add(offer);
    }
}
```

### 必须出现的日志
- ID 无效时：`[MoonTrade] KATANA_CACHE_SKIP merchant=xxx reason=id_still_empty` 或 `reason=resolve_failed`
- 正常路径无额外日志（已有 resolve 日志）

---

## P1-1: 创造模式排除惩罚

### 改动

`applyAttackPunishment` 方法开头加：
```java
if (player.isCreative()) return;
```

`applyKillPunishment` 方法开头加：
```java
if (player.isCreative()) return;
```

### 必须出现的日志
- 无新日志（静默跳过）

---

## P1-2: spawnTick == 0 边界修复

### 改动

`readCustomDataFromNbt` 中：

```java
// 旧代码
this.spawnTick = nbt.getLong(NBT_SPAWN_TICK);
if (this.spawnTick == 0) this.spawnTick = -1;

// 新代码
if (nbt.contains(NBT_SPAWN_TICK)) {
    this.spawnTick = nbt.getLong(NBT_SPAWN_TICK);
} else {
    this.spawnTick = -1;  // 兼容旧数据：无此 key 视为未初始化
}
```

---

## 执行顺序

```
P0-1 → build → 验证 despawn 清理
P0-2 → build → 验证 sigil 缓存不变
P0-3 → build → 验证 secret page 出太刀
P1-1 → build → 验证 creative 无惩罚
P1-2 → build（边界修复，无需专门验证）
```

## 进入游戏验证脚本

```
# 验证 1: despawn 清理（P0-1）
# 前置：DEBUG_DESPAWN = true（临时开启）
/summon xqanzd_moonlit_broker:mysterious_merchant
# 等待 60 秒或 /kill @e[type=xqanzd_moonlit_broker:mysterious_merchant]
# 检查日志：NOTIFY_STATE_CLEAR reason=DISCARD 或 reason=DESPAWN
# 检查：下一轮 trySpawn 能正常触发

# 验证 2: sigil 不刷新（P0-2）
# 需要一个 tradeCount >= 15 的玩家
# 右键商人 → 记录 sigil 列表
# 关闭 UI → 再右键 → 列表必须完全一致
# 如果有 Refresh 按钮：按下 → 列表应改变

# 验证 3: secret katana（P0-3）
# 解锁 hidden → 切到 secret page → 应出太刀
# 关闭 → 重开 → 仍然出太刀
# 购买太刀 → secretSold=true → secret page 无太刀

# 验证 4: creative 惩罚（P1-1）
/gamemode creative
# 攻击商人 → 无失明/反胃
# 击杀商人 → 无惩罚效果
/gamemode survival
# 攻击商人 → 有失明/反胃
```

## 不做的事情（本轮排除）

- ❌ 不改 `afterUsing` 的交易计数逻辑（低价交易刷计数是已知风险，但不是 P0）
- ❌ 不做跨页 offer 状态保留（当前设计为"切页 = 换货柜"）
- ❌ 不做 Mark 在 ScreenHandler 层验证（取决于后续设计方向）
- ❌ 不改 Spawn audit P1-1 下雨绕过（需确认当前版本是否已修）
- ❌ 不更新设计文档对齐（P1 文档任务，不阻塞代码）
