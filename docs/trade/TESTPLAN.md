# Trade System Test Plan

## 1. First Meet
- [ ] Interact with merchant first time -> Guide Scroll granted
- [ ] Re-interact -> no additional Guide Scroll

## 2. Scroll Usage Costs
- [ ] Open Normal page consumes uses -1
- [ ] Refresh current page consumes uses -1
- [ ] Secret switch consumes -2 only when switch succeeds

## 3. Secret Gate
- [ ] Missing Mark -> denied
- [ ] Has Mark + Sealed scroll but uses < 2 -> denied
- [ ] All above but rep < 15 -> denied
- [ ] All 3 satisfied -> allowed

## 4. Reputation Hook
- [ ] Open/select offer without taking result -> no rep gain
- [ ] Complete purchase and take result -> rep +1

## 5. Secret One-Time Limit
- [ ] Buy epic katana from merchant A -> secret_sold=true
- [ ] Try buying another epic from same merchant A -> blocked
- [ ] Merchant B can have independent sale

## 6. Drops/Loot/Bounty
- [ ] Chest scroll appears only in 4 target categories at 15%
- [ ] Mob scroll drop obeys 0.5% + condition
- [ ] Bounty submit always grants 1 Trade Scroll + small Silver

## 7. Silver Anti-Inflation     
- [ ] Repeated mob kills under same window -> throttled

## 8. Multiplayer Isolation
- [ ] Reputation per-player
- [ ] Cooldowns per-player
- [ ] Drop limits per-player

## 9. Persistence
- [ ] Save/reload world -> player state valid
- [ ] Save/reload world -> merchant secret flags valid

## 10. Bounty v1 Submit/Refresh — Quick Verify

```
# 1. 准备
/give @p mymodtest:merchant_mark
/bountycontract give @s zombie 3

# 2. reject 验证（INVALID / NOT_DONE）
# 2.1 空白契约（无 target）右键商人
# 预期日志:
#   action=BOUNTY_SUBMIT_REJECT reason=INVALID ...
# 2.2 未完成契约（progress < required）右键商人
# 预期日志:
#   action=BOUNTY_SUBMIT_REJECT reason=NOT_DONE target=... progress=.../...

# 3. 杀目标（3只）
/summon minecraft:zombie ~ ~ ~2
# 重复杀 3 次，观察日志:
#   action=BOUNTY_PROGRESS ... completed=true

# 4. accept 提交
# 手持契约 + 右键商人（非潜行）
# 预期日志:
#   action=BOUNTY_SUBMIT_ACCEPT side=S player=... target=minecraft:zombie progress=3/3 rewardScroll=1 rewardSilver=3
#   action=BOUNTY_REWARD side=S player=... rewardScroll=1 rewardSilver=3 dropped=false|true

# 5. 验证 NORMAL 刷新链路
# 5.1 右键商人打开 UI（记录第一次 OPEN_UI 的 offersHash）
# 5.2 使用奖励 Trade Scroll 执行 REFRESH（NORMAL 页）
# 5.3 再次打开 UI（记录第二次 OPEN_UI 的 offersHash）
# 预期:
#   两次 offersHash 必须变化
#   Scroll Uses: 3 -> 2
#   日志出现 action=REFRESH_* 且出现 NORMAL_BUILD

# 6. 边界测试
# 潜行 + 手持契约 + 右键 → 打开交易UI（不提交）
# 手持空白契约 + 右键 → "这不是有效的悬赏契约" + 日志 reason=INVALID
# 手持未完成契约 + 右键 → "悬赏未完成！" + 日志 reason=NOT_DONE
# 另一玩家正在交易时提交 → hasCustomer 保护，走 super
```

## 11. Build
- [ ] `./gradlew build` -> BUILD SUCCESSFUL
