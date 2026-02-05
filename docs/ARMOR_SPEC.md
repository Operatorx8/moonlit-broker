# ARMOR_SPEC.md - 盔甲系统规格

## 定位

神秘商人盔甲系列：独立散件，各有独特效果，偏向**生存探索**风格，兼顾**战斗辅助**。

- **头盔（5件）**：侦察、防御、保命
- **胸甲（5件）**：经验、输出、抗性

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

### 不允许

1. ❌ 不引入新的伤害系统（只用原版 damage/attribute）
2. ❌ 不修改原版机制行为（如图腾触发逻辑）
3. ❌ 不在物品类硬编码数值（配置驱动）
4. ❌ 不做每 tick 的耗时操作（低频扫描）

---

## 装备列表

### 头盔（5件）

| # | 内部 ID | 显示名 | 稀有度 | 定位 |
|---|---------|--------|--------|------|
| 1 | `sentinel_helmet` | 哨兵的最后瞭望 | EPIC | 探索/侦察 |
| 2 | `silent_oath_helmet` | 沉默之誓约 | EPIC | 防御/减伤 |
| 3 | `exile_mask_helmet` | 流亡者的面甲 | EPIC | 进攻/背水一战 |
| 4 | `relic_circlet_helmet` | 遗世之环 | EPIC | 防御/预警 |
| 5 | `retracer_ornament_helmet` | 回溯者的额饰 | EPIC | 生存/保命 |

### 胸甲（5件）

| # | 内部 ID | 显示名 | 稀有度 | 定位 |
|---|---------|--------|--------|------|
| 1 | `old_market_chestplate` | 旧市护甲 | RARE | 经验/收益 |
| 2 | `blood_pact_chestplate` | 流血契约之胸铠 | RARE | 进攻/以血换刃 |
| 3 | `ghost_god_chestplate` | 鬼神之铠 | EPIC | 防御/抗亡灵 |
| 4 | `windbreaker_chestplate` | 商人的防风衣 | UNCOMMON | 机动/生存 |
| 5 | `void_devourer_chestplate` | 虚空之噬 | RARE | 进攻/真伤 |

---

## 头盔详细规格

### 1. 哨兵的最后瞭望 (Sentinel's Last Watch)

**效果**：黑暗中侦测敌对生物并标记

**机制**：
- 仅在光照 ≤ 7 时可触发
- 低频扫描（每 20 ticks 检查一次）
- 触发时对范围内敌对生物施加 Glowing 5s
- 范围：16-20 格（建议 18 格）
- 冷却：40s (800 ticks)

**Lore**：
- EN: "When the world goes dim, it makes predators confess their shape."
- Subtitle: "A lantern for enemies—paid with silence."

**触发条件**：
```
穿戴该头盔 AND 光照 ≤ 7 AND CD 就绪
```

**日志**：
```
[MoonTrace|Armor|TRIGGER] action=trigger result=OK effect=sentinel_glow targets=3 range=18 ctx{p=Steve dim=overworld light=3}
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

## 胸甲详细规格

### 1. 旧市护甲 (Old Market Chestplate)

**效果**：经验"分流增益"（交易/击杀双通道）

**机制**：
- **交易经验**：50% 概率 ×1.5，CD 60s，仅首次完成该交易槽
- **击杀经验**：25% 概率 ×2，CD 30s
- 两条增益各自独立冷却

**Lore**：
- EN: "Old coins still ring true—if you know where to spend them."

**Mixin 入口**：
1. 交易：`TradeOutputSlot#onTakeItem(PlayerEntity, ItemStack)`
2. 击杀：`LivingEntity#dropXp()` / `LivingEntity#dropExperience()`

**交易首次触发判定**：
```java
// key: playerUUID + merchantUUID + tradeIndex
String tradeKey = player.getUuid() + ":" + merchant.getUuid() + ":" + tradeIndex;
if (triggeredTrades.contains(tradeKey)) return; // 已触发过
triggeredTrades.add(tradeKey);
```

**击杀经验判定**：
```java
// 仅限玩家击杀
Entity attacker = source.getAttacker();
if (!(attacker instanceof PlayerEntity)) return;
// 排除经验瓶/指令等非击杀来源
```

**触发条件**：
```
穿戴该胸甲 AND
(
  (交易完成 AND 首次该交易槽 AND RNG 50% AND 交易CD就绪)
  OR
  (击杀怪物 AND RNG 25% AND 击杀CD就绪)
)
```

**日志**：
```
[MoonTrace|Armor|TRIGGER] action=trigger result=OK effect=trade_xp_bonus rng{roll=0.3 need=0.5 hit=YES} ctx{p=Steve merchant=WanderingTrader trade_idx=2}
[MoonTrace|Armor|APPLY] action=apply result=OK effect=xp_multiplier final{base=10 bonus=5 total=15} ctx{p=Steve}

[MoonTrace|Armor|TRIGGER] action=trigger result=OK effect=kill_xp_bonus rng{roll=0.1 need=0.25 hit=YES} ctx{p=Steve target=Zombie}
[MoonTrace|Armor|APPLY] action=apply result=OK effect=xp_multiplier final{base=5 bonus=5 total=10} ctx{p=Steve}
```

---

### 2. 流血契约之胸铠 (Blood Pact Chestplate)

**效果**：受击"血契储能" → 强化下一击

**机制**：
- 受击时 50% 概率触发血契
- 触发时额外扣血：min(本次伤害 × 50%, 2♥)
- 储能池上限：4♥ 等价伤害（8.0 damage）
- 下一击窗口：10s（超时清空）
- 玩家下一次有效近战命中消耗储能，转为额外物理伤害

**Lore**：
- EN: "Signed in blood—paid on the next strike."

**Mixin 入口**：
1. 受击储能：`LivingEntity#damage(DamageSource, float)`（玩家作为受害者）
2. 下一击结算：`PlayerEntity#attack(Entity target)`

**触发源限定**（关键）：
```java
boolean isValidSource(DamageSource source) {
    Entity attacker = source.getAttacker();
    if (!(attacker instanceof LivingEntity)) return false;
    
    // 排除非直接伤害
    if (source.isExplosion()) return false;
    if (source.isFire()) return false;
    if (source.isMagic()) return false;
    if (source.isOutOfWorld()) return false;
    
    // 只允许 EntityDamageSource / ProjectileDamageSource
    return true;
}
```

**储能逻辑**：
```java
// 在 damage mixin 中
float extraDamage = Math.min(amount * 0.5f, 4.0f); // 上限 2♥
bloodPool = Math.min(bloodPool + extraDamage, 8.0f); // 池上限 4♥
poolExpireTime = currentTick + 200; // 10s 窗口

// 修改本次伤害（额外扣血）
return amount + extraDamage;
```

**结算逻辑**：
```java
// 在 attack mixin 中
if (bloodPool > 0 && currentTick < poolExpireTime) {
    float bonusDamage = bloodPool;
    target.damage(playerDamageSource, bonusDamage);
    bloodPool = 0;
}
```

**触发条件**：
```
穿戴该胸甲 AND 
受击来源=LivingEntity直接伤害 AND 
RNG 50%
```

**日志**：
```
[MoonTrace|Armor|TRIGGER] action=trigger result=OK effect=blood_pact_charge rng{roll=0.3 need=0.5 hit=YES} ctx{p=Steve t=Zombie}
[MoonTrace|Armor|APPLY] action=apply result=OK effect=blood_charge extra_dmg=2.0 pool{before=0 after=2.0 cap=8.0} ctx{p=Steve}

[MoonTrace|Armor|TRIGGER] action=trigger result=OK effect=blood_pact_release ctx{p=Steve t=Skeleton pool=4.0}
[MoonTrace|Armor|APPLY] action=apply result=OK effect=bonus_damage final{amount=4.0} pool_cleared=true ctx{p=Steve}
```

---

### 3. 鬼神之铠 (Ghost God Chestplate)

**效果**：对亡灵的"减伤 + 负面抗性"

**机制**：
- **亡灵伤害减免**：30% 概率使该次伤害 -15%（Boss 15% 概率）
- **亡灵 Debuff 免疫**：50% 概率免疫 Wither/Hunger/Slowness（Boss 25%）

**Lore**：
- EN: "Undead curses stop at the edge of this steel."

**Mixin 入口**：
1. 伤害减免：`LivingEntity#damage(DamageSource, float)`（玩家受害者）
2. Debuff 免疫：`LivingEntity#addStatusEffect(StatusEffectInstance, Entity source)`

**亡灵判定**：
```java
boolean isUndead(Entity entity) {
    if (entity instanceof LivingEntity living) {
        return living.getGroup() == EntityGroup.UNDEAD;
    }
    return false;
}
```

**伤害减免逻辑**：
```java
// 在 damage mixin 中
Entity attacker = source.getAttacker();
if (!isUndead(attacker)) return amount;

float chance = isBoss(attacker) ? 0.15f : 0.30f;
if (!rollChance(chance)) return amount;

return amount * 0.85f; // -15% 伤害
```

**Debuff 免疫逻辑**：
```java
// 在 addStatusEffect mixin 中
StatusEffect effect = instance.getEffectType();
if (effect != StatusEffects.WITHER && 
    effect != StatusEffects.HUNGER && 
    effect != StatusEffects.SLOWNESS) {
    return true; // 允许添加
}

Entity source = /* 效果来源 */;
if (!isUndead(source)) return true;

float chance = isBoss(source) ? 0.25f : 0.50f;
if (rollChance(chance)) {
    return false; // 拒绝添加（免疫）
}
return true;
```

**触发条件**：
```
穿戴该胸甲 AND 来源=亡灵 AND RNG 通过
```

**日志**：
```
[MoonTrace|Armor|TRIGGER] action=trigger result=OK effect=undead_damage_reduction rng{roll=0.2 need=0.3 hit=YES} ctx{p=Steve t=Zombie}
[MoonTrace|Armor|APPLY] action=apply result=OK effect=damage_reduction final{amount=4.25} src{original=5.0} ctx{p=Steve}

[MoonTrace|Armor|TRIGGER] action=trigger result=OK effect=undead_debuff_immune rng{roll=0.3 need=0.5 hit=YES} debuff=wither ctx{p=Steve t=WitherSkeleton}
[MoonTrace|Armor|APPLY] action=apply result=OK effect=debuff_blocked debuff=wither ctx{p=Steve}
```

---

### 4. 商人的防风衣 (Merchant's Windbreaker)

**效果**：防风（击退抗性）+ 低血机动爆发

**机制**：
- **击退抗性**：+0.3（原版属性 0~1）
- **低血速度**：生命从 ≥50% 跌到 <50% 时，获得 Speed I 5s，CD 90s
- **边沿触发**：触发后需回到 ≥60% 才允许下次触发

**Lore**：
- EN: "Wind can't push you—panic can."

**实现入口**：
1. 击退抗性：`ArmorItem` 的 `AttributeModifier`（穿戴时自动应用）
2. 低血速度：Server tick（每 20 ticks 检查）

**边沿触发状态机**：
```java
// per-player 状态
boolean wasAboveThreshold = true;  // 上一次是否在 50% 以上
boolean rearmReady = true;         // 是否已回到 60% 可重新触发

void tickCheck(Player player) {
    float healthRatio = player.getHealth() / player.getMaxHealth();
    
    // 检查是否需要重新武装
    if (healthRatio >= 0.6f) {
        rearmReady = true;
    }
    
    // 边沿检测：从 ≥50% 跌到 <50%
    boolean isAboveThreshold = healthRatio >= 0.5f;
    if (wasAboveThreshold && !isAboveThreshold && rearmReady && cdReady) {
        // 触发！
        applySpeed(player);
        rearmReady = false;
        setCooldown();
    }
    
    wasAboveThreshold = isAboveThreshold;
}
```

**触发条件**：
```
穿戴该胸甲 AND 
血量从 ≥50% 跌到 <50%（边沿） AND 
rearmReady=true AND 
CD 就绪
```

**日志**：
```
[MoonTrace|Armor|STATE] action=state_change result=OK state=rearm_ready enabled=true health_pct=0.62 ctx{p=Steve}
[MoonTrace|Armor|TRIGGER] action=trigger result=OK effect=low_health_speed edge{from=0.52 to=0.48} ctx{p=Steve}
[MoonTrace|Armor|APPLY] action=apply result=OK effect=speed final{dur=100 amp=0} ctx{p=Steve}
```

---

### 5. 虚空之噬 (Void Devourer)

**效果**：低频"虚空咬合"真实伤害

**机制**：
- 对任意目标造成攻击伤害时：附加 4% 真实伤害
- CD：5s（100 ticks）
- Boss：仅附加 2% 真实伤害

**Lore**：
- EN: "Every five seconds, the void takes its bite."

**Mixin 入口**：`PlayerEntity#attack(Entity target)`

**真实伤害实现**：
```java
// 在 attack mixin 中
if (!(target instanceof LivingEntity living)) return;
if (!cdReady) return;

float baseDamage = /* 本次攻击伤害 */;
float trueDamageRatio = isBoss(target) ? 0.02f : 0.04f;
float trueDamage = baseDamage * trueDamageRatio;

// 使用绕过护甲的 DamageSource
DamageSource bypassArmor = player.getDamageSources().magic(); // 或自定义
living.damage(bypassArmor, trueDamage);

setCooldown(100); // 5s CD
```

**触发条件**：
```
穿戴该胸甲 AND 
攻击命中 LivingEntity AND 
CD 就绪
```

**日志**：
```
[MoonTrace|Armor|TRIGGER] action=trigger result=OK effect=void_bite ctx{p=Steve t=Zombie base_dmg=6.0}
[MoonTrace|Armor|APPLY] action=apply result=OK effect=true_damage final{amount=0.24 ratio=0.04} ctx{p=Steve t=Zombie}

[MoonTrace|Armor|TRIGGER] action=trigger result=OK effect=void_bite boss_modifier=true ctx{p=Steve t=Wither base_dmg=6.0}
[MoonTrace|Armor|APPLY] action=apply result=OK effect=true_damage final{amount=0.12 ratio=0.02} ctx{p=Steve t=Wither}
```

---

## 物品显示规范

### 名称颜色

- EPIC → §5 紫色
- RARE → §9 蓝色
- UNCOMMON → §e 黄色

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
§7"When the world goes dim, it makes predators confess their shape."
§8A lantern for enemies—paid with silence.

§7【哨兵视野】
§7在黑暗中侦测附近敌对生物，使其发光 5 秒。
§7范围：18 格

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
