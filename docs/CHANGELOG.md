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

### Changed

- Katana 横扫行为修复（1.21.1）
  - 新增 `mymodtest:katana` 物品标签，统一标记 5 把神器 katana
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
