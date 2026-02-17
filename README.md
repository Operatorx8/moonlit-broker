# 🌙 Moonlit Broker（月下掮客）

**Languages:** [English](#english) | [简体中文](#简体中文)

> **Explore & Fight → Earn Resources → Trade in Village → Get Stronger → Go Deeper**  
> **战斗与探索 → 资源 → 回村交易 → 变强 → 更深的地方**

A progression-focused RPG mod built around a single, clear feedback loop.  
Hunt mobs, collect currency, return to the village, trade with the Moonlit Broker — get strong enough to go further.

---

## English

### ✨ Features

- **5 Legendary Katanas** — Contract-bound weapons, each with a unique combat effect. Lose it? Reclaim it.
- **Moonlit Broker NPC** — A mysterious merchant who visits villages. Find him naturally, or summon him yourself.
- **Bounty System** — Glowing contract drops, milestone progress (25/50/75/100%), actionbar + chat feedback.
- **4-Tier Currency Chain** — Silver Notes, Trade Scrolls, Bounty Contracts, Mysterious Coins. Each has one clear job.
- **Tiered Trading** — Fixed Top trades + random refreshable Shelf + rare Spark surprises.
- **Multiplayer Friendly** — One broker per dimension; farewell mechanic lets players release the slot cleanly.

---

### 🔄 Gameplay Loop

```text
Explore & Fight → Drop Resources → Return to Village → Trade with Broker → Get Stronger → Go Deeper
Every system exists to serve this loop. Nothing requires you to break it.

⚡ Quick Start
Find a village — Use it as your home base.

Fight nearby mobs — Accumulate Silver Notes and Trade Scrolls. Bounty Contracts may drop too.

Meet the Moonlit Broker — He spawns naturally near villages. First meeting grants a Merchant Mark and a Guide book.

Trade Silver Notes for gear — Get stronger faster.

Complete Bounties — Turn contracts in for rewards, including a guaranteed Mysterious Coin on your first completion.

Explore structures — Strongholds, Ancient Cities, Trial Chambers, and more. Structure chests can contain Mysterious Coins.

Unlock Legendary Katanas — Use the coin route to claim contract-bound weapons.

💰 Items & Currencies
Item	Source	Used For
Silver Note（银票）	Most mob drops; elites drop more	Core currency, most trades
Trade Scroll（交易卷轴）	Trading & limited drops	Refresh merchant shelf; farewell ritual
Bounty Contract（悬赏契约）	Specific mobs (see Guide for tag list)	Submit to Broker for rewards
Mysterious Coin（神秘硬币）	Structure chests / bounty rewards	High-tier trades, katana routes

⚠️ Mysterious Coins cannot be obtained through trading. This is intentional — multiple safeguards enforce it.

🏪 The Moonlit Broker
Natural Spawning
The Broker only appears near villages (structure detection enforced).

Early on, he tries harder to find you (Bootstrap phase) so your first meeting isn't too late.

After you've met him once, he settles into a regular visit rhythm.

Rain increases his appearance chance — atmosphere bonus, not a hard requirement.

One Broker per dimension at a time. If one is active somewhere, another won't spawn until the slot is released.

Summoning Ritual
Once you have a Merchant Mark, you can summon the Broker:

Go to a village Bell (must be within village bounds).

Hold your Merchant Mark and right-click the Bell.

Confirm the ritual — costs 3 Silver Notes, has a personal cooldown and a global cooldown.

The Broker arrives around dusk.

Summoning doesn’t require rain. It’s your tool for controlling pacing.

Merchant Mark
Received automatically on your first interaction with the Broker, along with a Guide book.

Lost your Mark? The Broker replaces it once for free (after unlock). After that, buy a replacement via trading.

Progress is tied to your player unlock state, not the item. Losing the Mark won’t lock you out.

🛒 Trading Rules
Shelf refreshing
Spend a Trade Scroll to randomize the Shelf trades on normal pages.

Hidden / Secret pages cannot be refreshed. If you try, nothing is consumed and you’ll receive a clear notification.

Refreshing does not affect fixed Top trades or the current Spark item.

Economy limits
Each trade has a use limit (maxUses). Once exhausted, that slot is locked for this visit.

Mysterious Coins cannot be minted via trading.

Legendary Katanas cannot be purchased twice. Ownership is tracked. If you already own one, the slot will direct you to Reclaim instead.

⚔️ Legendary Katanas（传说太刀）
The five katanas are contract-bound:

Name	—
月之光芒	Moon's Radiance
残念之刃	Blade of Lingering Regret
暗月之蚀	Dark Moon Eclipse
窃念之黯	Shadow of Stolen Thoughts
先觉之谕	Oracle's Foresight

Why might my katana not work?

Unowned warning — You’re holding a katana you didn’t purchase or reclaim. Go to the Broker and use Reclaim to bind it.

Dormant / contract mismatch — The item instance doesn’t match your contract (creative, commands, another player). Reclaim it. Reminder messages have a short cooldown to avoid spam.

Lost your katana? Use the Broker’s Reclaim trade. You won’t need to pay full price again.

🎯 Bounty System
Getting contracts
Certain mobs drop Bounty Contracts (see docs/guide.md for the mob tag list).

On drop: actionbar notification + sound + glowing item entity (until pickup or despawn).

Tracking progress
Milestones at 25% / 50% / 75% (actionbar).

On completion (100%): actionbar + chat confirms submission is ready.

Submitting
Turn the contract in at the Broker for rewards (scrolls/gear/coin rolls).

First bounty completion guarantees 1 Mysterious Coin — so the economy can start even with bad luck.

👥 Multiplayer
One Broker serves the whole server per dimension — this is by design.

Farewell mechanic — release the active slot cleanly:

Hold a Trade Scroll

Sneak + right-click the Broker

Repeat within 3 seconds to confirm

Costs 1 Trade Scroll (free in Creative, but still advances global cooldown)

Broker despawns via cleanup chain; active lock releases; cooldown advances

After cooldown, a new Broker can spawn naturally or be summoned.

❓ FAQ / Troubleshooting
“Why won’t the scroll refresh this page?”
You’re on a Hidden/Secret page. These pages are intentionally locked from refreshing. No scroll is consumed.

“My katana does nothing / gives a warning.”
It’s unowned or dormant. Find the Broker and use Reclaim to bind it to yourself.

“I lost my Merchant Mark.”
The Broker replaces it once for free after unlock. After that, buy a replacement through trading. Your progress is not lost.

“No Broker spawned / my summon did nothing.”
A Broker may already be active in your dimension. Only one exists at a time. Find him and use the farewell mechanic, or wait for the slot to clear, then summon again.

“Can I get Mysterious Coins from trading?”
No. Coins come from structure chests and bounty rewards only.

📋 Requirements
Version
Minecraft	1.21.1
Fabric Loader	0.15.11
Fabric API	0.116.8+1.21.1
Java	21

📦 Installation
Install Fabric Loader

Download Fabric API and place it in your mods/ folder

Download the 1.0.0 release .jar from Releases

Place it into .minecraft/mods/

Launch Minecraft with the Fabric profile

Tip: If you’re not sure which file to download, grab the one named like
xqanzd_moonlit_broker-1.0.0.jar (exact name depends on your build config).

📸 Screenshots
Recommended folder:

assets/screenshots/01-broker-ui.png

assets/screenshots/02-bounty-drop.png

assets/screenshots/03-bell-summon.png

Embed:

md
Copy code
![Trading UI](assets/screenshots/01-broker-ui.png)
![Bounty Drop Feedback](assets/screenshots/02-bounty-drop.png)
📖 Documentation
Guide（游戏指南） — Full gameplay rules, trade routes, katana details

Known Issues — Current bugs and workarounds

Changelog — Version history

🐛 Reporting Issues
Open an Issue and include:

Minecraft + Fabric Loader + Fabric API versions

Steps to reproduce

.minecraft/logs/latest.log (or debug.log)

📄 License
MIT License — see LICENSE for details.

简体中文
✨ 特性
5 把传说太刀 — 契约绑定的武器，每把都有独特战斗特效。丢了？可以 Reclaim 补发。

月下掮客 NPC — 神秘商人会在村庄附近来访；你也可以主动召唤他。

悬赏系统 — 契约掉落发光、里程碑进度（25/50/75/100%）、actionbar + chat 双反馈。

四层货币链 — 银票 / 交易卷轴 / 悬赏契约 / 神秘硬币。每个道具职责清晰。

分层交易结构 — 固定 Top 交易 + 可刷新 Shelf 货架 + 稀有 Spark 惊喜位。

多人友好 — 每个维度同一时间仅一个掮客；送别机制可“干净释放名额”。

🔄 主循环
text
Copy code
战斗与探索 → 掉落资源 → 回村找掮客交易 → 变强 → 去更深的地方
所有系统都服务于这个循环。你不需要绕开它才能推进进度。

⚡ 快速上手
先找村庄 — 把村庄当作基地。

在村庄附近打怪 — 积累 银票 和 交易卷轴，同时可能掉落 悬赏契约。

首次遇见月下掮客 — 商人会在村庄附近自然出现；第一次会给你 商人印记（Merchant Mark） 和指南书。

用银票换装备 — 更快提升战力。

做悬赏 — 提交契约换奖励；第一次完成悬赏 必定获得 1 枚神秘硬币。

探索结构 — 要塞、远古城市、试炼密室等；结构箱子有概率出 神秘硬币。

解锁传说太刀 — 走硬币路线，获得契约绑定的太刀。

💰 物品与货币
物品	来源	用途
Silver Note（银票）	大多数怪物掉落；精英掉更多	主货币，绝大多数交易
Trade Scroll（交易卷轴）	交易与限量掉落	刷新货架；送别仪式
Bounty Contract（悬赏契约）	指定怪物掉落（见指南 tag 列表）	提交给掮客换奖励
Mysterious Coin（神秘硬币）	结构箱 / 悬赏奖励	高阶交易、太刀路线

⚠️ 神秘硬币无法通过交易获得。这是刻意设计，并由多层防线强制执行。

🏪 月下掮客
自然生成
掮客只会在 村庄附近出现（强制结构判定）。

前期会更积极地尝试出现（Bootstrap 阶段），保证你不会很晚才第一次遇到。

第一次解锁后，进入更稳定的来访节奏。

下雨会增加出现概率（气氛加成），不是硬门槛。

同一维度同一时间仅一个掮客：如果已有活跃掮客，就不会再刷新的。

召唤仪式
拿到 商人印记（Merchant Mark） 后，你可以召唤掮客：

到 村庄钟（Bell）（必须在村庄范围内）

手持 Merchant Mark 对钟 右键

确认仪式：消耗 3 银票，并具有 个人冷却 + 全局冷却

掮客会在 黄昏附近到来

召唤不需要下雨。它是你“掌控节奏”的工具。

商人印记（Merchant Mark）
第一次与掮客交互会自动获得（同时给指南书）。

丢了怎么办？ 解锁后掮客会 免费补发一次；之后需要通过交易购买补发。

进度绑定在 玩家解锁状态，不是印记本体；把印记放箱子也不会卡死进度。

🛒 交易规则
货架刷新
使用 交易卷轴 刷新普通页的 Shelf 货架位。

Hidden / Secret 页禁止刷新：尝试刷新时 不消耗卷轴，并会给出明确提示。

刷新不会影响固定 Top 位与当前 Spark 位。

经济阀门
每条交易都有使用次数上限（maxUses），用完即锁（本次来访）。

神秘硬币无法通过交易“铸币式产出”。

传说太刀无法重复购买：已拥有时，交易会引导你走 Reclaim。

⚔️ 传说太刀
五把太刀均为 契约绑定：

名称	英文
月之光芒 Moonlight
心殇之刃 Trauma
暗月之蚀 Eclipse Blade
窃念之黯 Oblivion Edge
先觉之谕	Nmap

为什么太刀不生效？

未拥有（Unowned）：你拿到的太刀不属于你（未购买/未补发）。去找掮客使用 Reclaim 绑定即可。

休眠/契约不匹配（Dormant）：物品实例与契约不一致（创造/指令/他人转交）。Reclaim 即可。提示有短冷却防刷屏。

太刀丢了？ 找掮客走 Reclaim，无需重新全价购买。

🎯 悬赏系统
契约掉落
指定怪物会掉落 悬赏契约（见 docs/guide.md 的 tag 列表）。

掉落时：actionbar 提示 + 音效 + 地面实体发光（直到拾取/消失）。

进度反馈
里程碑：25% / 50% / 75%（actionbar）。

完成（100%）：actionbar + chat 双提示，明确告诉你可提交。

提交奖励
把契约交给掮客换奖励（卷轴/装备/硬币概率等）。

第一次完成悬赏必定获得 1 枚神秘硬币，避免“运气差导致体系开不起来”。

👥 多人联机
同一维度同一时间仅一个掮客，这是设计选择。

送别机制：干净释放掮客名额（便于换节奏/换变体）

手持 交易卷轴

潜行 + 右键掮客

3 秒内再次执行确认

消耗 1 卷轴（创造模式免消耗，但仍推进全局冷却）

掮客走 despawn + cleanup 链路离开，释放 active lock，并推进冷却

冷却结束后，可自然刷新或再次召唤。

❓ 常见问题
“为什么卷轴不能刷新这个页面？”
你在 Hidden/Secret 页，这些页刻意禁止刷新。不会消耗卷轴。

“太刀没反应/提示警告。”
太刀未绑定或契约不匹配。去掮客处使用 Reclaim 绑定即可。

“我的商人印记丢了。”
解锁后可免费补发一次；再次丢失需要通过交易购买。你的进度不会丢。

“没有掮客出现/召唤没反应。”
可能该维度已有活跃掮客。同一时间只允许一个。找到他送别，或等待名额释放后再召唤。

“能不能通过交易刷神秘硬币？”
不能。硬币仅来自结构箱与悬赏奖励。

📋 运行环境
版本
Minecraft	1.21.1
Fabric Loader	0.15.11
Fabric API	0.116.8+1.21.1
Java	21

📦 安装
安装 Fabric Loader

下载 Fabric API 并放入 mods/ 文件夹

在 Releases 下载 1.0.0 的 .jar

把 .jar 放入 .minecraft/mods/

使用 Fabric 启动游戏

不确定下哪个文件就下载形如
xqanzd_moonlit_broker-1.0.0.jar 的那个（具体名字以你构建产物为准）。

📖 文档
Guide（游戏指南）

Known Issues

Changelog

🐛 Bug 反馈
请在 Issues 提交并附带：

MC / Loader / Fabric API 版本

复现步骤

.minecraft/logs/latest.log（或 debug.log）

📄 许可
MIT License — 见 LICENSE

Moonlit Broker is a first mod project. Thanks for playing.