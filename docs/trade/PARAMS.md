# Trade System Parameters

## Gate Thresholds
| Parameter | Value | Unit | Description |
|-----------|-------|------|-------------|
| SECRET_REP_THRESHOLD | 15 | int | 进入隐藏页所需声望 |
| SECRET_SCROLL_USES_MIN | 2 | int | 进入隐藏页所需卷轴次数 |

## Scroll Costs
| Parameter | Value | Unit | Description |
|-----------|-------|------|-------------|
| COST_OPEN_NORMAL | 1 | uses | 打开普通页消耗 |
| COST_SWITCH_SECRET | 2 | uses | 切换隐藏页消耗 |
| COST_REFRESH | 1 | uses | 刷新当前页消耗 |

## Scroll Initial Values
| Parameter | Value | Unit | Description |
|-----------|-------|------|-------------|
| SCROLL_USES_NORMAL | 3 | int | 普通卷轴初始次数 |
| SCROLL_USES_SEALED | 5 | int | 封印卷轴初始次数 |

## Drop Rates
| Parameter | Value | Unit | Description |
|-----------|-------|------|-------------|
| CHEST_SCROLL_CHANCE | 0.15 | float | 宝箱卷轴掉率 (15%) |
| MOB_SCROLL_CHANCE | 0.005 | float | 怪物卷轴掉率 (0.5%) |

## Silver Economy
| Parameter | Value | Unit | Description |
|-----------|-------|------|-------------|
| SILVER_MOB_DROP_CAP | 10 | int | 每窗口期最大银币掉落 |
| SILVER_CAP_WINDOW | 12000 | ticks | 银币限制窗口 (10分钟) |
| BOUNTY_SILVER_REWARD | 3 | int | 悬赏银币奖励 |

## Cooldowns
| Parameter | Value | Unit | Description |
|-----------|-------|------|-------------|
| PAGE_ACTION_COOLDOWN | 10 | ticks | 页面操作冷却 (0.5秒) |
| SECRET_REFRESH_COOLDOWN | 100 | ticks | 隐藏页刷新冷却 (5秒) |

## Chest Loot Targets
- `minecraft:chests/simple_dungeon`
- `minecraft:chests/abandoned_mineshaft`
- `minecraft:chests/stronghold_corridor`
- `minecraft:chests/stronghold_crossing`
- `minecraft:chests/stronghold_library`
- `minecraft:chests/shipwreck_treasure`
- `minecraft:chests/shipwreck_supply`
