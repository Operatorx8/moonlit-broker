# CHANGELOG.md - 更新记录

面向玩家的更新记录。

---

## [Unreleased]

### Added

- 盔甲系统：5 个神秘商人头盔
  - 哨兵的最后瞭望：黑暗中侦测敌对生物
  - 沉默之誓约：首次受击减伤
  - 流亡者的面甲：低血增伤
  - 遗世之环：被盯上时获得护盾
  - 回溯者的额饰：爆炸致死保护

### Fixed

- 交易 UI 分页修复：点击 `next/prev` 仅切换分页，不再触发交易选择同步，修复翻页误触发商人拒绝音效/字幕（`disagree`）的问题

### Changed

- 商人音效字幕命名统一为 Moonlit Broker（月光掮客），覆盖 `ambient/hurt/death/agree/disagree/trade` 六类事件
- 商人动态名称池调整为英文名；旧存档中的中文动态名会在读取时自动迁移为对应英文名
- `moon_glow_katana` 显示名调整为 `Moonlight`（中英文语言文件同步）

- Mysterious Anvil 规则收紧（服务端权威）
  - 仅允许修复本模组耐久装备/武器，且修复材料仅允许 `sacrifice`
  - 禁止使用附魔书/书进行附魔合成，非法输入不再产出结果
  - 修复按每个 `sacrifice` 固定百分比计算，按缺口向上取整消耗
  - 神秘铁砧使用后不再进入破损状态（永不损坏）

- Katana 审计与文案对齐（代码为准）
  - 新增 `docs/AUDIT_KATANA.md`，逐把列出真实触发条件、冷却、伤害/Debuff计算与来源文件
  - 统一 katana/过渡武器 tooltip key（`tooltip.xqanzd_moonlit_broker.<item>.subtitle/line1/line2/line3/params`）
  - 修正过期文案（例如 Regret/Eclipse/Oblivion 的穿透与冷却描述）

- Tooltip 集中化（客户端）
  - 新增 `TooltipHelper`，统一读取结构化多行 tooltip key
  - `ModTooltips` 改为集中注入，不再按物品类散落手写

- 特效盔甲紫黑占位修复（最小资源改动）
  - `MerchantArmorMaterial` 与 `BootsArmorMaterial` 贴图 layer 临时对齐 `minecraft:iron`
  - 20 件特效盔甲 item model 统一回退到原版盔甲 parent，避免缺失纹理导致紫黑方块
  - 不改动任何盔甲特效逻辑与数值

- Katana 横扫行为修复（1.21.1）
  - 新增 `xqanzd_moonlit_broker:katana` 物品标签，统一标记 5 把神器 katana
  - 对 `PlayerEntity#attack` 的横扫分支做 katana 定向阻断：移除横扫 AOE、横扫音效、横扫粒子
  - 保留剑类附魔生态与附魔系数，仅禁止 `Sweeping Edge` 对 katana 生效
  - 即使通过 NBT/指令强行写入 Sweeping，katana 攻击也不会触发横扫

- Moon Glow（月之光芒）平衡调整
  - 保留原有月光触发路径（夜晚 + 露天 + 天空光照阈值）
  - 新增光照触发路径：当月光条件不满足时，可按总光照（`max(blockLight, skyLight)`）触发 LIGHT_MARK
  - 引入独立 LIGHT_MARK 状态与独立冷却，不影响原月光标记平衡
  - LIGHT_MARK 消耗增伤按倍率降低（默认 `0.70`）
  - 新增调试配置：`LIGHT_MARK_ENABLED`、`LIGHT_MARK_MIN_LIGHT`、`LIGHT_MARK_DAMAGE_MULT`
  - DEBUG 日志补充：标记来源（MOONLIGHT/LIGHT）、光照值与触发原因、消耗时倍率

- Regret Blade（残念之刃）平衡调整
  - LifeCut 触发目标由亡灵扩展为任意 `LivingEntity`
  - 保留 30% 触发率与 cannot-kill 机制
  - 维持/明确每目标触发成功后 60 ticks（3 秒）冷却作为安全阀
  - 护甲穿透统一为 `35%`（普通与 Boss）
  - DEBUG 日志补充：触发详情（攻击者、目标 UUID、maxHealth、cut、结果血量、冷却）与穿透值

- Eclipse Blade（暗月之蚀）Debuff 轮盘扩展
  - 新增 `Poison`（中毒）进入加权轮盘，并参与随机组合概率计算
  - 增加轮盘保护：当总权重异常时回退到安全默认组合，避免随机 `nextInt(0)` 崩溃
  - 当前配置对齐：`BOSS_DURATION_MULTIPLIER=1.0`（Boss 不减半），穿透状态文案基准为 `25% / 25%`

- Oblivion Edge（窃念之黯）机制与手感调整
  - 第一人称右手模型修正：刀刃朝外（`display.firstperson_righthand.rotation` 修正）
  - 穿透补偿从“仅 ReadWrite 生效”调整为“基础命中即生效”
  - 穿透分层：基础 `25%`；`ReadWrite` 目标 `35%`；`ReadWrite + Boss` 为 `40%`

- Nmap Katana（先觉之谕）规则修正
  - Vulnerability Scan 不再因目标是 Wither/Dragon 被硬性禁用（移除 boss 直接排除）
  - Port Enumeration（按敌对目标数提高穿透，上限 35%）机制保持不变

- 五把神器 Katana 展示框贴合优化（仅模型 `display.fixed`）
  - `moon_glow_katana` / `regret_blade` / `eclipse_blade` / `oblivion_edge` / `nmap_katana`
  - 统一补齐/对齐 `rotation`、`translation`、`scale`，并将 Z 轴朝底板方向微调，展示更贴框

- 过渡武器资源接入与数值调整
  - `acer` / `velox` / `fatalis` item model 改为指向 `assets/.../models/item/tran_katana/*`
  - 新增对应 Blockbench 模型与纹理到 `assets/xqanzd_moonlit_broker/models/item/tran_katana/` 与 `textures/item/tran_katana/`
  - 基础数值：`Acer=8`，`Velox=7` 且攻速 `2.4`，`Fatalis=12`

- 商人相关 icon 资源迁移与调用
  - 新增 `assets/xqanzd_moonlit_broker/models/item/icon/*` 与 `textures/item/icon/*`
  - `guide_scroll` / `mysterious_coin` / `sacrifice` / `merchant_mark` / `silver_note` / `trade_scroll` 入口模型改为 parent 引用 icon 子模型
  - 兼容保留顶层 item model 包装（`models/item/<id>.json`）

---

## [0.8.0] - Katana 武器系统

### Added

- 5 把太刀武器
- 月痕标记系统
- 各武器独特效果

---

## [0.7.0] - 神秘商人核心

### Added

- 神秘商人 NPC
- 交易系统 + 隐藏交易
- AI 行为（逃跑、自保、趋光）
- 自然生成系统
- 交互惩罚机制
