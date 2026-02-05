# Bounty Submit Schema

## Overview
轻量级悬赏提交系统，无需任务UI，仅需2个物品输入。

## Submit Flow
1. 玩家手持指定物品右键商人
2. 服务端验证物品
3. 原子性移除物品
4. 发放奖励

## Input Requirements
| Slot | Item | Count |
|------|------|-------|
| Main Hand | Bounty Item A | 1 |
| Off Hand | Bounty Item B | 1 |

## Bounty Items (Configurable)
- Zombie Head + Skeleton Skull
- Blaze Rod + Ender Pearl
- Ghast Tear + Magma Cream

## Output Rewards
| Reward | Amount |
|--------|--------|
| Trade Scroll (NORMAL) | 1 |
| Silver Note | 3 |

## Validation Rules
1. Both items must be present
2. Both items must match expected types
3. Player must be interacting with valid merchant

## Anti-Abuse
- Optional per-player cooldown (default: disabled)
- Server-side validation only
- Atomic item removal (all or nothing)

## Error Messages
| Condition | Message |
|-----------|---------|
| Missing item A | "需要 [物品A] 才能提交悬赏" |
| Missing item B | "需要 [物品B] 才能提交悬赏" |
| On cooldown | "悬赏提交冷却中，请稍后再试" |
| Success | "悬赏已提交！获得交易卷轴和银币" |
