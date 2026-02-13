# TESTPLAN

1) 召唤商人并准备材料
- `/summon mymodtest:mysterious_merchant ~ ~ ~`
- `/give @s emerald 256`
- `/give @s diamond 64`
- `/give @s mymodtest:mysterious_coin 4`
- `/give @s blaze_rod 16`

2) 交易计数与资格提示
- 与商人完成 15 次交易（随便用基础交易刷够）。
- 第 15 次完成时应出现提示：`[神秘商人] 你已获得解封资格。`

3) eligible 后新交易与 Sigil 随机池
- 打开交易界面，确认出现 `神秘硬币 -> 封印卷轴` 交易。
- 观察 Sigil 交易池：每次打开显示 3~5 条（A/B/C 混合）。
- 连续打开交易界面 3 次（未解锁状态），第 3 次必须包含 `Blaze Rod x2 -> Sigil`，且尽量不出现 A 类。

4) 解封交易与隐藏交易
- 用 `封印卷轴 + Sigil -> 已解封卷轴` 完成一次交易。
- 应出现提示：`[神秘商人] 你解封了卷轴，隐藏交易已开启。`
- 重新打开交易界面，确认出现隐藏 Katana 交易（若未注册 katana，会显示为下界合金剑占位）。

5) 持久化验证（可选）
- 退出并重新进入世界，确认解封提示不会重复出现，隐藏交易仍然可见。
