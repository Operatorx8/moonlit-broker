# ARMOR_TODO.md - 盔甲系统待办

## Now (当前)

- [x] 5 个头盔物品注册
- [x] 5 个头盔效果实现
- [x] CooldownManager 统一冷却
- [x] 国际化 (en_us / zh_cn)
- [x] 物品模型 JSON（贴图待添加）
- [ ] 头盔贴图（需要 16x16 PNG）
- [ ] 穿戴模型贴图

## Next (下个迭代)

- [ ] 回溯额饰自定义音效
- [ ] 商人盔甲交易联动
- [ ] 盔甲专属粒子效果
- [ ] DEBUG 命令（清除冷却等）

## Later (远期)

- [ ] 胸甲 / 护腿 / 靴子
- [ ] 套装效果系统
- [ ] 配置文件（数值外置）
- [ ] 性能监控（扫描耗时统计）

## Known Issues

- 遗世之环对末影人/猪灵的愤怒检测可能有延迟（20 ticks 低频检测）
- enchantability 分档已接线到材质，但仍需按 `ARMOR_TESTPLAN` 完成附魔台分布抽样验证（UNCOMMON/RARE/EPIC 对照原版材质）
