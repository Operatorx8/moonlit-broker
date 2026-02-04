# ARMOR_CHANGELOG.md - 盔甲系统变更记录

---

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
