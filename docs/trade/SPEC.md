# Trade System Specification

## Overview
神秘商人交易系统，包含普通交易页和隐藏交易页，使用卷轴消耗机制和声望门槛。

## Core Items
| Item | ID | Purpose |
|------|-----|---------|
| Merchant Mark | `merchant_mark` | UUID绑定会员标识 |
| Trade Scroll | `trade_scroll` | 交易卷轴，NBT: uses(3/5), grade(NORMAL/SEALED) |
| Silver Note | `silver_note` | 货币 |
| Guide Scroll | `guide_scroll` | 首次见面赠送，仅信息展示 |

## Page System
- **Normal Page**: 基础交易
- **Secret Page**: 隐藏交易（需满足3个门槛）

## Secret Gate Requirements (ALL 3 required)
1. 持有 Merchant Mark
2. 持有 SEALED 等级的 Trade Scroll，且 `uses >= 2`
3. `reputation >= 15`

## Cost Rules
| Action | Cost |
|--------|------|
| Open Normal page | scroll uses -1 |
| Switch to Secret page | scroll uses -2 (only on success) |
| Refresh current page | scroll uses -1 |

## Reputation
- 仅在成功完成交易（take-result）时 +1
- 选择/预览不增加声望

## Secret Limit (Per Merchant Entity)
- 每个商人实体只能售出一把 epic katana
- `secret_sold` 和 `secret_katana_id` 持久化到实体 NBT

## Invariants
- `reputation >= 0`
- `scroll.uses >= 0`
- `scroll.grade` in {NORMAL, SEALED}
- 失败操作不消耗任何资源
- 所有验证在服务端执行
