# Acer - 锐锋

> 暴击强化剑，提升暴击伤害乘数

## 定位

过渡层近战武器，专注于提升暴击效率。适合擅长跳劈的玩家。

## 不变量

- 防火：是
- 铁砧修复：否
- 主手持握才生效
- 仅对 LivingEntity 生效

## 效果描述

**[Keen Edge / 锋锐之刃]**

暴击伤害乘数从原版 1.5× 提升至 1.7×。

公式：`finalDamage = baseDamage × 1.7`（原版：`baseDamage × 1.5`）

### 触发条件

1. 主手持有 Acer
2. 目标为 LivingEntity
3. 满足原版暴击判定（跳起下落中、非骑乘、非水中、非攀爬、攻击冷却 > 0.9）

### 排除伤害源

- 荆棘 (thorns)
- 魔法 (magic)
- 火焰 (onFire, inFire, lava)
- 投射物 (arrow, trident, fireball, etc.)
