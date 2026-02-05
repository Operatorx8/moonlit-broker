# ARMOR_SPEC.md - 盔甲系统规格

## 定位

神秘商人头盔系列：5 件独立头盔，各有独特效果，偏向**生存探索**风格，兼顾**战斗辅助**。

### 设计理念

- **独特性**：每件头盔解决一个特定场景的问题
- **决策感**：玩家需要根据当前任务选择头盔
- **平衡性**：强力效果配长 CD，弱效果可频繁触发
- **可追溯**：所有触发都有日志，便于验证和调试

---

## 设计约束（不变量）

### 必须遵守

1. **单件效果独立**：每件头盔只提供一种主效果，不互相依赖
2. **冷却隔离**：各头盔冷却独立，互不影响
3. **服务端权威**：所有判定在服务端执行，客户端只负责展示
4. **固定 UUID**：动态属性修改器使用预定义 UUID，避免叠加 bug
5. **日志规范**：所有触发必须输出 INFO 级别日志
6. **附魔系数分档**：头盔 enchantability 按 `Rarity` 映射原版材质档位
   `UNCOMMON -> IRON`, `RARE -> CHAIN`, `EPIC -> NETHERITE`

### 不允许

1. ❌ 不引入新的伤害系统（只用原版 damage/attribute）
2. ❌ 不修改原版机制行为（如图腾触发逻辑）
3. ❌ 不在物品类硬编码数值（配置驱动）
4. ❌ 不做每 tick 的耗时操作（低频扫描）

---

## 头盔列表

| # | 内部 ID | 显示名 | 定位 |
|---|---------|--------|------|
| 1 | `sentinel_helmet` | 哨兵的最后瞭望 | 探索/侦察 |
| 2 | `silent_oath_helmet` | 沉默之誓约 | 防御/减伤 |
| 3 | `exile_mask_helmet` | 流亡者的面甲 | 进攻/背水一战 |
| 4 | `relic_circlet_helmet` | 遗世之环 | 防御/预警 |
| 5 | `retracer_ornament_helmet` | 回溯者的额饰 | 生存/保命 |

---

## 头盔详细规格

### 1. 哨兵的最后瞭望 (Sentinel's Last Watch)

**效果**：Echo Pulse（回声测距，仅给玩家线索）

**机制**：
- 仅在光照 ≤ 7 时可触发
- 低频扫描（每 20 ticks 检查一次）
- 触发时仅给玩家 `Speed I`，持续 30 ticks（1.5s）
- 触发时播放原版警示音（`minecraft:block.bell.use`）
- 不对敌对生物施加任何状态，不生成粒子环
- 范围：16 格
- 冷却：40s (800 ticks)

**Lore**：
- EN: "When the dark moves, the watch bell answers."

**触发条件**：
```
穿戴该头盔 AND 光照 ≤ 7 AND 16 格内存在敌对生物 AND CD 就绪
```

**日志**：
```
[MoonTrace|Armor|TRIGGER] action=trigger result=OK effect=sentinel_echo_pulse targets=3 range=16 ctx{p=Steve dim=overworld light=3}
[MoonTrace|Armor|APPLY] action=apply result=OK effect=speed final{dur=30 amp=0} sound=minecraft:block.bell.use ctx{p=Steve}
```

---

### 2. 沉默之誓约 (Silent Oath)

**效果**：冷却窗口内首次受伤减伤（仅怪物伤害）

**机制**：
- 仅对敌对生物伤害生效
- 原始伤害 ≥ 2 点才生效
- 减免 2 点伤害
- 冷却：30s (600 ticks)

**Lore**：
- EN: "The first strike is forgiven—after that, you pay in full."
- Subtitle: "One mercy per moon. The rest is debt."

**触发条件**：
```
穿戴该头盔 AND 伤害来源=敌对生物 AND 原始伤害 ≥ 2 AND CD 就绪
```

**敌对生物定义**：
- 原生敌对：僵尸、骷髅、苦力怕等
- 被激怒的中立：末影人（被看）、猪灵（被偷）、僵尸猪人（被打）

**日志**：
```
[MoonTrace|Armor|TRIGGER] action=trigger result=OK effect=silent_oath_reduction ctx{p=Steve t=Zombie}
[MoonTrace|Armor|APPLY] action=damage_modify result=OK final{amount=3.0} src{original=5.0} reduction=2.0 ctx{p=Steve}
```

---

### 3. 流亡者的面甲 (Exile's Mask)

**效果**：低血增伤（实时计算、无叠加 bug）

**机制**：
- 生命值 < 50% 时激活
- 每损失 1.5♥ (7.5%) → 攻击力 +0.5♥ (1.0 damage)
- 上限：+2♥ (4.0 damage)
- 每 20 ticks 更新一次
- 使用固定 UUID 的 AttributeModifier 覆盖式更新

**Lore**：
- EN: "Hunger makes the blade honest."
- Subtitle: "The lower you fall, the sharper you become."

**计算公式**：
```
if healthPercent >= 0.5:
    damageBonus = 0
else:
    lostPercent = 0.5 - healthPercent
    stacks = floor(lostPercent / 0.075)  // 每 7.5% 一层
    damageBonus = min(stacks * 1.0, 4.0)  // 上限 4.0
```

**示例**：
| 血量百分比 | 损失百分比 | 层数 | 增伤 |
|-----------|-----------|------|------|
| 50%+ | 0% | 0 | +0 |
| 42.5% | 7.5% | 1 | +1.0 |
| 35% | 15% | 2 | +2.0 |
| 27.5% | 22.5% | 3 | +3.0 |
| 20% | 30% | 4 | +4.0 (上限) |
| 10% | 40% | 5 | +4.0 (上限) |

**日志**：
```
[MoonTrace|Armor|STATE] action=state_change result=OK state=exile_mask_active enabled=true health_pct=0.35 damage_bonus=2.0 ctx{p=Steve}
[MoonTrace|Armor|APPLY] action=attribute_update result=OK attr=generic.attack_damage value=2.0 ctx{p=Steve}
```

---

### 4. 遗世之环 (Relic Circlet)

**效果**：被盯上时获得短暂吸收护盾

**机制**：
- **边沿触发**：怪物首次将玩家设为目标时触发
- 给予 Absorption I，持续 3s (60 ticks)
- 冷却：30s (600 ticks)

**Lore**：
- EN: "Attention is a curse—this ring turns it into shelter."
- Subtitle: "When eyes turn to you, the world blinks first."

**边沿触发定义**：
- 普通怪物：`EntityTargetEvent` 中 `previousTarget != player && newTarget == player`
- 末影人/猪灵/僵尸猪人：检测愤怒状态变化（每 20 ticks 低频检测）

**避免重复触发**：
- 维护"已触发过的怪物集合"
- 怪物死亡/脱战后从集合移除
- 同一怪物在 CD 内不重复触发

**日志**：
```
[MoonTrace|Armor|TRIGGER] action=trigger result=OK effect=relic_circlet_shield first_target=true ctx{p=Steve mob=Zombie}
[MoonTrace|Armor|APPLY] action=apply result=OK effect=absorption final{dur=60 amp=0} ctx{p=Steve}
```

---

### 5. 回溯者的额饰 (Retracer's Ornament)

**效果**：爆炸致死保护（第二条命）

**机制**：
- 仅对爆炸伤害生效
- 仅在会致死时触发 (`damage >= currentHealth`)
- 玩家持有图腾时**不触发**（图腾优先）
- 阻止死亡，设置血量为 1♥ (2.0 HP)
- 给予短暂保护 1-2s
- 播放独特音效/粒子
- 冷却：15min (18000 ticks)

**Lore**：
- EN: "Not immortality—just one stolen heartbeat."
- Subtitle: "A receipt from death—redeemable once in a long while."

**爆炸伤害判定**：
```java
boolean isExplosion(DamageSource source) {
    return source.isExplosion()
        || source.getMsgId().equals("explosion")
        || source.getMsgId().equals("explosion.player");
}
```

**触发条件**：
```
穿戴该头盔 AND 
伤害类型=爆炸 AND 
伤害会致死 AND 
玩家无图腾 AND 
CD 就绪
```

**图腾检测**：
```java
boolean hasTotem(Player player) {
    return player.getMainHandItem().is(Items.TOTEM_OF_UNDYING)
        || player.getOffhandItem().is(Items.TOTEM_OF_UNDYING);
}
```

**日志**：
```
[MoonTrace|Armor|TRIGGER] action=trigger result=OK effect=retracer_save ctx{p=Steve dmg_src=Creeper dmg_type=explosion}
[MoonTrace|Armor|APPLY] action=apply result=OK effect=death_prevention final{health_set=2.0} ctx{p=Steve}
[MoonTrace|Armor|APPLY] action=apply result=OK effect=resistance final{dur=40 amp=4} ctx{p=Steve}
```

---

## 物品显示规范

### 名称颜色

- `UNCOMMON` -> 绿色名称
- `RARE` -> 蓝色名称
- `EPIC` -> 紫色名称

### Tooltip 结构

```
§5头盔名称
§7"英文 Lore 第一行"
§8英文 Subtitle

§7【效果】
§7效果描述文字

§8冷却：XXs
```

### 示例：哨兵的最后瞭望

```
§5哨兵的最后瞭望
§7"When the dark moves, the watch bell answers."

§7【回声测距】
§7在黑暗中侦测附近敌对生物，触发后为玩家提供 Speed I 1.5 秒与提示音。
§7范围：16 格

§8冷却：40 秒
```

---

## 材质/音效规范

### 材质

- 使用链甲模型 + 自定义贴图
- 贴图路径：`assets/mysterious_merchant/textures/armor/`

### 音效

- 穿戴音效：链甲音效（原版）
- 回溯额饰触发：自定义音效 + 粒子效果

---

## 获取方式

### Phase 1 (MVP)

- `/give` 命令
- 创造模式物品栏

### Phase 4 (商人联动)

- 神秘商人独家出售
- 高价格 + 稀有刷新
