# 神秘商人模组 - 开发进度

## 当前状态：Phase 3 已完成

最后更新：2026-01-27

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

## 📋 Phase 4 - 生成与消失（未开始）

- [ ] 生成条件检测（村庄附近100格）
- [ ] 雨天前置条件
- [ ] Despawn 逻辑（7天/30天）
- [ ] 消失预警（身体闪烁）

---

## 📋 Phase 5 - 交互与惩罚（未开始）

- [ ] 攻击惩罚（失明、反胃）
- [ ] 击杀惩罚（效果翻倍 + 消息）
- [ ] 特殊交互（展示神秘硬币）

---

## 📋 Phase 6 - NBT完善（未开始）

- [ ] spawnTick 保存
- [ ] secretOffers 保存
- [ ] merchantName 保存

---

## 下次开发指令

验证 Phase 3 功能，然后开始 Phase 4：
```
1. 验证 Phase 3 AI 行为：
   - 逃跑测试: /summon mymodtest:mysterious_merchant 然后 /summon zombie
   - 自保测试: 打商人到半血
   - 趋光测试: /time set night 后在附近放火把

2. 开始 Phase 4：
   - 生成条件检测（村庄附近）
   - Despawn 逻辑
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

---

## 待确定数值（仍需决定）

参考 docs/TODO.md