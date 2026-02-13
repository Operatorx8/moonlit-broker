# 神秘商人模组 - 开发进度

## 当前状态：Phase 7 已完成 (Spawner 状态持久化)

最后更新：2026-01-29

---

## ✅ Phase 1 - 基础实体框架（已完成）

- [x] MysteriousMerchantEntity 继承 WanderingTraderEntity
- [x] EntityType 注册
- [x] 属性注册 (FabricDefaultAttributeRegistry)
- [x] 渲染器注册 (使用原版流浪商人渲染器)
- [x] 编译通过
- [x] /summon 可召唤
- [x] 右键可打开交易UI

---

## 🔄 Phase 2 - 交易系统（进行中）

### 已完成
- [x] Step 2.1: fillRecipes 添加 3 个测试交易
- [x] Step 2.2: PlayerTradeData 类
- [x] Step 2.3: playerDataMap + getOrCreatePlayerData()
- [x] Step 2.4: 交易回调 - tradeCount++, 给玩家 buff
- [x] Step 2.5: NBT 持久化 (hasEverTraded, playerDataMap)
- [x] 编译通过

### 已验证
- [x] 右键商人看到 3 个交易选项
- [x] 完成交易获得 SPEED + REGENERATION buff
- [x] 控制台打印交易日志

### 待验证
- [ ] 完成 10 次交易后，解锁隐藏交易
- [ ] 控制台打印 "解锁隐藏交易"
- [ ] 持久化验证：交易几次 → 保存退出 → 重进 → 数据保持

---

## ✅ Phase 3 - AI行为（已完成）

### 已完成
- [x] Phase 3.1: 逃跑状态强化 (EnhancedFleeGoal)
  - 检测威胁范围: 16格
  - 受伤记忆: 60 ticks (3秒)
  - 逃跑速度倍率: 1.4x
  - 重新选路间隔: 20 ticks (1秒)
- [x] Phase 3.2: 自保机制 (DrinkPotionGoal)
  - 隐身药水: 威胁存在时触发, 冷却100秒, 持续40秒
  - 治疗药水: 生命值<60%时触发, 冷却45秒
  - 喝药动作持续1秒, 带粒子和音效
- [x] Phase 3.3: 趋光性 (SeekLightGoal)
  - 仅夜晚生效
  - 扫描范围: 16格
  - 低光照阈值: <7
  - 光照差值阈值: 4
- [x] DEBUG_AI 调试开关已添加

### 新增文件
- src/main/java/mod/test/mymodtest/entity/ai/EnhancedFleeGoal.java
- src/main/java/mod/test/mymodtest/entity/ai/DrinkPotionGoal.java
- src/main/java/mod/test/mymodtest/entity/ai/SeekLightGoal.java

### 待验证
- [ ] 逃跑测试: 召唤僵尸/骷髅靠近商人
- [ ] 自保测试: 打商人到半血观察喝药
- [ ] 趋光测试: 夜晚在野外召唤, 附近放火把

---

## ✅ Phase 4 - 生成与消失（已完成）

### 已完成
- [x] Phase 4.1: Despawn 逻辑
  - spawnTick 记录生成时间
  - 调试模式：30秒警告 / 60秒消失
  - 正常模式：7天警告 / 30天消失
  - NBT 持久化支持
- [x] Phase 4.2: 消失预警效果
  - 闪烁效果：发光轮廓切换
  - 传送门粒子效果
- [x] Phase 4.3: 消失动画
  - 末影人传送音效
  - 大量传送门+烟雾粒子
- [x] Phase 4.4: 自然生成系统 (MysteriousMerchantSpawner)
  - 村庄检测（100格范围）
  - 雨天前置条件（可配置）
  - 每天概率检查（30%）
  - 调试模式：每30秒检查一次
- [x] 编译通过

### 新增文件
- src/main/java/mod/test/mymodtest/entity/spawn/MysteriousMerchantSpawner.java

### 调试开关
- `MysteriousMerchantEntity.DEBUG_DESPAWN`: 使用短时间测试 despawn
- `MysteriousMerchantSpawner.DEBUG`: 启用生成器调试日志
- `MysteriousMerchantSpawner.DEBUG_SKIP_RAIN_CHECK`: 调试时跳过雨天检测

### 待验证
- [ ] Despawn 测试：召唤商人等待 30秒 看闪烁，60秒后消失
- [ ] 生成测试：在村庄附近等待 30秒，查看控制台日志
- [ ] 持久化测试：召唤商人 → 保存退出 → 重进 → spawnTick 保持

---

## ✅ Phase 5 - 交互与惩罚（已完成）

### 已完成
- [x] Phase 5.1: 攻击惩罚
  - 失明效果：5秒
  - 反胃效果：7秒
  - 不祥音效（远古守卫者诅咒）
- [x] Phase 5.2: 击杀惩罚
  - 失明 + 反胃效果翻倍（4倍时长，等级提升）
  - 缓慢效果
  - 不幸效果：20分钟
  - 警告消息（聊天栏 + ActionBar）
  - 凋零生成音效 + 女巫粒子
- [x] Phase 5.3: 神秘硬币交互
  - 临时物品：金粒（后续可替换为自定义物品）
  - 消耗 1 个硬币
  - 奖励：增加 3 次交易计数
  - 效果：幸运II(5分钟) + 速度II(1分钟) + 再生II(30秒) + 村庄英雄(10分钟)
  - 附魔音效 + 附魔粒子 + 绿色爱心粒子
  - 可快速解锁隐藏交易
- [x] 编译通过

### 待验证
- [ ] 攻击测试：打商人一下，观察失明+反胃效果
- [ ] 击杀测试：击杀商人，观察效果+消息+音效
- [ ] 硬币测试：手持金粒右键商人，观察效果

---

## ✅ Phase 6 - NBT完善与收尾（已完成）

### 已完成
- [x] Phase 6.1: 自定义物品 - 神秘硬币
  - ModItems.java 物品注册
  - 物品模型 JSON
  - 中英文语言文件
  - 稀有度：Uncommon（青色名称）
- [x] Phase 6.2: 隐藏交易内容
  - 3 神秘硬币 → 附魔金苹果 (限1次)
  - 5 神秘硬币 + 64 绿宝石 → 不死图腾 (限1次)
  - 2 神秘硬币 → 3 龙息 (限3次)
  - 8 神秘硬币 + 16 钻石 → 下界之星 (限1次)
  - 10 神秘硬币 + 64 绿宝石 → 鞘翅 (限1次)
- [x] Phase 6.3: 普通交易添加神秘硬币购买
  - 32 绿宝石 → 1 神秘硬币 (限3次)
- [x] Phase 6.4: NBT 完善
  - merchantName 保存/读取
  - secretOffers 状态保存
- [x] 编译通过

### 新增文件
- src/main/java/mod/test/mymodtest/registry/ModItems.java
- src/main/resources/assets/mymodtest/models/item/mysterious_coin.json
- src/main/resources/assets/mymodtest/lang/en_us.json
- src/main/resources/assets/mymodtest/lang/zh_cn.json

### 待完成
- [ ] 神秘硬币纹理文件 (textures/item/mysterious_coin.png)

### 待验证
- [ ] 给予物品: /give @p mymodtest:mysterious_coin
- [ ] 购买硬币: 与商人交易 32 绿宝石换硬币
- [ ] 隐藏交易: 交易 10 次后查看新交易

---

## ✅ Phase 7 - Spawner 状态持久化（已完成）

### 已完成
- [x] Phase 7.1: MerchantSpawnerState 类
  - 继承 PersistentState
  - 使用 Codec 进行序列化（Fabric 1.21+ API）
  - 保存字段：lastSpawnDay, spawnCountToday, cooldownUntil, totalSpawnedCount
  - 存储位置: world/data/mymodtest_merchant_spawner.dat
- [x] Phase 7.2: 修改 MysteriousMerchantSpawner
  - 使用 state.canSpawnToday() 替代内存日期检查
  - 使用 state.isCooldownExpired() 检查冷却
  - 生成成功后调用 state.recordSpawn()
  - 调试模式冷却时间: 2 分钟 (2400 ticks)
  - 正常模式冷却时间: 1 天 (24000 ticks)
- [x] Phase 7.3: 增强日志
  - [SpawnerState] GET_STATE: 获取状态时输出
  - [SpawnerState] NEW_DAY: 新的一天重置
  - [SpawnerState] RECORD_SPAWN: 记录生成
  - [Spawner] SKIP_COOLDOWN: 冷却中跳过
- [x] 编译通过

### 新增文件
- src/main/java/mod/test/mymodtest/world/MerchantSpawnerState.java

### 待验证
- [ ] 持久化测试：生成商人 → 退出 → 重进 → 检查 lastSpawnDay 是否保持
- [ ] 冷却测试：商人消失后 → 等待冷却 → 检查是否按时生成新商人
- [ ] 跨日测试：等待游戏内新的一天 → 检查 spawnCountToday 是否重置

---

## 下次开发指令

核心功能已完成！验证所有功能：
```
1. 基础测试：
   /summon mymodtest:mysterious_merchant
   /give @p mymodtest:mysterious_coin 64
   /give @p emerald 64

2. 交易测试：
   - 右键商人打开交易
   - 用绿宝石买神秘硬币
   - 交易 10 次后检查隐藏交易

3. 惩罚测试：
   - /gamemode survival
   - 攻击商人 → 观察失明+反胃
   - 击杀商人 → 观察严重惩罚

4. 硬币特殊交互：
   - 手持神秘硬币右键商人 → 观察祝福效果

5. Despawn 测试：
   - 等待 30秒 → 闪烁
   - 等待 60秒 → 消失

6. 待补充：
   - 添加神秘硬币纹理: textures/item/mysterious_coin.png (16x16 PNG)
```

---

## 关键文件位置

| 文件 | 路径 |
|------|------|
| 主入口 | src/main/java/mod/test/mymodtest/Mymodtest.java |
| 实体类 | src/main/java/mod/test/mymodtest/entity/MysteriousMerchantEntity.java |
| 玩家数据 | src/main/java/mod/test/mymodtest/entity/data/PlayerTradeData.java |
| 实体注册 | src/main/java/mod/test/mymodtest/registry/ModEntities.java |
| Client入口 | src/client/java/mod/test/mymodtest/client/MymodtestClient.java |
| 逃跑AI | src/main/java/mod/test/mymodtest/entity/ai/EnhancedFleeGoal.java |
| 自保AI | src/main/java/mod/test/mymodtest/entity/ai/DrinkPotionGoal.java |
| 趋光AI | src/main/java/mod/test/mymodtest/entity/ai/SeekLightGoal.java |
| 生成器 | src/main/java/mod/test/mymodtest/entity/spawn/MysteriousMerchantSpawner.java |
| 生成器状态 | src/main/java/mod/test/mymodtest/world/MerchantSpawnerState.java |
| 物品注册 | src/main/java/mod/test/mymodtest/registry/ModItems.java |

---

## 待确定数值（仍需决定）

参考 docs/TODO.md