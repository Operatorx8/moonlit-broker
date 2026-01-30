# 神秘商人生成系统 - 发布版参数微调与日志收敛

**版本:** 1.0.1-release
**日期:** 2026-01-29
**作者:** Claude (维护助手)

---

## 1. 目的

本次微调的目标是将神秘商人生成系统从"调试期的极端参数 + 大量 stdout 日志"收敛为"发布默认配置"。

具体来说：
- **关闭调试刷屏日志**：默认关闭所有过程性日志（如 CHECK_START、CHANCE_CHECK、VILLAGE_FOUND 等），仅在 `DEBUG=true` 时才打印
- **保留必要的 warn/error 日志**：异常性日志（如保险机制触发、商人死亡清理）保留为 `[WARN]` 级别，发布版也会打印但极少出现
- **发布默认参数调整**：将调试期的高概率、短时间参数调整为合理的发布默认值，确保玩家体验既有稀有感又不会过于难以遇到
- **语义修正**：修正概率参数命名和村庄范围单位，使代码自解释、可维护

---

## 2. 修改总览

| 项目 | 文件路径:行号 | 修改前 | 修改后 | 理由 |
|------|---------------|--------|--------|------|
| DEBUG | Spawner.java:37 | `true` | `false` | 发布版关闭调试模式 |
| DEBUG_AI | Entity.java:44 | `true` | `false` | 发布版关闭 AI 调试日志 |
| DEBUG_DESPAWN | Entity.java:46 | `true` | `false` | 发布版关闭 despawn 调试日志 |
| ~~SPAWN_CHANCE_PER_DAY~~ | Spawner.java:52 | `0.3f` (30%) | **重命名** | 语义修正 |
| **SPAWN_CHANCE_PER_CHECK** | Spawner.java:52 | (新增) | `0.02f` (2%) | per-check 概率，非 per-day |
| NORMAL_CHECK_INTERVAL | Spawner.java:58 | `24000` (1天) | `3600` (3分钟) | 提高体感灵敏度 |
| ~~VILLAGE_DETECTION_RANGE~~ | Spawner.java:40 | `100` (blocks) | **拆分** | 单位修正 |
| **VILLAGE_DETECTION_RANGE_CHUNKS** | Spawner.java:44 | (新增) | `8` (chunks) | locateStructure 参数单位 |
| **VILLAGE_DETECTION_RANGE_BLOCKS** | Spawner.java:46 | (新增) | `128` (blocks) | 距离验证单位 |
| NORMAL_COOLDOWN_TICKS | Spawner.java:62 | `24000` (1天) | `18000` (15分钟) | 防止短时间内重复生成 |
| NORMAL_EXPECTED_LIFETIME | Spawner.java:66 | `720000` (30天) | `120000` (5天) | 配合事件 NPC 模式 |
| WARNING_TIME_NORMAL | Entity.java:56 | `168000` (7天) | `48000` (2天) | 事件 NPC：提前 2 天预警 |
| DESPAWN_TIME_NORMAL | Entity.java:58 | `720000` (30天) | `120000` (5天) | 事件 NPC：5 天后消失 |
| BLINK_INTERVAL | Entity.java:64 | `10` (0.5秒) | `20` (1秒) | 闪烁频率更舒适 |
| 日志 gating | 多个文件 | 无条件打印 | `if (DEBUG)` | 过程性日志默认不输出 |

**文件路径缩写：**
- `Spawner.java` = `src/main/java/mod/test/mymodtest/entity/spawn/MysteriousMerchantSpawner.java`
- `Entity.java` = `src/main/java/mod/test/mymodtest/entity/MysteriousMerchantEntity.java`
- `State.java` = `src/main/java/mod/test/mymodtest/world/MerchantSpawnerState.java`

---

## 3. 语义修正说明

### 3.1 概率语义：per-check vs per-day

**问题**：原常量名 `SPAWN_CHANCE_PER_DAY` 具有误导性，实际代码是每次检查都执行一次概率判定（per-check），不是 per-day。

**修正**：
- 重命名为 `SPAWN_CHANCE_PER_CHECK`
- 添加详细注释说明概率换算公式

**概率换算公式**：
```
checksPerDay = 24000 / NORMAL_CHECK_INTERVAL
             = 24000 / 3600
             ≈ 6.67 次/天

dailyChance ≈ 1 - (1 - SPAWN_CHANCE_PER_CHECK)^checksPerDay
            = 1 - (1 - 0.02)^6.67
            = 1 - 0.98^6.67
            ≈ 1 - 0.873
            ≈ 12.7%/天（满足其他条件时）
```

**实际生成频率**（考虑所有条件）：
- 需要下雨（约 15% 时间）
- 需要在村庄附近
- 需要无活跃商人、不在冷却中
- 实际约 **1.9%/天** 或更低

### 3.2 村庄范围单位：chunks vs blocks

**问题**：`locateStructure(tag, pos, radius, false)` 的 `radius` 参数单位是**区块数**（chunks），不是格数（blocks）。原代码 `VILLAGE_DETECTION_RANGE / 16` 存在整数截断风险（100/16=6 而非 6.25）。

**修正**：
- 拆分为两个常量，明确单位：
  - `VILLAGE_DETECTION_RANGE_CHUNKS = 8` — 用于 `locateStructure` 调用
  - `VILLAGE_DETECTION_RANGE_BLOCKS = CHUNKS * 16 = 128` — 用于距离验证
- 避免运行时除法和截断问题

---

## 4. 详细变更

### 4.1 MysteriousMerchantSpawner.java

**改动点：**
- 将 `DEBUG` 从 `true` 改为 `false`
- **重命名** `SPAWN_CHANCE_PER_DAY` → `SPAWN_CHANCE_PER_CHECK`，值从 `0.3f` 改为 `0.02f`
- **拆分** `VILLAGE_DETECTION_RANGE` → `VILLAGE_DETECTION_RANGE_CHUNKS` + `VILLAGE_DETECTION_RANGE_BLOCKS`
- 将 `NORMAL_CHECK_INTERVAL` 从 `24000` 改为 `3600`
- 将 `NORMAL_COOLDOWN_TICKS` 从 `24000` 改为 `18000`
- 将 `NORMAL_EXPECTED_LIFETIME` 从 `720000` 改为 `120000`
- 更新文件头部注释，说明概率语义
- 更新 `isNearVillage` 方法，使用新常量名
- 更新 `CHANCE_CHECK` 日志，使用 `chancePerCheck=` 而非 `chance=`

**关键 diff 摘要：**
```java
// 常量定义
- private static final int VILLAGE_DETECTION_RANGE = 100;
- private static final float SPAWN_CHANCE_PER_DAY = 0.3f;
+ private static final int VILLAGE_DETECTION_RANGE_CHUNKS = 8;  // 8 chunks = 128 blocks
+ private static final int VILLAGE_DETECTION_RANGE_BLOCKS = VILLAGE_DETECTION_RANGE_CHUNKS * 16;
+ /**
+  * 每次检查的生成概率 (0.0 ~ 1.0)
+  * checksPerDay = 24000 / 3600 ≈ 6.67
+  * dailyChance ≈ 1 - 0.98^6.67 ≈ 12.7%/天（满足条件时）
+  */
+ private static final float SPAWN_CHANCE_PER_CHECK = 0.02f;

// isNearVillage 方法
  BlockPos villagePos = world.locateStructure(
      StructureTags.VILLAGE,
      pos,
-     VILLAGE_DETECTION_RANGE / 16,
+     VILLAGE_DETECTION_RANGE_CHUNKS,  // 单位：区块
      false
  );
- return distance <= VILLAGE_DETECTION_RANGE;
+ return distance <= VILLAGE_DETECTION_RANGE_BLOCKS;  // 单位：格

// 概率检查
- boolean passed = roll <= SPAWN_CHANCE_PER_DAY;
+ boolean passed = roll <= SPAWN_CHANCE_PER_CHECK;
- System.out.println("[Spawner] CHANCE_CHECK chance=" + SPAWN_CHANCE_PER_DAY + ...);
+ System.out.println("[Spawner] CHANCE_CHECK chancePerCheck=" + SPAWN_CHANCE_PER_CHECK + ...);
```

### 4.2 MysteriousMerchantEntity.java

**改动点：**
- 将 `DEBUG_AI` 从 `true` 改为 `false`
- 将 `DEBUG_DESPAWN` 从 `true` 改为 `false`
- 将 `WARNING_TIME_NORMAL` 从 `7 * TICKS_PER_DAY` 改为 `2 * TICKS_PER_DAY`
- 将 `DESPAWN_TIME_NORMAL` 从 `30 * TICKS_PER_DAY` 改为 `5 * TICKS_PER_DAY`
- 将 `BLINK_INTERVAL` 从 `10` 改为 `20`
- 多处日志改为 `if (DEBUG_DESPAWN)` / `if (DEBUG_AI)` 控制
- 异常日志添加 `[ERROR]` 前缀

**关键 diff 摘要：**
```java
// 调试开关
- public static final boolean DEBUG_AI = true;
- public static final boolean DEBUG_DESPAWN = true;
+ public static final boolean DEBUG_AI = false;
+ public static final boolean DEBUG_DESPAWN = false;

// 事件 NPC 模式
- private static final int WARNING_TIME_NORMAL = 7 * TICKS_PER_DAY;
- private static final int DESPAWN_TIME_NORMAL = 30 * TICKS_PER_DAY;
+ private static final int WARNING_TIME_NORMAL = 2 * TICKS_PER_DAY;
+ private static final int DESPAWN_TIME_NORMAL = 5 * TICKS_PER_DAY;

// 闪烁间隔
- private static final int BLINK_INTERVAL = 10;
+ private static final int BLINK_INTERVAL = 20;
```

### 4.3 MerchantSpawnerState.java

**改动点：**
- 导入 `MysteriousMerchantSpawner` 以访问 `DEBUG` 开关
- 多处日志改为 `if (MysteriousMerchantSpawner.DEBUG)` 控制
- `ACTIVE_MERCHANT_EXPIRED` 日志添加 `[WARN]` 前缀

---

## 5. 发布默认参数清单

### 5.1 发布版（NORMAL 模式）

| 参数 | 值 | 说明 |
|------|-----|------|
| **调试开关** | | |
| DEBUG | `false` | 主调试开关 |
| DEBUG_AI | `false` | AI 行为调试 |
| DEBUG_DESPAWN | `false` | 消失机制调试 |
| **生成参数** | | |
| SPAWN_CHANCE_PER_CHECK | `0.02f` (2%) | **每次检查**生成概率 |
| NORMAL_CHECK_INTERVAL | `3600` ticks (3分钟) | 检查间隔 |
| VILLAGE_DETECTION_RANGE_CHUNKS | `8` chunks | 村庄检测范围（locateStructure 参数） |
| VILLAGE_DETECTION_RANGE_BLOCKS | `128` blocks | 村庄检测范围（距离验证） |
| MAX_MERCHANTS_PER_DAY | `1` | 每日上限 |
| NORMAL_COOLDOWN_TICKS | `18000` ticks (15分钟) | 生成后冷却 |
| REQUIRE_RAIN | `true` | 需要下雨 |
| **存活参数（事件 NPC 模式）** | | |
| WARNING_TIME_NORMAL | `48000` ticks (2天) | 消失预警时间 |
| DESPAWN_TIME_NORMAL | `120000` ticks (5天) | 强制消失时间 |
| NORMAL_EXPECTED_LIFETIME | `120000` ticks (5天) | 预期存活时间 |
| BLINK_INTERVAL | `20` ticks (1秒) | 闪烁间隔 |

### 5.2 概率体感估算

```
每次检查概率 (pCheck)     = 0.02 (2%)
每天检查次数 (checksPerDay) = 24000 / 3600 ≈ 6.67
每天生成概率 (无其他限制)   = 1 - (1 - 0.02)^6.67 ≈ 12.7%
考虑下雨概率 (~15%)        ≈ 12.7% × 15% ≈ 1.9%/天
```

**注意**：实际频率还受村庄距离、冷却时间、每日上限等影响，会更低。

### 5.3 调试模式（DEBUG = true）

| 参数 | 值 | 说明 |
|------|-----|------|
| DEBUG_CHECK_INTERVAL | `600` ticks (30秒) | 快速检查间隔 |
| DEBUG_COOLDOWN_TICKS | `2400` ticks (2分钟) | 短冷却 |
| DEBUG_EXPECTED_LIFETIME | `1200` ticks (60秒) | 短存活时间 |
| DEBUG_SKIP_RAIN_CHECK | `true` | 跳过下雨检测 |
| WARNING_TIME_DEBUG | `600` ticks (30秒) | 快速预警 |
| DESPAWN_TIME_DEBUG | `1200` ticks (60秒) | 快速消失 |

---

## 6. 验证步骤

### 6.1 发布版验证（DEBUG = false）

1. **启动游戏，确认无刷屏日志**
   - 观察控制台，不应出现 `[Spawner] CHECK_START`、`[Spawner] CHANCE_CHECK`、`[Spawner] VILLAGE_FOUND` 等过程性日志
   - 只有在商人实际生成时才会看到 `[Spawner] SPAWN_SUCCESS` 日志

2. **验证商人生成**
   - 在村庄附近等待下雨（或使用 `/weather rain`）
   - 生成成功时应看到简洁的 `[Spawner] SPAWN_SUCCESS pos=... uuid=... nearPlayer=... totalSpawned=...`

3. **验证消失预警**
   - 商人存活 2 天后应开始闪烁（发光效果 + 传送门粒子）
   - 不应出现 `[Merchant] WARNING_ENTER` 日志

4. **验证强制消失**
   - 商人存活 5 天后应消失
   - 应看到简洁的 `[Merchant] DESPAWN_TRIGGER lifetime=...s uuid=...`

5. **验证异常日志保留**
   - 如果出现保险机制触发，应看到 `[SpawnerState][WARN] ACTIVE_MERCHANT_EXPIRED`
   - 如果商人在已加载区块死亡，应看到 `[Spawner][WARN] ACTIVE_MERCHANT_DEAD`

### 6.2 调试版验证（DEBUG = true）

1. 修改 `MysteriousMerchantSpawner.DEBUG` 为 `true`
2. 修改 `MysteriousMerchantEntity.DEBUG_AI` 和 `DEBUG_DESPAWN` 为 `true`
3. 重新构建并启动游戏
4. 验证过程性日志正常输出，包括 `chancePerCheck=0.02`
5. 验证商人快速生成（30 秒检查、2 分钟冷却）
6. 验证商人快速消失（30 秒预警、60 秒消失）
7. 验证 despawn 后 state 已清理

---

## 7. 回滚策略

### 7.1 快速回滚（仅改调试开关）

```java
// MysteriousMerchantSpawner.java
public static final boolean DEBUG = true;

// MysteriousMerchantEntity.java
public static final boolean DEBUG_AI = true;
public static final boolean DEBUG_DESPAWN = true;
```

### 7.2 完全回滚（恢复所有原值）

| 参数 | 文件 | 回滚值 |
|------|------|--------|
| DEBUG | Spawner.java | `true` |
| DEBUG_AI | Entity.java | `true` |
| DEBUG_DESPAWN | Entity.java | `true` |
| SPAWN_CHANCE_PER_CHECK | Spawner.java | 改回 `SPAWN_CHANCE_PER_DAY = 0.3f` |
| VILLAGE_DETECTION_RANGE_* | Spawner.java | 改回 `VILLAGE_DETECTION_RANGE = 100` |
| NORMAL_CHECK_INTERVAL | Spawner.java | `24000` |
| NORMAL_COOLDOWN_TICKS | Spawner.java | `24000` |
| NORMAL_EXPECTED_LIFETIME | Spawner.java | `720000` |
| WARNING_TIME_NORMAL | Entity.java | `7 * TICKS_PER_DAY` |
| DESPAWN_TIME_NORMAL | Entity.java | `30 * TICKS_PER_DAY` |
| BLINK_INTERVAL | Entity.java | `10` |

### 7.3 Git 回滚

```bash
git revert HEAD  # 回滚最新提交
# 或
git checkout HEAD~1 -- <file>  # 回滚单个文件
```

---

## 附录：日志分类

### 过程性日志（DEBUG 控制）
- `[Spawner] CHECK_START`
- `[Spawner] SKIP_DAILY_LIMIT`
- `[Spawner] SKIP_COOLDOWN`
- `[Spawner] SKIP_ACTIVE_MERCHANT_EXISTS`
- `[Spawner] SKIP_ACTIVE_MERCHANT_UNLOADED`
- `[Spawner] SKIP_EXISTING_LOADED`
- `[Spawner] SKIP_NO_RAIN`
- `[Spawner] CHANCE_CHECK` (现在显示 `chancePerCheck=`)
- `[Spawner] SKIP_NO_PLAYERS`
- `[Spawner] SKIP_NO_VILLAGE`
- `[Spawner] SKIP_NO_VALID_POS`
- `[Spawner] VILLAGE_FOUND` (现在显示 `maxRange=128`)
- `[SpawnerState] NEW_DAY`
- `[SpawnerState] RECORD_SPAWN`
- `[SpawnerState] CLEAR_ACTIVE_MERCHANT`
- `[Merchant] INIT_SPAWN_TICK`
- `[Merchant] WARNING_ENTER`
- `[Merchant] FX_SPAWN`
- `[Merchant] NOTIFY_STATE_CLEAR`
- `[Merchant] NBT_SAVE`
- `[Merchant] NBT_LOAD`
- `[MerchantAI] *` (所有 AI 日志)
- `[MysteriousMerchant] 交易次数:...`

### 重要事件日志（始终保留）
- `[Spawner] SPAWN_SUCCESS` - 商人生成成功
- `[Merchant] DESPAWN_TRIGGER` - 商人消失
- `[MysteriousMerchant] 玩家 xxx 解锁了隐藏交易!` - 隐藏交易解锁
- `[MysteriousMerchant] 玩家 xxx 击杀了商人，施加严重惩罚！` - 击杀惩罚

### 异常性日志（[WARN]/[ERROR] 标记，始终保留）
- `[Spawner][WARN] ACTIVE_MERCHANT_DEAD` - 商人已死亡
- `[SpawnerState][WARN] ACTIVE_MERCHANT_EXPIRED` - 保险机制触发
- `[Merchant][ERROR] Failed to notify SpawnerState` - 状态通知失败
- `[MysteriousMerchant] 无效的 UUID: xxx` - UUID 解析错误
