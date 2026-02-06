# 过渡护甲 LEGS/FEET 实现任务指令（Claude Code）

> 目标：为 Mysterious Merchant mod（Fabric 1.21.1）新增 6 件过渡护甲（3 LEGS + 3 FEET），补齐文档体系，实现 2 件特效逻辑。
> 前置：`docs/armor/transitional/` 已有 HEAD×3 + CHEST×3 的完整文档（INDEX + 4件套）。
> 约定：共同硬约束不变——fireproof=true, anvilRepair=false, knockbackResistance=0, enchantability=BY_RARITY。
> 伤害类特效统一走 `LivingEntity.applyDamage` 的 `@ModifyVariable(argsOnly=true)` 链路，不走 `damage()` HEAD。

---

## Task 0：常量追加

在已有 transitional armor 常量区域追加：

```java
// === Cushion Hiking Boots ===
public static final float CUSHION_FALL_FLAT_REDUCTION = 2.0f;
public static final float CUSHION_FALL_CLAMP_MIN = 0.0f;

// === Cargo Pants ===
public static final float CARGO_TORCH_SAVE_CHANCE = 0.15f;
public static final int CARGO_TORCH_CD_TICKS = 200; // 10s
```

不要为无特效的 4 件创建逻辑常量。

---

## Task 1：物品注册（6 件）

在已有 transitional armor 注册体系下追加：

| 装备 | 注册ID | 部位 | 耐久 | 护甲 | 韧性 | 稀有度 | 特效 |
|---|---|---|---|---|---|---|---|
| 裹腿 | wrapped_leggings | LEGS | 200 | 4 | 0.5 | UNCOMMON | 无 |
| 加固胫甲 | reinforced_greaves | LEGS | 280 | 6 | 0.5 | UNCOMMON | 无 |
| 工装裤 | cargo_pants | LEGS | 225 | 5 | 0.0 | RARE | Torch 返还 |
| 赎罪之靴 | penitent_boots | FEET | 200 | 3 | 0.5 | UNCOMMON | 无 |
| 标准铁靴 | standard_iron_boots | FEET | 210 | 2 | 0.5 | UNCOMMON | 无 |
| 缓冲登山靴 | cushion_hiking_boots | FEET | 260 | 3 | 0.5 | RARE | 摔落减伤 |

共同：fireproof=true, anvilRepair=false, knockbackResistance=0。
无特效的 4 件不需要子类，纯配置注册即可。

---

## Task 2：Cushion Hiking Boots — 摔落减伤逻辑

### 机制口径
穿戴于 FEET 槽位时，**最终摔落伤害** flat -2.0，clamp >= 0。

### 实现路径（关键：走 applyDamage 链路）

**Mixin 目标**：`LivingEntity.applyDamage(DamageSource, float)`

```java
@Mixin(LivingEntity.class)
public abstract class LivingEntityApplyDamageMixin {

    @ModifyVariable(
        method = "applyDamage",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0  // float amount 参数
    )
    private float modifyFallDamage(float amount, DamageSource source) {
        LivingEntity self = (LivingEntity)(Object)this;
        if (!(self instanceof PlayerEntity player)) return amount;
        
        // 1. 检查 damageType 是否为 fall
        if (!source.isOf(DamageTypes.FALL)) return amount;
        
        // 2. 检查 FEET 槽位是否穿戴 cushion_hiking_boots
        ItemStack boots = player.getEquippedStack(EquipmentSlot.FEET);
        if (!boots.isOf(ModItems.CUSHION_HIKING_BOOTS)) return amount;
        
        // 3. 减伤
        float reduced = Math.max(amount - CUSHION_FALL_FLAT_REDUCTION, CUSHION_FALL_CLAMP_MIN);
        
        // 4. Debug 日志
        if (ModConfig.DEBUG) {
            LOGGER.info("[TransArmor] item=cushion_hiking_boots player={} dmgType=fall raw={} out={}",
                uuidShort(player), amount, reduced);
        }
        
        return reduced;
    }
}
```

**⚠️ 注意事项**：
- `DamageTypes.FALL` 在 1.21.1 Yarn 中确认存在（`net.minecraft.entity.damage.DamageTypes`）
- 如果项目中已有其他装备在 `applyDamage` 上挂 `@ModifyVariable`，**必须确认 priority 不冲突**——多个 ModifyVariable 同方法同参数时 Mixin 按 priority 排序
- 如果已有 Sanctified Hood / Reactive Bug Plate 的减伤也挂在 `applyDamage` 上，建议统一到一个 Mixin 类内用 if-else 分发，避免 priority 竞争
- 若它们当前挂在 `damage()` HEAD 上，**本次不迁移**（最小改动原则），但在本件上必须走 `applyDamage`

### 口径确认
- "最终入血扣减"：指 `applyDamage` 收到的 `amount` 已经过护甲计算、附魔减伤、抗性等全部流程，这里是最后一道 flat 扣减
- 如果 amount 经过 `applyDamage` 时已经被护甲削到 1.5，那减 2.0 后 clamp 到 0——玩家不受摔落伤害
- Feather Falling 附魔等效果在 `applyDamage` 之前已经生效，所以本减伤与 Feather Falling **可叠加**

---

## Task 3：Cargo Pants — Torch 返还逻辑

### 机制口径
穿戴于 LEGS 槽位时，放置 `minecraft:torch` 成功后，有 15% 概率返还 1 个火把（抵消系统扣除），每玩家 CD 200 ticks。

### 实现路径（Mixin，Fabric 环境）

**不要使用任何 Forge/NeoForge 事件**（`BlockEvent.EntityPlaceEvent` 等不存在于 Fabric）。

**Mixin 目标**：`ItemStack.useOnBlock(ItemUsageContext)` 或 `BlockItem.place(ItemPlacementContext)`

推荐方案：Mixin `BlockItem.place(ItemPlacementContext)`，在 `@Inject(at = @At("RETURN"))` 检查返回值是否为 `ActionResult.SUCCESS`（或 `isAccepted()`）。

```java
@Mixin(BlockItem.class)
public abstract class BlockItemPlaceMixin {

    @Inject(
        method = "place(Lnet/minecraft/item/ItemPlacementContext;)Lnet/minecraft/util/ActionResult;",
        at = @At("RETURN")
    )
    private void onPlaceReturn(ItemPlacementContext context, CallbackInfoReturnable<ActionResult> cir) {
        // 1. 只在放置成功时触发
        ActionResult result = cir.getReturnValue();
        if (!result.isAccepted()) return;
        
        // 2. 检查放置的是否为 minecraft:torch
        BlockItem self = (BlockItem)(Object)this;
        if (self.getBlock() != Blocks.TORCH) return;
        
        // 3. 检查是否为玩家操作
        PlayerEntity player = context.getPlayer();
        if (player == null || player.getWorld().isClient()) return;
        
        // 4. 检查 LEGS 槽位
        ItemStack leggings = player.getEquippedStack(EquipmentSlot.LEGS);
        if (!leggings.isOf(ModItems.CARGO_PANTS)) return;
        
        // 5. CD 检查（per-player Map<UUID, Long>）
        long now = player.getServer().getOverworld().getTime(); // 统一 tick 来源
        Long cdReady = cargoCdMap.get(player.getUuid());
        if (cdReady != null && now < cdReady) {
            // CD 中，不触发
            return;
        }
        
        // 6. 概率判定
        float roll = player.getRandom().nextFloat();
        if (roll >= CARGO_TORCH_SAVE_CHANCE) return;
        
        // 7. 返还：当前 hand 的 stack +1
        ItemStack handStack = context.getStack(); // 放置后 count 已被系统 -1
        handStack.increment(1);
        
        // 8. 设置 CD
        cargoCdMap.put(player.getUuid(), now + CARGO_TORCH_CD_TICKS);
        
        // 9. Debug 日志
        if (ModConfig.DEBUG) {
            LOGGER.info("[TransArmor] item=cargo_pants player={} itemId=torch rawCount={} outCount={} roll={} cdRemaining={}",
                uuidShort(player),
                handStack.getCount() - 1,  // 返还前的数量
                handStack.getCount(),       // 返还后
                String.format("%.3f", roll),
                CARGO_TORCH_CD_TICKS);
        }
    }
}
```

**⚠️ 关键注意事项**：

1. **`context.getStack()` 时机**：在 `@At("RETURN")` 时，系统已经对 stack 执行了 `decrement(1)`。此时 `increment(1)` 等于"还回去"。但要确认：
   - 如果玩家是创造模式，`decrement` 可能未执行——需判断 `!player.isCreative()`
   - 如果 stack count 已经是 0（放完最后一个），`increment(1)` 后 count=1 但 stack 可能已从 slot 移除——需要处理边界情况：检查 hand slot 是否已被清空，如果是则 `player.getInventory().insertStack(new ItemStack(Items.TORCH, 1))`

2. **仅限 `minecraft:torch`**：不对 `soul_torch`、`wall_torch`、`redstone_torch` 生效。如果设计上需要扩展，改为 tag 判定。

3. **CD Map 清理**：玩家退出时清理 `cargoCdMap`，或用 WeakHashMap/定期清理。可以复用已有的 per-player state 清理机制。

4. **1.21.1 Yarn 方法签名**：确认 `BlockItem.place(ItemPlacementContext)` 的精确 descriptor，可能与旧版不同。用 `./gradlew genSources` 后在反编译源码中核对。

---

## Task 4：文档生成（6 件 × 4 文件 + INDEX 更新）

### 4a. INDEX.md 更新

在已有 `docs/armor/transitional/INDEX.md` 总表末尾追加 6 行：

| # | 显示名 | 英文名 | 部位 | 耐久 | 护甲 | 韧性 | 稀有度 | 特效 |
|---|---|---|---|---|---|---|---|---|
| 7 | 裹腿 | Wrapped Leggings | LEGS | 200 | 4 | 0.5 | UNCOMMON | 无 |
| 8 | 加固胫甲 | Reinforced Greaves | LEGS | 280 | 6 | 0.5 | UNCOMMON | 无 |
| 9 | 工装裤 | Cargo Pants | LEGS | 225 | 5 | 0.0 | RARE | Torch 返还 15% |
| 10 | 赎罪之靴 | Penitent Boots | FEET | 200 | 3 | 0.5 | UNCOMMON | 无 |
| 11 | 标准铁靴 | Standard Iron Boots | FEET | 210 | 2 | 0.5 | UNCOMMON | 无 |
| 12 | 缓冲登山靴 | Cushion Hiking Boots | FEET | 260 | 3 | 0.5 | RARE | 摔落减伤 -2.0 |

**不要删除/重写已有的 #1-#6 行。**

### 4b. 无特效装备文档（4 件批量）

对 Wrapped Leggings、Reinforced Greaves、Penitent Boots、Standard Iron Boots 各生成 4 个文件。

副标题（自拟）：
- Wrapped Leggings: "Layered cloth over leather—mobile, if not elegant."
- Reinforced Greaves: "Heavy plate from hip to knee—built for the front line."
- Penitent Boots: "Walk in humility; the road forgives no one."
- Standard Iron Boots: "Standard issue—nothing more, nothing less."

模板与 Task 1（HEAD/CHEST 批次）完全一致，仅替换部位为 LEGS/FEET、填入对应数值。

TESTPLAN 模板（6 条通用）：
```
| # | 场景 | 预期 |
|---|---|---|
| 1 | 穿戴后查看护甲值 | 护甲 +<defense>，韧性 +<toughness> |
| 2 | 防火：扔进岩浆 | 物品不消失 |
| 3 | 铁砧修复 | 不可修复 |
| 4 | 稀有度颜色 | 物品名颜色 = <rarity> 对应色 |
| 5 | 耐久消耗 | 受击后耐久正常递减 |
| 6 | 附魔台 | 可附魔，概率符合 rarity 档位 |
```

### 4c. Cargo Pants 文档

**CARGO_PANTS_SPEC.md**：
```
# 工装裤（Cargo Pants）
> "Deep pockets, lucky finds—keep the lights burning."

## 定位
LEGS 槽位工具型护甲，牺牲韧性换取资源效率。

## 不变量
- 部位：LEGS（LEGS_ONLY）
- 防火：是
- 铁砧修复：不可
- 击退抗性：0
- 韧性：0.0
- 特效：放置 minecraft:torch 时 15% 概率返还

## 效果口径
- 仅对 minecraft:torch 放置成功生效（不含 soul_torch / wall_torch / redstone_torch）
- 概率：15%（CARGO_TORCH_SAVE_CHANCE）
- 命中：返还 1 个火把到玩家手持 stack（抵消系统扣除）
- CD：200 ticks（10s），per-player
- 创造模式不触发
- SERVER_ONLY

## 边界
- 仅 LEGS 槽位
- 仅服务端
- 不影响非 torch 方块放置
- CD 期间概率判定直接跳过（不 roll）
```

**CARGO_PANTS_PARAMS.md**：
```
## 基础属性
| 参数 | 值 |
|---|---|
| durability | 225 |
| defense | 5 |
| toughness | 0.0 |
| rarity | RARE |
| enchantability | BY_RARITY |
| fireproof | true |
| anvilRepair | false |

## 逻辑参数
| 参数 | 值 | 说明 |
|---|---|---|
| CARGO_TORCH_SAVE_CHANCE | 0.15 | 返还概率 |
| CARGO_TORCH_CD_TICKS | 200 | 每玩家冷却（10s） |
| APPLY_SLOT | LEGS | 仅腿部槽位 |
| TARGET_BLOCK | minecraft:torch | 仅普通火把 |
| SERVER_ONLY | true | |
```

**CARGO_PANTS_TESTPLAN.md**：
```
| # | 场景 | 预期 |
|---|---|---|
| 1 | 穿戴，连续放置 20 个 torch | 约 3 个被返还（±RNG），日志可见 roll 值 |
| 2 | 穿戴，触发返还后 10s 内再放 | CD 中不触发（日志无输出） |
| 3 | 穿戴，CD 过后再放 | 可再次触发 |
| 4 | 穿戴，放置 soul_torch | 不触发（不在 target 范围） |
| 5 | 不穿戴，放置 torch | 不触发 |
| 6 | 创造模式穿戴放置 torch | 不触发 |
| 7 | 放置最后 1 个 torch 且命中返还 | stack 恢复为 1（不会凭空消失） |
| 8 | 防火 + 铁砧 | 同通用 |
| 9 | DEBUG=true 日志格式 | 字段完整：player, itemId, rawCount, outCount, roll, cdRemaining |
| 10 | ./gradlew build | 编译通过 |
```

**CARGO_PANTS_CHANGELOG.md**：
```
- [<今天日期>] 初始化：SPEC/PARAMS/TESTPLAN 创建；Torch 返还 15% + 200t CD 设计确认
```

### 4d. Cushion Hiking Boots 文档

**CUSHION_HIKING_BOOTS_SPEC.md**：
```
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
```

**CUSHION_HIKING_BOOTS_PARAMS.md**：
```
## 基础属性
| 参数 | 值 |
|---|---|
| durability | 260 |
| defense | 3 |
| toughness | 0.5 |
| rarity | RARE |
| enchantability | BY_RARITY |
| fireproof | true |
| anvilRepair | false |

## 逻辑参数
| 参数 | 值 | 说明 |
|---|---|---|
| CUSHION_FALL_FLAT_REDUCTION | 2.0 | flat 减伤值 |
| CUSHION_FALL_CLAMP_MIN | 0.0 | 最终下限 |
| APPLY_SLOT | FEET | 仅脚部 |
| DAMAGE_TYPE | DamageTypes.FALL | 仅摔落 |
| SERVER_ONLY | true | |
```

**CUSHION_HIKING_BOOTS_TESTPLAN.md**：
```
| # | 场景 | 预期 |
|---|---|---|
| 1 | 穿戴，从 4 格高跳下（raw fall ≈ 1） | 减 2.0 → clamp 0，无伤害 |
| 2 | 穿戴，从 10 格高跳下（raw fall ≈ 7） | 经护甲/附魔后的最终值再 -2.0 |
| 3 | 穿戴 + Feather Falling IV，从 10 格高 | FF 先削 → 再 -2.0，叠加生效 |
| 4 | 不穿戴，从 10 格高跳下 | 正常摔落伤害 |
| 5 | 穿戴，受火焰伤害 | 不触发（非 fall） |
| 6 | 穿戴，被实体攻击 | 不触发（非 fall） |
| 7 | 放入副手/背包 | 不触发（FEET_ONLY） |
| 8 | 防火 + 铁砧 | 同通用 |
| 9 | DEBUG=true 日志格式 | player, dmgType=fall, raw, out |
| 10 | ./gradlew build | 编译通过 |
```

**CUSHION_HIKING_BOOTS_CHANGELOG.md**：
```
- [<今天日期>] 初始化：SPEC/PARAMS/TESTPLAN 创建；fall flat -2.0 走 applyDamage 链路确认
```

---

## Task 5：Mixin 注册

在 `mixins.json`（或 `modid.mixins.json`）中追加：

1. `LivingEntityApplyDamageMixin`（Cushion Hiking Boots fall 减伤）
   - 如果已有其他装备的 applyDamage Mixin，合并到同一个类中
2. `BlockItemPlaceMixin`（Cargo Pants torch 返还）

确认 `./gradlew build` 通过。

---

## Task 6：文件树增量

本次在 `docs/armor/transitional/` 下新增：

```
+ WRAPPED_LEGGINGS_SPEC.md
+ WRAPPED_LEGGINGS_PARAMS.md
+ WRAPPED_LEGGINGS_TESTPLAN.md
+ WRAPPED_LEGGINGS_CHANGELOG.md
+ REINFORCED_GREAVES_SPEC.md
+ REINFORCED_GREAVES_PARAMS.md
+ REINFORCED_GREAVES_TESTPLAN.md
+ REINFORCED_GREAVES_CHANGELOG.md
+ CARGO_PANTS_SPEC.md
+ CARGO_PANTS_PARAMS.md
+ CARGO_PANTS_TESTPLAN.md
+ CARGO_PANTS_CHANGELOG.md
+ PENITENT_BOOTS_SPEC.md
+ PENITENT_BOOTS_PARAMS.md
+ PENITENT_BOOTS_TESTPLAN.md
+ PENITENT_BOOTS_CHANGELOG.md
+ STANDARD_IRON_BOOTS_SPEC.md
+ STANDARD_IRON_BOOTS_PARAMS.md
+ STANDARD_IRON_BOOTS_TESTPLAN.md
+ STANDARD_IRON_BOOTS_CHANGELOG.md
+ CUSHION_HIKING_BOOTS_SPEC.md
+ CUSHION_HIKING_BOOTS_PARAMS.md
+ CUSHION_HIKING_BOOTS_TESTPLAN.md
+ CUSHION_HIKING_BOOTS_CHANGELOG.md
~ INDEX.md (追加 6 行)
```

共 24 个新文件 + 1 个修改。

---

## 实施顺序

```
Task 0 (常量)
→ Task 1 (6 件物品注册)
→ Task 4b (4 件无特效文档，批量)
→ Task 2 (Cushion Hiking Boots 逻辑 + Mixin)
→ Task 4d (Cushion Hiking Boots 文档)
→ Task 3 (Cargo Pants 逻辑 + Mixin)
→ Task 4c (Cargo Pants 文档)
→ Task 4a (INDEX.md 追加)
→ Task 5 (Mixin 注册 + build 验证)
→ Task 6 (文件树核对)
```

先注册 → 纯数值文档 → 简单特效（fall flat 减伤）→ 复杂特效（Mixin BlockItem + CD + 边界）→ 索引 → build。

---

## ⚠️ 需人工确认 / 核对

1. **`BlockItem.place()` 方法签名**：1.21.1 Yarn 中可能是 `place(ItemPlacementContext)` 返回 `ActionResult`，需 `./gradlew genSources` 后核对精确 descriptor
2. **`ActionResult.isAccepted()`**：1.21.1 中 ActionResult 可能改为 `ActionResult.Success` 子类模式（1.20.5+ 变更），确认判定方式
3. **applyDamage Mixin 冲突**：如果 Sanctified Hood / Reactive Bug Plate 也在 `applyDamage` 上挂了 ModifyVariable，确认 priority 排序或合并到同一 Mixin 类
4. **创造模式 torch 放置**：创造模式下 `BlockItem.place()` 不 decrement stack，`increment(1)` 会导致火把凭空增加——必须 `if (!player.isCreative())` 门控
5. **最后 1 个 torch 放置命中返还**：stack count 从 1→0 后系统可能清空 slot，此时 `increment(1)` 操作的对象已是 `ItemStack.EMPTY`——需要 fallback 到 `player.getInventory().insertStack(new ItemStack(Items.TORCH, 1))`
