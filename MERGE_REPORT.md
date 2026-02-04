# 项目合并报告 - MysteriousMerchant

## 概述

将 `MyModTest`（神秘商人系统）和 `Katana`（太刀武器系统）合并为单一 Fabric mod。

**合并日期**: 2026-02-02  
**最终 Mod ID**: `mymodtest`  
**最终项目路径**: `~/IdeaProjects/MysteriousMerchant`

---

## 1. 目录迁移

### 源项目
| 项目 | 原路径 |
|------|--------|
| MyModTest | `~/IdeaProjects/MyModTest` |
| Katana | `~/IdeaProjects/Katana` |

### 迁移后结构
```
MysteriousMerchant/
├── src/main/java/mod/test/mymodtest/
│   ├── Mymodtest.java                    # 主入口（已更新）
│   ├── entity/                           # 商人实体系统
│   ├── registry/                         # 商人物品注册
│   ├── world/                            # 持久化状态
│   └── katana/                           # [新增] Katana 子系统
│       ├── KatanaInit.java               # [新增] Katana 初始化入口
│       ├── item/
│       │   ├── KatanaItems.java          # 原 ModItems.java，已重命名
│       │   ├── MoonGlowKatanaItem.java
│       │   ├── RegretBladeItem.java
│       │   ├── EclipseBladeItem.java
│       │   ├── OblivionEdgeItem.java
│       │   └── NmapKatanaItem.java
│       ├── effect/
│       │   ├── MoonTraceHandler.java
│       │   ├── LifeCutHandler.java
│       │   ├── EclipseHandler.java
│       │   ├── OblivionHandler.java
│       │   └── nmap/
│       │       ├── NmapScanHandler.java
│       │       ├── NmapAttackHandler.java
│       │       └── ...
│       ├── sound/
│       │   └── ModSounds.java
│       └── mixin/
│           └── LivingEntityMixin.java
├── src/main/resources/assets/mymodtest/
│   ├── lang/
│   │   ├── en_us.json                    # 合并后的英文翻译
│   │   └── zh_cn.json                    # 合并后的中文翻译
│   ├── models/item/
│   │   ├── mysterious_coin.json
│   │   ├── moon_glow_katana.json         # [新增]
│   │   ├── regret_blade.json             # [新增]
│   │   ├── eclipse_blade.json            # [新增]
│   │   ├── oblivion_edge.json            # [新增]
│   │   └── nmap_katana.json              # [新增]
│   ├── sounds/                           # [新增] 音效文件
│   └── sounds.json                       # [新增] 音效配置
└── src/main/resources/
    ├── fabric.mod.json                   # 已更新
    ├── mymodtest.mixins.json
    └── mymodtest.katana.mixins.json      # [新增] Katana mixin 配置
```

---

## 2. 包名/引用变更

### Java 包名变更
| 原包名 | 新包名 |
|--------|--------|
| `katana.items.katana.item` | `mod.test.mymodtest.katana.item` |
| `katana.items.katana.effect` | `mod.test.mymodtest.katana.effect` |
| `katana.items.katana.effect.nmap` | `mod.test.mymodtest.katana.effect.nmap` |
| `katana.items.katana.mixin` | `mod.test.mymodtest.katana.mixin` |
| `katana.items.katana.client` | `mod.test.mymodtest.katana.client` |
| `katana.sound` | `mod.test.mymodtest.katana.sound` |

### 类名变更
| 原类名 | 新类名 | 原因 |
|--------|--------|------|
| `katana.items.katana.item.ModItems` | `mod.test.mymodtest.katana.item.KatanaItems` | 避免与商人 ModItems 冲突 |

### Registry ID 变更
| 原 ID | 新 ID |
|-------|-------|
| `katana:moon_glow_katana` | `mymodtest:moon_glow_katana` |
| `katana:regret_blade` | `mymodtest:regret_blade` |
| `katana:eclipse_blade` | `mymodtest:eclipse_blade` |
| `katana:oblivion_edge` | `mymodtest:oblivion_edge` |
| `katana:nmap_katana` | `mymodtest:nmap_katana` |
| `katana:moontrace.mark` (sound) | `mymodtest:moontrace.mark` |

### 翻译键变更
| 原键 | 新键 |
|------|------|
| `item.katana.*` | `item.mymodtest.*` |

---

## 3. 最终入口类

### 主入口
- **类**: `mod.test.mymodtest.Mymodtest`
- **职责**: 
  - 注册商人物品 (`ModItems.register()`)
  - 注册商人实体 (`ModEntities.register()`)
  - 初始化 Katana 子系统 (`KatanaInit.init()`)
  - 注册商人生成器 tick 事件

### Katana 子系统入口
- **类**: `mod.test.mymodtest.katana.KatanaInit`
- **职责**:
  - 注册太刀物品 (`KatanaItems.register()`)
  - 注册攻击事件处理器
  - 注册 tick 事件（效果清理、延迟伤害等）
  - 初始化音效

### 客户端入口
- **类**: `mod.test.mymodtest.client.MymodtestClient`

---

## 4. 商人交易整合

商人隐藏交易现在直接引用 `KatanaItems.MOON_GLOW_KATANA`：

```java
// MysteriousMerchantEntity.java
import mod.test.mymodtest.katana.item.KatanaItems;

private ItemStack resolveKatanaStack() {
    // 直接引用 KatanaItems.MOON_GLOW_KATANA，不再使用字符串查找
    return new ItemStack(KatanaItems.MOON_GLOW_KATANA, 1);
}
```

---

## 5. 发布 JAR

### 构建命令
```bash
cd ~/IdeaProjects/MysteriousMerchant
./gradlew clean build
```

### 输出文件
```
build/libs/MyModTest-1.0-SNAPSHOT.jar
```

### 发布步骤
1. 运行 `./gradlew clean build`
2. 从 `build/libs/` 获取 JAR 文件
3. 上传到 CurseForge/Modrinth

---

## 6. 验证步骤

### 启动游戏
```bash
cd ~/IdeaProjects/MysteriousMerchant
./gradlew runClient
```

### 测试命令
```
# 召唤商人
/summon mymodtest:mysterious_merchant

# 获取太刀
/give @s mymodtest:moon_glow_katana
/give @s mymodtest:regret_blade
/give @s mymodtest:eclipse_blade
/give @s mymodtest:oblivion_edge
/give @s mymodtest:nmap_katana

# 获取商人交易材料
/give @s mymodtest:mysterious_coin 10
/give @s mymodtest:sealed_ledger
/give @s mymodtest:sigil
/give @s emerald 256
```

### 验证清单
- [ ] 商人可召唤并显示交易界面
- [ ] 5 种太刀可通过 /give 获取
- [ ] 太刀攻击效果正常（月痕、残念等）
- [ ] 商人解锁系统正常（15 次交易后出现解封交易）
- [ ] 解锁后隐藏交易显示月之光芒太刀

---

## 7. 版本信息

| 组件 | 版本 |
|------|------|
| Minecraft | 1.21.1 |
| Fabric Loader | 0.15.11 |
| Fabric API | 0.116.8+1.21.1 |
| Yarn Mappings | 1.21.1+build.3 |
| Java | 21 |

---

## 8. 注意事项

1. **Mod ID 统一**: 所有物品、实体、音效都使用 `mymodtest` 命名空间
2. **旧存档兼容**: 使用旧版 `katana:*` ID 的物品将无法识别
3. **Mixin 配置**: Katana 的 mixin 使用独立配置文件 `mymodtest.katana.mixins.json`
4. **KatanaItems vs ModItems**: 太刀物品在 `KatanaItems` 类中，商人物品在 `ModItems` 类中
