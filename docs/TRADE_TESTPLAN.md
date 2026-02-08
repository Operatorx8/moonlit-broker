# Trade Release Test Plan

> 发布前必须全部通过；任意一项失败即阻塞发布。

## 环境准备
- [ ] 使用开发环境启动客户端（`./gradlew runClient`）。
- [ ] 准备日志窗口：`grep "MoonTrade" run/logs/latest.log | tail -n 120`。

## T1 UI 翻页 / 滚轮 / 空行点击
### Steps
1. `/summon mymodtest:mysterious_merchant` 生成商人并打开交易界面。
2. 将列表翻到有空白行的页面，点击空白行。
3. 使用 Next/Prev 和滚轮滚动列表。
### Expected
- 日志出现 `action=CLICK_EMPTY_IGNORED`。
- 页面切换正常，不崩溃，不出现选中错位。
- 滚轮可用且不会导致界面卡死。
### Pass/Fail
- Pass: 空行点击无崩溃且日志命中。
- Fail: 任意崩溃、卡死、或点击空行触发异常选择。

## T2 Refresh 是否真正重建 offers
### Steps
1. 打开商人界面，记录当前 `offersHash`。
2. 点击 refresh（满足卷轴条件）。
3. 再次查看日志。
### Expected
- 出现 `action=REBUILD_DONE`。
- `refreshSeenCount` 递增。
- `offersHash` 变化（或列表可见变化）。
### Pass/Fail
- Pass: `REBUILD_DONE` + `refreshSeenCount` 变化。
- Fail: 点击 refresh 无重建日志或 hash 长期不变。

## T3 唯一隐藏刀（同一实体不重复）
### Steps
1. 用同一个商人实体完成隐藏刀交易。
2. 立即关闭 UI 再打开。
3. 退出世界并重新进入（确保同一实体仍在），再次打开 UI。
### Expected
- 首次成交时日志出现 `SECRET_KATANA_PURCHASED` 与 `SECRET_SOLD`。
- 同一实体后续不再出现隐藏刀交易。
### Pass/Fail
- Pass: 同一实体只会卖出一次隐藏刀。
- Fail: 重开 UI 或重进世界后同一实体再次出现隐藏刀。

## T4 新商人 vs 同一商人
### Steps
1. 对已售出隐藏刀的商人再次打开 UI（同一实体）。
2. `/summon` 生成一个新商人实体并打开 UI。
### Expected
- 旧实体：隐藏刀仍然不出现。
- 新实体：允许再次出现隐藏刀（符合“唯一性按实体隔离”）。
### Pass/Fail
- Pass: 旧实体禁售、新实体可售。
- Fail: 旧实体复活隐藏刀，或新实体永远不出隐藏刀。

## T5 隐藏刀多样性（Moon 不再垄断）
### Steps
1. 连续 `/summon` 至少 10 个新商人，记录每个商人的 `action=SECRET_PICK` 日志。
2. 统计 `chosenId` 分布。
### Expected
- `candidatesSize` 为 5。
- `candidates` 含：`moon_glow_katana|regret_blade|eclipse_blade|oblivion_edge|nmap_katana`。
- `chosenId` 不是几乎恒定为 `moon_glow_katana`。
### Pass/Fail
- Pass: 10 次中出现至少 2 种以上 `chosenId`。
- Fail: `candidatesSize<5` 或 `chosenId` 长期单一。

## T6 Release 开关清单（默认必须关闭）
### Steps
1. 检查 `MysteriousMerchantEntity` 调试开关。
2. 运行一次正常交易流程并检查日志。
### Expected
- `DEBUG_SCROLL_INJECT=false`。
- `DEBUG_REFRESH_INJECT=false`（发布版必须关闭）。
- 发布日志中不应出现调试注入 tag：`MM_DEBUG_REFRESH_INJECT`。
### Pass/Fail
- Pass: 调试开关关闭且日志干净。
- Fail: 任意 debug 注入仍开启或日志持续打印调试 tag。

## 快速 grep（建议）
```bash
grep -E "CLICK_EMPTY_IGNORED|REBUILD_DONE|SECRET_PICK|SECRET_KATANA_PURCHASED|SECRET_SOLD|MM_DEBUG_REFRESH_INJECT" run/logs/latest.log | tail -n 200
```
