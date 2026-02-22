你是代码代理。请在 Moonlit Broker 项目中实现“Bounty 完成瞬间自动结算基础奖励；背包满则延迟且不 consume；Coin 仍然走 pending（见商人领取）”机制。要求：BUILD SUCCESSFUL（compileJava + compileClientJava + verifyI18nGuardrails），旧存档安全，防重复结算、防刷 Coin、防刷屏。

====================
目标行为（必须满足）
====================
A) 自动结算（无任何物品触发）
- 玩家持有 Bounty Contract 并在击杀事件中使进度达到 required 的“最后一击”时：
  1) 尝试结算基础奖励（Trade Scroll x1 + Silver Note x3）
  2) 如果背包可容纳两项奖励：发放奖励 -> roll Coin（写 pending）-> consume 契约 -> actionbar 成功提示
  3) 如果背包无法容纳两项奖励：不发放、不 roll Coin、不 consume 契约，只 actionbar 提示“背包已满，清理后自动结算”
- 后续重试：玩家下次击杀任何怪物时，如果背包里仍有“已完成但未结算”的契约，则再次尝试结算（无需再杀目标怪）。

B) Coin（概率/冷却/保底公式完全不变）
- Coin 的 roll 发生在“基础奖励成功发放”的那一刻（同一次结算里）。
- Coin 不直接给玩家：命中则 progress.pendingCoinReward += 1，提示“硬币暂存于商人处”
- Coin 领取在 MysteriousMerchantEntity.interactMob 开头、打开 UI 之前：
  - pendingCoinReward > 0 且背包可塞 coin -> 发放并扣减 pending
  - 背包满 -> 不发放、不扣减，提示“背包已满，硬币暂存”

C) 不掉地上
- 自动结算路径严禁 dropItem（因为可能在岩浆/虚空）。
- 商人面对面提交原逻辑可保持 dropItem（可选：不改），但本任务只改自动结算。

====================
需要改动的代码点（按文件）
====================

1) MerchantUnlockState.Progress：新增 pendingCoinReward（持久化）
文件：src/main/java/dev/xqanzd/moonlitbroker/world/MerchantUnlockState.java
- Progress 新增字段：int pendingCoinReward（default 0）
- NBT keys：PendingCoinReward
- getter/setter
- reset_mark 时清零（可选，但建议）

2) MysteriousMerchantEntity.interactMob：在任何 open/sync 前兑付 pendingCoinReward
文件：src/main/java/dev/xqanzd/moonlitbroker/entity/MysteriousMerchantEntity.java
- 在 interactMob() 开头（busy 判断/发 guide/mark 前即可）调用：
  tryPayoutPendingCoins(serverWorld, serverPlayer)
- 发放规则：
  - coinStack = ModItems.MYSTERIOUS_COIN x1（可堆叠）
  - 若 hasRoomFor(player, coinStack) false -> actionbar 提示一次（可限频），return（不扣 pending）
  - else insertStack 并确认 stack.isEmpty；pendingCoinReward--；state.markDirty()
  - 建议每次 interact 尽可能多发（while pending>0 && hasRoom）但不要刷屏（只提示一次即可）

3) 自动结算触发点：BountyProgressHandler（或你当前推进 progress 的击杀回调）
文件：你项目中更新契约进度的回调（你提到的 BountyProgressHandler / 或类似 onMobDeath 后续处理进度的地方）
要求：
- 在“进度+1”后，若已完成（progress >= required 且 !completed 标记或 completed==true）：
  调用 tryAutoSettleIfCompleted(world, player)
- 另外加一条“重试分支”：
  - 若发现玩家背包里存在 completed 契约（completed=true 且仍在背包）：
    即使本次击杀不是目标怪，也调用 tryAutoSettleIfCompleted(world, player)
  （重试只要在任意击杀回调里触发即可）

4) 结算函数：tryAutoSettleIfCompleted（严禁 drop，背包满则不 consume）
新建或放在 BountyHandler/BountyProgressHandler 内皆可（建议单独 helper）
逻辑（必须按顺序）：
- 从玩家背包扫描一张 Bounty Contract（理论上 maxCount=1，但做防御）
  - 必须 completed=true
  - 若没有 -> return
- 背包容量检查（关键）：
  - rewardScroll = Trade Scroll x1（可能不可堆叠）
  - rewardSilver = Silver Note x3（可堆叠）
  - hasRoomFor(player, rewardScroll) && hasRoomFor(player, rewardSilver) 必须都为 true
  - 否则：actionbar 红字提示（限频，避免每杀一只怪都刷屏），return
- 发放奖励（insertStack，两次）：
  - insertStack(scroll); insertStack(silver)
  - 若任意 stack 非 empty（理论上预检查后不应发生）：
    - LOGGER.error（带 player/freeSlots/leftover）
    - 不 consume 契约，不 roll Coin，return
- Coin roll（只在奖励成功后执行）：
  - 复用你现有 coin roll + 冷却 + 首次保底逻辑（抽成 helper，不改公式）
  - 若命中 coin：progress.pendingCoinReward++；state.markDirty(); 提示“硬币已暂存”
- consume 契约：
  - 从玩家背包精确移除这张契约（只移除 1 张）
- actionbar 成功提示：
  - “悬赏完成！获得 Silver Note×3 + Trade Scroll×1”
  - 若 coin 命中，再追加一条提示或同条拼接

5) 限频提示（必须）
- 对“背包已满，结算暂缓”的 actionbar 做 per-player 限频（建议 200 ticks / 10s 或 600 ticks / 30s）
- 可以用一个 static Map<UUID, Long> lastWarnTick（用 Overworld tick 口径）

6) hasRoomFor 工具（必须正确处理可堆叠物品）
- 实现 hasRoomFor(player, stack)：
  - 若存在空槽 -> true
  - 或存在同物品同NBT且 count+stack.count <= maxCount -> true
  - 否则 false
- Silver/Coin 都是可堆叠，要考虑“并入已有堆叠”的情况，而不是只看空槽
- TradeScroll 若不可堆叠则主要看空槽

7) 文案 keys（中英）
新增（建议）：
- actionbar.xqanzd_moonlit_broker.bounty.auto_complete
- actionbar.xqanzd_moonlit_broker.bounty.defer_inventory_full
- actionbar.xqanzd_moonlit_broker.bounty.coin_pending
- actionbar.xqanzd_moonlit_broker.coin.payout_deferred
- actionbar.xqanzd_moonlit_broker.coin.payout_received (可选)
文案要简短、非刷屏。

====================
验收用例（必须跑）
====================
1) 背包有空间：击杀最后一个目标 -> 契约消失 -> Scroll+Silver 到手 -> coin 若命中写 pending 并提示
2) 背包满（无空槽且无法合并）：击杀最后一个目标 -> 不 consume、不 roll coin、提示暂缓；清理背包后再杀任意怪 -> 自动结算成功
3) 连杀多怪：背包满提示不刷屏（限频生效）
4) pendingCoinReward：右键商人 -> 在 UI 打开前自动发 coin；背包满则不发、不扣 pending
5) 旧存档：pendingCoinReward 缺失默认 0；旧契约无新字段也能正常结算/提交
6) compileJava + compileClientJava + verifyI18nGuardrails 全通过

我再补两条“你现在这套最容易漏的坑”（建议你在 agent prompt 里也加一句）

Coin roll 必须只在“奖励成功发放后”执行。背包满时如果你先 roll 再 defer，会被刷出 pendingCoinReward（灾难）。

重试触发要限频提示：否则背包满的时候玩家刷怪=actionbar轰炸。

把“需要复用的 Coin roll 逻辑”抽取位置也建议一下（比如从商人提交的 coin 逻辑里抽一个 tryRollCoin(world, player, required, tier)），保证公式完全一致、不会出现“远程结算 coin 概率不一样”的隐患。