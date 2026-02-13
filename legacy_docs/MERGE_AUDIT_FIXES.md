# MERGE_AUDIT_FIXES

## 审计结论概览
- 目标：清理合并残留/错配，保证 clean build + runClient 可启动。
- 范围：仅修复配置/资源/命名空间不一致与缺失项；不做设计/结构改动。

## A. fabric.mod.json

### 问题 1：entrypoints 指向不存在的类
- 命中：`src/main/resources/fabric.mod.json`
- 原因：`mod.test.mymodtest.client.MymodtestClient` 与 `mod.test.mymodtest.client.MymodtestDataGenerator` 在项目中不存在，Fabric 启动时会因找不到类而崩溃。
- 修复：移除 `client` 与 `fabric-datagen` entrypoints，仅保留 `main`。
- 为什么必须改：保证 runClient 不因缺失类而直接崩溃。

### 问题 2：mixins 列表包含不存在的配置文件
- 命中：`src/main/resources/fabric.mod.json`
- 原因：`mymodtest.client.mixins.json` 文件不存在，Fabric 解析 mixins 时会失败。
- 修复：移除该 client mixin 配置项，保留 `mymodtest.mixins.json` 与 `mymodtest.katana.mixins.json`。
- 为什么必须改：避免运行时 mixin 配置解析失败。

### 问题 3：icon 路径指向不存在资源
- 命中：`src/main/resources/fabric.mod.json`
- 原因：`assets/mymodtest/icon.png` 不存在。
- 修复：移除 `icon` 字段。
- 为什么必须改：避免启动时资源缺失警告/潜在资源加载错误。

## B. 全局残留扫描（输出命中位置）

搜索关键词：`katana:`、`assets/katana`、`item.katana`、`katana.items`、`katana.items.katana.*`
- 命中位置：
  - `MERGE_REPORT.md:77-102`（旧命名空间映射记录）
  - `MERGE_REPORT.md:87`（旧包名映射记录）
  - 代码中搜索无 `katana:`、`item.katana` 等残留（仅存在包名 `mod.test.mymodtest.katana.*`，为合并后新命名空间）
- 处理结论：
  - 命中仅存在于 `MERGE_REPORT.md`（历史记录），不需要改动代码/资源。

## C. Registry ID 与资源一致性

### 问题 4：注册的物品缺少对应 models/item JSON
- 命中：
  - 注册：`src/main/java/mod/test/mymodtest/registry/ModItems.java`（sealed_ledger / arcane_ledger / sigil）
  - 资源缺失：`assets/mymodtest/models/item/` 下无对应 JSON
- 修复：新增
  - `assets/mymodtest/models/item/sealed_ledger.json`
  - `assets/mymodtest/models/item/arcane_ledger.json`
  - `assets/mymodtest/models/item/sigil.json`
- 为什么必须改：保证所有注册的 Identifier 都有匹配的模型文件，避免资源查找不一致。

### 问题 5：mysterious_coin 模型引用不存在纹理
- 命中：`assets/mymodtest/models/item/mysterious_coin.json`
- 原因：`mymodtest:item/mysterious_coin` 纹理不存在，会显示缺失贴图。
- 修复：改用现有原版纹理 `minecraft:item/gold_nugget` 作为占位。
- 为什么必须改：避免运行时缺失纹理与资源错误日志。

### 问题 6：sounds.json 有 subtitle 但语言文件缺失 key
- 命中：
  - `assets/mymodtest/sounds.json` 使用 `subtitles.mymodtest.moontrace.mark`
  - `assets/mymodtest/lang/en_us.json` 与 `zh_cn.json` 缺少对应 key
- 修复：在 en_us / zh_cn 添加 `subtitles.mymodtest.moontrace.mark`
- 为什么必须改：保证 lang 覆盖所有 sound subtitle key，满足资源一致性检查。

## D. 初始化时序
- 检查：`src/main/java/mod/test/mymodtest/Mymodtest.java` 中 `KatanaInit.init()` 先于商人交易使用的时序触发，且交易构造发生于交互时（非静态初始化）。
- 结论：无修改必要。

## E. Client / Server 隔离
- 检查：代码中无 `net.minecraft.client` 依赖；未发现 server 侧引用 client-only 类。
- 结论：无修改必要。

## 修改清单
- `src/main/resources/fabric.mod.json`
  - 移除不存在的 client/datagen entrypoints
  - 移除不存在的 client mixin 配置
  - 移除无效 icon 路径
- `src/main/resources/assets/mymodtest/models/item/mysterious_coin.json`
  - 使用原版纹理占位，避免缺失纹理
- 新增模型文件：
  - `src/main/resources/assets/mymodtest/models/item/sealed_ledger.json`
  - `src/main/resources/assets/mymodtest/models/item/arcane_ledger.json`
  - `src/main/resources/assets/mymodtest/models/item/sigil.json`
- `src/main/resources/assets/mymodtest/lang/en_us.json`
- `src/main/resources/assets/mymodtest/lang/zh_cn.json`
  - 补充 `subtitles.mymodtest.moontrace.mark`
