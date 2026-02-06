# 反应Bug装甲板（Reactive Bug Plate）
> "A plate that 'patches out' arthropod bites on contact."

## 定位
重甲胸甲，针对节肢类敌人的专项物理减伤。

## 不变量
- 部位：CHEST（CHEST_ONLY）
- 防火：是
- 铁砧修复：不可
- 击退抗性：0
- 特效：节肢类近战伤害 flat -1.0

## 效果口径
- 穿戴于胸部槽位时，受到**节肢类实体的近战物理伤害**时，最终伤害 -1.0（clamp >= 0）
- "节肢类"定义：Spider, Cave Spider, Endermite, Silverfish, Bee
- "近战物理"定义：排除 projectile、magic、thorns、fire、explosion 等非直接近战来源
- 作用范围：SERVER_ONLY

## 边界
- 仅 CHEST 槽位生效
- 仅服务端计算
- 减伤为 flat 值（不是百分比），clamp 后最低 0
- 不影响非节肢来源、非近战来源
