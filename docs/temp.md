---
# TEMP — Trade Pages v1（目标：立刻不空 + 不刷爆钻石/下界合金）

## CRITICAL MEMORY DISCIPLINE（关键内存纪律）

**Allowed reads (只允许读这些文件)：**

1. `src/main/java/dev/xqanzd/moonlitbroker/entity/MysteriousMerchantEntity.java`（只看 offers build 相关方法）
2. `src/main/java/dev/xqanzd/moonlitbroker/trade/TradeConfig.java`
3. `src/main/java/dev/xqanzd/moonlitbroker/trade/TradePage.java`
4. `src/main/java/dev/xqanzd/moonlitbroker/registry/ModItems.java`（只为确认物品 ID / 引用）
5. `src/main/java/dev/xqanzd/moonlitbroker/armor/item/ArmorItems.java` & `.../armor/transitional/TransitionalArmorItems.java`（只为确认装备 ID / 引用）

**NO AUTO-SCANNING：**禁止扫描整个 `src/`，禁止“先全局搜一遍再说”。
只允许在上述文件里用 `rg` 精准定位方法名/常量名。

**NO ZOMBIE FILES：**禁止读取仓库根目录下任何历史 `.md` / 临时笔记 / 旧方案文件。
只以本文档为准，用完即弃。

---

## Diff Only（只输出差异）

* 修改时只输出 **unified diff**（只包含改动的行块）。
* 禁止整文件重打 500 行。
* 每个文件最多输出必要的几个 hunks。

---

## No Yapping（禁止废话）

最终输出必须只有三段：

1. **Plan**（<= 10 行）
2. **Diffs**（只给 diff）
3. **Verification checklist**（<= 10 行）

---

## GOAL（硬目标）

* **总条目数 > 35**（指同一个商人的全部 offers：Page1 + Page2 + Page3，加起来 > 35；列表允许滚动）
* **钻石/下界合金不会被绿宝石刷爆**：

  * 发布默认：**不存在** `Emerald -> Diamond` / `Emerald -> Netherite`
  * 允许 debug：仅在 `TradeConfig.MASTER_DEBUG == true` 时出现这类交易
* **允许用铁换非稀缺物资**：铁只用于“便利/杂货”，**绝不**参与稀缺物资与绿宝石循环

  * 绝对禁止：`Iron -> Emerald`、`Iron -> Diamond/Netherite`、`Emerald -> Diamond/Netherite（release）`

---

## RNG & 约束规则（写死，别再发散）

### Random B（三条你指定的规则）

1. **随机层：每变体抽 1 件（不是 2）**
2. **anti-repeat：同一玩家同一变体不连续给同一随机 B（最多重抽 1 次）**
3. **每页最多 1 EPIC**（同一页：anchor + random + 其它都算；如果 anchor 是 EPIC，则 random 必须避开 EPIC）

### 去重（解决“Page1 重复特效装”）

* **同一页 output 去重**：同一个 `sell item id`（+ count）不允许出现两次
* 对于随机抽取：若撞重复 → **最多重抽 1 次**，仍撞 → **直接跳过/换下一候选**
* **每次 build 页面前必须 clear 当前 offers 容器**（防 rebuild 被调用两次时叠加）

---

## Variant Special（你之前的核心需求，保持不变）

每个商人变体必须保证：

* **Page1（Normal）固定 1 件 A 特效过渡装备（锚点）**
* **Page3（Arcane）固定 1 件 B 招牌装备（锚点）**
* **Page3（Arcane）再给 1 件 Random B（按主题池抽）**

> 注意：这些锚点必须基于 **merchant variant**，不要依赖 “secret katanaId” ——因为你有“乱入”。

---

## B 装备按主题分池（20 件）

> 下面直接用你给的内部 ID；agent 以代码里的实际注册名为准对齐。

### 探索（Exploration）

* `sentinel_helmet`
* `relic_circlet_helmet`
* `boundary_walker_boots`
* `windbreaker_chestplate`

### 防御（Defense）

* `silent_oath_helmet`
* `ghost_god_chestplate` (EPIC)
* `graze_guard_leggings` (EPIC)
* `ghost_step_boots`
* `retracer_ornament_helmet`

### 进攻（Offense）

* `exile_mask_helmet`
* `blood_pact_chestplate`
* `void_devourer_chestplate`
* `clear_ledger_leggings`
* `marching_boots`

### 收益（Profit）

* `old_market_chestplate`
* `smuggler_shin_leggings`
* `smuggler_pouch_leggings`

### 机动（Mobility）

* `untraceable_treads_boots` (EPIC)
* `stealth_shin_leggings`
* `gossamer_boots`

---

## 每个变体的 B 锚点（固定必出，尽量非 EPIC）

* Variant A（探索向）→ **B Anchor：** `sentinel_helmet`
* Variant B（防御向）→ **B Anchor：** `silent_oath_helmet`
* Variant C（进攻向）→ **B Anchor：** `blood_pact_chestplate`
* Variant D（收益向）→ **B Anchor：** `old_market_chestplate`
* Variant E（机动向）→ **B Anchor：** `marching_boots`

Random B 候选池按变体主题走（上面 5 个池），并应用 anti-repeat + EPIC cap。

---

## A 特效过渡（Page1 锚点）分配

使用你过渡装里“带特效”的这些（以实际存在为准）：

* `sanctified_hood`（魔法伤害 ×0.85）
* `reactive_bug_plate`（节肢近战 -1.0）
* `cargo_pants`（Torch 返还 15%）
* `cushion_hiking_boots`（摔落减伤 -2.0）

建议分配（允许重复，但别全挤一个）：

* 探索向 → `cargo_pants`
* 防御向 → `cushion_hiking_boots`
* 进攻向 → `reactive_bug_plate`
* 收益向 → `sanctified_hood`
* 机动向 → `cargo_pants`（或 `sanctified_hood`，二选一）

---

# Page1 / Page2 / Page3 具体交易清单（保证总数 > 35）

> 说明：
>
> * “E”= Emerald，“I”= Iron Ingot，“D”= Diamond
> * maxUses 可用默认（消耗品 12~16，工具 6~8，特殊 1~2），不强制。
> * **发布版默认不出现 debug trades**（见文末 Debug Gate）。

---

## Page 1 (NORMAL) — Supplies / 杂货与生存（Base offers：24 条）

> 另外还要插入：**Variant A 锚点（A 特效过渡 1 条）**

1. 2E → 16 Bread
2. 3E → 8 Cooked Beef
3. 2E → 16 Baked Potato
4. 2E → 16 Carrot
5. 2E → 16 Apple
6. 3E → 16 Torch
7. 3E → 16 Ladder
8. 2E → 32 Cobblestone
9. 3E → 16 Glass
10. 3E → 16 Sand
11. 3E → 16 Logs（随机木头种类）
12. 2E → 16 Planks（随机木头种类）
13. 4E → 16 Paper
14. 6E → 4 Book
15. 4E → 1 Compass
16. 4E → 1 Clock
17. 5E → 1 Boat（任意木船）
18. 5E → 1 Bed（任意颜色）

**铁换便利（铁不换稀缺，不换绿宝石）：**
19. 3I → 32 Torch
20. 4I → 32 Arrow
21. 5I → 16 Rail
22. 3I → 1 Bucket
23. 4I → 4 Lantern
24. 2I → 1 Shears

---

## Page 2 (NORMAL) — Tools / Combat / 实用方块（Base offers：16 条）

1. 6E + 1I → Iron Sword
2. 7E + 1I → Iron Pickaxe
3. 7E + 1I → Iron Axe
4. 6E + 1I → Iron Shovel
5. 5E + 1I → Shield
6. 5E → Bow
7. 7E + 1I → Crossbow
8. 3E → 16 Arrow
9. 4E → 16 Redstone Dust
10. 4E → 16 Lapis Lazuli
11. 8E → 2 Ender Pearl
12. 6E → Potion of Healing (Instant Health)
13. 6E → Potion of Fire Resistance
14. 6E → Potion of Night Vision
15. 5E → 8 Experience Bottle
16. 6E → 1 Anvil?（如果你不想卖铁砧就删；这里只是占位填充“看起来不空”）

---

## Page 3 (ARCANE / HIDDEN) — Arcane Market（Base offers：12 条）

> 另外还要插入：
>
> * Katana 页逻辑（你说不用管）
> * Reclaim（你已实现）
> * **Variant B Anchor（B 招牌 1 条）**
> * **Random B（1 条）**

**钻石作为“消耗输入”，可以；但禁止产出钻石：**

1. 12E + 1D → Enchanted Book: Unbreaking II
2. 12E + 1D → Enchanted Book: Protection II
3. 12E + 1D → Enchanted Book: Sharpness II
4. 16E + 2D → Enchanted Book: Unbreaking III

**Arcane 便利（不产稀缺）：**
5. 10E → 4 Ender Pearl
6. 10E → 4 Blaze Powder
7. 12E → 1 Totem of Undying（如果你觉得太强就删；这是“让 Arcane 看起来像奖励”的填充位）
8. 8E → 1 Golden Apple（把你现在 10E 的金苹果也可以挪到这里，变成 Arcane 专属）

**你的模组物品（按实际存在对齐 ID）：**
9. X Emerald + Y Silver Notes → `Sacrifice`（修复材料）
10. X Emerald + Y Silver Notes → `Unseal Scroll`（解封卷轴/相关物品）
11. X Emerald → `Silver Note`（如果你需要 emerald→银币入口，只允许单向，禁止银币→emerald）
12. （预留位）任何你认为“Arcane 解锁后必有奖励”的小东西

---

## DEBUG Gate（必须加，发布默认关）

在 `TradeConfig` 加布尔开关（默认 false，或直接用 MASTER_DEBUG）：

* `ENABLE_DEBUG_DIAMOND_TRADES = MASTER_DEBUG;`

**仅在 debug 时出现：**

* 5E → 1 Diamond
* 24E → 1 Netherite Ingot

> 发布时只要 MASTER_DEBUG 为 false，这两条就自动消失。

---

# Implementation Notes（给 agent 的落地要求）

* 交易清单以 **TradeConfig 常量/数组**形式定义（便于后续一键调参）。
* `MysteriousMerchantEntity` 构建 offers 时按 Page 分类批量插入。
* **插入前先 clear**（防 rebuild 两次叠加）。
* **同一页 output 去重**（sell item id + count），随机抽取最多重抽 1 次。
* 保留你已实现的：Random B / anti-repeat / EPIC cap。
* 禁止引入新系统；只做“填充 offers + debug gate + 去重”。

---

# Verification checklist（进游戏一次性验完）

1. 任意变体商人：Page1 + Page2 + Page3（解锁后）合计 offers 数量 **> 35**（允许滚动）
2. Page1：必有 **A 特效过渡锚点**（且不重复出现）
3. Arcane：必有 **B anchor + random B**，并满足 anti-repeat（最多重抽 1 次）
4. 任意页面：**最多 1 个 EPIC**（anchor 是 EPIC 时 random 必须避开 EPIC）
5. 发布模式（MASTER_DEBUG=false）：**看不到** `Emerald -> Diamond/Netherite`
6. 铁相关交易：铁 **不能**换绿宝石/钻石/下界合金
---
