# ARMOR_PARAMS.md - 盔甲数值参数表

> 纯数值表，不含解释。设计理由见 ARMOR_SPEC.md 和 DECISIONS.md

---

## 基础属性

### 头盔通用属性（5 件头盔共享）

| 属性 | 值 | 说明 |
|------|-----|------|
| `durabilityMultiplier` | 25 | 基础耐久乘数（头盔实际耐久 = 25 × 11 = 275） |
| `protection` | 3 | 护甲值（头盔槽位） |
| `toughness` | 2.0 | 韧性 |
| `knockbackResistance` | 0.1 | 击退抗性 10% |
| `enchantability` | 按稀有度分档 | 见下方映射表（继承原版材质） |
| `fireproof` | true | 防火（物品不会被熔岩/火焰销毁） |
| `repairable` | false | 不可铁砧修复 |
| `rarity` | 按头盔定义 | 不同头盔使用不同稀有度 |

### 胸甲通用属性（5 件胸甲共享）

| 属性 | 值 | 说明 |
|------|-----|------|
| `durabilityMultiplier` | 25 | 基础耐久乘数（胸甲实际耐久 = 25 × 16 = 400） |
| `protection` | 8 | 护甲值（胸甲槽位） |
| `toughness` | 2.0 | 韧性 |
| `knockbackResistance` | 0.0 | 默认无击退抗性（防风衣例外：+0.3） |
| `enchantability` | 按稀有度分档 | 见下方映射表（继承原版材质） |
| `fireproof` | true | 防火（物品不会被熔岩/火焰销毁） |
| `repairable` | false | 不可铁砧修复 |
| `rarity` | 按胸甲定义 | 不同胸甲使用不同稀有度 |

### 材质属性

| 属性 | 值 |
|------|-----|
| `equipSound` | `minecraft:item.armor.equip_chain` |
| `repairIngredient` | `null`（不可修复） |
| `textureBase` | `mysterious_merchant:textures/armor/merchant` |

### Enchantability 映射（全局约定）

> **Armor items (helmet/chest/legs/boots) all follow rarity-tier enchantability mapping**

| 稀有度 | 继承原版材质 | enchantability 值 |
|------|------------|---------------------|
| `UNCOMMON` | `IRON` | 9 |
| `RARE` | `CHAIN` | 12 |
| `EPIC` | `NETHERITE` | 15 |

### 5 件头盔稀有度分配

| 头盔 | rarity | enchantability 档位 |
|------|--------|----------------------|
| `sentinel_helmet` | `UNCOMMON` | `IRON` (9) |
| `silent_oath_helmet` | `RARE` | `CHAIN` (12) |
| `exile_mask_helmet` | `RARE` | `CHAIN` (12) |
| `relic_circlet_helmet` | `UNCOMMON` | `IRON` (9) |
| `retracer_ornament_helmet` | `EPIC` | `NETHERITE` (15) |

### 5 件胸甲稀有度分配

| 胸甲 | rarity | enchantability 档位 |
|------|--------|----------------------|
| `old_market_chestplate` | `RARE` | `CHAIN` (12) |
| `blood_pact_chestplate` | `RARE` | `CHAIN` (12) |
| `ghost_god_chestplate` | `EPIC` | `NETHERITE` (15) |
| `windbreaker_chestplate` | `UNCOMMON` | `IRON` (9) |
| `void_devourer_chestplate` | `RARE` | `CHAIN` (12) |

---

## 效果参数

### 1. 哨兵的最后瞭望 (sentinel_helmet)

| 参数 | 值 | 单位 |
|------|-----|------|
| `effectId` | `sentinel_echo_pulse` | - |
| `scanRange` | 16.0 | blocks |
| `lightThreshold` | 7 | (光照 ≤ 7 触发) |
| `scanInterval` | 20 | ticks (1s) |
| `hostileRequired` | true | (16 格内需有敌对生物) |
| `buffEffectId` | `minecraft:speed` | - |
| `buffDuration` | 30 | ticks (1.5s) |
| `buffAmplifier` | 0 | (等级 I) |
| `soundEvent` | `minecraft:block.bell.use` | - |
| `soundVolume` | 0.7 | - |
| `soundPitch` | 1.2 | - |
| `cooldown` | 800 | ticks (40s) |
| `triggerChance` | 1.0 | (100%) |
| `applyToHostiles` | false | (不施加 Glowing/其他状态) |
| `spawnParticleRing` | false | - |

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
| `excludedSources` | `EXPLOSION, FIRE, MAGIC, OUT_OF_WORLD` | - |

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
    
    // 韧性
    public static final float ARMOR_TOUGHNESS = 2.0f;
    
    // 击退抗性
    public static final float KNOCKBACK_RESISTANCE = 0.1f;
    
    // 附魔等级
    public static final int ENCHANTABILITY = 15;
}

public class EffectConstants {
    // 哨兵瞭望
    public static final float SENTINEL_SCAN_RANGE = 16.0f;
    public static final int SENTINEL_LIGHT_THRESHOLD = 7;
    public static final int SENTINEL_SCAN_INTERVAL = 20;
    public static final int SENTINEL_SPEED_DURATION = 30;
    public static final int SENTINEL_SPEED_AMPLIFIER = 0;
    public static final String SENTINEL_SOUND_ID = "minecraft:block.bell.use";
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
