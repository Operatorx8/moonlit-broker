# OVERVIEW.md - 架构概览

## 模块结构

```
mymodtest/
├── Mymodtest.java           # 主入口
├── entity/
│   ├── MysteriousMerchantEntity.java
│   ├── data/PlayerTradeData.java
│   ├── ai/                  # AI 行为
│   └── spawn/               # 生成器
├── registry/
│   ├── ModItems.java        # 物品注册
│   ├── ModBlocks.java       # 方块注册
│   └── ModEntities.java     # 实体注册
├── katana/                  # 太刀子系统
│   ├── KatanaInit.java
│   ├── item/                # 太刀物品
│   ├── effect/              # 效果处理器
│   ├── mixin/               # Mixin 注入
│   └── sound/               # 音效
├── armor/                   # 盔甲子系统
│   ├── ArmorInit.java
│   ├── item/                # 盔甲物品
│   ├── effect/              # 效果处理器
│   └── util/                # 工具类
└── world/
    └── MerchantSpawnerState.java
```

## 数据流

```
Event (Attack/Damage/Tick)
    │
    ▼
Handler (shouldTrigger)
    │
    ▼
CooldownManager (isReady)
    │
    ▼
Effect (apply)
    │
    ▼
Logger (INFO)
```

## 关键约定

1. **配置驱动**：数值在 Config 类，不硬编码
2. **固定 UUID**：AttributeModifier 使用固定 UUID
3. **统一冷却**：CooldownManager 管理所有 CD
4. **日志规范**：遵循 LOGGING.md
