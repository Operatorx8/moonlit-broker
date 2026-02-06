# 过渡武器实现任务指令（Claude Code）

> 目标：为 Mysterious Merchant mod（Fabric 1.21.1）实现 3 把过渡武器：Acer、Velox、Fatalis。
> 定位：铁剑到 Katana 之间的过渡层，设计极简，只有 Acer 有特效逻辑。
> 约定：复用已有的 Katana 注册体系和附魔系数常量；Velox/Fatalis 无任何特效代码，纯数值武器。

---

## Task 0：常量定义

在已有常量体系中新增 `TransitionalWeaponConstants`（或直接加到武器常量文件里，看项目现状）：

```
// --- Acer ---
ACER_BASE_DAMAGE = 6
ACER_ATTACK_SPEED = 1.8
ACER_DURABILITY = <IRON_SWORD_DURABILITY * 1.15>  // 取 15%，你可以微调到 10~25% 区间内
ACER_CRIT_BONUS_MULT = 0.15

// --- Velox ---
VELOX_BASE_DAMAGE = 5
VELOX_ATTACK_SPEED = 2.2
VELOX_DURABILITY = <IRON_SWORD_DURABILITY * 1.05>  // "只略高"

// --- Fatalis ---
FATALIS_BASE_DAMAGE = 10
FATALIS_ATTACK_SPEED = 1.8
FATALIS_DURABILITY = <IRON_SWORD_DURABILITY * 1.15>  // 同 Acer 档位
```

`IRON_SWORD_DURABILITY` 在 1.21.1 中是 250。算出来的值取整（`int`）。

---

## Task 1：物品注册

在已有武器注册体系下，注册 3 个 SwordItem（或自定义子类）。

| 武器 | Damage | Speed | 耐久 | 稀有度 | 防火 | 铁砧修复 | Enchantability |
|---|---|---|---|---|---|---|---|
| acer | 6 | 1.8 | IRON*1.15 | UNCOMMON | ✅ | ❌ | KATANA_ENCHANTABILITY |
| velox | 5 | 2.2 | IRON*1.05 | COMMON | ✅ | ❌ | SWORD_ENCHANTABILITY（原版剑类） |
| fatalis | 10 | 1.8 | IRON*1.15 | UNCOMMON | ✅ | ❌ | KATANA_ENCHANTABILITY |

**注意**：
- Velox 和 Fatalis **不需要自定义子类**，如果你的注册体系允许纯配置注册就用纯配置，避免空壳类
- Acer 需要挂暴击逻辑，可能需要子类或通过事件监听走（见 Task 2）
- 三把都继承 `fireproof` 设置，铁砧不可修复

---

## Task 2：Acer 暴击加成逻辑

**这是三把武器中唯一需要写逻辑的地方。**

### 机制口径
Acer 的 `+15%` 加成**仅作用于暴击额外伤害部分**，不是总伤害。

MC 暴击公式：`finalDamage = baseDamage * 1.5`（暴击乘 1.5×）
Acer 修改后：`finalDamage = baseDamage * 1.5 + baseDamage * 0.5 * CRIT_BONUS_MULT`
即：额外的 `baseDamage * 0.5`（暴击多出来的部分）再乘 1.15。

简化：`finalDamage = baseDamage * (1.5 + 0.5 * 0.15) = baseDamage * 1.575`

### 实现方式

监听 `AttackEntityCallback` 或更精确的伤害事件（参考你 Katana 系统已有的事件钩子）。

**判定条件（全部满足才生效）**：
```
1. player.getMainHandStack().getItem() == ModItems.ACER   // MAIN_HAND_ONLY
2. entity instanceof LivingEntity                          // PLAYER_ONLY 对 LivingEntity
3. player.isAttacking() 且本次为暴击（player.hasVehicle() == false, fallDistance > 0, etc.）
4. damageSource 不属于排除列表：THORNS, MAGIC, FIRE, PROJECTILE
```

**暴击检测**：MC 原版暴击条件是 `fallDistance > 0 && !onGround && !climbing && !inWater && !blind && !riding && attackCooldown > 0.9`。可以：
- 方案 A：在 `ModifyDamageCallback`（如果你项目有）中检测 damage 是否已被 1.5× 放大过
- 方案 B：Hook 到 `PlayerEntity.attack()` 的暴击判定分支（Mixin `@Inject` 在暴击分支内）
- **推荐方案 B**，更精确，不依赖数值反推

### 排除伤害源
```java
private static final Set<String> EXCLUDED_SOURCES = Set.of(
    "thorns", "magic", "onFire", "inFire", "lava"
    // projectile 类：arrow, trident 等走 ProjectileDamageSource，也排除
);
```
如果 damageSource 名在排除集中 → 不加成，直接 return。

### Debug 日志
仅在全局 `DEBUG = true` 时输出，格式：
```
[TransWeapon] item=acer player=<uuid_short8> target=<uuid_short8> isCrit=true base=6.0 bonus=0.45 final=9.45 dim=overworld
```
字段说明：
- `uuid_short8`：UUID 前 8 位即可
- `bonus`：本次暴击加成的额外伤害量
- `final`：最终伤害

Velox 和 Fatalis **不输出任何日志**，没特效就别吵。

---

## Task 3：Velox — 纯注册

无特效、无事件监听、无 Mixin。只做 Task 1 中的物品注册。

确认 attack speed 2.2 正确生效（比默认剑 1.6 快很多，手感会明显不同）。

---

## Task 4：Fatalis — 纯注册

同 Velox，纯数值。Base Damage 10 是重击定位（比钻石剑的 7 高不少），attack speed 1.8 略慢于默认剑。

确认高 damage + 正常 speed 的手感符合"一刀定音"的设计意图。

---

## Task 5：文档生成

在 `docs/transitional_weapons/` 下按以下结构生成文档骨架：

```
docs/transitional_weapons/
├── INDEX.md              # 三把总览表（数值对比 + 定位一句话）
├── ACER_SPEC.md          # 定位/不变量/暴击加成口径/边界约束
├── ACER_PARAMS.md        # 纯数值表 + CRIT_BONUS_MULT 等逻辑参数
├── ACER_TESTPLAN.md      # 测试用例（见 Task 6）
├── ACER_CHANGELOG.md     # 空模板，首行写 "Initial implementation - <date>"
├── VELOX_SPEC.md         # 定位/无特效声明/数值
├── VELOX_PARAMS.md       # 纯数值
├── VELOX_TESTPLAN.md
├── VELOX_CHANGELOG.md
├── FATALIS_SPEC.md
├── FATALIS_PARAMS.md
├── FATALIS_TESTPLAN.md
└── FATALIS_CHANGELOG.md
```

SPEC 文件保持"短而硬"风格，参考 Katana 系列已有文档。每个 SPEC 必须包含：
- 一句话定位
- 不变量列表（防火、不可修复等）
- 效果描述（Velox/Fatalis 写 "None"）

---

## Task 6：测试清单

### Acer
| # | 场景 | 预期 |
|---|---|---|
| 1 | 主手持 Acer，跳起暴击普通僵尸 | 伤害 = 6 * 1.575 = 9.45（观察 debug 日志确认） |
| 2 | 主手持 Acer，平地普通攻击 | 伤害 = 6 * 1.5 = 9（无暴击加成） |
| 3 | 副手持 Acer，主手空手暴击 | 无加成（MAIN_HAND_ONLY） |
| 4 | 荆棘反伤触发时 | 不触发加成（排除 THORNS） |
| 5 | 防火测试：扔进岩浆 | 不消失 |
| 6 | 铁砧测试：尝试修复 | 不可修复 |

### Velox
| # | 场景 | 预期 |
|---|---|---|
| 1 | 攻击速度手感 | 明显快于铁剑（2.2 vs 1.6） |
| 2 | 伤害数值 | base 5，暴击 7.5 |
| 3 | 防火 + 铁砧 | 同上 |

### Fatalis
| # | 场景 | 预期 |
|---|---|---|
| 1 | 伤害数值 | base 10，暴击 15 |
| 2 | 攻击节奏 | 1.8 speed，略慢于默认剑，单刀重击感 |
| 3 | 防火 + 铁砧 | 同上 |

---

## 实施顺序

`Task 0 → Task 1 → Task 3 → Task 4 → Task 2 → Task 5 → Task 6`

先把两个纯数值武器（Velox、Fatalis）注册跑通，确认注册体系无问题，再做 Acer 的暴击逻辑。文档和测试最后补。
