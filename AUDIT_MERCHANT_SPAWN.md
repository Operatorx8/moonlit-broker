# 神秘商人生成系统 Post-Commit Audit

## 结论：建议修复后发布
1) 生成条件里“下雨”要求目前被永久绕过，导致实际生成频率与注释/调参预期不一致，会误导平衡与玩家体验。  
2) 发布版仍有多处 `System.out.println` 直接输出运行日志，不符合“默认静默，仅保留关键 warn/error”的发布标准。  
3) 状态机整体可用，但仍有少量边界会让状态与实体短期不一致，建议用最小补丁加固。

## 高风险问题（P0）
无（未发现必然导致崩溃/刷屏/永远卡死的硬故障）。

## 中风险问题（P1）
### P1-1 生成条件语义不一致：下雨要求被永久跳过
- 现象：即使设置了 `REQUIRE_RAIN = true`，实际生成不受天气限制。  
- 原因：`MysteriousMerchantSpawner.trySpawn()` 中的判断是 `if (REQUIRE_RAIN && !DEBUG_SKIP_RAIN_CHECK)`；而 `DEBUG_SKIP_RAIN_CHECK` 默认是 `true`，且与 `DEBUG` 无关联，导致始终跳过雨量判断。  
  - 位置：`src/main/java/mod/test/mymodtest/entity/spawn/MysteriousMerchantSpawner.java` → `trySpawn()` → “检查天气条件”块 → 常量 `DEBUG_SKIP_RAIN_CHECK`。  
- 影响：
  - 实际生成频率显著高于注释中的“约 1.9%/天（含下雨）”估算，调参语义被破坏；
  - 玩家体验偏离“稀有但不玄学”的预期，且难以通过调参复现目标节奏。  
- 最小修复建议：
  - 将判断改为仅在调试模式下跳过雨：
    - 例：`if (REQUIRE_RAIN && !(DEBUG && DEBUG_SKIP_RAIN_CHECK)) { ... }`
  - 或者将 `DEBUG_SKIP_RAIN_CHECK` 设为 `false` 且只在 `DEBUG` 分支覆盖。

### P1-2 发布版仍在使用 stdout 输出关键事件（未通过日志系统 gating）
- 现象：生成成功、消失触发、击杀惩罚等仍在 `System.out.println` 输出。  
- 原因：多个关键路径没有使用 logger 或开关 gating。  
  - 位置示例：
    - `MysteriousMerchantSpawner.trySpawn()` → `SPAWN_SUCCESS`  
    - `MysteriousMerchantEntity.performDespawn()` → `DESPAWN_TRIGGER`  
    - `MysteriousMerchantEntity.applyKillPunishment()` → 击杀输出  
    - `MerchantSpawnerState.hasActiveMerchant()` → `ACTIVE_MERCHANT_EXPIRED`  
- 影响：
  - 服务器控制台“刷屏”和日志噪音（尤其在多人或高频触发场景）；
  - 不符合“发布版默认静默，仅保留关键 warn/error”的目标。  
- 最小修复建议：
  - 将 `System.out.println` 替换为 `LOGGER.info/warn/error`；
  - 非异常信息（如 `SPAWN_SUCCESS`、`DESPAWN_TRIGGER`）仅在 `DEBUG` 或配置开关下输出。

## 低风险改进（P2）
### P2-1 限定生成逻辑只在主世界执行
- 现象：`trySpawn()` 在任意维度调用都会执行完整检查链（虽然 Nethe/End 通常无法找到村庄）。  
- 原因：`trySpawn()` 未显式过滤 `World.OVERWORLD`。  
- 影响：浪费少量 CPU；同时 `canSpawnToday()` 的“天数”基准可能来自非主世界时间，存在语义歧义。  
- 最小修复建议：在 `trySpawn()` 开头加：`if (world.getRegistryKey() != World.OVERWORLD) return;`。

### P2-2 “预期过期时间”作为保险机制仍可造成短暂双商人
- 现象：若旧商人长时间在未加载区块，`activeMerchantExpireAt` 到期会清理状态，随后可能生成新商人；旧商人在区块加载后会立刻 despawn。  
- 原因：实体 tick 不执行时不会触发 despawn，但状态已被保险机制清除。  
- 影响：极短时间可能出现两个商人同时存在（直到旧商人首次 tick 时自动消失）。  
- 最小修复建议：在 `performDespawn()`/`onDeath()`之外，增加一次“实体首次 tick 检测若状态不存在则自清理”或允许短暂重叠并记录说明（可选）。

## 配置“甜点值”评估
### 当前参数体感评估
- 以实际代码为准：雨天条件被跳过，`SPAWN_CHANCE_PER_CHECK=0.02`，`NORMAL_CHECK_INTERVAL=3600`。
- 估算：每天约 6.67 次检查，日生成概率约 `1-(1-0.02)^6.67≈12.7%`（仅在“玩家附近且有村庄”时成立）。
- 体感：如果玩家不常在村庄附近，实际出现频率会远低于 12.7%/天；整体偏“过稀”。

### 建议 Profile（最小改动，便于调参）
- Rare（稀有，但不玄学）
  - `REQUIRE_RAIN = true`
  - `NORMAL_CHECK_INTERVAL = 2400`（2 分钟）
  - `SPAWN_CHANCE_PER_CHECK = 0.035`  
  - 体感：村庄附近、下雨时日概率约 28% 左右；乘以雨天概率后约 4–6%/天。

- Normal（中等频率，保持惊喜感）
  - `REQUIRE_RAIN = false`
  - `NORMAL_CHECK_INTERVAL = 2400`
  - `SPAWN_CHANCE_PER_CHECK = 0.05`
  - 体感：村庄附近日概率约 40%；对普通玩家约 2–4 天遇到一次（视村庄停留时间而定）。

> 注：若坚持“必须下雨”，建议把 `SPAWN_CHANCE_PER_CHECK` 拉到 0.04~0.06 区间，否则会极稀。

## 最小 patch 清单（不做大重构）
1) `src/main/java/mod/test/mymodtest/entity/spawn/MysteriousMerchantSpawner.java`  
   - 方法：`trySpawn()`  
   - 改动：修正雨天判断逻辑（仅 DEBUG 时跳过雨）。  

2) `src/main/java/mod/test/mymodtest/entity/spawn/MysteriousMerchantSpawner.java`  
   - 方法：`trySpawn()`  
   - 改动：用 logger 替换 `SPAWN_SUCCESS` 的 stdout（或加 DEBUG gating）。

3) `src/main/java/mod/test/mymodtest/entity/MysteriousMerchantEntity.java`  
   - 方法：`performDespawn()`、`applyKillPunishment()`  
   - 改动：将 stdout 改为 logger（或仅在 DEBUG 输出）。

4) `src/main/java/mod/test/mymodtest/world/MerchantSpawnerState.java`  
   - 方法：`hasActiveMerchant()`  
   - 改动：`ACTIVE_MERCHANT_EXPIRED` 使用 logger.warn（避免 stdout）。

5) （可选）`src/main/java/mod/test/mymodtest/entity/spawn/MysteriousMerchantSpawner.java`  
   - 方法：`trySpawn()`  
   - 改动：只在主世界执行检查，避免维度语义混乱。

## 验证清单（10 分钟内）
1) 新建世界，确保在主世界村庄附近停留，观察至少 1–2 次 `trySpawn` 检查周期内是否正常生成。  
2) 关闭并重进存档，确认 `activeMerchantUuid` 状态可恢复且不会重复生成。  
3) 触发商人 despawn（等待或调试缩短时间），确认状态清理、无重复生成或刷屏。  
4) 玩家击杀商人，确认惩罚生效且 `activeMerchantUuid` 被清理。  
5) 在非主世界（下界/末地）停留 3–5 分钟，确认无错误生成与无多余日志。  
6) 若启用雨天限制：在晴天与雨天分别观察生成差异，确保逻辑生效。

---
如需我基于上述最小 patch 清单直接打补丁，请确认（我将只改 1~10 行并同步调参说明文档）。
