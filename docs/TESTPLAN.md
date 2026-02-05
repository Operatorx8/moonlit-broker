# TESTPLAN.md - 全局测试清单

发版前验证。

---

## 基础功能

| # | 测试项 | 通过 |
|---|--------|------|
| 1 | 模组正常加载 | [ ] |
| 2 | 神秘商人可召唤 | [ ] |
| 3 | 太刀系统正常 | [ ] |
| 4 | 盔甲系统正常 | [ ] |

---

## 各子系统测试

- 商人系统：见 PROGRESS.md 测试步骤
- 太刀系统：在游戏中测试各武器效果
- 盔甲系统：见 [features/armor/ARMOR_TESTPLAN.md](./features/armor/ARMOR_TESTPLAN.md)

### 太刀附魔约束（横扫之刃）

| 用例 ID | 场景 | 步骤 | 预期 |
|---|---|---|---|
| K-E01 | 附魔台候选池 | 将任意 katana 放入附魔台，多次刷新候选附魔 | `Sweeping Edge` 不出现 |
| K-E02 | 铁砧 + 附魔书 | 左槽放 katana，右槽放 `Sweeping Edge` 附魔书 | 不允许合成（结果槽无有效产物） |
| K-E03 | 指令附魔 | 执行 `/enchant @s sweeping <level>` 且主手为 katana | 指令失败或对 katana 无效 |
