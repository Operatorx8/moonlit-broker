# ARMOR_CHANGELOG.md - 盔甲系统变更记录

---

## [0.11.3] - 借用皮革模型装备的染色/清洗修复

### Fixed

- 修复可染色装备在部分场景下 UI 图标不变的问题（客户端颜色提供器统一注册）
- 修复水炼药锅清洗后图标/外观逐步隐形的问题（fallback 颜色补全 alpha）
- 修复默认色只在 `getDefaultStack()` 注入导致的来源不一致问题（改为物品默认组件）
- 补齐自定义可染色装备的炼药锅清洗映射注册（与 vanilla 清洗行为对齐）

### Docs

- 新增问题定位与修复复盘文档：`docs/features/armor/DYEABLE_LEATHER_ARMOR_DEBUG_REPORT.md`

---

## [0.11.2] - 护腿二轮审计修复

### Fixed

- **tick 源统一**：护腿 handler 全部改用 `server.getTicks()` 作为时钟源，不再混用 `world.getTime()`
- **ClearLedger 自然过期**：移除 `removeStatusEffect(SPEED)` 调用，只维护 `speedExpiresTick` Map；Speed 由游戏自然衰减或被新效果覆盖
- **脱装清理增强**：`SmugglerPouchHandler` / `StealthShinHandler` / `ClearLedgerHandler` tick 方法检测未穿戴时立即清理全部状态；Pouch 额外清理冷却

### Technical

- `ArmorInit:60` 新增 `long serverTick = server.getTicks()` 供护腿 handler 使用
- `ClearLedgerHandler:45-68` 新增 `tick()` 方法用于 expiresTick 清理和脱装检测
- `LivingEntityDeathMixin:91-96,135-140` 改用 `server.getTicks()` 替代 `world.getTime()`

---

## [0.11.1] - 护腿 P0/P1 修复

### Fixed

- **P0: 走私者之胫掉落采集为空**
  - `LivingEntityDeathMixin` 重写：在 `dropLoot` HEAD 记录周围 ItemEntity 快照，TAIL 对比筛选新生成的掉落物
  - `SmugglerShinHandler.onEntityDeath()` 现在能正确接收真实掉落列表

- **P1: 核心资源减半未实现**
  - 新增 `ModTags.Items.CORE_LOOT` 物品 tag（路径 `data/xqanzd_moonlit_broker/tags/item/core_loot.json`）
  - 默认包含：`nether_star`, `elytra`, `totem_of_undying`, `dragon_egg`, `dragon_breath`
  - `SmugglerShinHandler` 判断 `isSpecial = isBoss || containsCoreLoot(drops)`，两项概率均减半

- **P1: 走私者暗袋激活期每 tick 拉取**
  - `SmugglerPouchHandler` 激活期现在也受 20 ticks 扫描间隔限制，不再每 tick 拉取
  - 新增 `lastPullTick` 追踪最后拉取时间

- **P1: Map key 用 player.getId() 导致死亡/重登漂移**
  - `SmugglerPouchHandler`, `StealthShinHandler`, `ClearLedgerHandler` 所有 Map key 改为 UUID
  - 新增 `onPlayerRespawn()` 方法用于死亡/重生时清理状态
  - `ArmorInit` 注册 `ServerPlayerEvents.AFTER_RESPAWN` 调用清理

- **P1: ClearLedger Speed 来源识别不稳**
  - 不再依赖 `amplifier + ambient` 判断
  - 新增 `speedExpiresTick` Map 记录效果到期 tick
  - `isOurSpeedActive()` 通过比较 tick 判断是否为本效果

### Added

- `ModTags.java` - 自定义 Tag 定义（`CORE_LOOT`）
- `data/xqanzd_moonlit_broker/tags/item/core_loot.json` - 核心战利品 tag

### Technical

- 所有护腿 handler 状态清理统一走 UUID key
- 死亡后冷却重置：`ServerPlayerEvents.AFTER_RESPAWN` → `CooldownManager.clearAllCooldowns()`
- 日志新增 `boss` / `core` 字段标注特殊掉落

---

## [0.11.0] - 5 件护腿实现

### Added

- 5 件神秘商人护腿
  - `smuggler_shin_leggings` - 走私者之胫（击杀掉落增益：20% 额外掉落 + 10% 双倍掉落）
  - `smuggler_pouch_leggings` - 走私者的暗袋（磁吸掉落物：6 格范围，5s 持续，35s CD）
  - `graze_guard_leggings` - 擦身护胫（18% 概率减伤 60%，12s CD）
  - `stealth_shin_leggings` - 潜行之胫（45s 充能 1 层，最多 2 层，摔落 >=3HP 消耗减伤 80%）
  - `clear_ledger_leggings` - 清账步态（击杀给 Speed I 3s，CD 内 +1s，上限 6s，CD 16s）

### Technical

- 新增护腿效果处理器：
  - `SmugglerShinHandler` - 击杀掉落增益（额外 loot roll + 双倍掉落）
  - `SmugglerPouchHandler` - 磁吸掉落物（ServerTick 低频扫描 + 持续牵引）
  - `GrazeGuardHandler` - 概率减伤（RNG + CD 判定）
  - `StealthShinHandler` - 充能型摔落减伤（层数管理 + 门槛判定）
  - `ClearLedgerHandler` - 击杀速度加成（初始触发 + CD 内延长）
- 新增 Mixin：
  - `LivingEntityDeathMixin` - 死亡事件处理（走私者掉落 + 清账步态）
- 更新 `ArmorDamageMixin` - 扩展支持擦身护胫和潜行之胫
- 更新 `ArmorInit` - 注册护腿 tick 事件和玩家下线清理
- 更新 `MerchantArmorMaterial` - 护腿护甲值（6 armor）
- 更新 `ArmorConfig` - 护腿效果参数常量
- 更新 `ArmorItems` - 护腿物品注册

### Assets

- 新增 5 个护腿物品模型 JSON
- 更新 `en_us.json` / `zh_cn.json` 语言文件

### Notes

- 护腿 enchantability 沿用全局稀有度分档：UNCOMMON→IRON(9), RARE→CHAIN(12), EPIC→NETHERITE(15)
- 护腿耐久 = 25 × 15 = 375
- 所有效果 server-side 判定，冷却统一走 CooldownManager
- 走私者之胫与清账步态 PVP 排除
- 贴图暂缺，使用占位模型

---

## [0.10.1] - Leggings 文档收尾（5 件护腿）

### Added

- 在 `ARMOR_SPEC.md` 新增 5 件护腿完整规格：
  - `smuggler_shin_leggings` - 走私者之胫（掉落增益 + 双倍掉落，PVP 排除，Boss/核心减半）
  - `smuggler_pouch_leggings` - 走私者的暗袋（6 格吸附，5s 持续，35s CD，仅 ItemEntity）
  - `graze_guard_leggings` - 擦身护胫（18% 概率，减伤 60%，12s CD）
  - `stealth_shin_leggings` - 潜行之胫（45s 充能，最多 2 层，>=3 伤害门槛才消耗）
  - `clear_ledger_leggings` - 清账步态（3s 初始，CD 内击杀 +1s，上限 6s，CD 16s）
- 在 `ARMOR_PARAMS.md` 新增护腿通用属性、稀有度分档、LeggingsEffectParams、LeggingsEffectConstants。
- 在 `ARMOR_TESTPLAN.md` 新增护腿 5 组测试用例（正向、CD、边界、叠加、多人/服务端一致性）。

### Changed

- `ARMOR_PARAMS.md` 交易参数分块统一为 `cost / weight / minLevel / maxLevel / rarityGate` 字段，并补齐头盔/胸甲/护腿三类占位参数。
- 冷却时间汇总与扫描间隔汇总表补入护腿条目。
- `ARMOR_SPEC.md` 明确引用全局约定：`CooldownManager`、server-side 判定、低频扫描、固定 UUID Modifier。

### Notes

- 本次仅文档收尾，不涉及代码逻辑改动。
- 根目录旧路径 `docs/ARMOR_*.md` 与 `docs/features/armor/*.md` 仍存在并行文档，后续建议统一单一权威路径。

---

## [0.10.0] - 5 件胸甲实现 + 附魔分档全局化

### Added

- 5 件神秘商人胸甲
  - `old_market_chestplate` - 旧市护甲（交易经验 50%×1.5 + 击杀经验 25%×2）
  - `blood_pact_chestplate` - 流血契约之胸铠（受击储能 + 释放伤害）
  - `ghost_god_chestplate` - 鬼神之铠（亡灵减伤 30%→-15% + Debuff 免疫 50%）
  - `windbreaker_chestplate` - 商人的防风衣（+30% 击退抗性 + 低血边沿速度）
  - `void_devourer_chestplate` - 虚空之噬（攻击追加 4% 真实伤害，CD 5s）

### Changed

- **Enchantability 分档策略全局化**：头盔 + 胸甲（未来腿/靴同样适用）都遵循：
  - `UNCOMMON -> IRON enchantability (9)`
  - `RARE -> CHAIN enchantability (12)`
  - `EPIC -> NETHERITE enchantability (15)`
- `MerchantArmorMaterial` 现在支持胸甲护甲值（8 armor）

### Technical

- 新增胸甲效果处理器：
  - `OldMarketHandler` - 交易/击杀经验双通道
  - `BloodPactHandler` - 受击储能 + 攻击释放
  - `GhostGodHandler` - 亡灵伤害/Debuff 处理
  - `WindbreakerHandler` - 击退抗性 + 低血边沿触发
  - `VoidDevourerHandler` - 真实伤害追加
- 新增 Mixin：
  - `PlayerAttackMixin` - 攻击时处理血契释放、虚空真伤
  - `StatusEffectMixin` - Debuff 免疫处理
  - `LivingEntityDropXpMixin` - 击杀经验加成
  - `TradeOutputSlotMixin` - 交易经验加成
- `ArmorDamageMixin` 扩展支持胸甲效果
- `ArmorInit` 注册胸甲 tick 事件和玩家下线清理

### Notes

- 防风衣击退抗性使用固定 UUID `b2c3d4e5-f6a7-8901-bcde-f23456789012`
- 血契池上限 4♥（8.0 HP），窗口 10s
- 旧市护甲交易经验需"首次完成该交易槽"才触发
- 鬼神之铠对 Boss 概率减半

---

## [0.9.2] - 哨兵头盔重做为 Echo Pulse

### Changed

- `sentinel_helmet` 从“对敌对生物施加 Glowing”重做为“Echo Pulse 回声测距”：
  - 触发后只给玩家 `Speed I`（30 ticks）
  - 播放原版提示音 `minecraft:block.bell.use`
  - 不再对敌对生物施加任何状态，不生成粒子环
- 哨兵参数收敛为：半径 16、光照阈值 7、扫描 20 ticks、冷却 800 ticks。

### Technical

- 沿用原有 ServerTick + handler + CooldownManager 管线，替换 Sentinel 应用段逻辑为“玩家增益 + 音效”。
- 更新中英文文案与测试用例，确保附加行为不包含透视效果。

## [0.9.1] - enchantability 分档策略落地

### Changed

- 头盔材质由单一 `MERCHANT_ARMOR` 调整为三档材质：
  - `UNCOMMON -> IRON enchantability`
  - `RARE -> CHAIN enchantability`
  - `EPIC -> NETHERITE enchantability`
- 5 个头盔按各自 `Rarity` 绑定对应材质，附魔系数由材质实际生效。

### Technical

- 新增 `merchant_uncommon` / `merchant_rare` / `merchant_epic` 三个 `ArmorMaterial` 注册项。
- `ArmorItems` 改为按头盔 `rarity` 选择 `MerchantArmorMaterial.byRarity(...)` 注册。

## [0.9.0] - 初始实现

### Added

- 5 个神秘商人头盔
  - `sentinel_helmet` - 哨兵的最后瞭望（黑暗侦测）
  - `silent_oath_helmet` - 沉默之誓约（首次减伤）
  - `exile_mask_helmet` - 流亡者的面甲（低血增伤）
  - `relic_circlet_helmet` - 遗世之环（被盯护盾）
  - `retracer_ornament_helmet` - 回溯者的额饰（爆炸保命）

- 统一冷却管理器 `CooldownManager`
- 盔甲效果处理器
- 国际化支持（en_us / zh_cn）

### Technical

- 基础属性：护甲 3，韧性 2.0，击退抗性 10%
- 耐久：275（25 × 11）
- 稀有度：EPIC
- 防火：是
- 可修复：否

### Notes

- 流亡面甲使用固定 UUID `a1b2c3d4-e5f6-7890-abcd-ef1234567890`
- 回溯额饰图腾优先逻辑已实现
- 遗世之环边沿触发已实现
