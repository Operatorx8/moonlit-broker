— Coin 经济规则落地（Moonlit Broker / 1.21.1 Fabric）

目标：让 Mysterious Coin 成为“稀罕物门票 / 可选捷径”，不再卡进度；并保证无套利闭环、无刷币路径、多人服可流通。

---

## 0. 硬规则（必须满足）
1) 任何 release/非 debug 情况下：禁止出现 “Silver/Emerald -> Coin” 或 “Coin 作为输出” 的交易。
2) Coin 来源只保留：
   - 高级结构箱（不可重复开启）
   - Bounty 小概率（per-player 冷却 2 MC 日 = 48000 ticks，按“尝试”冷却，不是按“成功”冷却）
   精英怪掉落渠道：删除/禁用。
3) Katana 成本：二选一（OR，不叠加）
   - 1 Coin OR 16 Silver Note
   语义：Coin 是捷径，不是门槛。
4) Reclaim/补发：绝不收 Coin（只走 Silver/Sacrifice 等现有成本）。
5) 供奉（右键交互）：保留 buff + 额外给 1 Trade Scroll
   - 服务端处理
   - 20 ticks 连点冷却（runtime-only，不持久化）
   - give 失败则 drop
   -“Coin->Sealed Ledger” 的 maxUses 与出现位置：
它最好固定在稀罕柜台（每商人 1 条），maxUses=1~2；否则玩家会把 coin 的第一用途永远锁死在 ledger，上手体验会变得单一。

---

## 1. 需要改哪些文件（最小改动）
A) Coin 掉落来源
- 找到你现在 Coin 的掉落/发放逻辑（MobDropHandler / LootTableEvents / Bounty 奖励发放点）
- 删除/禁用 elite mob 掉 Coin 的分支（不改 isEliteMob 判定也行，但 coin 不走这个渠道）

B) Bounty 发 Coin（新增 per-player 冷却）
- 在 MerchantUnlockState.Progress 增加字段：
  - long lastCoinBountyTick
- NBT 持久化读写（遵循现有字段风格）
- 在 Bounty 发奖逻辑里：
  - now = serverWorld.getTime()
  - if now - lastCoinBountyTick >= COIN_BOUNTY_CD_TICKS:
      - lastCoinBountyTick = now   // 注意：按“尝试”写入，避免刷到成功为止
      - roll 小概率给 1 coin（概率由现有掉落概率配置风格决定，建议 10%~20%）
      - give 失败则 drop
  - else: 不 roll，不给 coin

C) 高级结构箱注入 Coin
- 使用 Fabric LootTableEvents.MODIFY（或你项目里现有的 loot 注入方式）
- 目标 loot table（先按这些 identifier 注入；若你项目已有日志/调试输出，进入游戏确认命中）：
  - minecraft:chests/stronghold_library
  - minecraft:chests/ancient_city
  - minecraft:chests/trial_chambers/*（按你当前 MC 1.21.1 实际存在的 table id 覆盖：common/rare/supply 等；实现上可以用 startsWith("minecraft:chests/trial_chambers") 做前缀匹配）
- 注入策略（建议）：
  - stronghold_library：小概率 1 coin（中等）
  - ancient_city：小概率 1 coin（偏低）
  - trial_chambers：小概率 1 coin（中等偏低）
- 重要：Coin 不可“稳定获得”，保持稀有与仪式感。

D) 交易：Katana 成本二选一（UI 清晰 + 不被去重误杀）
实现方案：保留两条交易（coin-route 与 silver-route），并修改去重逻辑保证两条都能出现。

1) 在构建 katana offers 的位置（createKatanaOffer / addKatanaHiddenOffers / createKatanaOffer(player)）：
   - 为同一把 katana 生成两条 offer：
     - Offer K-COIN: 输入包含 1 Coin（以及你原本的其他成本项）
     - Offer K-SILVER: 输入包含 16 Silver Note（以及同样的其他成本项）
   - 两条 offer 的 maxUses 与既有 ownership/全局唯一规则保持一致（通常 maxUses=1）。
   - Reclaim offer 不收 Coin（确认其 cost builder 不包含 coin）。

2) 修正 “sell item 去重” 对 katana 的误杀：
   - 你当前有按 sell item 的 loop guard（避免重复注入）。
   - 对 katana sell item：放开 sell-item 去重（或改成用 exactOfferKey 作为 key）。
   - 推荐实现：
     - if (sellItem is KatanaItem OR in tag moonlitbroker:katana):
         use exactOfferKey for loop-guard key
       else:
         use existing sellItem key strategy
   - 末端 exactOfferKey 去重保留（已包含 maxUses/merchantXp/priceMultiplier 等），避免真正重复配方出现两份。

E) 交易：Coin -> Sealed Ledger
- 保留现有条目：1 Coin -> 1 Sealed Ledger
- 放在 “Relic Shelf（稀罕柜台）” 段（每商人最多 1~2 条 coin 相关交易）
- maxUses 建议 1~2（按 refresh 或按商人实例，遵循你现有体系）

F) 供奉交互：buff + Scroll + 防连点
- 在 handleMysteriousCoinInteraction（或等价函数）里：
  - server side guard
  - runtime 字段 long lastCoinOfferTick（不进 NBT）
  - if (now - lastCoinOfferTick < 20) return PASS/FAIL（不要重复消耗）
  - consume coin
  - apply buff（保留）
  - grant Trade Scroll x1（give else drop）
  - lastCoinOfferTick = now

G) release 自检（防经济炸）
- 你已有构建后扫描 “禁止输出钻石/下界合金系” 的逻辑。
- 扩展：
  1) forbidden outputs 增加 Coin（以及任何能“稳定回流价值”的票据类，如果你有）
  2) 扫描交易输入：禁止出现 “Silver/Emerald -> Coin”
- 行为：
  - 发现违规则：不注入该 offer + warn（或直接 fail-fast，按你现有策略）

---

## 2. 常量建议
- TradeConfig：
  - public static final long COIN_BOUNTY_CD_TICKS = 48000L;
  - public static final int KATANA_ALT_SILVER_COST = 16;
  - public static final int COIN_OFFER_CD_TICKS = 20;

---

## 3. 验证清单（代码阶段先挡住低级错）
1) ./gradlew compileJava
2) ./gradlew runClient
3) 进游戏，用 spawn egg 反复刷同一 variant 商人：
   - 未解锁 Arcane：检查 Page1/Page2 是否稳定显示（无空洞/无重复异常）
   - 解锁 Arcane 后：检查 Page3
4) 专测 katana：
   - 同一把 katana 是否出现两条路线（Coin / 16 Silver）
   - ownership 是否阻止重复拿取（即使两条都在）
   - reclaim 不收 coin
5) 专测供奉：
   - 连点 20 ticks 内不重复消耗 coin
   - 背包满：scroll 会掉地
6) 专测 bounty coin：
   - 连续提交 bounty：2 MC 日内只 roll 一次（按尝试）
7) 观察日志：
   - release 自检无报错
   - 无 “Silver/Emerald -> Coin” 交易被注入

交付要求：提交涉及文件列表 + 改动摘要 + 通过 compileJava + runClient
