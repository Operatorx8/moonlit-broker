# ARMOR_TODO.md - 盔甲系统待办

## Now (当前)

- [x] 5 个头盔物品注册
- [x] 5 个头盔效果实现
- [x] 5 个胸甲物品注册
- [x] 5 个胸甲效果实现
- [x] 5 个护腿物品注册
- [x] 5 个护腿效果实现
- [x] CooldownManager 统一冷却
- [x] 国际化 (en_us / zh_cn) - 含护腿
- [x] 护腿模型层（`assets/*/models/item/*_leggings.json`）
- [x] 护腿 lang key（`en_us` / `zh_cn`）与 Lore 文案
- [x] **护腿 P0/P1 修复**（v0.11.1）
  - [x] 走私者之胫掉落采集修复（LivingEntityDeathMixin before/after 扫描）
  - [x] 核心资源减半实现（`xqanzd_moonlit_broker:core_loot` tag）
  - [x] 走私者暗袋激活期 20t 节奏限制
  - [x] Map key 改 UUID + respawn 清理
  - [x] ClearLedger Speed 来源用 expiresTick 判定
- [ ] 头盔贴图（需要 16x16 PNG）
- [ ] 胸甲贴图（需要 16x16 PNG）
- [ ] 护腿 5 件贴图（`smuggler_shin` / `smuggler_pouch` / `graze_guard` / `stealth_shin` / `clear_ledger`）
- [ ] 穿戴模型贴图（头盔/胸甲/护腿）
- [ ] 护腿音效资源（暗袋激活、潜行充能提示）与 `sounds.json` 注册（当前使用原版替代）

## Next (下个迭代)

- [ ] 回溯额饰自定义音效
- [ ] 商人盔甲交易联动
- [ ] 盔甲专属粒子效果
- [ ] DEBUG 命令（清除冷却等）
- [ ] 护腿效果游戏内抽样验收（掉落概率 / 双倍掉落概率）
- [ ] 附魔台分布抽样（护腿 UNCOMMON/RARE/EPIC 对照原版材质）
- [ ] 暗袋吸附性能抽样（多人同区 ItemEntity 峰值）

## Later (远期)

- [ ] 靴子（Boots）设计与落地
- [ ] 套装效果系统
- [ ] 配置文件（数值外置）
- [ ] 性能监控（扫描耗时统计）
- [ ] 商人售卖池参数定稿（cost/weight/min/max/rarity gate）

## Known Issues

- 遗世之环对末影人/猪灵的愤怒检测可能有延迟（20 ticks 低频检测）
- enchantability 分档已接线到材质，但仍需按 `ARMOR_TESTPLAN` 完成附魔台分布抽样验证（UNCOMMON/RARE/EPIC 对照原版材质）
- 走私者之胫与清账步态涉及击杀归属判定，需在多人环境复测最终归属一致性
