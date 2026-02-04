# LOGGING.md - 日志规范

## 目标

默认只看 **INFO** 就能验收；开 **TRACE** 才看细节；**WARN** 提醒异常但不中断。

日志必须：**可读 / 可筛 / 可对照 / 可关**

---

## Level 定义

| 级别 | 用途 | 输出条件 |
|------|------|----------|
| **TRACE** | 高频细节（判定链、rng、cd） | 仅 `debug=true` 时输出 |
| **INFO** | 低频事件（触发/应用/状态变化/配置加载） | 始终输出 |
| **WARN** | 缺配置用默认值、参数被 clamp、异常状态 | 始终输出 |

---

## 统一格式

```
[MoonTrace|<FEATURE>|<PHASE>] action=<...> result=<OK|BLOCKED|SKIP|FAIL> reason=<...?> <kv...> ctx{<kv...>}
```

### 字段说明

| 字段 | 说明 | 示例 |
|------|------|------|
| `FEATURE` | 功能模块 | `Armor`, `Merchant`, `Katana` |
| `PHASE` | 阶段 | `BOOT`, `TRIGGER`, `APPLY`, `STATE` |
| `action` | 具体动作 | `trigger`, `apply`, `check`, `config_load` |
| `result` | 结果 | `OK`, `BLOCKED`, `SKIP`, `FAIL` |
| `reason` | 原因（仅 result!=OK） | 使用词典中的固定词 |
| `kv` | 键值对 | `effect=regeneration dur=60` |
| `ctx{}` | 上下文 | `ctx{p=PlayerName t=Zombie dim=overworld}` |

---

## Trigger 规则

- **INFO**：只打 **first-fail**（失败只输出一个主因）
- **TRACE**：允许逐步打印判定链（可选）

### 示例

```
// INFO - 触发成功
[MoonTrace|Armor|TRIGGER] action=trigger result=OK effect=sentinel_glow ctx{p=Steve dim=overworld}

// INFO - 触发失败（只输出主因）
[MoonTrace|Armor|TRIGGER] action=check result=BLOCKED reason=cd_hit ctx{p=Steve}

// TRACE - 判定链细节
[MoonTrace|Armor|TRIGGER] action=check_light result=OK light=3 threshold=7 ctx{p=Steve}
[MoonTrace|Armor|TRIGGER] action=check_cd result=BLOCKED cd_total=800 cd_left=120 ctx{p=Steve}
```

---

## 必打数字

### 冷却 (CD)

```
cd_total=<总冷却ticks> cd_left=<剩余ticks> cd_key=<冷却key>
```

示例：
```
[MoonTrace|Armor|TRIGGER] action=check result=BLOCKED reason=cd_hit cd_total=800 cd_left=120 cd_key=sentinel_glow ctx{p=Steve}
```

### 随机 (RNG)

```
rng{roll=<实际值> need=<阈值> hit=<YES/NO>}
```

示例：
```
[MoonTrace|Armor|TRIGGER] action=rng_check result=OK rng{roll=0.12 need=0.15 hit=YES} ctx{p=Steve}
```

---

## 启动/应用/状态 打印策略

### BOOT（一次）

配置加载时输出 debug 开关 + 关键参数摘要：

```
[MoonTrace|Armor|BOOT] action=config_load result=OK debug=false helmets_loaded=5
[MoonTrace|Armor|BOOT] action=register result=OK item=sentinel_helmet
```

### APPLY（事件）

应用效果时输出 final 值；如有修正则输出 `src + clamp`：

```
[MoonTrace|Armor|APPLY] action=apply result=OK effect=glowing final{dur=100 amp=0} targets=3 ctx{p=Steve}
[MoonTrace|Armor|APPLY] action=apply result=OK effect=damage_reduction final{amount=2.0} src{original=5.0} ctx{p=Steve t=Zombie}
```

### STATE（变化）

套装完成/解除、重要状态变化：

```
[MoonTrace|Armor|STATE] action=state_change result=OK state=low_health_mode enabled=true health_pct=0.35 ctx{p=Steve}
[MoonTrace|Armor|STATE] action=state_change result=OK state=target_detected mob=Zombie first_time=true ctx{p=Steve}
```

---

## reason 词典（固定用词）

| 词 | 含义 |
|----|------|
| `not_wearing_helmet` | 未穿戴对应头盔 |
| `not_wearing_full_set` | 未穿戴全套 |
| `not_overworld` | 不在主世界 |
| `not_night` | 不是夜晚 |
| `not_outdoor` | 不在户外 |
| `not_dark` | 光照不足以触发 |
| `cd_hit` | 冷却中 |
| `rng_fail` | 概率未命中 |
| `boss_block` | Boss 实体豁免 |
| `target_invalid` | 目标无效 |
| `damage_too_low` | 伤害不足阈值 |
| `damage_not_hostile` | 伤害来源非敌对生物 |
| `damage_not_explosion` | 伤害非爆炸类型 |
| `health_above_threshold` | 血量高于阈值 |
| `totem_active` | 图腾优先触发 |
| `already_triggered` | 已触发过（边沿触发） |
| `config_missing_defaulted` | 配置缺失，使用默认值 |
| `param_clamped` | 参数被限制在合法范围 |

---

## Copy-Paste 模板

### Trigger OK (INFO)

```java
logger.info("[MoonTrace|Armor|TRIGGER] action=trigger result=OK effect={} ctx{{p={} t={} dim={}}}",
    effectId, player.getName(), target, dimension);
```

### Trigger BLOCKED (INFO)

```java
logger.info("[MoonTrace|Armor|TRIGGER] action=check result=BLOCKED reason={} ctx{{p={} t={}}}",
    reason, player.getName(), target);
```

### Apply OK (INFO)

```java
logger.info("[MoonTrace|Armor|APPLY] action=apply result=OK effect={} final{{dur={} amp={}}} ctx{{p={} t={}}}",
    effectId, duration, amplifier, player.getName(), target);
```

### WARN clamp / missing

```java
logger.warn("[MoonTrace|Armor|BOOT] action=param_validate result=FAIL reason=param_clamped key={} src={} clamp_to={}",
    paramKey, srcValue, clampedValue);

logger.warn("[MoonTrace|Armor|BOOT] action=config_load result=FAIL reason=config_missing_defaulted key={} default={}",
    configKey, defaultValue);
```

---

## 验收必备日志（每个 Feature）

每个功能模块必须能在 INFO 级别看到以下日志：

1. `config_load OK` - 配置加载成功
2. `register OK` - 物品注册成功
3. `trigger OK` - 效果触发成功
4. `apply OK` - 效果应用成功
5. `state_change OK` - 状态变化（如有）

---

## Java 实现示例

```java
public class ModLogger {
    private static final Logger LOGGER = LoggerFactory.getLogger("MoonTrace");
    private static boolean debugEnabled = false;

    public static void setDebug(boolean enabled) {
        debugEnabled = enabled;
    }

    public static void trace(String feature, String phase, String message, Object... args) {
        if (debugEnabled) {
            LOGGER.info("[MoonTrace|{}|{}] " + message, prepend(feature, phase, args));
        }
    }

    public static void info(String feature, String phase, String message, Object... args) {
        LOGGER.info("[MoonTrace|{}|{}] " + message, prepend(feature, phase, args));
    }

    public static void warn(String feature, String phase, String message, Object... args) {
        LOGGER.warn("[MoonTrace|{}|{}] " + message, prepend(feature, phase, args));
    }

    private static Object[] prepend(String feature, String phase, Object[] args) {
        Object[] result = new Object[args.length + 2];
        result[0] = feature;
        result[1] = phase;
        System.arraycopy(args, 0, result, 2, args.length);
        return result;
    }
}
```

---

## 日志筛选命令

```bash
# 只看 Armor 模块
grep "MoonTrace|Armor" latest.log

# 只看 TRIGGER 阶段
grep "TRIGGER" latest.log

# 只看失败
grep "result=BLOCKED\|result=FAIL" latest.log

# 只看特定玩家
grep "p=Steve" latest.log
```
