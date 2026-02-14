# DYEABLE_LEATHER_ARMOR_DEBUG_REPORT.md

## 背景

本次修复目标是“借用皮革模型的装备”在 1.21.1 下的染色与清洗一致性问题。  
涉及两类装备：

- 主盔甲（`ArmorItems` 注册）
- 过渡盔甲（`TransitionalArmorItems` 注册）

它们都使用 `DefaultDyedLeatherArmorItem` + 皮革可染色材质层。

---

## 问题现象

### 现象 1

- 装备可被染色（合成逻辑触发）
- 但染色后：
  - UI 图标不变
  - 穿戴外观异常（看起来退回默认皮革色）

### 现象 2

- 可与有水炼药锅交互
- 每洗一次外观/图标会进一步“消失”
- 多次后几乎完全隐形（功能仍在）

---

## 定位过程

### 1) 先确认服务端数据链路

确认以下链路存在：

- `minecraft:item/dyeable` 标签已包含目标装备
- 染色通过 `DyedColorComponent.setColor(...)`
- 清洗通过移除 `DataComponentTypes.DYED_COLOR`

结论：服务端“染/洗数据”链路本身可达。

### 2) 排查客户端图标着色

检查 `ItemColorProvider` 后发现两个关键点：

- 自定义装备需要显式注册颜色提供器，否则图标不会按 `DYED_COLOR` 变色
- fallback 颜色被写成 `0xA06540`（无 alpha），在颜色计算路径里会导致透明渲染

这直接解释了“清洗后逐步隐形”的现象：  
清洗移除 `DYED_COLOR` 后走 fallback，而 fallback 的 alpha 为 0。

### 3) 排查默认色注入时机

原实现把默认色写在 `getDefaultStack()`。  
这只覆盖部分来源（例如创造栏/某些默认栈），不覆盖全部 `new ItemStack(item)` 场景（交易产物、掉落、命令等）。

结论：默认色注入点过窄，导致很多堆栈没有初始 `DYED_COLOR`。

### 4) 排查炼药锅行为注册

在 1.21.1 下，仅在 `dyeable` 标签里声明不等于自动具备水炼药锅清洗行为。  
需要把物品绑定到 `CauldronBehavior.WATER_CAULDRON_BEHAVIOR` 的映射中。

---

## 修复方案

### 修复 A：默认染色组件改为“物品默认组件”

文件：`src/main/java/dev/xqanzd/moonlitbroker/armor/item/DefaultDyedLeatherArmorItem.java`

- 删除 `getDefaultStack()` 注入路径
- 改为在构造时通过 `settings.component(DataComponentTypes.DYED_COLOR, ...)` 写入默认组件

效果：

- 所有来源的新堆栈都携带默认颜色
- 不再依赖调用方是否使用 `getDefaultStack()`

### 修复 B：统一注册客户端颜色提供器

文件：`src/client/java/dev/xqanzd/moonlitbroker/client/registry/ArmorItemColorProviders.java`

- 自动扫描并注册所有 `DefaultDyedLeatherArmorItem`（限定本模组命名空间）
- 着色逻辑：
  - `tintIndex == 0`：读取 `DyedColorComponent.getColor(...)`
  - 其他层：`-1`（不染色）
- fallback 改为 `0xFFA06540`（补全 alpha）

效果：

- UI 图标和染色状态一致
- 清洗后不会因透明 fallback 导致图标/外观“隐形”

### 修复 C：客户端注册时机兜底

文件：`src/client/java/dev/xqanzd/moonlitbroker/client/MymodtestClient.java`

- 在 `onInitializeClient()` 注册一次
- 在 `CLIENT_STARTED` 再兜底注册一次（内部幂等）

效果：

- 降低因初始化时序导致的 provider 漏挂风险

### 修复 D：显式绑定炼药锅清洗行为

文件：

- `src/main/java/dev/xqanzd/moonlitbroker/armor/item/ArmorDyeSupport.java`
- `src/main/java/dev/xqanzd/moonlitbroker/Mymodtest.java`

做法：

- 启动时遍历本模组所有 `DefaultDyedLeatherArmorItem`
- 注册到 `CauldronBehavior.WATER_CAULDRON_BEHAVIOR.map()`，行为使用 `CLEAN_DYEABLE_ITEM`

效果：

- 清洗行为与 vanilla 皮革装备一致
- 清洗后外观与 UI 图标同步变化

---

## 验证结果

### 功能验证

- 工作台染色后：
  - 图标颜色变化正常
  - 穿戴外观变化正常
- 水炼药锅清洗后：
  - 染色被移除
  - 图标与外观恢复正常，不再出现逐步隐形

### 构建验证

```bash
./gradlew compileJava compileClientJava --rerun-tasks --no-daemon
```

---

## 开源经验总结

1. `DataComponent` 时代，默认值应尽量放在 `Item.Settings.component(...)`，不要只靠 `getDefaultStack()`。
2. 自定义可染色物品必须补齐客户端 `ItemColorProvider`，否则“数据改了但图不变”。
3. 颜色常量要特别注意 alpha 位，`0xRRGGBB` 在某些路径会被当作透明色。
4. 炼药锅行为在新版中是“显式映射注册”，不是“标签自动继承”。
