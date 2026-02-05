# ARMOR_CHANGELOG.md - 盔甲系统变更记录

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
