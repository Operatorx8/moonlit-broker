# Reactive Bug Plate 参数表

## 基础属性
| 参数 | 值 |
|---|---|
| durability | 260 |
| defense | 6 |
| toughness | 1 |
| rarity | RARE |
| enchantability | BY_RARITY (12) |
| fireproof | true |
| anvilRepair | false |

## 逻辑参数
| 参数 | 值 | 说明 |
|---|---|---|
| FLAT_REDUCTION | 1.0 | 固定减伤值 |
| CLAMP_MIN | 0.0 | 最终伤害下限 |
| APPLY_SLOT | CHEST | 仅胸部槽位 |
| SERVER_ONLY | true | 仅服务端 |
| MELEE_ONLY | true | 排除非近战伤害源 |

## 攻击者过滤
节肢类实体判定：

```java
ARTHROPOD_TYPES = Set.of(
    EntityType.SPIDER,
    EntityType.CAVE_SPIDER,
    EntityType.ENDERMITE,
    EntityType.SILVERFISH,
    EntityType.BEE
);
```

## 伤害源过滤（MELEE_ONLY）
**排除**：projectile, explosion, fire, fall, drowning, freezing
**允许**：有攻击者的直接伤害
