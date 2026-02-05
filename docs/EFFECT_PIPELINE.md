# EFFECT_PIPELINE.md - 特效管线详解

## 概述

特效管线是所有装备效果的统一处理流程，确保触发逻辑一致、可追溯、可调试。

```
Event → Context → Check → Trigger → Apply → Log
```

---

## ShouldTrigger 详解

### 输入

```java
public class TriggerInput {
    // 玩家状态
    PlayerState playerState;
    
    // 战斗上下文（可为 null）
    CombatContext combatContext;
    
    // 目标上下文（可为 null）
    TargetContext targetContext;
    
    // 效果配置
    EffectConfig config;
    
    // 当前游戏时间
    long currentTick;
}
```

### PlayerState

```java
public class PlayerState {
    String playerName;           // 玩家名（用于日志）
    UUID playerId;               // 玩家 UUID（用于冷却 key）
    
    float currentHealth;         // 当前血量
    float maxHealth;             // 最大血量
    float healthPercent;         // currentHealth / maxHealth
    
    int lightLevel;              // 脚下光照等级 (0-15)
    boolean isInDarkness;        // lightLevel <= 7
    
    boolean isOnFire;
    boolean isInWater;
    boolean isSprinting;
    boolean isSneaking;
    
    String dimension;            // "overworld" / "the_nether" / "the_end"
    BlockPos position;           // 当前位置
    
    // 装备状态
    ItemStack headSlot;
    ItemStack chestSlot;
    ItemStack legsSlot;
    ItemStack feetSlot;
    int equippedSetPieces;       // 套装件数
    
    // 手持物品（用于图腾检测）
    ItemStack mainHand;
    ItemStack offHand;
    boolean hasTotem;            // mainHand 或 offHand 有图腾
}
```

### CombatContext

```java
public class CombatContext {
    Entity source;               // 伤害来源实体
    Entity target;               // 受伤目标
    
    float damageAmount;          // 原始伤害值
    String damageType;           // "mob_attack" / "explosion" / "fall" / ...
    
    boolean isHostileMob;        // source 是否为敌对生物
    boolean isBossEntity;        // source 是否为 Boss
    boolean isExplosion;         // 是否为爆炸伤害
    boolean wouldKill;           // damageAmount >= target.health
}
```

### TargetContext

```java
public class TargetContext {
    Entity mob;                  // 锁定玩家的怪物
    Entity previousTarget;       // 之前的目标
    Entity newTarget;            // 新目标（玩家）
    
    boolean isFirstTarget;       // previousTarget != player && newTarget == player
    boolean isAngered;           // 用于末影人/猪灵的愤怒状态
}
```

### 输出

```java
public class TriggerResult {
    boolean shouldTrigger;       // 是否应该触发
    String reason;               // 原因（使用词典中的固定词）
    
    // 调试信息（仅 TRACE 级别使用）
    List<String> checkChain;     // 判定链记录
}
```

---

## 判定顺序

按优先级从高到低，**first-fail** 原则：

```
1. 装备检查    → not_wearing_helmet
2. 条件检查    → not_dark / health_above_threshold / ...
3. 目标检查    → target_invalid / boss_block / ...
4. 边沿检查    → already_triggered (遗世之环专用)
5. 图腾检查    → totem_active (回溯额饰专用)
6. CD 检查     → cd_hit
7. RNG 检查    → rng_fail
8. 全部通过    → OK
```

### 判定链示例

```
[TRACE] check_equipment result=OK helmet=sentinel_helmet
[TRACE] check_condition result=OK light=3 threshold=7 is_dark=true
[TRACE] check_cd result=OK cd_total=800 cd_left=0
[TRACE] check_rng result=OK roll=0.12 need=1.0 hit=YES
[INFO] trigger result=OK effect=sentinel_glow
```

---

## 冷却系统

### 冷却 Key 格式

```
<player_uuid>:<effect_id>
```

示例：
```
550e8400-e29b-41d4-a716-446655440000:sentinel_glow
550e8400-e29b-41d4-a716-446655440000:silent_oath_reduction
```

### CooldownManager API

```java
public class CooldownManager {
    // 检查冷却是否就绪
    boolean isReady(UUID playerId, String effectId, long currentTick);
    
    // 获取剩余冷却
    long getRemainingTicks(UUID playerId, String effectId, long currentTick);
    
    // 设置冷却
    void setCooldown(UUID playerId, String effectId, long currentTick, long durationTicks);
    
    // 清除冷却（调试用）
    void clearCooldown(UUID playerId, String effectId);
    
    // 清除玩家所有冷却（玩家下线时）
    void clearAllCooldowns(UUID playerId);
}
```

### 冷却存储

```java
// 内部存储结构
Map<String, Long> cooldowns = new ConcurrentHashMap<>();
// key: "<player_uuid>:<effect_id>"
// value: 冷却结束的 tick
```

---

## 随机系统

### RNG 检查

```java
boolean checkRng(float chance) {
    if (chance >= 1.0f) return true;
    if (chance <= 0.0f) return false;
    
    float roll = random.nextFloat();  // [0, 1)
    boolean hit = roll < chance;
    
    // TRACE 日志
    logger.trace("rng{roll=" + roll + " need=" + chance + " hit=" + (hit ? "YES" : "NO") + "}");
    
    return hit;
}
```

### 概率配置示例

| 效果 | 概率 | 说明 |
|------|------|------|
| 哨兵瞭望 | 1.0 | 100%，无随机 |
| 沉默誓约 | 1.0 | 100%，无随机 |
| 流亡面甲 | 1.0 | 100%，无随机 |
| 遗世之环 | 1.0 | 100%，无随机 |
| 回溯额饰 | 1.0 | 100%，无随机 |

> 当前设计所有头盔效果无概率，但保留 RNG 接口以便未来扩展

---

## Boss 例外规则

### Boss 实体列表

```java
Set<EntityType<?>> BOSS_ENTITIES = Set.of(
    EntityType.ENDER_DRAGON,
    EntityType.WITHER,
    EntityType.ELDER_GUARDIAN,
    EntityType.WARDEN
);

boolean isBoss(Entity entity) {
    return BOSS_ENTITIES.contains(entity.getType());
}
```

### 各效果对 Boss 的处理

| 效果 | Boss 处理 |
|------|-----------|
| 哨兵瞭望 | 正常标记（Glowing 对 Boss 有效） |
| 沉默誓约 | 正常减伤（Boss 也是敌对生物） |
| 流亡面甲 | N/A（不涉及目标） |
| 遗世之环 | 正常触发（Boss 锁定也算） |
| 回溯额饰 | 正常触发（Boss 爆炸也算） |

---

## 日志/Debug 开关策略

### 开关配置

```java
public class ModConfig {
    // 日志开关
    boolean debugEnabled = false;  // 控制 TRACE 输出
    
    // 各效果开关（用于快速禁用问题效果）
    boolean sentinelEnabled = true;
    boolean silentOathEnabled = true;
    boolean exileMaskEnabled = true;
    boolean relicCircletEnabled = true;
    boolean retracerEnabled = true;
}
```

### 日志触发点

| 触发点 | 级别 | 内容 |
|--------|------|------|
| 配置加载 | INFO | 加载成功/失败 |
| 物品注册 | INFO | 注册成功 |
| 判定开始 | TRACE | 开始判定 |
| 每步判定 | TRACE | 判定链细节 |
| 判定结束 | INFO | 最终结果 |
| 效果应用 | INFO | 应用的效果参数 |
| 状态变化 | INFO | 重要状态变更 |
| 异常情况 | WARN | 配置缺失/参数 clamp |

---

## 各头盔的 Pipeline 特化

### 1. 哨兵的最后瞭望

```
ServerTickEvent (每 20 ticks)
    │
    ▼
检查是否穿戴 → not_wearing_helmet
    │
    ▼
检查光照 ≤ 7 → not_dark
    │
    ▼
检查 CD → cd_hit
    │
    ▼
扫描范围内敌对生物 (16-20 格)
    │
    ▼
对每个敌对生物施加 Glowing 5s
    │
    ▼
进入 CD (800 ticks = 40s)
```

### 2. 沉默之誓约

```
LivingHurtEvent
    │
    ▼
检查是否穿戴 → not_wearing_helmet
    │
    ▼
检查伤害来源是否敌对生物 → damage_not_hostile
    │
    ▼
检查原始伤害 ≥ 2 → damage_too_low
    │
    ▼
检查 CD → cd_hit
    │
    ▼
减免 2 点伤害
    │
    ▼
进入 CD (600 ticks = 30s)
```

### 3. 流亡者的面甲

```
ServerTickEvent (每 20 ticks)
    │
    ▼
检查是否穿戴 → not_wearing_helmet
    │
    ▼
计算 healthPercent
    │
    ├── healthPercent >= 0.5 → 移除/归零 AttributeModifier
    │
    └── healthPercent < 0.5 → 计算增伤并覆盖式更新
        │
        ▼
        damageBonus = min((0.5 - healthPercent) / 0.15, 4) * 0.5
        // 每损失 1.5♥(7.5%) → +0.5♥，上限 +2♥
```

### 4. 遗世之环

```
EntityTargetEvent
    │
    ▼
检查是否穿戴 → not_wearing_helmet
    │
    ▼
检查 newTarget 是否为玩家 → target_invalid
    │
    ▼
检查是否为边沿触发 (previousTarget != player) → already_triggered
    │
    ▼
检查 CD → cd_hit
    │
    ▼
施加 Absorption I (3s)
    │
    ▼
进入 CD (600 ticks = 30s)

--- 末影人/猪灵特殊分支 ---

ServerTickEvent (每 20 ticks)
    │
    ▼
检查是否穿戴
    │
    ▼
检查附近是否有愤怒状态的末影人/猪灵/僵尸猪人
    │
    ▼
检查该怪物是否已触发过 → already_triggered
    │
    ▼
检查 CD → cd_hit
    │
    ▼
施加 Absorption I (3s)
    │
    ▼
记录该怪物已触发
    │
    ▼
进入 CD
```

### 5. 回溯者的额饰

```
LivingHurtEvent (伤害计算阶段)
    │
    ▼
检查是否穿戴 → not_wearing_helmet
    │
    ▼
检查是否爆炸伤害 → damage_not_explosion
    │
    ▼
检查是否会致死 (damage >= currentHealth) → (不触发)
    │
    ▼
检查玩家是否持有图腾 → totem_active (图腾优先)
    │
    ▼
检查 CD → cd_hit
    │
    ▼
取消致死：setHealth(2.0f)
    │
    ▼
施加 Resistance V (1-2s)
    │
    ▼
播放独特音效/粒子
    │
    ▼
进入 CD (18000 ticks = 15min)
```

---

## 各胸甲的 Pipeline 特化

### 1. 旧市护甲

```
=== 交易经验通道 ===

Mixin: TradeOutputSlot#onTakeItem
    │
    ▼
检查是否穿戴 → not_wearing_chestplate
    │
    ▼
检查是否首次该交易槽 → trade_already_triggered
    │ (key: playerUUID + merchantUUID + tradeIndex)
    ▼
检查 CD → cd_hit (交易CD独立)
    │
    ▼
RNG 50% → rng_fail
    │
    ▼
计算额外经验 = 原经验 × 0.5
    │
    ▼
补发经验给玩家
    │
    ▼
标记该交易槽已触发 + 进入 CD (1200 ticks = 60s)

=== 击杀经验通道 ===

Mixin: LivingEntity#dropXp
    │
    ▼
检查 attacker 是否为玩家 → not_player_kill
    │
    ▼
检查是否穿戴 → not_wearing_chestplate
    │
    ▼
检查 CD → cd_hit (击杀CD独立)
    │
    ▼
RNG 25% → rng_fail
    │
    ▼
计算额外经验 = 原掉落经验 × 1
    │
    ▼
补发经验
    │
    ▼
进入 CD (600 ticks = 30s)
```

### 2. 流血契约之胸铠

```
=== 受击储能 ===

Mixin: LivingEntity#damage (玩家受害者)
    │
    ▼
检查是否穿戴 → not_wearing_chestplate
    │
    ▼
验证伤害来源 → invalid_damage_source
    │ (必须: attacker instanceof LivingEntity)
    │ (排除: explosion, fire, magic, outOfWorld, DOT)
    ▼
RNG 50% → rng_fail
    │
    ▼
计算额外扣血 = min(amount × 0.5, 4.0)
    │
    ▼
累加到血契池 = min(pool + extra, 8.0)
    │
    ▼
设置过期时间 = currentTick + 200
    │
    ▼
修改本次伤害 amount += extra (原地增加)
    │
    ▼
[INFO] 日志储能

=== 下一击结算 ===

Mixin: PlayerEntity#attack
    │
    ▼
检查是否穿戴 → not_wearing_chestplate
    │
    ▼
检查血契池 > 0 → pool_empty
    │
    ▼
检查窗口未过期 (currentTick < expireTime) → pool_expired
    │
    ▼
对 target 追加伤害 = pool (物理伤害源)
    │
    ▼
清空池
    │
    ▼
[INFO] 日志释放
```

### 3. 鬼神之铠

```
=== 伤害减免 ===

Mixin: LivingEntity#damage (玩家受害者)
    │
    ▼
检查是否穿戴 → not_wearing_chestplate
    │
    ▼
检查 attacker 是否亡灵 → not_undead
    │ (attacker.getGroup() == UNDEAD)
    ▼
RNG: isBoss ? 15% : 30% → rng_fail
    │
    ▼
修改 amount *= 0.85 (减伤 15%)
    │
    ▼
[INFO] 日志减伤

=== Debuff 免疫 ===

Mixin: LivingEntity#addStatusEffect (玩家被施加)
    │
    ▼
检查是否穿戴 → not_wearing_chestplate
    │
    ▼
检查效果类型是否为 Wither/Hunger/Slowness → debuff_not_tracked
    │
    ▼
检查 source 是否亡灵 → not_undead
    │
    ▼
RNG: isBoss ? 25% : 50% → rng_fail
    │
    ▼
返回 false (取消效果添加)
    │
    ▼
[INFO] 日志免疫
```

### 4. 商人的防风衣

```
=== 击退抗性 ===

穿戴时自动应用 AttributeModifier
    │
    ▼
GENERIC_KNOCKBACK_RESISTANCE +0.3
    │ (使用固定 UUID)
    ▼
脱下时自动移除

=== 低血速度 (边沿触发) ===

ServerTickEvent (每 20 ticks)
    │
    ▼
检查是否穿戴 → not_wearing_chestplate
    │
    ▼
计算 healthRatio = current / max
    │
    ├── healthRatio >= 0.6 → 设置 rearmReady = true
    │
    ▼
边沿检测: wasAboveThreshold && !isAboveThreshold
    │ (从 ≥50% 跌到 <50%)
    ▼
检查 rearmReady == true → not_rearmed
    │
    ▼
检查 CD → cd_hit
    │
    ▼
施加 Speed I (100 ticks = 5s)
    │
    ▼
设置 rearmReady = false
    │
    ▼
更新 wasAboveThreshold
    │
    ▼
进入 CD (1800 ticks = 90s)
```

### 5. 虚空之噬

```
Mixin: PlayerEntity#attack
    │
    ▼
检查是否穿戴 → not_wearing_chestplate
    │
    ▼
检查 target instanceof LivingEntity → target_not_living
    │
    ▼
检查 CD → cd_hit
    │
    ▼
计算基础伤害 (本次攻击伤害)
    │
    ▼
计算真伤比例 = isBoss ? 0.02 : 0.04
    │
    ▼
计算真伤 = baseDamage × ratio
    │
    ▼
使用绕过护甲的 DamageSource (magic)
    │
    ▼
对 target 追加独立伤害调用
    │
    ▼
进入 CD (100 ticks = 5s)
```

---

## 效果应用层

### 通用应用函数

```java
void applyStatusEffect(Player player, String effectId, int amplifier, int duration, boolean showParticles) {
    StatusEffectInstance effect = new StatusEffectInstance(
        getEffectById(effectId),
        duration,
        amplifier,
        false,           // ambient
        showParticles,   // showParticles
        true             // showIcon
    );
    player.addStatusEffect(effect);
    
    logger.info("[MoonTrace|Armor|APPLY] action=apply result=OK effect={} final{{dur={} amp={}}} ctx{{p={}}}",
        effectId, duration, amplifier, player.getName());
}
```

### 伤害修改

```java
void modifyDamage(LivingHurtEvent event, float reduction) {
    float original = event.getAmount();
    float modified = Math.max(0, original - reduction);
    event.setAmount(modified);
    
    logger.info("[MoonTrace|Armor|APPLY] action=damage_modify result=OK final{{amount={}}} src{{original={}}} reduction={} ctx{{p={}}}",
        modified, original, reduction, event.getEntity().getName());
}
```

### 属性修改

```java
void updateAttributeModifier(Player player, UUID modifierId, String attribute, double value, Operation op) {
    AttributeInstance instance = player.getAttribute(attribute);
    
    // 移除旧的（如果存在）
    instance.removeModifier(modifierId);
    
    // 添加新的（如果 value != 0）
    if (value != 0) {
        AttributeModifier modifier = new AttributeModifier(
            modifierId,
            "exile_mask_damage",
            value,
            op
        );
        instance.addTransientModifier(modifier);
    }
    
    logger.info("[MoonTrace|Armor|APPLY] action=attribute_update result=OK attr={} value={} ctx{{p={}}}",
        attribute, value, player.getName());
}
```
