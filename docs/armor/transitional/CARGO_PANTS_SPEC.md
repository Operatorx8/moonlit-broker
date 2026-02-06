# 多袋工装裤（Cargo Pants）
> "Deep pockets, lucky finds—keep the lights burning."

## 定位
LEGS 槽位工具型护甲，牺牲韧性换取资源效率。

## 不变量
- 部位：LEGS（LEGS_ONLY）
- 防火：是
- 铁砧修复：不可
- 击退抗性：0
- 韧性：0.0
- 特效：放置 minecraft:torch 时 15% 概率返还

## 效果口径
- 仅对 minecraft:torch 放置成功生效（不含 soul_torch / wall_torch / redstone_torch）
- 概率：15%（CARGO_TORCH_SAVE_CHANCE）
- 命中：返还 1 个火把到玩家手持 stack（抵消系统扣除）
- CD：200 ticks（10s），per-player
- 创造模式不触发
- SERVER_ONLY

## 边界
- 仅 LEGS 槽位
- 仅服务端
- 不影响非 torch 方块放置
- CD 期间概率判定直接跳过（不 roll）
