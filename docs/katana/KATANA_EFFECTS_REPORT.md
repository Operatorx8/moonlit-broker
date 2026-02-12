# Katana Effects Report
Generated on: 2026-02-05T01:48:27-08:00

## Quick Index
- moon_glow_katana
- regret_blade
- eclipse_blade
- oblivion_edge
- nmap_katana

---

## moon_glow_katana
- Item ID: `xqanzd_moonlit_broker:moon_glow_katana`

### Trigger Conditions
- Attack trigger entrypoint: `AttackEntityCallback` handler in `MoonTraceHandler.register()`.
- Must be server-side, target must be `LivingEntity`, attacker main hand must be `MOON_GLOW_KATANA`.
- Effect order per hit:
- First tries to consume existing MoonTrace mark via `MoonTraceManager.getAndConsume(target, player)` (only if mark unexpired and created by same player UUID).
- If no consumable mark, tries to apply new mark via `shouldTrigger(...)`.
- New-mark trigger predicates:
- Night required: `MoonTraceConfig.REQUIRE_NIGHT = true`.
- Moonlight required: sky visible + sky light >= `MoonTraceConfig.SKY_LIGHT_THRESHOLD` (8).
- Boss allowed (`FORBID_BOSS = false`). Boss check includes only `EnderDragonEntity` / `WitherEntity`.
- Per-target mark cooldown by entity ID must be expired (`MoonTraceConfig.MARK_COOLDOWN`).
- If critical-hit condition true and `CRIT_GUARANTEES_MARK = true`, mark triggers without RNG.
- Otherwise RNG: `roll < clamp(CHANCE_BASE + CHANCE_MOON_SCALE * moonFactor, CHANCE_MIN, CHANCE_MAX)`.
- Passive buff trigger entrypoint: `MoonTraceHandler.tickSpeedBuff(world)` (called each world tick from `KatanaInit`).
- Player must hold Moon Glow in main hand.
- Must satisfy night + moonlit checks (same logic as above).
- Refresh interval gate via `speedBuffLastRefresh` map.

### Primary Effects
- On new mark trigger:
- Immediate physical damage (`playerAttack`) random range 1.0-2.0.
- Applies `Slowness I` for 15 ticks.
- Applies `Glowing` for 12 ticks.
- Stores mark in `MoonTraceManager` for 60 ticks with source player UUID.
- On mark consumption:
- Bonus physical damage with armor formula and penetration:
- Normal targets: 50% armor penetration.
- Boss targets: 25% armor penetration.
- Base random pre-mitigation bonus 5.0-9.0.
- Schedules delayed magic bonus next tick (anti-invulnerability-frame):
- `baseMagic + min(maxHP * percent, cap)`.
- Normal: `1.0 + min(maxHP*0.02, 4.0)`.
- Boss: `0.5 + min(maxHP*0.01, 2.0)`.
- Passive buffs while holding at valid moon conditions:
- `Speed I` for 60 ticks (refresh style).
- `Night Vision` for 60 ticks.

### Cooldown / Frequency
- Mark application cooldown: per-target (`entityId`) 40 ticks.
- Mark lifetime: 60 ticks.
- Speed/NightVision refresh interval: 40 ticks.
- Delayed magic queue processed every server tick via `tickDelayedMagic`.

### Parameters (source-of-truth)
- `REQUIRE_NIGHT = true`, `REQUIRE_MOONLIGHT = true`, `SKY_LIGHT_THRESHOLD = 8` (unit: skylight level), `MoonTraceConfig`.
- `CRIT_GUARANTEES_MARK = true`, `CHANCE_BASE = 0.20`, `CHANCE_MOON_SCALE = 0.30`, `CHANCE_MIN = 0.15`, `CHANCE_MAX = 0.55`, `MoonTraceConfig`.
- `MOON_PHASE_NEW = 0.20`, `MOON_PHASE_FULL = 1.00` (moon factor), `MoonTraceConfig`.
- `INSTANT_DAMAGE_MIN = 1.0`, `INSTANT_DAMAGE_MAX = 2.0` (health points), `MoonTraceConfig`.
- `SLOWNESS_DURATION = 15` ticks, `SLOWNESS_LEVEL = 0`, `MoonTraceConfig`.
- `MARK_DURATION = 60` ticks, `MARK_COOLDOWN = 40` ticks, `MoonTraceConfig`.
- `GLOWING_DURATION = 12` ticks, `MoonTraceConfig`.
- `CONSUME_DAMAGE_MIN = 5.0`, `CONSUME_DAMAGE_MAX = 9.0`, `MoonTraceConfig`.
- `ARMOR_PEN_NORMAL = 0.50`, `ARMOR_PEN_BOSS = 0.25`, `MoonTraceConfig`.
- `MAGIC_BASE_NORMAL = 1.0`, `MAGIC_BASE_BOSS = 0.5`, `MAGIC_PERCENT_HP_NORMAL = 0.02`, `MAGIC_PERCENT_HP_BOSS = 0.01`, `MAGIC_PERCENT_CAP_NORMAL = 4.0`, `MAGIC_PERCENT_CAP_BOSS = 2.0`, `MoonTraceConfig`.
- `SPEED_BUFF_DURATION = 60` ticks, `SPEED_BUFF_LEVEL = 0`, `SPEED_BUFF_REFRESH_INTERVAL = 40` ticks, `MoonTraceConfig`.
- `NIGHT_VISION_DURATION = 60` ticks, `MoonTraceConfig`.

### Code Entry Points
- `src/main/java/dev/xqanzd/moonlitbroker/katana/effect/MoonTraceHandler.java` - `public static void register()`
- `src/main/java/dev/xqanzd/moonlitbroker/katana/effect/MoonTraceHandler.java` - `public static void tickSpeedBuff(World world)`
- `src/main/java/dev/xqanzd/moonlitbroker/katana/effect/MoonTraceHandler.java` - `public static void tickDelayedMagic(ServerWorld world)`
- `src/main/java/dev/xqanzd/moonlitbroker/katana/effect/MoonTraceManager.java` - `public static void applyMark(...)`, `public static Optional<MoonTraceState> getAndConsume(...)`
- `src/main/java/dev/xqanzd/moonlitbroker/katana/KatanaInit.java` - `public static void init()` (tick wiring)

### Notes / Risks
- Boss recognition is hardcoded to Wither/Dragon only; other "boss-like" entities are treated as normal.
- `MoonTraceConfig.DEBUG` defaults to `true` (high log volume risk).

## regret_blade
- Item ID: `xqanzd_moonlit_broker:regret_blade`

### Trigger Conditions
- Attack trigger entrypoint: `AttackEntityCallback` in `LifeCutHandler.register()`.
- Must be server-side, target `LivingEntity`, attacker main hand `REGRET_BLADE`.
- Per-target cooldown keyed by target UUID must be expired.
- Trigger guard checks:
- Target current HP must be >= `MIN_HEALTH_TO_TRIGGER` (10.0).
- `ONLY_UNDEAD = true`: target must match undead list (`Zombie`, `Skeleton`, `AbstractSkeleton`, `Phantom`, `Wither`, `ZombifiedPiglin`).
- RNG: `roll < TRIGGER_CHANCE`.

### Primary Effects
- On trigger, computes and queues delayed damage for next tick (`pendingDamage`):
- Base cut: `currentHp * HEALTH_CUT_RATIO` (30%).
- If boss (`Wither`/`Dragon`) and `ALLOW_BOSS=true`, base cut multiplied by `BOSS_EFFECT_MULTIPLIER` (0.333).
- Armor mitigation uses vanilla armor formula after applying configured penetration:
- Normal: 0% penetration.
- Boss: 35% penetration.
- If `CANNOT_KILL=true`, damage is clamped so target HP never drops below `MIN_HEALTH_AFTER_CUT` (1.0).
- Damage applied next tick with `playerAttack` source when player exists, else generic source.
- Visual/audio feedback immediately on schedule (damage indicator + soul particles, attack/warden sounds).

### Cooldown / Frequency
- Trigger chance: 30% per qualifying hit.
- Per-target cooldown: 60 ticks.
- Delayed damage is processed once per tick by `tickDelayedDamage`.

### Parameters (source-of-truth)
- `TRIGGER_CHANCE = 0.30`, `MIN_HEALTH_TO_TRIGGER = 10.0`, `LIFECUT_TRIGGER_CD_TICKS = 60`, `LifeCutConfig`.
- `HEALTH_CUT_RATIO = 0.30`, `LifeCutConfig`.
- `ARMOR_PENETRATION_NORMAL = 0.00`, `ARMOR_PENETRATION_BOSS = 0.35`, `LifeCutConfig`.
- `ALLOW_BOSS = true`, `BOSS_EFFECT_MULTIPLIER = 0.333`, `LifeCutConfig`.
- `ONLY_UNDEAD = true`, `CANNOT_KILL = true`, `MIN_HEALTH_AFTER_CUT = 1.0`, `LifeCutConfig`.

### Code Entry Points
- `src/main/java/dev/xqanzd/moonlitbroker/katana/effect/LifeCutHandler.java` - `public static void register()`
- `src/main/java/dev/xqanzd/moonlitbroker/katana/effect/LifeCutHandler.java` - `public static void tickDelayedDamage(ServerWorld world)`
- `src/main/java/dev/xqanzd/moonlitbroker/katana/effect/LifeCutHandler.java` - `public static void cleanupCooldowns(long currentTick)`
- `src/main/java/dev/xqanzd/moonlitbroker/katana/KatanaInit.java` - `public static void init()` (tick wiring)

### Notes / Risks
- Ender Dragon is treated as boss in `isBoss`, but also fails `ONLY_UNDEAD`; result: dragon never triggers LifeCut.
- Tooltip text says "Armor Penetration: 80% (Boss: 35%)" but config implements `0%` normal / `35%` boss.

## eclipse_blade
- Item ID: `xqanzd_moonlit_broker:eclipse_blade`

### Trigger Conditions
- Attack trigger entrypoint: `AttackEntityCallback` in `EclipseHandler.register()`.
- Must be server-side, target `LivingEntity`, attacker main hand `ECLIPSE_BLADE`.
- Per-target trigger cooldown map (`TRIGGER_COOLDOWNS`) keyed by target UUID must pass (`elapsed >= TRIGGER_CD_TICKS`).
- RNG: `roll < TRIGGER_CHANCE`.
- No night/weather/sky requirement.

### Primary Effects
- On trigger:
- Compute mark/debuff duration: 60 ticks, boss duration halved (`*0.5` => 30 ticks for Wither/Dragon).
- Apply `Glowing` for duration.
- Apply exactly two debuffs from weighted combo table (`DebuffCombo`), with strong-control mutual exclusion (no Darkness+Blindness pair).
- Debuffs chosen from Darkness/Blindness/Weakness/Slowness/Wither.
- All debuff amplifiers are config value `0` (level I).
- Applies Eclipse mark in `EclipseManager` (used for state tracking and debug/log context).
- Spawns particles/sounds.

### Cooldown / Frequency
- Trigger chance: 40% per hit.
- Per-target trigger cooldown: 50 ticks.
- Mark/debuff duration: 60 ticks normal, 30 ticks boss.

### Parameters (source-of-truth)
- `TRIGGER_CHANCE = 0.40`, `TRIGGER_CD_TICKS = 50`, `EclipseConfig`.
- `MARK_DURATION_TICKS = 60`, `BOSS_DURATION_MULTIPLIER = 0.5`, `EclipseConfig`.
- `BASE_ARMOR_PENETRATION = 0.15`, `MARKED_ARMOR_PENETRATION = 0.25`, `EclipseConfig` (logging/display only in current code).
- Amplifiers: `WEAKNESS_AMPLIFIER = 0`, `WITHER_AMPLIFIER = 0`, `SLOWNESS_AMPLIFIER = 0`, `BLINDNESS_AMPLIFIER = 0`, `DARKNESS_AMPLIFIER = 0`, `EclipseConfig`.
- Combo weights:
- `WEIGHT_DARKNESS_WEAKNESS=12`, `WEIGHT_DARKNESS_SLOWNESS=12`, `WEIGHT_BLINDNESS_WEAKNESS=12`, `WEIGHT_BLINDNESS_SLOWNESS=12`, `WEIGHT_WEAKNESS_SLOWNESS=10`, `WEIGHT_DARKNESS_WITHER=4`, `WEIGHT_BLINDNESS_WITHER=4`, `WEIGHT_WEAKNESS_WITHER=2`, `WEIGHT_SLOWNESS_WITHER=2`, `EclipseConfig`.
- Runtime adjustment: +15 is added to Darkness+Weakness and Blindness+Weakness in `EclipseHandler` static init.

### Code Entry Points
- `src/main/java/dev/xqanzd/moonlitbroker/katana/effect/EclipseHandler.java` - `public static void register()`
- `src/main/java/dev/xqanzd/moonlitbroker/katana/effect/EclipseManager.java` - `public static boolean hasMark(...)`, `public static void applyMark(...)`
- `src/main/java/dev/xqanzd/moonlitbroker/katana/effect/EclipseManager.java` - `public static void tickCleanup(long currentTick)`
- `src/main/java/dev/xqanzd/moonlitbroker/katana/KatanaInit.java` - `public static void init()` (tick wiring)

### Notes / Risks
- Config comments/lang imply stronger armor penetration behavior (35%/70%), but implementation only logs/display-equivalent 15%/25% and does not modify damage.
- `WEIGHT_DARKNESS_BLINDNESS` exists in config but is not used by runtime combo builder.

## oblivion_edge
- Item ID: `xqanzd_moonlit_broker:oblivion_edge`

### Trigger Conditions
- Attack trigger entrypoint: `AttackEntityCallback` in `OblivionHandler.register()`.
- Must be server-side, target `LivingEntity`, attacker main hand `OBLIVION_EDGE`.
- On every valid hit, reads whether target currently has ReadWrite mark via `OblivionManager.hasReadWrite(target, currentTick)`.
- ReadWrite application trigger:
- Target ReadWrite cooldown must pass (`OblivionManager.canApplyReadWrite`).
- RNG: `roll < READWRITE_CHANCE`.
- Causality (HP equalization) trigger requires:
- Target currently has ReadWrite mark.
- Player HP ratio `< PLAYER_HP_THRESHOLD` (strictly < 50%).
- Target current HP `>` player HP before hit.
- Player causality cooldown expired.
- RNG chance (`CAUSALITY_CHANCE` or boss variant).

### Primary Effects
- Layer 1/2 (ReadWrite):
- Apply ReadWrite mark state (stores expire tick + source player UUID).
- Set per-target ReadWrite cooldown.
- Apply `Glowing` + one random debuff (50/50): `Weakness II` or `Slowness II` for mark duration.
- Layer 3 (Causality):
- Set target HP directly to `min(targetHpBefore, playerHpBeforeHit)`; effectively equalizes down to player HP when condition passes.
- Sets player-level causality cooldown (normal/boss values differ).
- Layer 4 (armor penetration compensation):
- If target has ReadWrite, apply bonus magic damage based on target armor and penetration ratio.
- Formula uses hardcoded `baseDamage = 5.0`; `armorReduction = min(armor*0.04, 0.8)`; `bonus = baseDamage * armorReduction * penetration`.
- Bonus only applied if `bonus > 0.3`.

### Cooldown / Frequency
- ReadWrite chance: 25% on eligible hits.
- ReadWrite duration: 50 ticks normal, 25 ticks boss.
- ReadWrite target cooldown: 100 ticks normal, 200 ticks boss.
- Causality chance: 20% normal, 6.6667% boss.
- Causality cooldown: 500 ticks normal, 900 ticks boss (player-level).

### Parameters (source-of-truth)
- `READWRITE_CHANCE = 0.25`, `READWRITE_DURATION_TICKS = 50`, `READWRITE_COOLDOWN_TICKS = 100`, `OblivionConfig`.
- `BOSS_DURATION_MULTIPLIER = 0.5`, `BOSS_COOLDOWN_MULTIPLIER = 2.0`, `OblivionConfig`.
- `DEBUFF_AMPLIFIER = 1` (level II), `OblivionConfig`.
- `PLAYER_HP_THRESHOLD = 0.5`, `CAUSALITY_CHANCE = 0.20`, `CAUSALITY_CHANCE_BOSS = 0.066667`, `OblivionConfig`.
- `CAUSALITY_COOLDOWN_TICKS = 500`, `CAUSALITY_COOLDOWN_BOSS_TICKS = 900`, `OblivionConfig`.
- `ARMOR_PENETRATION = 0.35`, `ARMOR_PENETRATION_BOSS = 0.175`, `OblivionConfig`.

### Code Entry Points
- `src/main/java/dev/xqanzd/moonlitbroker/katana/effect/OblivionHandler.java` - `public static void register()`
- `src/main/java/dev/xqanzd/moonlitbroker/katana/effect/OblivionManager.java` - `canApplyReadWrite(...)`, `applyReadWrite(...)`, `hasReadWrite(...)`, `isCausalityOnCooldown(...)`, `setCausalityCooldown(...)`
- `src/main/java/dev/xqanzd/moonlitbroker/katana/effect/OblivionManager.java` - `public static void tickCleanup(long currentTick)`
- `src/main/java/dev/xqanzd/moonlitbroker/katana/KatanaInit.java` - `public static void init()` (tick wiring)

### Notes / Risks
- Causality uses `setHealth` direct assignment (bypasses normal damage pipeline/events).
- Tooltip/lang says 20% armor penetration on marked targets, but config/logic use 35% normal targets-with-mark and 17.5% boss.

## nmap_katana
- Item ID: `xqanzd_moonlit_broker:nmap_katana`

### Trigger Conditions
- Attack trigger entrypoint: `AttackEntityCallback` in `NmapAttackHandler.register()`.
- Must be server-side, target `LivingEntity`, attacker main hand `NMAP_KATANA`.
- Scan/shield entrypoint: `ServerTickEvents.END_SERVER_TICK` in `NmapScanHandler.register()`.
- Player must hold Nmap in main hand or offhand.
- Runs scans on interval depending on cooldown state.
- Debuff/projectile firewall entrypoints via mixin hooks in `LivingEntityMixin`:
- `addStatusEffect(..., source)` HEAD cancel path for harmful debuffs from hostile source.
- `damage(...)` HEAD cancel path for projectile damage from hostile owner.
- Shield-break cooldown trigger via same damage mixin (`NmapScanHandler.onPlayerDamaged`).

### Primary Effects
- Host Discovery module:
- Periodically scans hostile entities in radius 50 around player.
- If hostiles found and scan not in cooldown: applies `Resistance V` and marks shield active.
- If player is damaged while shield active: shield drops and scan enters cooldown (duration depends on damage source hostility).
- Port Enumeration module:
- During scan, each distinct hostile UUID inside 10s window increases penetration +5%, capped at 35% (max count 7).
- Attack consumes current penetration value to add bonus magic damage vs armored targets.
- Vulnerability Scan module:
- On attack, if target armor == 0 and target is not Wither/Dragon and vuln cooldown ready: applies extra damage for 1.5x total hit equivalent (`+50%` of hardcoded base 5.0).
- Firewall Bypass module:
- Chance to cancel harmful status effects from hostile source (debuff firewall).
- Chance to cancel hostile projectile damage.
- Separate cooldowns for debuff and projectile interceptions.

### Cooldown / Frequency
- Scan radius: 50 blocks.
- Scan interval: 60 ticks normally; 40 ticks while scan cooldown active.
- Shield effect duration: 60 ticks per activation.
- Shield break cooldowns:
- Hostile hit: 1200 ticks.
- Non-hostile/other damage: 3600 ticks.
- Enumeration window: 200 ticks.
- Penetration growth: +5% per unique hostile, cap 35%, max 7 counted.
- Vulnerability crit cooldown: 60 ticks.
- Firewall chances/cooldowns:
- Debuff: 40% normal hostile source, 20% boss source; 120-tick cooldown.
- Projectile: 35% normal hostile owner, 15% boss owner; 120-tick cooldown.

### Parameters (source-of-truth)
- `SCAN_RADIUS = 50`, `SCAN_INTERVAL_TICKS = 60`, `COOLDOWN_SCAN_INTERVAL_TICKS = 40`, `NmapConfig`.
- `RESISTANCE_DURATION_TICKS = 60`, `RESISTANCE_AMPLIFIER = 4`, `NmapConfig`.
- `COOLDOWN_HOSTILE_HIT_TICKS = 1200`, `COOLDOWN_OTHER_DAMAGE_TICKS = 3600`, `NmapConfig`.
- `ENUM_WINDOW_TICKS = 200`, `PENETRATION_PER_HOSTILE = 0.05`, `PENETRATION_CAP = 0.35`, `PENETRATION_MAX_COUNT = 7`, `NmapConfig`.
- `VULN_CRIT_COOLDOWN_TICKS = 60`, `VULN_CRIT_MULTIPLIER = 1.5`, `NmapConfig`.
- `FIREWALL_DEBUFF_CHANCE = 0.40`, `FIREWALL_DEBUFF_CHANCE_BOSS = 0.20`, `FIREWALL_DEBUFF_COOLDOWN_TICKS = 120`, `NmapConfig`.
- `FIREWALL_PROJ_CHANCE = 0.35`, `FIREWALL_PROJ_CHANCE_BOSS = 0.15`, `FIREWALL_PROJ_COOLDOWN_TICKS = 120`, `NmapConfig`.

### Code Entry Points
- `src/main/java/dev/xqanzd/moonlitbroker/katana/effect/nmap/NmapAttackHandler.java` - `public static void register()`
- `src/main/java/dev/xqanzd/moonlitbroker/katana/effect/nmap/NmapScanHandler.java` - `public static void register()`, `public static void onPlayerDamaged(...)`
- `src/main/java/dev/xqanzd/moonlitbroker/katana/effect/nmap/NmapFirewallHandler.java` - `shouldBlockDebuff(...)`, `shouldBlockProjectile(...)`
- `src/main/java/dev/xqanzd/moonlitbroker/katana/mixin/LivingEntityMixin.java` - `katana$onAddStatusEffect(...)`, `katana$onDamage(...)`
- `src/main/java/dev/xqanzd/moonlitbroker/katana/effect/nmap/NmapManager.java` - state/cooldown/penetration management APIs

### Notes / Risks
- Debug log string says "Resistance V for 6s" but config duration is 60 ticks (3s).
- Multiple formulas use hardcoded `baseDamage = 5.0` in handlers, not derived from actual weapon attribute damage.
- "Vuln guaranteed crit" is gated by 60-tick cooldown and target filters (armor==0, not dragon/wither).

---

## Shared Systems & Helpers
- `src/main/java/dev/xqanzd/moonlitbroker/katana/KatanaInit.java`
- Responsibility: initializes katana subsystem, registers item/effect handlers, and central server-tick maintenance.
- Called from mod bootstrap `Mymodtest.onInitialize()`.
- `src/main/java/dev/xqanzd/moonlitbroker/katana/item/KatanaItems.java`
- Responsibility: registry for 5 katana items and item IDs (`xqanzd_moonlit_broker:*`).
- `src/main/java/dev/xqanzd/moonlitbroker/katana/mixin/LivingEntityMixin.java`
- Responsibility: central interception points for Nmap firewall and shield cooldown-on-damage.
- `src/main/java/dev/xqanzd/moonlitbroker/katana/mixin/EnchantmentMixin.java`
- Responsibility: blocks Sweeping Edge compatibility for all katanas.
- `src/main/java/dev/xqanzd/moonlitbroker/katana/effect/MoonTraceManager.java`
- Responsibility: Moon Glow mark lifecycle (`apply`, `consume`, cleanup).
- Called by `MoonTraceHandler` + `KatanaInit` cleanup tick.
- `src/main/java/dev/xqanzd/moonlitbroker/katana/effect/EclipseManager.java`
- Responsibility: Eclipse mark lifecycle + cleanup.
- Called by `EclipseHandler` + `KatanaInit` cleanup tick.
- `src/main/java/dev/xqanzd/moonlitbroker/katana/effect/OblivionManager.java`
- Responsibility: ReadWrite marks, per-target ReadWrite cooldowns, per-player causality cooldowns, cleanup.
- Called by `OblivionHandler` + `KatanaInit` cleanup tick.
- `src/main/java/dev/xqanzd/moonlitbroker/katana/effect/nmap/NmapManager.java`
- Responsibility: per-player Nmap state (scan cooldowns, shield active state, enumeration set/window, firewall cooldowns).
- Called by `NmapScanHandler`, `NmapAttackHandler`, `NmapFirewallHandler`, and `KatanaInit` cleanup tick.
- Delayed damage queues:
- `MoonTraceHandler.pendingMagicDamage` processed by `tickDelayedMagic` each world tick.
- `LifeCutHandler.pendingDamage` processed by `tickDelayedDamage` each world tick.

---

## Open Questions / TODOs
- Verify in-game whether Eclipse should have real armor penetration bonus or is intentionally debuff-only now (code currently debuff-only + logs).
- Suggested confirmation logs: `[Eclipse] Attack... (marked: ..., pen: ...%)` and combat damage traces.
- Verify Regret Blade undead-only scope is intended (Ender Dragon excluded by undead check despite boss handling path).
- Suggested confirmation logs: `[LifeCut] Skip: not undead` on dragon targets.
- Verify Nmap shield intended duration (log says 6s, config is 3s).
- Suggested confirmation logs: `[Nmap] SHIELD UP...` plus status effect remaining ticks in HUD.
- Verify all `DEBUG` flags should default to `true` in release builds:
- `MoonTraceConfig.DEBUG`, `LifeCutConfig.DEBUG`, `EclipseConfig.DEBUG`, `OblivionConfig.DEBUG`, `NmapConfig.DEBUG`.
