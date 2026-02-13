MM Trade Expansion — Pages + Safe Economy (TEMP DOC)
TASK

把交易列表扩容到 ≥ 40 条，并分成：

**Page 1 (BASE/NORMAL)：**生存补给 + 基础材料 + 少量探索便利（含 5 变体的 A 特效过渡固定映射）

**Page 2 (BASE/NORMAL)：**建造/生产/红石/附魔周边（含铁兑换非稀缺物资）

**Page 3 (HIDDEN/ARCANE)：**B 装备锚点 + 随机 B（按现有规则）+ 少量“解锁后奖励”的高价值便利交易
禁止任何“绿宝石轻松换钻石/下界合金”的交易；debug 专用交易必须用开关隔离。

CRITICAL MEMORY DISCIPLINE

NO AUTO-SCANNING: 禁止扫描整个 src/，只读 ALLOWED FILES。

NO ZOMBIE FILES: 禁止读取根目录杂文件/旧笔记/历史临时稿。

USE-AND-DISCARD: 只按本文档实现；出 bug 另开修复任务。

ALLOWED FILES (只读这些)

src/main/java/dev/xqanzd/moonlitbroker/trade/TradeConfig.java

src/main/java/dev/xqanzd/moonlitbroker/entity/MysteriousMerchantEntity.java

src/main/java/dev/xqanzd/moonlitbroker/trade/TradePage.java（如需）

src/main/java/dev/xqanzd/moonlitbroker/trade/TradeConfig.java 里已有的数据结构/常量风格必须沿用

（仅当确实需要引用装备 item id 注册处）registry/ModItems.java 或对应注册文件

OUTPUT RULES

**Diff Only：**只输出改动行，每处最多上下文 2~3 行

**No Yapping：**不写背景、不写教程，只给文件 diff + 最短验证清单（≤5条）

ECONOMY HARD RULES (必须遵守)

NO emerald→diamond / emerald→netherite（发布版禁止）

允许 iron → 非稀缺物资（方块/食物/耗材），但：

铁不参与任何稀缺交易（diamond、netherite、ancient debris、净herite scrap 等）

禁止 iron→emerald（避免铁农场刷绿宝石）

稀缺物资只能用以下方式进入经济：

玩家自己挖/探索获得

或通过 Arcane 页的“高门槛”交易（昂贵且不可被 farm）

DEBUG TRADE ISOLATION (必须实现)

在 TradeConfig 加开关（沿用你现有风格）：

public static final boolean DEBUG_TRADES = MASTER_DEBUG;

把所有 debug 专用条目放入 addDebugOffers(...) 并用 if (TradeConfig.DEBUG_TRADES) 包起来。
发布默认关闭（没有 -Dmm.debug 时不出现）。

EXISTING A/B INSERTION RULES (不要改结构，只填数据)
A) Normal 页：A 特效过渡固定映射（必出）

Moonglow → Cargo Pants

Regret → Reactive Bug Plate

Eclipse → Sanctified Hood

Oblivion → Cushion Hiking Boots

Nmap → Cargo Pants（允许重复）

B) Arcane 页：B 锚点（解锁后必出）

Moonglow → sentinel_helmet

Regret → blood_pact_chestplate

Eclipse → retracer_ornament_helmet

Oblivion → untraceable_treads_boots

Nmap → smuggler_pouch_leggings

C) Arcane 随机层（每变体抽 1，不重复，EPIC≤1）

（保持你已实现的 RNG 三条规则，不重写）

TRADE LIST (≥ 40 条) — 具体条目清单

记号：

E=Emerald, B=Emerald Block, I=Iron Ingot, IB=Iron Block

所有数量可在实现时用常量表达

所有条目 maxUses 建议 ≥ 12（消耗品），功能方块/强力条目可 ≤ 6

若你系统只能 2 输入，按下列格式实现即可

PAGE 1 / BASE (生存与基础，约 18 条 + A 固定)

P1-01：2E -> 16 Torch
P1-02：1E -> 6 Bread
P1-03：2E -> 4 Cooked Beef
P1-04：3E -> 3 Golden Carrot
P1-05：4E -> 16 Arrow
P1-06：5E -> 8 Glass
P1-07：3E -> 16 Logs (any one type you choose)
P1-08：4E -> 16 Planks
P1-09：4E -> 8 Wool
P1-10：6E -> 1 Bucket
P1-11：7E -> 1 Shield
P1-12：8E -> 1 Bow
P1-13：10E -> 1 Saddle
P1-14：12E -> 1 Boat (or 2 Boats)
P1-15：16E -> 3 Ender Pearl（便利但不便宜）
P1-16：8E -> 1 Clock
P1-17：8E -> 1 Compass
P1-18：12E -> 4 Experience Bottle（Page1 少量，不要太便宜）

+ A 固定插入（每个变体 1 条）：Cargo Pants / Reactive Bug Plate / Sanctified Hood / Cushion Hiking Boots

A 装备的价格沿用你当前设定（agent不要自行重定价）。

PAGE 2 / BASE (建造/生产/红石/附魔周边，约 18 条，含铁兑换)
2A) 铁兑换非稀缺（严格禁止换稀缺/换绿宝石）

P2-01：8I -> 16 Torch（铁换耗材，方便但不刷稀缺）
P2-02：16I -> 16 Glass
P2-03：16I -> 16 Stone
P2-04：24I -> 16 Bricks
P2-05：24I -> 8 Ladder
P2-06：32I -> 16 Rails（注意：不含 powered rail）
P2-07：IB + 8I -> 1 Anvil (Vanilla)（原版铁砧高门槛，且不卖便宜）

2B) 红石与生产（仅 emerald）

P2-08：6E -> 16 Redstone Dust
P2-09：10E -> 8 Slimeball
P2-10：12E -> 1 Piston
P2-11：14E -> 1 Hopper
P2-12：10E -> 8 Quartz
P2-13：8E -> 16 String
P2-14：10E -> 8 Leather

2C) 附魔周边（只卖“周边”，不卖顶级书）

P2-15：8E -> 16 Lapis Lazuli
P2-16：12E -> 8 Book
P2-17：16E -> 4 Bookshelf
P2-18：20E -> 1 Enchanting Table（不便宜，但可买到）

PAGE 3 / HIDDEN/ARCANE (解锁奖励 + 高门槛实用，约 8 条 + B 锚点 + 随机B)

注意：这里的条目必须走你现有的 secret gate（arcane 解锁 + 交易次数等）。

3A) 高价值便利（允许更划算，但不刷爆稀缺）

P3-01：24E -> 16 Experience Bottle（比 Page1 划算，作为解锁奖励）
P3-02：20E -> 8 Ender Pearl（更划算，但仍然贵）
P3-03：16E -> 1 Elytra Repair Kit?（如果你没有此物品，跳过本条；不要新增物品）
P3-03 (替代)：24E -> 1 Totem of Undying（如果你觉得太强就删掉，不强求）

3B) 模组铁砧（你要求的两条）

P3-04：Diamond Block x3 -> Mod Anvil（第一输入：3 钻石块）
P3-05：Netherite Ingot x1 -> Mod Anvil（第二条：少量下界合金锭换取）

这两条满足：模组 anvil 门槛高；且不依赖 emerald 直换稀缺。

3C) “稀缺换稀缺”仅限 1~2 条（可选，谨慎）

P3-06 (可选)：Diamond x16 + 24E -> 1 Netherite Scrap（注意：不是 ingot；且非常贵）
P3-07 (可选)：Diamond x24 + 32E -> 1 Netherite Ingot（如果你觉得太强，删掉，保守即可）

3D) B 装备插入（保持你现有逻辑）

B 锚点必出 1 件

随机 B 抽 1 件（anti-repeat + EPIC≤1）

DEBUG-ONLY OFFERS (发布必须隐藏)

以下仅在 TradeConfig.DEBUG_TRADES == true 时出现：

D-01：5E -> 1 Diamond（debug 快速验证专用）
D-02：1E -> 64 Lapis（debug 附魔测试专用，可选）

这些必须放在独立方法里并 gated；发布默认不出现。

IMPLEMENTATION NOTES (最小改动指令)

不要重构交易系统：只在现有 offers 构建处追加固定条目。

Page1/Page2 的条目是全局固定 base offers（不依赖变体），A 特效过渡仍按你现有“每变体插入”的逻辑。

Page3 的条目加入到你现有的 HIDDEN/ARCANE 构建路径里（与 B 锚点/随机 B 同页）。

严格遵守经济硬规则：没有任何 iron→emerald、iron→diamond/netherite；没有 emerald→diamond/netherite（debug 除外）。

DONE WHEN (验收)

总条目数（不含 debug）≥ 40（Page1+Page2+Page3+B/A 插入合计）

未解锁 arcane 时：看不到 Page3 条目、看不到 B 装备

任何情况下：不存在 emerald→diamond / emerald→netherite（debug 关闭时）

存在 iron→非稀缺交易，但不存在 iron→emerald 或 iron→稀缺

现有 A/B 插入与 RNG 三条规则仍然生效