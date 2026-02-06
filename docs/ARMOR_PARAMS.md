# ARMOR_PARAMS.md - 盔甲数值参数表

> 纯数值表，不含解释。设计理由见 ARMOR_SPEC.md 和 DECISIONS.md

---

## 基础属性

### 头盔通用属性（5件共享）

| 属性 | 值 | 说明 |
|------|-----|------|
| `durabilityMultiplier` | 25 | 基础耐久乘数（头盔实际耐久 = 25 × 11 = 275） |
| `protection` | 3 | 护甲值（头盔槽位） |
| `toughness` | 2.0 | 韧性 |
| `knockbackResistance` | 0.1 | 击退抗性 10% |
| `enchantability` | 15 | 附魔等级 |
| `fireproof` | true | 防火（物品不会被熔岩/火焰销毁） |
| `repairable` | false | 不可铁砧修复 |

### 胸甲通用属性（5件共享）

| 属性 | 值 | 说明 |
|------|-----|------|
| `durabilityMultiplier` | 25 | 基础耐久乘数（胸甲实际耐久 = 25 × 16 = 400） |
| `protection` | 8 | 护甲值（胸甲槽位） |
| `toughness` | 2.0 | 韧性 |
| `knockbackResistance` | 0.0 | 默认无击退抗性（防风衣例外） |
| `enchantability` | 15 | 附魔等级 |
| `fireproof` | true | 防火 |
| `repairable` | false | 不可铁砧修复 |
| `model` | `minecraft:iron` | 暂用原版铁胸甲模型 |

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

## 胸甲效果参数

### 1. 旧市护甲 (old_market_chestplate)

| 参数 | 值 | 单位 |
|------|-----|------|
| `rarity` | RARE | - |
| **交易经验** | | |
| `tradeXpChance` | 0.5 | (50%) |
| `tradeXpMultiplier` | 1.5 | (×1.5) |
| `tradeXpCooldown` | 1200 | ticks (60s) |
| `tradeFirstOnly` | true | (仅首次该交易槽) |
| **击杀经验** | | |
| `killXpChance` | 0.25 | (25%) |
| `killXpMultiplier` | 2.0 | (×2) |
| `killXpCooldown` | 600 | ticks (30s) |

### 2. 流血契约之胸铠 (blood_pact_chestplate)

| 参数 | 值 | 单位 |
|------|-----|------|
| `rarity` | RARE | - |
| `chargeChance` | 0.5 | (50%) |
| `extraDamageRatio` | 0.5 | (本次伤害的 50%) |
| `extraDamageCap` | 4.0 | HP (单次上限 2♥) |
| `poolCap` | 8.0 | HP (池上限 4♥) |
| `poolWindow` | 200 | ticks (10s) |
| `validSourceTypes` | `ENTITY_ATTACK, PROJECTILE` | (直接伤害) |
| `excludedSources` | `EXPLOSION, FIRE, MAGIC, OUT_OF_WORLD, DOT` | - |

### 3. 鬼神之铠 (ghost_god_chestplate)

| 参数 | 值 | 单位 |
|------|-----|------|
| `rarity` | EPIC | - |
| **伤害减免** | | |
| `damageReductionChance` | 0.30 | (30%) |
| `damageReductionChanceBoss` | 0.15 | (Boss 15%) |
| `damageReductionAmount` | 0.15 | (伤害 -15%) |
| **Debuff 免疫** | | |
| `debuffImmuneChance` | 0.50 | (50%) |
| `debuffImmuneChanceBoss` | 0.25 | (Boss 25%) |
| `immuneDebuffs` | `WITHER, HUNGER, SLOWNESS` | - |
| `undeadOnly` | true | (仅亡灵来源) |

### 4. 商人的防风衣 (windbreaker_chestplate)

| 参数 | 值 | 单位 |
|------|-----|------|
| `rarity` | UNCOMMON | - |
| **击退抗性** | | |
| `knockbackResistance` | 0.3 | (30% 抗性) |
| `knockbackResistanceUUID` | `b2c3d4e5-f6a7-8901-bcde-f23456789012` | - |
| **低血速度** | | |
| `healthTriggerThreshold` | 0.5 | (血量 < 50% 触发) |
| `healthRearmThreshold` | 0.6 | (血量 ≥ 60% 解锁) |
| `speedEffectId` | `minecraft:speed` | - |
| `speedDuration` | 100 | ticks (5s) |
| `speedAmplifier` | 0 | (等级 I) |
| `speedCooldown` | 1800 | ticks (90s) |
| `checkInterval` | 20 | ticks (1s) |

### 5. 虚空之噬 (void_devourer_chestplate)

| 参数 | 值 | 单位 |
|------|-----|------|
| `rarity` | RARE | - |
| `trueDamageRatio` | 0.04 | (4%) |
| `trueDamageRatioBoss` | 0.02 | (Boss 2%) |
| `cooldown` | 100 | ticks (5s) |
| `damageSourceType` | `MAGIC` | (绕过护甲) |
| `targetMustBeLiving` | true | - |

---

## 护腿效果参数

### 1. 走私者之胫 (smuggler_shin_leggings)

| 参数 | 值 | 单位 |
|------|-----|------|
| `rarity` | UNCOMMON | - |
| `lootBonusChance` | 0.20 | (20%) |
| `lootBonusChanceBoss` | 0.10 | (Boss 10%) |
| `doubleLootChance` | 0.10 | (10%) |
| `doubleLootChanceBoss` | 0.05 | (Boss 5%) |
| `cooldown` | 800 | ticks (40s) |
| `excludePvp` | true | - |

### 2. 走私者的暗袋 (smuggler_pouch_leggings)

| 参数 | 值 | 单位 |
|------|-----|------|
| `rarity` | UNCOMMON | - |
| `magnetRadius` | 6.0 | blocks |
| `magnetDuration` | 100 | ticks (5s) |
| `magnetScanInterval` | 20 | ticks (1s) |
| `magnetPullSpeed` | 0.3 | velocity |
| `magnetPullSpeedY` | 0.1 | velocity (额外向上) |
| `cooldown` | 700 | ticks (35s) |
| `affectExpOrbs` | false | - |

### 3. 擦身护胫 (graze_guard_leggings)

| 参数 | 值 | 单位 |
|------|-----|------|
| `rarity` | EPIC | - |
| `triggerChance` | 0.18 | (18%) |
| `damageReduction` | 0.60 | (减伤 60%, 即 ×0.40) |
| `cooldown` | 240 | ticks (12s) |
| `requireLivingAttacker` | true | - |

### 4. 潜行之胫 (stealth_shin_leggings)

| 参数 | 值 | 单位 |
|------|-----|------|
| `rarity` | RARE | - |
| `chargeInterval` | 900 | ticks (45s) |
| `maxCharges` | 2 | - |
| `fallReduction` | 0.80 | (减伤 80%, 即 ×0.20) |
| `minFallDamageToConsume` | 3.0 | HP (1.5♥) |
| `notifyOnCharge` | true | (ActionBar + 音效) |
| `checkInterval` | 20 | ticks (1s) |

### 5. 清账步态 (clear_ledger_leggings)

| 参数 | 值 | 单位 |
|------|-----|------|
| `rarity` | RARE | - |
| `speedEffectId` | `minecraft:speed` | - |
| `speedAmplifier` | 0 | (等级 I) |
| `initialDuration` | 60 | ticks (3s) |
| `extendDuration` | 20 | ticks (+1s) |
| `maxDuration` | 120 | ticks (6s) |
| `cooldown` | 320 | ticks (16s) |
| `excludePvp` | true | - |

---

## 冷却时间汇总

### 头盔

| 头盔 | 冷却 (ticks) | 冷却 (秒) | 冷却 (分钟) |
|------|-------------|-----------|-------------|
| 哨兵的最后瞭望 | 800 | 40 | 0.67 |
| 沉默之誓约 | 600 | 30 | 0.5 |
| 流亡者的面甲 | N/A | N/A | N/A |
| 遗世之环 | 600 | 30 | 0.5 |
| 回溯者的额饰 | 18000 | 900 | 15 |

### 胸甲

| 胸甲 | 效果 | 冷却 (ticks) | 冷却 (秒) |
|------|------|-------------|-----------|
| 旧市护甲 | 交易经验 | 1200 | 60 |
| 旧市护甲 | 击杀经验 | 600 | 30 |
| 流血契约 | 储能窗口 | 200 | 10 |
| 鬼神之铠 | N/A | N/A | N/A |
| 商人防风衣 | 低血速度 | 1800 | 90 |
| 虚空之噬 | 真伤 | 100 | 5 |

### 护腿

| 护腿 | 效果 | 冷却 (ticks) | 冷却 (秒) |
|------|------|-------------|-----------|
| 走私者之胫 | 掉落增益 | 800 | 40 |
| 走私者暗袋 | 磁吸 | 700 | 35 |
| 擦身护胫 | 减伤 | 240 | 12 |
| 潜行之胫 | 充能间隔 | 900 | 45 |
| 清账步态 | 速度 | 320 | 16 |

---

## 扫描/更新间隔汇总

### 头盔

| 头盔 | 间隔 (ticks) | 间隔 (秒) |
|------|-------------|-----------|
| 哨兵的最后瞭望 | 20 | 1 |
| 流亡者的面甲 | 20 | 1 |
| 遗世之环（愤怒检测） | 20 | 1 |

### 胸甲

| 胸甲 | 间隔 (ticks) | 间隔 (秒) |
|------|-------------|-----------|
| 商人的防风衣（低血检测） | 20 | 1 |

### 护腿

| 护腿 | 间隔 (ticks) | 间隔 (秒) |
|------|-------------|-----------|
| 走私者暗袋（磁吸扫描） | 20 | 1 |
| 潜行之胫（充能检测） | 20 | 1 |

---

## 固定 UUID 列表

| 用途 | UUID |
|------|------|
| 流亡者面甲攻击力加成 | `a1b2c3d4-e5f6-7890-abcd-ef1234567890` |
| 商人防风衣击退抗性 | `b2c3d4e5-f6a7-8901-bcde-f23456789012` |

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
    
    // 胸甲护甲值
    public static final int CHESTPLATE_PROTECTION = 8;
    
    // 韧性
    public static final float ARMOR_TOUGHNESS = 2.0f;
    
    // 默认击退抗性（头盔）
    public static final float HELMET_KNOCKBACK_RESISTANCE = 0.1f;
    
    // 附魔等级
    public static final int ENCHANTABILITY = 15;
}

public class HelmetEffectConstants {
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

public class ChestplateEffectConstants {
    // 旧市护甲
    public static final float OLD_MARKET_TRADE_XP_CHANCE = 0.5f;
    public static final float OLD_MARKET_TRADE_XP_MULTIPLIER = 1.5f;
    public static final int OLD_MARKET_TRADE_XP_COOLDOWN = 1200;
    public static final float OLD_MARKET_KILL_XP_CHANCE = 0.25f;
    public static final float OLD_MARKET_KILL_XP_MULTIPLIER = 2.0f;
    public static final int OLD_MARKET_KILL_XP_COOLDOWN = 600;
    
    // 流血契约
    public static final float BLOOD_PACT_CHARGE_CHANCE = 0.5f;
    public static final float BLOOD_PACT_EXTRA_DAMAGE_RATIO = 0.5f;
    public static final float BLOOD_PACT_EXTRA_DAMAGE_CAP = 4.0f;   // 2♥
    public static final float BLOOD_PACT_POOL_CAP = 8.0f;           // 4♥
    public static final int BLOOD_PACT_POOL_WINDOW = 200;           // 10s
    
    // 鬼神之铠
    public static final float GHOST_GOD_DAMAGE_REDUCTION_CHANCE = 0.30f;
    public static final float GHOST_GOD_DAMAGE_REDUCTION_CHANCE_BOSS = 0.15f;
    public static final float GHOST_GOD_DAMAGE_REDUCTION_AMOUNT = 0.15f;
    public static final float GHOST_GOD_DEBUFF_IMMUNE_CHANCE = 0.50f;
    public static final float GHOST_GOD_DEBUFF_IMMUNE_CHANCE_BOSS = 0.25f;
    
    // 商人防风衣
    public static final float WINDBREAKER_KNOCKBACK_RESISTANCE = 0.3f;
    public static final UUID WINDBREAKER_KB_UUID = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f23456789012");
    public static final float WINDBREAKER_HEALTH_TRIGGER = 0.5f;
    public static final float WINDBREAKER_HEALTH_REARM = 0.6f;
    public static final int WINDBREAKER_SPEED_DURATION = 100;
    public static final int WINDBREAKER_SPEED_COOLDOWN = 1800;
    public static final int WINDBREAKER_CHECK_INTERVAL = 20;
    
    // 虚空之噬
    public static final float VOID_DEVOURER_TRUE_DAMAGE_RATIO = 0.04f;
    public static final float VOID_DEVOURER_TRUE_DAMAGE_RATIO_BOSS = 0.02f;
    public static final int VOID_DEVOURER_COOLDOWN = 100;
}

public class LeggingsEffectConstants {
    // 走私者之胫
    public static final float SMUGGLER_SHIN_LOOT_BONUS_CHANCE = 0.20f;
    public static final float SMUGGLER_SHIN_LOOT_BONUS_CHANCE_BOSS = 0.10f;
    public static final float SMUGGLER_SHIN_DOUBLE_LOOT_CHANCE = 0.10f;
    public static final float SMUGGLER_SHIN_DOUBLE_LOOT_CHANCE_BOSS = 0.05f;
    public static final int SMUGGLER_SHIN_COOLDOWN = 800;
    
    // 走私者暗袋
    public static final float SMUGGLER_POUCH_RADIUS = 6.0f;
    public static final int SMUGGLER_POUCH_DURATION = 100;
    public static final int SMUGGLER_POUCH_SCAN_INTERVAL = 20;
    public static final float SMUGGLER_POUCH_PULL_SPEED = 0.3f;
    public static final float SMUGGLER_POUCH_PULL_SPEED_Y = 0.1f;
    public static final int SMUGGLER_POUCH_COOLDOWN = 700;
    
    // 擦身护胫
    public static final float GRAZE_GUARD_TRIGGER_CHANCE = 0.18f;
    public static final float GRAZE_GUARD_REDUCTION = 0.60f;
    public static final int GRAZE_GUARD_COOLDOWN = 240;
    
    // 潜行之胫
    public static final int STEALTH_SHIN_CHARGE_INTERVAL = 900;
    public static final int STEALTH_SHIN_MAX_CHARGES = 2;
    public static final float STEALTH_SHIN_FALL_REDUCTION = 0.80f;
    public static final float STEALTH_SHIN_MIN_FALL_DAMAGE = 3.0f;
    public static final int STEALTH_SHIN_CHECK_INTERVAL = 20;
    
    // 清账步态
    public static final int CLEAR_LEDGER_INITIAL_DURATION = 60;
    public static final int CLEAR_LEDGER_EXTEND_DURATION = 20;
    public static final int CLEAR_LEDGER_MAX_DURATION = 120;
    public static final int CLEAR_LEDGER_COOLDOWN = 320;
}
```

---

## 交易参数（Phase 4）

> 预留，商人联动时填充

### 头盔

| 头盔 | 交易价格 | 刷新概率 |
|------|----------|----------|
| 哨兵的最后瞭望 | TBD | TBD |
| 沉默之誓约 | TBD | TBD |
| 流亡者的面甲 | TBD | TBD |
| 遗世之环 | TBD | TBD |
| 回溯者的额饰 | TBD | TBD |

### 胸甲

| 胸甲 | 交易价格 | 刷新概率 |
|------|----------|----------|
| 旧市护甲 | TBD | TBD |
| 流血契约之胸铠 | TBD | TBD |
| 鬼神之铠 | TBD | TBD |
| 商人的防风衣 | TBD | TBD |
| 虚空之噬 | TBD | TBD |
