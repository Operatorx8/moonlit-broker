你现在是 Fabric 1.21.1 Java 模组的代码维护助手。  
项目：MysteriousMerchant（MOD_ID = mymodtest）  
目标：每次新增物品后，把它们统一出现在自定义创造模式 Tab：mymodtest:main（显示名 key：itemGroup.mymodtest.main），并按固定分区排序；不要再挤进原版 Combat/Ingredients 等分组。

当前项目已存在并必须遵守：
- 自定义 Tab 注册文件：`src/main/java/mod/test/mymodtest/registry/ModItemGroups.java`
  - Tab id: `mymodtest:main`
  - icon: `KatanaItems.MOON_GLOW_KATANA`
  - `entries(...)` 内按分区 `add`
  - 对 `ArmorItems` 使用 `addIfPresent(...)` 防止初始化顺序导致 NPE（见 `ModItemGroups.java:~88`）
- 初始化入口：`src/main/java/mod/test/mymodtest/Mymodtest.java` 中调用 `ModItemGroups.init()`
- 语言 key 已存在：
  - `assets/mymodtest/lang/en_us.json`: `itemGroup.mymodtest.main`
  - `assets/mymodtest/lang/zh_cn.json`: `itemGroup.mymodtest.main`

【你要做的工作】
1) 将下面“新增物品清单”里的所有物品，加入 `ModItemGroups.MAIN` 的 `entries(...)` 中。  
2) 加入位置必须符合“分区规则”和“排序规则”（见下）。  
3) 如果这些物品目前也被注册到了原版分组（例如 `ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT/INGREDIENTS/TOOLS...)`），请把对应添加逻辑移除或注释掉，让物品只出现在 `mymodtest:main`。  
4) 若发现某个物品字段可能为 `null`（初始化顺序/可选注册），必须使用 `addIfPresent(...)` 包装，避免 NPE。  
5) 修改后执行 `./gradlew build`，保证编译通过。  
6) 输出：简洁的改动总结（文件 + 关键行范围 + 原因），并给出进入游戏前最短验证步骤（如何在 Tab 中快速确认新物品出现）。

【分区规则（entries(...) 的顺序固定，不要乱）】
A. 货币 / 卷轴 / 商人道具  
B. 材料 / 战利品 / Tag 相关物品  
C. 神器 Katana（5 把）  
D. 过渡武器（Acer/Velox/Fatalis 等）  
E. Armor（按：头 Helmet -> 胸 Chestplate -> 腿 Leggings -> 靴 Boots；同部位内按稀有度从低到高）

【排序规则】
- 每个分区内：优先按“你在物品容器类里定义的顺序”；若未给出顺序，就按我在“新增物品清单”中的顺序。  
- 任何物品都不要重复加入两次。  
- 不要引入新的复杂抽象（保持最小改动）。

【新增物品清单（我会每次更新这里，你只对这批做变更）】
- 分区：`<A/B/C/D/E>`
  - 物品字段（Java 常量）：`<例如 ArmorItems.GOSSAMER_BOOTS>`
  - 注册 id（Identifier path，可选）：`<例如 gossamer_boots>`
  - 备注（可选）：`<例如 RARE，Boots>`

【本次新增物品清单】
- 分区：`<在此填入>`
  - `<ItemContainer.FIELD_1>`
  - `<ItemContainer.FIELD_2>`
  - ...

开始执行。优先只改 `ModItemGroups.java`；只有在确实存在“被塞进原版分组”的旧逻辑时，才去对应文件删除/注释那段 `ItemGroupEvents`。  
这样就能让 AI agent 们把新物品按规则加入你的 Mod Tab，并避免挤进原版分组。

你以后怎么用（最省事的填法）

你每次只要把最后这段填上，例如：

【本次新增物品清单】  
- 分区：E
  - `ArmorItems.NIGHT_SENTRY_HELMET`
  - `ArmorItems.NIGHT_SENTRY_CHESTPLATE`
  - `ArmorItems.NIGHT_SENTRY_LEGGINGS`
  - `ArmorItems.NIGHT_SENTRY_BOOTS`

或者武器：

【本次新增物品清单】  
- 分区：D
  - `TransitionalWeaponItems.ACER`
  - `TransitionalWeaponItems.VELOX`
  - `TransitionalWeaponItems.FATALIS`

额外加一条“防傻”规则（固定）：

禁止新增任何 `ItemGroupEvents.modifyEntriesEvent(ItemGroups.*)` 的添加逻辑；如发现旧逻辑存在，必须移除。
