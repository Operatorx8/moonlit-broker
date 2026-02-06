# Sanctified Hood 参数表

## 基础属性
| 参数 | 值 |
|---|---|
| durability | 165 |
| defense | 1 |
| toughness | 0 |
| rarity | RARE |
| enchantability | BY_RARITY (12) |
| fireproof | true |
| anvilRepair | false |

## 逻辑参数
| 参数 | 值 | 说明 |
|---|---|---|
| MAGIC_REDUCTION_MULT | 0.85 | 最终魔法伤害 = raw × 0.85 |
| APPLY_SLOT | HEAD | 仅头部槽位 |
| SERVER_ONLY | true | 仅服务端 |

## damageType 白名单
以下 damageType ID 被视为"魔法伤害"并触发减免：

| damageType ID | 来源示例 |
|---|---|
| `magic` | 瞬间伤害药水 |
| `indirect_magic` | 女巫投掷药水、滞留药水 |
| `sonic_boom` | Warden 音波 |
| `wither` | 凋零效果 |

不在白名单：
- `thorns` - 荆棘是物理反伤
