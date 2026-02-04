# ARMOR_PARAMS.md - 盔甲数值参数表

> 纯数值表，不含解释。设计理由见 ARMOR_SPEC.md 和 DECISIONS.md

---

## 基础属性

### 通用属性（5 件头盔共享）

| 属性 | 值 | 说明 |
|------|-----|------|
| `durabilityMultiplier` | 25 | 基础耐久乘数（头盔实际耐久 = 25 × 11 = 275） |
| `protection` | 3 | 护甲值（头盔槽位） |
| `toughness` | 2.0 | 韧性 |
| `knockbackResistance` | 0.1 | 击退抗性 10% |
| `enchantability` | 15 | 附魔等级 |
| `fireproof` | true | 防火（物品不会被熔岩/火焰销毁） |
| `repairable` | false | 不可铁砧修复 |
| `rarity` | EPIC | 稀有度（紫色名称） |

### 材质属性

| 属性 | 值 |
|------|-----|
| `equipSound` | `minecraft:item.armor.equip_chain` |
| `repairIngredient` | `null`（不可修复） |
| `textureBase` | `mysterious_merchant:textures/armor/merchant` |

---

## 效果参数

### 1. 哨兵的最后瞭望 (sentinel_helmet)

| 参数 | 值 | 单位 |
|------|-----|------|
| `effectId` | `minecraft:glowing` | - |
| `effectDuration` | 100 | ticks (5s) |
| `effectAmplifier` | 0 | (等级 I) |
| `scanRange` | 18.0 | blocks |
| `lightThreshold` | 7 | (光照 ≤ 7 触发) |
| `scanInterval` | 20 | ticks (1s) |
| `cooldown` | 800 | ticks (40s) |
| `triggerChance` | 1.0 | (100%) |
| `showParticles` | false | - |

### 2. 沉默之誓约 (silent_oath_helmet)

| 参数 | 值 | 单位 |
|------|-----|------|
| `damageReduction` | 2.0 | HP (1♥) |
| `minDamageToTrigger` | 2.0 | HP (伤害 ≥ 2 才生效) |
| `cooldown` | 600 | ticks (30s) |
| `triggerChance` | 1.0 | (100%) |
| `hostileOnly` | true | (仅敌对生物) |

### 3. 流亡者的面甲 (exile_mask_helmet)

| 参数 | 值 | 单位 |
|------|-----|------|
| `healthThreshold` | 0.5 | (血量 < 50% 激活) |
| `damagePerStack` | 1.0 | HP (+0.5♥ 攻击力) |
| `stackThreshold` | 0.075 | (每损失 7.5% 血量一层) |
| `maxStacks` | 4 | (上限 4 层) |
| `maxDamageBonus` | 4.0 | HP (上限 +2♥) |
| `updateInterval` | 20 | ticks (1s) |
| `attributeUUID` | `a1b2c3d4-e5f6-7890-abcd-ef1234567890` | - |
| `attributeOperation` | `ADDITION` | - |

### 4. 遗世之环 (relic_circlet_helmet)

| 参数 | 值 | 单位 |
|------|-----|------|
| `effectId` | `minecraft:absorption` | - |
| `effectDuration` | 60 | ticks (3s) |
| `effectAmplifier` | 0 | (等级 I) |
| `cooldown` | 600 | ticks (30s) |
| `triggerChance` | 1.0 | (100%) |
| `showParticles` | true | - |
| `angeredCheckInterval` | 20 | ticks (1s) |

### 5. 回溯者的额饰 (retracer_ornament_helmet)

| 参数 | 值 | 单位 |
|------|-----|------|
| `healthSetTo` | 2.0 | HP (1♥) |
| `resistanceEffectId` | `minecraft:resistance` | - |
| `resistanceDuration` | 40 | ticks (2s) |
| `resistanceAmplifier` | 4 | (等级 V = 100% 减伤) |
| `cooldown` | 18000 | ticks (15min) |
| `triggerChance` | 1.0 | (100%) |
| `totemPriority` | true | (图腾优先) |
| `soundEvent` | `mysterious_merchant:helmet.retracer_save` | - |
| `particleType` | `minecraft:totem_of_undying` | (复用图腾粒子) |

---

## 冷却时间汇总

| 头盔 | 冷却 (ticks) | 冷却 (秒) | 冷却 (分钟) |
|------|-------------|-----------|-------------|
| 哨兵的最后瞭望 | 800 | 40 | 0.67 |
| 沉默之誓约 | 600 | 30 | 0.5 |
| 流亡者的面甲 | N/A | N/A | N/A |
| 遗世之环 | 600 | 30 | 0.5 |
| 回溯者的额饰 | 18000 | 900 | 15 |

---

## 扫描/更新间隔汇总

| 头盔 | 间隔 (ticks) | 间隔 (秒) |
|------|-------------|-----------|
| 哨兵的最后瞭望 | 20 | 1 |
| 流亡者的面甲 | 20 | 1 |
| 遗世之环（愤怒检测） | 20 | 1 |

---

## 固定 UUID 列表

| 用途 | UUID |
|------|------|
| 流亡者面甲攻击力加成 | `a1b2c3d4-e5f6-7890-abcd-ef1234567890` |

---

## 常量定义（代码中使用）

```java
public class ArmorConstants {
    // 耐久度乘数（原版：头11, 胸16, 腿15, 脚13）
    public static final int[] DURABILITY_MULTIPLIER = {11, 16, 15, 13};
    
    // 神秘商人盔甲基础耐久乘数
    public static final int MERCHANT_DURABILITY_BASE = 25;
    
    // 头盔护甲值
    public static final int HELMET_PROTECTION = 3;
    
    // 韧性
    public static final float ARMOR_TOUGHNESS = 2.0f;
    
    // 击退抗性
    public static final float KNOCKBACK_RESISTANCE = 0.1f;
    
    // 附魔等级
    public static final int ENCHANTABILITY = 15;
}

public class EffectConstants {
    // 哨兵瞭望
    public static final int SENTINEL_GLOW_DURATION = 100;
    public static final float SENTINEL_SCAN_RANGE = 18.0f;
    public static final int SENTINEL_LIGHT_THRESHOLD = 7;
    public static final int SENTINEL_SCAN_INTERVAL = 20;
    public static final int SENTINEL_COOLDOWN = 800;
    
    // 沉默誓约
    public static final float SILENT_OATH_REDUCTION = 2.0f;
    public static final float SILENT_OATH_MIN_DAMAGE = 2.0f;
    public static final int SILENT_OATH_COOLDOWN = 600;
    
    // 流亡面甲
    public static final float EXILE_HEALTH_THRESHOLD = 0.5f;
    public static final float EXILE_DAMAGE_PER_STACK = 1.0f;
    public static final float EXILE_STACK_THRESHOLD = 0.075f;
    public static final int EXILE_MAX_STACKS = 4;
    public static final float EXILE_MAX_DAMAGE_BONUS = 4.0f;
    public static final int EXILE_UPDATE_INTERVAL = 20;
    public static final UUID EXILE_MODIFIER_UUID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    
    // 遗世之环
    public static final int RELIC_ABSORPTION_DURATION = 60;
    public static final int RELIC_COOLDOWN = 600;
    public static final int RELIC_ANGERED_CHECK_INTERVAL = 20;
    
    // 回溯额饰
    public static final float RETRACER_HEALTH_SET = 2.0f;
    public static final int RETRACER_RESISTANCE_DURATION = 40;
    public static final int RETRACER_RESISTANCE_AMPLIFIER = 4;
    public static final int RETRACER_COOLDOWN = 18000;
}
```

---

## 交易参数（Phase 4）

> 预留，商人联动时填充

| 头盔 | 交易价格 | 刷新概率 |
|------|----------|----------|
| 哨兵的最后瞭望 | TBD | TBD |
| 沉默之誓约 | TBD | TBD |
| 流亡者的面甲 | TBD | TBD |
| 遗世之环 | TBD | TBD |
| 回溯者的额饰 | TBD | TBD |
