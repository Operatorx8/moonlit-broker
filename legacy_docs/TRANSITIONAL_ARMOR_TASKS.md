# 过渡护甲文档+逻辑任务指令（Claude Code）

> 目标：为 Mysterious Merchant mod（Fabric 1.21.1）的 6 件过渡护甲建立完整文档体系，并为 2 件有特效的装备补齐实现指令和 debug 日志。
> 约定：最小改动，不重写现有架构。文档风格与 Katana / Boots 系列保持一致——短而硬。
> 共同硬约束（6 件全部）：fireproof=true, anvilRepair=false, knockbackResistance=0, enchantability 由 rarity 决定（BY_RARITY）。

---

## Task 0：INDEX.md 总览表

创建 `docs/armor/transitional/INDEX.md`，内容为一张总表 + 每件一句话定位：

| # | 显示名 | 英文名 | 部位 | 耐久 | 护甲 | 韧性 | 稀有度 | 特效 |
|---|---|---|---|---|---|---|---|---|
| 1 | 拾荒者的风镜 | Scavenger's Goggles | HEAD | 180 | 2 | 0 | UNCOMMON | 无 |
| 2 | 生铁护面盔 | Cast Iron Sallet | HEAD | 200 | 2 | 0.5 | UNCOMMON | 无 |
| 3 | 祝圣兜帽 | Sanctified Hood | HEAD | 165 | 1 | 0 | RARE | 魔法减伤 15% |
| 4 | 反应Bug装甲板 | Reactive Bug Plate | CHEST | 260 | 6 | 1 | RARE | 节肢近战减伤 -1.0 |
| 5 | 补丁皮大衣 | Patchwork Coat | CHEST | 240 | 5 | 0 | UNCOMMON | 无 |
| 6 | 仪式罩袍 | Ritual Robe | CHEST | 220 | 5 | 1 | UNCOMMON | 无 |

表下方加一行：`共同属性：fireproof=true, anvilRepair=false, knockbackResistance=0`

---

## Task 1：无特效装备文档（4 件批量）

对以下 4 件无特效装备，**每件生成 4 个文件**，内容极简：

- Scavenger's Goggles (`SCAVENGER_GOGGLES_*`)
- Cast Iron Sallet (`CAST_IRON_SALLET_*`)
- Patchwork Coat (`PATCHWORK_COAT_*`)
- Ritual Robe (`RITUAL_ROBE_*`)

### *_SPEC.md 模板
```
# <显示名>（<英文名>）
> "<English subtitle>"

## 定位
<一句话>

## 不变量
- 部位：HEAD / CHEST
- 防火：是
- 铁砧修复：不可
- 击退抗性：0
- 特效：无

## 边界
- 仅对应槽位穿戴生效
- 服务端/客户端：无逻辑，纯数值
```

### *_PARAMS.md 模板
```
# <英文名> 参数表
| 参数 | 值 |
|---|---|
| durability | <值> |
| defense | <值> |
| toughness | <值> |
| rarity | <值> |
| enchantability | BY_RARITY |
| fireproof | true |
| anvilRepair | false |

逻辑参数：无
```

### *_CHANGELOG.md 模板
```
# <英文名> Changelog
- [<今天日期>] 初始化：文档创建，纯数值装备，无特效逻辑
```

### *_TESTPLAN.md 模板
```
# <英文名> 测试计划
| # | 场景 | 预期 |
|---|---|---|
| 1 | 穿戴后查看护甲值 | 护甲 +<defense>，韧性 +<toughness> |
| 2 | 防火：扔进岩浆 | 物品不消失 |
| 3 | 铁砧修复 | 不可修复 |
| 4 | 稀有度颜色 | 物品名颜色 = <rarity> 对应色 |
| 5 | 耐久消耗 | 受击后耐久正常递减 |
| 6 | 附魔台 | 可附魔，概率符合 rarity 档位 |
```

**不要为这 4 件添加任何日志代码。**

---

## Task 2：Sanctified Hood 文档 + 逻辑指令

### SANCTIFIED_HOOD_SPEC.md
```
# 祝圣兜帽（Sanctified Hood）
> "Consecrated cloth that dulls hostile sorcery."

## 定位
轻甲头盔，牺牲物理防御换取魔法减伤。

## 不变量
- 部位：HEAD（HEAD_ONLY）
- 防火：是
- 铁砧修复：不可
- 击退抗性：0
- 韧性：0
- 特效：魔法伤害减免 15%

## 效果口径
- 穿戴于头部槽位时，受到的**魔法类伤害** × 0.85
- 作用范围：SERVER_ONLY
- damageType 判定方式：**白名单**（见 PARAMS）

## 边界
- 仅 HEAD 槽位生效
- 仅服务端计算
- 不影响非白名单内的伤害类型
- 不叠加（穿一件就是 15%，不存在多件叠加场景——头部只有一个槽）
```

### SANCTIFIED_HOOD_PARAMS.md
```
# Sanctified Hood 参数表

## 基础属性
| 参数 | 值 |
|---|---|
| durability | 165 |
| defense | 1 |
| toughness | 0 |
| rarity | RARE |
| enchantability | BY_RARITY |
| fireproof | true |
| anvilRepair | false |

## 逻辑参数
| 参数 | 值 | 说明 |
|---|---|---|
| MAGIC_REDUCTION_MULT | 0.85 | 最终魔法伤害 = raw × 0.85 |
| APPLY_SLOT | HEAD | 仅头部槽位 |
| SERVER_ONLY | true | 仅服务端 |

## damageType 白名单
以下 damageType ID 被视为"魔法伤害"并触发减免：

| damageType ID (1.21.1 Yarn) | 来源示例 |
|---|---|
| `magic` | 瞬间伤害药水 |
| `indirect_magic` | 女巫投掷药水、滞留药水 |
| `sonic_boom` | Warden 音波（⚠️ 需确认 1.21.1 是否归为 magic tag） |
| `thorns` | ❌ 不在白名单——荆棘是物理反伤 |

⚠️ 需要在 1.21.1 Yarn mapping 中核对：
- Guardian laser 的 damageType ID（可能是 `magic` 或独立 ID `thorns`/`mob_attack_no_aggro`——需查 `DamageTypes` 注册表）
- Evoker fangs 的 damageType ID（可能是 `magic` 或 `mob_attack`）
- 替代方案：如果精确 ID 不确定，可改用 `DamageTypeTags.WITCH_RESISTANT_TO`（如存在）或自建 tag `c:magic_damage`，把需要的 type 注册进去

建议实现路径：
1. 优先用 `damageSource.isIn(DamageTypeTags.xxx)` 做 tag 判定
2. 若无现成 tag，退回到 `damageSource.getTypeRegistryEntry().matchesId(Identifier.of("minecraft", "magic"))` 逐 ID 匹配
3. 把白名单 ID 写成 Set<Identifier> 常量，方便后续扩展
```

### SANCTIFIED_HOOD_CHANGELOG.md
```
- [<今天日期>] 初始化：SPEC/PARAMS/TESTPLAN 创建；魔法减伤 15% 设计确认
```

### SANCTIFIED_HOOD_TESTPLAN.md
```
| # | 场景 | 预期 |
|---|---|---|
| 1 | 穿戴，被女巫投毒药水命中 | 伤害 = raw × 0.85，日志输出 |
| 2 | 穿戴，被守卫者激光命中 | 伤害 = raw × 0.85（前提：guardian laser 在白名单内） |
| 3 | 穿戴，被唤魔者尖牙命中 | 伤害 = raw × 0.85（前提：evoker fangs 在白名单内） |
| 4 | 穿戴，被僵尸近战攻击 | 无减免（物理伤害不在白名单） |
| 5 | 穿戴，被骷髅射箭命中 | 无减免（projectile 不在白名单） |
| 6 | 穿戴，被荆棘反伤 | 无减免（thorns 不在白名单） |
| 7 | 不穿戴，被女巫投毒 | 无减免（未穿戴） |
| 8 | 放入副手/背包 | 无减免（HEAD_ONLY） |
| 9 | 防火 + 铁砧 | 同通用测试 |
| 10 | DEBUG=true 时日志格式 | 一行输出，字段完整 |
```

### Debug 日志格式
```
[TransArmor] item=sanctified_hood player=<uuid8> dmgType=<damageTypeId> raw=<float> final=<float> dim=<dimension>
```
- 仅在效果实际触发时（即 damageType 命中白名单且穿戴中）打印
- 受全局 DEBUG 开关控制

---

## Task 3：Reactive Bug Plate 文档 + 逻辑指令

### REACTIVE_BUG_PLATE_SPEC.md
```
# 反应Bug装甲板（Reactive Bug Plate）
> "A plate that 'patches out' arthropod bites on contact."

## 定位
重甲胸甲，针对节肢类敌人的专项物理减伤。

## 不变量
- 部位：CHEST（CHEST_ONLY）
- 防火：是
- 铁砧修复：不可
- 击退抗性：0
- 特效：节肢类近战伤害 flat -1.0

## 效果口径
- 穿戴于胸部槽位时，受到**节肢类实体的近战物理伤害**时，最终伤害 -1.0（clamp >= 0）
- "节肢类"定义：attacker 的 EntityGroup == ARTHROPOD（或 1.21.1 等价判定）
- "近战物理"定义：排除 projectile、magic、thorns、fire、explosion 等非直接近战来源
- 作用范围：SERVER_ONLY

## 边界
- 仅 CHEST 槽位生效
- 仅服务端计算
- 减伤为 flat 值（不是百分比），clamp 后最低 0
- 不影响非节肢来源、非近战来源
```

### REACTIVE_BUG_PLATE_PARAMS.md
```
# Reactive Bug Plate 参数表

## 基础属性
| 参数 | 值 |
|---|---|
| durability | 260 |
| defense | 6 |
| toughness | 1 |
| rarity | RARE |
| enchantability | BY_RARITY |
| fireproof | true |
| anvilRepair | false |

## 逻辑参数
| 参数 | 值 | 说明 |
|---|---|---|
| FLAT_REDUCTION | 1.0 | 固定减伤值 |
| CLAMP_MIN | 0.0 | 最终伤害下限 |
| APPLY_SLOT | CHEST | 仅胸部槽位 |
| SERVER_ONLY | true | 仅服务端 |
| MELEE_ONLY | true | 排除非近战伤害源 |

## 攻击者过滤
节肢类实体判定（需在 1.21.1 Yarn 中确认）：

方案 A（推荐）：`attacker.getGroup() == EntityGroup.ARTHROPOD`
- Spider, CaveSpider, Endermite, Silverfish, Bee（⚠️ Bee 是否算节肢——设计决策）

方案 B（备选）：自建实体 Set
```java
private static final Set<EntityType<?>> ARTHROPOD_TYPES = Set.of(
    EntityType.SPIDER,
    EntityType.CAVE_SPIDER,
    EntityType.ENDERMITE,
    EntityType.SILVERFISH
    // Bee 根据设计决策决定是否加入
);
```

⚠️ 需核对：1.21.1 中 `EntityGroup` 是否仍存在，Yarn mapping 名可能是 `EntityGroup.ARTHROPOD` 或已迁移到 tag 系统。若已迁移，使用 `EntityTypeTags.ARTHROPOD` 或等价 tag。

## 伤害源过滤（MELEE_ONLY）
**允许**：`mob_attack`（标准近战）、`mob_attack_no_aggro`（被动生物反击）
**排除**：所有其他类型（projectile, magic, thorns, fire, explosion, etc.）

判定方式：
1. 优先：`damageSource.isIn(DamageTypeTags.IS_PROJECTILE)` → 排除
2. 优先：`damageSource.getAttacker() instanceof LivingEntity` + attacker 节肢判定
3. 退回：检查 `damageSource.getName()` 是否为 `"mob_attack"` 或 `"mob_attack_no_aggro"`
```

### REACTIVE_BUG_PLATE_CHANGELOG.md
```
- [<今天日期>] 初始化：SPEC/PARAMS/TESTPLAN 创建；节肢近战 flat -1.0 设计确认
```

### REACTIVE_BUG_PLATE_TESTPLAN.md
```
| # | 场景 | 预期 |
|---|---|---|
| 1 | 穿戴，被蜘蛛近战攻击（raw=2） | final = max(2-1, 0) = 1，日志输出 |
| 2 | 穿戴，被洞穴蜘蛛近战攻击 | final = raw - 1.0，日志输出（注意中毒是独立伤害源，不触发减伤） |
| 3 | 穿戴，被末影螨攻击 | 减伤触发 |
| 4 | 穿戴，被僵尸攻击 | 无减伤（非节肢） |
| 5 | 穿戴，被骷髅射箭 | 无减伤（projectile，非近战） |
| 6 | 穿戴，raw=0.5 的节肢攻击 | final = max(0.5-1.0, 0) = 0（clamp 生效） |
| 7 | 不穿戴，被蜘蛛攻击 | 无减伤 |
| 8 | 放入副手/背包 | 无减伤（CHEST_ONLY） |
| 9 | 防火 + 铁砧 | 同通用测试 |
| 10 | DEBUG=true 时日志格式 | 一行输出，字段完整 |
```

### Debug 日志格式
```
[TransArmor] item=reactive_bug_plate player=<uuid8> attacker=<entityTypeId> raw=<float> reduced=1.0 final=<float> dim=<dimension>
```
- 仅在减伤实际生效时打印（attacker 为节肢 + 近战 + 穿戴中）
- 受全局 DEBUG 开关控制

---

## Task 4：常量归集

在已有常量体系中新增（或追加到已有的 armor 常量文件）：

```java
// === Transitional Armor: Sanctified Hood ===
public static final float SANCTIFIED_MAGIC_REDUCTION_MULT = 0.85f;

// === Transitional Armor: Reactive Bug Plate ===
public static final float REACTIVE_BUG_FLAT_REDUCTION = 1.0f;
public static final float REACTIVE_BUG_CLAMP_MIN = 0.0f;
```

不要为无特效的 4 件装备创建任何逻辑常量。

---

## Task 5：文件树汇总

最终 `docs/armor/transitional/` 目录结构：

```
docs/armor/transitional/
├── INDEX.md
├── SCAVENGER_GOGGLES_SPEC.md
├── SCAVENGER_GOGGLES_PARAMS.md
├── SCAVENGER_GOGGLES_CHANGELOG.md
├── SCAVENGER_GOGGLES_TESTPLAN.md
├── CAST_IRON_SALLET_SPEC.md
├── CAST_IRON_SALLET_PARAMS.md
├── CAST_IRON_SALLET_CHANGELOG.md
├── CAST_IRON_SALLET_TESTPLAN.md
├── SANCTIFIED_HOOD_SPEC.md
├── SANCTIFIED_HOOD_PARAMS.md
├── SANCTIFIED_HOOD_CHANGELOG.md
├── SANCTIFIED_HOOD_TESTPLAN.md
├── REACTIVE_BUG_PLATE_SPEC.md
├── REACTIVE_BUG_PLATE_PARAMS.md
├── REACTIVE_BUG_PLATE_CHANGELOG.md
├── REACTIVE_BUG_PLATE_TESTPLAN.md
├── PATCHWORK_COAT_SPEC.md
├── PATCHWORK_COAT_PARAMS.md
├── PATCHWORK_COAT_CHANGELOG.md
├── PATCHWORK_COAT_TESTPLAN.md
├── RITUAL_ROBE_SPEC.md
├── RITUAL_ROBE_PARAMS.md
├── RITUAL_ROBE_CHANGELOG.md
└── RITUAL_ROBE_TESTPLAN.md
```

共 25 个文件。

---

## 实施顺序

```
Task 0 (INDEX)
→ Task 1 (4件无特效文档，批量生成)
→ Task 4 (常量)
→ Task 2 (Sanctified Hood 全套)
→ Task 3 (Reactive Bug Plate 全套)
→ Task 5 (验证文件树完整)
```

## ⚠️ 需人工确认 / 核对的点

1. **Guardian laser damageType ID**：1.21.1 Yarn 中可能是 `magic`、`mob_attack`、或独立 ID——需查 `net.minecraft.entity.damage.DamageTypes`
2. **Evoker fangs damageType ID**：同上
3. **`EntityGroup.ARTHROPOD`** 在 1.21.1 Yarn 中的存在性——可能已迁移到 tag 系统
4. **Bee 是否算节肢**（`EntityGroup.ARTHROPOD` 包含 Bee）——设计决策，影响 Reactive Bug Plate 的覆盖范围
5. **Sonic boom** 是否应被 Sanctified Hood 减免——Warden 的音波绕甲，减伤是否应该生效是设计层面的选择
