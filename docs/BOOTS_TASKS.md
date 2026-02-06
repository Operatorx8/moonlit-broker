# Boots 实现任务指令（Claude Code）

> 目标：为 Mysterious Merchant mod（Fabric 1.21.1）实现 5 个自定义靴子。
> 前置：项目已有 Katana 武器系统、护腿系统、自定义实体和交易 UI。
> 约定：tick 统一用 `server.getOverworld().getTime()` 或等效的 server tick；所有状态用 `expiresTick` Map / 自家标记，**不做全局 remove**。

---

## Task 0：常量类

在已有的常量体系下（参考护腿 `LeggingsEffectConstants` 的风格），新建 `BootsEffectConstants`。

常量全部从 `ARMOR_PARAMS.md` 的 "Boots 效果参数" 章节抄入，分组前缀：
- `BOOT_SCAN_INTERVAL_TICKS = 20`
- `UNTRACEABLE_*`（idle window 240, invis 60, cd 900）
- `BOUNDARY_*`（jump level 1, refresh 25）
- `GHOST_*`（combat lock 160, hit window 20, hit count 2, burst 20, burst cd 300）
- `MARCH_*`（no hit 160, no hurt 80, speed level 2, max duration 300, cd 240）
- `GOSSAMER_*`（web slow reduce 0.70, slowness downgrade 1, refresh 25）

不要硬编码任何数值到逻辑类中。

---

## Task 1：靴子物品注册

在已有的 armor 注册体系下，注册 5 个靴子 Item。参考 `ARMOR_PARAMS.md` 基础属性表：

| 靴子 | 耐久 | 护甲值 | 稀有度 | 防火 | 可铁砧修复 |
|---|---|---|---|---|---|
| untraceable_treads | 380 | 3 | EPIC | ✅ | ❌ |
| boundary_walker | 380 | 2 | UNCOMMON | ✅ | ❌ |
| ghost_step | 320 | 1 | RARE | ✅ | ❌ |
| marching_boots | 400 | 2 | UNCOMMON | ✅ | ❌ |
| gossamer_boots | 375 | 2 | RARE | ✅ | ❌ |

共同属性：韧性 0、击退抗性 0、防火 true、铁砧不可修复。

---

## Task 2：Boots Tick Handler 骨架

新建 `BootsTickHandler`（或融入已有的 armor tick 体系），注册 `ServerTickEvents.END_SERVER_TICK`。

核心循环：遍历所有在线玩家 → 检测脚部槽位 → 分发到对应靴子的 handler 方法。

**每个靴子的状态用一个 per-player 的状态对象**（`BootsPlayerState`），存到 `HashMap<UUID, BootsPlayerState>`。玩家退出时清理。

`BootsPlayerState` 需要的字段（全部为 long tick 或 int 计数）：
```
// 共用
lastHitLivingTick, lastHurtByLivingTick

// Boot1 - Untraceable
untraceableCdReadyTick, invisExpiresTick

// Boot2 - Boundary
jumpExpiresTick

// Boot3 - Ghost
damageWindowStartTick, damageWindowCount
lastDamageTick, lastAttackerUuid
ghostBurstCdReadyTick, ghostBurstExpiresTick

// Boot4 - March
marchActive, marchStartTick, marchCdReadyTick, speedExpiresTick

// Boot5 - Gossamer
webAssistExpiresTick, slownessAdjustExpiresTick
```

---

## Task 3：事件钩子（attack + damage）

需要两个额外事件监听，供 Boot3（Ghost Step）和 Boot4（Marching Boots）使用：

### 3a. 玩家攻击命中 LivingEntity
- 用 `AttackEntityCallback` 或等效 Fabric 事件
- 更新 `state.lastHitLivingTick = currentTick`
- 如果 Boot4 marchActive → 立即退出 march，设 CD（`marchCdReadyTick = now + MARCH_CD_TICKS`）

### 3b. 玩家受到 LivingEntity 伤害
- 用 `ServerLivingEntityEvents.ALLOW_DAMAGE` 或等效事件（只读监听，不拦截）
- 更新 `state.lastHurtByLivingTick = currentTick`
- Boot3 去抖逻辑：如果 `attacker instanceof LivingEntity`，检查 `currentTick != state.lastDamageTick || attacker.getUuid() != state.lastAttackerUuid`，通过则窗口计数 +1
- 更新 `lastDamageTick`, `lastAttackerUuid`

---

## Task 4：Boot1 — 无追索步履（Untraceable Treads）

**入口**：ServerTick 20t 扫描

**判定**：
```
穿戴本靴 
&& (now - lastHitLivingTick >= UNTRACEABLE_IDLE_WINDOW_TICKS)
&& (now - lastHurtByLivingTick >= UNTRACEABLE_IDLE_WINDOW_TICKS)
&& (now >= untraceableCdReadyTick)
```

**应用**：
- 给予 Invisibility（duration = UNTRACEABLE_INVIS_TICKS, amplifier 0, ambient true, showParticles false）
- 设 `invisExpiresTick = now + UNTRACEABLE_INVIS_TICKS`
- 设 `untraceableCdReadyTick = now + UNTRACEABLE_CD_TICKS`

**注意**：不限制维度（Overworld/Nether/End 均可）。

---

## Task 5：Boot2 — 边界行走（Boundary Walker）

**入口**：ServerTick 20t 扫描

**判定**：
```
穿戴本靴
&& player.getWorld().getRegistryKey() == World.OVERWORLD
&& player.getWorld().isSkyVisible(player.getBlockPos())
&& (isRainingAt(player) || isThundering() || isSnowingBiome(player) || world.isNight())
```

降水类型判断：用 `biome.getPrecipitation()` 区分雨/雪，结合 `world.isRaining()`。

**应用**：
- 给予 Jump Boost I（duration = BOUNDARY_REFRESH_TICKS, amplifier 0）
- 设 `jumpExpiresTick = now + BOUNDARY_REFRESH_TICKS`
- 每次扫描满足条件就刷新，不满足就让它自然过期（不主动 remove）

---

## Task 6：Boot3 — 幽灵步伐（Ghost Step）

最复杂的一个。三层逻辑，优先级从高到低：

### 6a. 战斗锁判定（每 20t）
```
boolean inCombat = (now - lastHitLivingTick < GHOST_COMBAT_LOCK_TICKS)
```
- `inCombat == true` → 强制关闭幽灵碰撞（效果 A 不生效）
- `inCombat == false` → 效果 A 生效（开启幽灵碰撞）

### 6b. 效果 A — 常驻幽灵碰撞
- 实现方式：`player.noClip` 只管碰撞推挤，或用 Mixin 修改 `pushAwayFrom` / entity collision 逻辑
- **警告**：`noClip = true` 同时会关闭方块碰撞（穿墙），不要用。应该 Mixin `PlayerEntity.pushAwayFrom()` 使其在幽灵状态下 return early

### 6c. 效果 B — 受击应急幽灵
- 在事件钩子 3b 中已做计数
- 20t 扫描时检查：`damageWindowCount >= GHOST_HIT_COUNT_THRESHOLD && now >= ghostBurstCdReadyTick`
- 满足 → 开启幽灵碰撞 1s（`ghostBurstExpiresTick = now + GHOST_BURST_TICKS`），设 CD
- **即使在战斗中**，效果 B 也允许开启，但只持续 1s
- 每次扫描重置窗口计数：`damageWindowCount = 0, damageWindowStartTick = now`

### Mixin 端
在 `pushAwayFrom` 的 Mixin 中，判断条件：
```java
if (ghostStateA_active || (ghostBurstExpiresTick > currentTick)) {
    ci.cancel(); // 取消推挤
}
```

---

## Task 7：Boot4 — 急行之靴（Marching Boots）

**入口**：ServerTick 20t 扫描 + attack 事件

### 进入（扫描）
```
穿戴本靴
&& !marchActive
&& (now - lastHitLivingTick >= MARCH_NO_HIT_TICKS)
&& (now - lastHurtByLivingTick >= MARCH_NO_HURT_TICKS)  // 注意：这里是任意伤害来源
&& (now >= marchCdReadyTick)
```
→ `marchActive = true`, `marchStartTick = now`, 给 Speed II

### 维持（扫描，marchActive == true）
- 检查条件是否仍满足（8s 未命中 + 4s 未受伤）
- 不满足 → 退出
- `now - marchStartTick >= MARCH_MAX_DURATION_TICKS` → 退出
- 满足 → 刷新 Speed II（duration = 25t 滚动刷新）

### 退出
- 清除 `marchActive`
- 设 `marchCdReadyTick = now + MARCH_CD_TICKS`
- Speed 效果让它自然过期（不主动 remove）

### attack 事件退出
- 在 Task 3a 中已处理：命中 LivingEntity 且 marchActive → 立即退出 + CD

---

## Task 8：Boot5 — 轻灵之靴（Gossamer Boots）

**入口**：ServerTick 20t 扫描

**判定**：玩家碰撞箱与 cobweb 方块相交
```java
// 方式1：检查玩家所在 blockPos 及周围是否有 cobweb
// 方式2：用 BlockPos.stream(player.getBoundingBox()) 遍历，any match Blocks.COBWEB
```

**应用**：
- Cobweb 减速降低 70%：需要 Mixin 到 `CobwebBlock#onEntityCollision` 或 `Entity#slowMovement`，在玩家穿戴本靴时将减速系数从 0.25 改为 `0.25 + (1-0.25)*0.70` ≈ 0.775（即保留更多速度）
- 具体实现：Mixin `Entity.slowMovement(BlockState, Vec3d)`，当 blockState 是 cobweb 且玩家穿本靴 → 修改 Vec3d 参数的减速倍率
- Slowness 降阶：如果玩家有 Slowness II（amplifier >= 1）→ 替换为 Slowness I（amplifier 0），用 `expiresTick` 标记来源，只回滚自己做的修改
- 刷新：`webAssistExpiresTick = now + GOSSAMER_REFRESH_TICKS`

**离开 cobweb**：下次扫描不满足 → 不刷新，效果自然过期

---

## Task 9：Mixin 清单汇总

需要新增的 Mixin：

1. **PlayerEntityMixin**（或已有的扩展）：
   - `pushAwayFrom` → Boot3 幽灵碰撞
   
2. **EntityMixin** 或 **LivingEntityMixin**：
   - `slowMovement` → Boot5 cobweb 减速修改

确保在 `mixins.json` 中注册。

---

## Task 10：测试要点

每个靴子完成后，按以下清单验证：

| 靴子 | 必测场景 |
|---|---|
| Boot1 | 12s 不战斗 → 隐身触发 → CD 45s 内不再触发 → CD 后再触发 |
| Boot2 | 白天晴天室外 → 无效果；夜晚室外 → Jump I；进室内 → 失效；下雨 → 生效 |
| Boot3 | 非战斗 → 怪物不推你；攻击怪物 → 8s 内可被推；1s 内被打 2 次 → 短促幽灵 1s |
| Boot4 | 站着不动 8s+ → Speed II；攻击怪物 → 立即退出 + 12s CD；持续 15s → 自动退出 |
| Boot5 | 走进蜘蛛网 → 明显比裸装快；有 Slowness II → 降为 I；离开蜘蛛网 → 恢复正常 |

---

## 实施顺序建议

`Task 0 → Task 1 → Task 2 → Task 3 → Task 4 → Task 5 → Task 7 → Task 8 → Task 6 → Task 9 → Task 10`

Boot3（Ghost Step）放最后实现，因为 Mixin 复杂度最高、依赖 Task 3 的事件钩子最多。Boot5 的 Mixin 相对独立，可以先做。
