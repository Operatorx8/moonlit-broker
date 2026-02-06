# 缓冲登山靴（Cushion Hiking Boots）
> "Shock-absorbent soles—the ground hits softer."

## 定位
FEET 槽位防摔专项，最终伤害链路上的 flat 减伤。

## 不变量
- 部位：FEET（FEET_ONLY）
- 防火：是
- 铁砧修复：不可
- 击退抗性：0
- 特效：最终摔落伤害 flat -2.0，clamp >= 0

## 效果口径
- 减伤发生在 LivingEntity.applyDamage 的 amount 参数上（@ModifyVariable argsOnly=true）
- 此时 amount 已经过护甲、附魔（含 Feather Falling）、抗性等全部计算
- 与 Feather Falling 可叠加（FF 先削，本效果再减）
- damageType 判定：DamageTypes.FALL
- SERVER_ONLY, FEET_ONLY

## 边界
- 仅 FEET 槽位
- 仅服务端
- 仅 fall 类型伤害
- clamp >= 0（不会"治疗"）
