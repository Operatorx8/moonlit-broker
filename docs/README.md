# 神秘商人模组 - 文档索引

> Mysterious Merchant Mod - Documentation Hub

## 这是什么

一个 Minecraft Fabric 模组，添加神秘商人 NPC 及其专属装备系统（武器、盔甲），强调独特的触发机制和套装效果。

---

## 文档导航

| 文件 | 用途 | 何时查阅 |
|------|------|----------|
| [ROADMAP.md](./ROADMAP.md) | 阶段目标与里程碑 | 确认当前开发阶段 |
| [TODO.md](./TODO.md) | 全局待办（Now/Next/Later） | 每天开工前 |
| [DECISIONS.md](./DECISIONS.md) | 决策日志（ADR lite） | 想改架构前先看 |
| [CHANGELOG.md](./CHANGELOG.md) | 面向玩家的更新记录 | 发版时更新 |
| [TESTPLAN.md](./TESTPLAN.md) | 全局测试清单 | 发版前逐条验证 |
| [LOGGING.md](./LOGGING.md) | 日志规范 | 写任何日志前必读 |

---

## 架构文档

| 文件 | 内容 |
|------|------|
| [architecture/OVERVIEW.md](./architecture/OVERVIEW.md) | 模块地图、数据流、层级职责 |
| [architecture/EFFECT_PIPELINE.md](./architecture/EFFECT_PIPELINE.md) | 特效管线详解（触发→判定→应用） |

---

## 功能文档

### 盔甲系统 (Armor)

| 文件 | 内容 |
|------|------|
| [features/armor/ARMOR_SPEC.md](./features/armor/ARMOR_SPEC.md) | 定位、设计约束、效果规则 |
| [features/armor/ARMOR_PARAMS.md](./features/armor/ARMOR_PARAMS.md) | 纯数值表（护甲值、触发参数） |
| [features/armor/ARMOR_CHANGELOG.md](./features/armor/ARMOR_CHANGELOG.md) | 盔甲系统变更记录 |
| [features/armor/ARMOR_TESTPLAN.md](./features/armor/ARMOR_TESTPLAN.md) | 盔甲专属测试清单 |
| [features/armor/ARMOR_TODO.md](./features/armor/ARMOR_TODO.md) | 盔甲专属待办 |

---

## 审计文档

| 文件 | 内容 |
|------|------|
| [audit/AUDIT_1.21.1_DOWNGRADE.md](./audit/AUDIT_1.21.1_DOWNGRADE.md) | 版本降级审计 |
| [audit/AUDIT_1.21.1_POST_DOWNGRADE.md](./audit/AUDIT_1.21.1_POST_DOWNGRADE.md) | 降级后审计 |

---

## 新功能文档模板

添加新功能时，复制 `_templates/` 下的模板：

```bash
cp docs/_templates/FEATURE_SPEC_TEMPLATE.md docs/features/<name>/<NAME>_SPEC.md
cp docs/_templates/FEATURE_PARAMS_TEMPLATE.md docs/features/<name>/<NAME>_PARAMS.md
cp docs/_templates/FEATURE_TESTPLAN_TEMPLATE.md docs/features/<name>/<NAME>_TESTPLAN.md
```

---

## 日志约定

所有运行时日志必须遵循 [LOGGING.md](./LOGGING.md) 规范，核心原则：

- **INFO**：验收级（触发、应用、状态变化）
- **TRACE**：调试级（判定链、RNG、CD 细节）
- **WARN**：异常但不中断（配置缺失用默认值）

---

## 代码约定

- 配置驱动，不在物品类硬编码数值
- 使用固定 UUID 的 AttributeModifier（避免叠加 bug）
- 所有冷却使用统一的 CooldownManager
- Boss 实体需特殊判定分支
