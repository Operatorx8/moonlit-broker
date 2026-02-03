# 1.21.1 降级审计报告（神秘商人系统）

结论：✅可进游戏验证

## 发现的问题列表（P0/P1/P2）
- P1：发布版日志策略不符合要求（存在非 DEBUG 的 System.out 输出），可能在长时间运行时刷屏，影响排查关键 warn/error。
- P2：个别错误日志使用 System.err 直出，难以与统一日志等级/格式对齐。

## 你做了哪些最小修复（文件+行/方法+摘要）
- `src/main/java/mod/test/mymodtest/Mymodtest.java`：初始化日志改为 LOGGER.debug，避免发布版刷屏。
- `src/main/java/mod/test/mymodtest/registry/ModItems.java`：物品注册日志改为 LOGGER.debug。
- `src/main/java/mod/test/mymodtest/entity/spawn/MysteriousMerchantSpawner.java`：活跃商人死亡的异常情况改为 LOGGER.warn，并输出短 UUID。
- `src/main/java/mod/test/mymodtest/entity/MysteriousMerchantEntity.java`：
  - 隐藏交易解锁等信息日志改为 DEBUG_AI 条件下的 LOGGER.debug。
  - notifySpawnerStateClear 异常改为 LOGGER.error。
  - 反序列化无效 UUID 改为 LOGGER.warn。

## 需要游戏验证的点
- 商人被强制移除（如 /kill、非死亡原因的 discard）时，SpawnerState 是否仍能及时清理活跃 UUID（依赖过期保险机制）。
- 村庄定位逻辑在 1.21.1 下的实际命中率是否符合预期（locateStructure + 距离二次校验）。

## 我接下来进入游戏前的 8 分钟验证清单（≤6 条）
1. 进入主世界并等待至少 10 分钟游戏时间，观察是否能正常生成商人（雨天条件满足时）。
2. 商人生成后强制击杀，确认不再阻塞新生成（SpawnerState 活跃 UUID 已清理）。
3. 商人自然消失后，确认不会长期卡住生成（过期保险机制生效）。
4. 使用神秘硬币触发隐藏交易解锁，确认消息/交易行为正常。
5. 交易 10 次解锁隐藏交易，确认不会重复添加交易。
6. 服务器重启后进入世界，确认持久化状态无异常（无重复/卡住）。

## 构建验证
- 命令：`./gradlew build`
- 结果：BUILD SUCCESSFUL
- 时间：2026-01-31 06:51:27 PST
