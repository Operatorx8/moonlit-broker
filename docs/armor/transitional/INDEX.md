# 过渡护甲索引 (Transitional Armor Index)

> 过渡护甲系列：填补早期到中期的装备空缺，属性适中、特效简洁、获取相对容易。
> 共同约束：fireproof=true, anvilRepair=false, knockbackResistance=0, enchantability=BY_RARITY

## 总览

| # | 显示名 | 英文名 | 部位 | 耐久 | 护甲 | 韧性 | 稀有度 | 特效 |
|---|---|---|---|---|---|---|---|---|
| 1 | 拾荒者的风镜 | Scavenger's Goggles | HEAD | 180 | 2 | 0.0 | UNCOMMON | 无 |
| 2 | 生铁护面盔 | Cast Iron Sallet | HEAD | 200 | 2 | 0.5 | UNCOMMON | 无 |
| 3 | 祝圣兜帽 | Sanctified Hood | HEAD | 165 | 1 | 0.0 | RARE | 魔法伤害 ×0.85 |
| 4 | 补丁皮大衣 | Patchwork Coat | CHEST | 240 | 5 | 0.0 | UNCOMMON | 无 |
| 5 | 仪式罩袍 | Ritual Robe | CHEST | 220 | 5 | 1.0 | UNCOMMON | 无 |
| 6 | 反应Bug装甲板 | Reactive Bug Plate | CHEST | 260 | 6 | 1.0 | RARE | 节肢近战 -1.0 |
| 7 | 缠布护腿 | Wrapped Leggings | LEGS | 200 | 4 | 0.5 | UNCOMMON | 无 |
| 8 | 加固护膝 | Reinforced Greaves | LEGS | 280 | 6 | 0.5 | UNCOMMON | 无 |
| 9 | 多袋工装裤 | Cargo Pants | LEGS | 225 | 5 | 0.0 | RARE | Torch 返还 15% |
| 10 | 苦行者之靴 | Penitent Boots | FEET | 200 | 3 | 0.5 | UNCOMMON | 无 |
| 11 | 制式铁靴 | Standard Iron Boots | FEET | 210 | 2 | 0.5 | UNCOMMON | 无 |
| 12 | 缓冲登山靴 | Cushion Hiking Boots | FEET | 260 | 3 | 0.5 | RARE | 摔落减伤 -2.0 |

## 获取方式

所有过渡护甲均通过神秘商人交易获取。

## 共同特性

- **防火**：所有物品均为 fireproof，扔入岩浆不会消失
- **不可铁砧修复**：anvilRepair=false，无法通过铁砧修复耐久
- **无击退抗性**：knockbackResistance=0
- **可附魔**：附魔系数按稀有度决定（UNCOMMON=9, RARE=12）

## 文档结构

每件装备包含 4 个文档：
- `*_SPEC.md` - 规格说明
- `*_PARAMS.md` - 参数表
- `*_TESTPLAN.md` - 测试计划
- `*_CHANGELOG.md` - 变更日志
