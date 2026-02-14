# Bounty 机制完整说明（当前代码基线）

> 检查范围：`src/main/java` + `src/client/java` 当前实现。  
> 本文描述的是“现在实际会发生什么”，不是历史设计稿。

## 1. 总览

当前 Bounty 实现分为两条链路：

1. 主链路（当前玩家实际流程）
   - 怪物击杀概率掉落悬赏契约
   - 击杀指定目标累计契约进度
   - 手持完成契约（非潜行）右键神秘商人提交
   - 发放卷轴/银币/概率硬币奖励
2. 遗留链路（兼容保留）
   - 通过旧接口提交 `Zombie Head + Skeleton Skull`
   - 仍可调用，但默认客户端界面没有该入口

## 2. 注册与触发入口

- 模组初始化注册：
  - `BountyProgressHandler.register()`（击杀进度）
  - `BountyDropHandler.register()`（契约掉落）
  - `BountySubmitCommand.register()`（调试发奖命令）
  - `BountyContractCommand.register()`（调试发契约命令）
- 核心入口文件：`src/main/java/dev/xqanzd/moonlitbroker/Mymodtest.java`

## 3. 主链路 A：契约掉落

实现：`src/main/java/dev/xqanzd/moonlitbroker/trade/loot/BountyDropHandler.java`

事件：`ServerLivingEntityEvents.AFTER_DEATH`

### 3.1 掉落前置条件（必须全部满足）

1. 击杀者是玩家（`ServerPlayerEntity`）
2. 被击杀生物在目标池内
3. 玩家持有“有效商人印记”（`MerchantMarkItem.playerHasValidMark`）
   - 必须是绑定到该玩家 UUID 的印记
4. 玩家主背包/副手中没有任何 `BOUNTY_CONTRACT`
5. 概率判定通过：`roll < 0.005`（0.5%）

### 3.2 目标池与需求数量

| 目标实体 | required 随机区间 |
| --- | --- |
| `minecraft:zombie` | 3-6 |
| `minecraft:skeleton` | 3-6 |
| `minecraft:spider` | 4-7 |
| `minecraft:creeper` | 3-5 |
| `minecraft:enderman` | 2-4 |

通过后会创建 `BOUNTY_CONTRACT` 并初始化 NBT，然后以掉落物形式落地。

## 4. 主链路 B：契约进度累计

实现：`src/main/java/dev/xqanzd/moonlitbroker/trade/loot/BountyProgressHandler.java`

事件：`ServerLivingEntityEvents.AFTER_DEATH`

流程：

1. 只处理玩家击杀
2. 扫描玩家 `main` 背包中的契约（不扫描 `offHand`）
3. 跳过无效契约、已完成契约
4. 目标匹配则进度 `+1`
5. 达到 `required` 时写入 `BountyCompleted=true`
6. 首次完成时提示“悬赏完成！可以右键商人提交契约”
7. 每次击杀只更新第一张匹配契约（`break`）

## 5. 主链路 C：提交契约给商人

入口：`MysteriousMerchantEntity.interactMob`

触发条件：

- 玩家手持 `BOUNTY_CONTRACT`
- 玩家不是潜行状态
- 商人当前未被其他玩家占用（`!hasCustomer()`）

提交实现：`handleBountyContractSubmit`

校验与行为：

1. 必须是有效契约（物品类型正确且目标非空）
2. 必须“严格完成”：
   - `BountyCompleted == true`
   - `progress >= required`
   - `required > 0`
3. 提交顺序是“先发奖励，再消耗契约 1 张”
4. 成功后发送成功提示与日志
5. 异常时不消耗契约，返回错误提示

## 6. 奖励发放细则（`BountyHandler.grantRewards`）

实现：`src/main/java/dev/xqanzd/moonlitbroker/trade/loot/BountyHandler.java`

### 6.1 固定奖励

1. `Trade Scroll x1`
   - 初始化为 `GRADE_NORMAL`
   - 默认可用次数来自 `TradeConfig.SCROLL_USES_NORMAL`（当前为 3）
2. `Silver Note x BOUNTY_SILVER_REWARD`
   - `TradeConfig.BOUNTY_SILVER_REWARD = 3`

### 6.2 概率奖励：Mysterious Coin

- 冷却键：按玩家维度记录 `lastCoinBountyTick`
- 冷却时长：`TradeConfig.COIN_BOUNTY_CD_TICKS = 48000`（2 MC 天）
- 触发策略：按“尝试”冷却
  - 只要进入本次 roll 流程，就先写入 `lastCoinBountyTick = now`
  - 再进行概率判定
- 概率：`TradeConfig.COIN_BOUNTY_CHANCE = 0.15`（15%）
- 命中后发放 `Mysterious Coin x1`

### 6.3 额外保障奖励：Merchant Mark

若玩家当前没有“有效商人印记”，会额外补发 1 个并绑定到该玩家。

### 6.4 背包满行为

任何奖励无法放入背包时会掉落在玩家脚下，并提示“部分奖励掉落在脚下”。

## 7. 数据结构与持久化

### 7.1 契约 NBT（`BountyContractItem`）

- `BountyTarget`：目标实体 ID（字符串）
- `BountyRequired`：需求击杀数
- `BountyProgress`：当前进度
- `BountyCompleted`：完成布尔值

### 7.2 Coin 冷却持久化（`MerchantUnlockState.Progress`）

- 字段：`lastCoinBountyTick`
- 存档键：`LastCoinBountyTick`
- 在 NBT 读写中均已落盘/回读，因此重启后冷却不会丢失

## 8. 遗留链路：2 物品直接提交（保留）

接口：`BountyHandler.trySubmitBounty`

规则：

1. 玩家背包（主背包+副手）需满足：
   - `Zombie Head x1`
   - `Skeleton Skull x1`
2. 通过后执行原子扣除（含回滚兜底）
3. 奖励仍复用 `grantRewards`

服务端入口：

- `TradeAction.SUBMIT_BOUNTY` -> `TradeActionHandler` -> `BountyHandler.trySubmitBounty`

现状说明：

- 当前客户端主界面未看到发送 `SUBMIT_BOUNTY` 的按钮逻辑，默认玩法不走这条链路。

## 9. 调试/测试命令

1. `/mm_bounty_contract`
   - OP 可用
   - 给自己一张随机契约（命令内目标池：zombie/skeleton/spider/creeper）
2. `/mm_bounty_submit`
   - OP 可用
   - 直接发放一轮 Bounty 奖励（不校验契约进度）

## 10. 行为细节与边界结论

1. 契约掉落要求玩家有绑定到自己的 Merchant Mark；没有印记就不会掉契约。
2. `BountyProgressHandler` 只扫主背包，不扫副手；副手单独持有契约不会涨进度。
3. 掉落门禁只看“是否存在 BOUNTY_CONTRACT 物品”，不区分契约是否有效/完成。
4. 提交契约需要“非潜行右键”；潜行时不会走提交逻辑。
5. 提交采用“先奖励后消耗契约”的原子顺序，降低误吞契约风险。
