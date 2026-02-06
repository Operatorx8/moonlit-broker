# 祝圣兜帽（Sanctified Hood）
> "Consecrated cloth that dulls hostile sorcery."

## 定位
轻甲头盔，牺牲物理防御换取魔法减伤。

## 不变量
- 部位：HEAD（HEAD_ONLY）
- 防火：是
- 铁砧修复：不可
- 击退抗性：0
- 韧性：0
- 特效：魔法伤害减免 15%

## 效果口径
- 穿戴于头部槽位时，受到的**魔法类伤害** × 0.85
- 作用范围：SERVER_ONLY
- damageType 判定方式：**白名单**（见 PARAMS）

## 边界
- 仅 HEAD 槽位生效
- 仅服务端计算
- 不影响非白名单内的伤害类型
- 不叠加（穿一件就是 15%，不存在多件叠加场景——头部只有一个槽）
