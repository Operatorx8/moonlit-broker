# 1.21.1 Post-downgrade Audit

结论：✅可进入游戏验证（未发现阻塞问题）

## 检查项清单
- System.out/System.err 输出
- 1.21.11 专有 API/签名风险（结构定位、Registry、Text、NBT、Entity 生命周期）
- 线程/side 错误（客户端/服务端逻辑交叉调用）
- PersistentState 读写一致性与无效 UUID 处理
- 商人实体移除路径（despawn/kill/death/discard）与 active UUID 清理
- 生成器关键不变量（daily_limit/cooldown/expireAt/activeMerchantUuid）

## 发现的问题
- P1：仍有 System.out 调试输出存在（虽然多数在 DEBUG 下，但不符合发布版日志策略）。
- P2：部分调试日志未统一到 LOGGER，影响日志级别控制与采样。

## 最小修复（diff 摘要）
- `src/main/java/dev/xqanzd/moonlitbroker/world/MerchantSpawnerState.java`
  - 将 DEBUG 下的 System.out 改为 LOGGER.debug，保留原有条件与内容。
- `src/main/java/dev/xqanzd/moonlitbroker/entity/spawn/MysteriousMerchantSpawner.java`
  - 将 DEBUG 下的 System.out 改为 LOGGER.debug；保持触发条件不变。
- `src/main/java/dev/xqanzd/moonlitbroker/entity/MysteriousMerchantEntity.java`
  - DEBUG_DESPAWN/DEBUG_AI 下的 System.out 改为 LOGGER.debug，避免发布版刷屏。
- `src/main/java/dev/xqanzd/moonlitbroker/entity/ai/EnhancedFleeGoal.java`
  - 新增 LOGGER，并将 DEBUG_AI 输出改为 LOGGER.debug。
- `src/main/java/dev/xqanzd/moonlitbroker/entity/ai/DrinkPotionGoal.java`
  - 新增 LOGGER，并将 DEBUG_AI 输出改为 LOGGER.debug。
- `src/main/java/dev/xqanzd/moonlitbroker/entity/ai/SeekLightGoal.java`
  - 新增 LOGGER，并将 DEBUG_AI 输出改为 LOGGER.debug。

## 生成器关键不变量校验
- daily_limit/cooldown/expireAt/activeMerchantUuid 在 PersistentState 中持久化，且有过期保险清理。
- locateStructure 半径单位为 chunk，并在逻辑中再次用 block 距离校验，避免单位混用。
- 若 activeMerchantUuid 指向已死亡实体，Spawner 会清理；若指向未加载区块，依赖 expireAt 保险机制避免锁死。

## 需要游戏验证的点
- 非死亡方式强制移除实体时（/kill 或其他 mod 直接 discard），是否依赖 expireAt 自动解锁。
- locateStructure 在 1.21.1 的实际命中率是否符合预期（村庄附近 8 chunks）。

## 建议的游戏内验证步骤（<=8）
1. 进入主世界，等待雨天并观察是否能生成商人。
2. 击杀商人后等待 1 个检查周期，确认不再阻塞生成。
3. 等待商人自然消失，确认 active UUID 能被清理或过期保险触发。
4. 与商人交易 10 次，确认隐藏交易只解锁一次。
5. 使用神秘硬币交互，确认奖励与交易解锁正常。
6. 重启服务器后进入存档，确认持久化状态无异常。

## Build 结果
- 命令：`./gradlew clean build`
- 结果：BUILD SUCCESSFUL
- 时间：2026-01-31 06:57:05 PST
- 备注：未运行 `./gradlew runClient`（非必须，按需再跑）
