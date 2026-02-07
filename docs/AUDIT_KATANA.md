# Katana / Katana-like Audit (Code as Source of Truth)

Date: 2026-02-07  
Project: MysteriousMerchant (`modid = mymodtest`)

## 1) Scope and roster (from code, no guessing)

### Registered katana artifacts
- `mymodtest:moon_glow_katana` (`src/main/java/mod/test/mymodtest/katana/item/KatanaItems.java`)
- `mymodtest:regret_blade` (`src/main/java/mod/test/mymodtest/katana/item/KatanaItems.java`)
- `mymodtest:eclipse_blade` (`src/main/java/mod/test/mymodtest/katana/item/KatanaItems.java`)
- `mymodtest:oblivion_edge` (`src/main/java/mod/test/mymodtest/katana/item/KatanaItems.java`)
- `mymodtest:nmap_katana` (`src/main/java/mod/test/mymodtest/katana/item/KatanaItems.java`)
- `mymodtest:acer` (`src/main/java/mod/test/mymodtest/weapon/transitional/item/TransitionalWeaponItems.java`)
- `mymodtest:velox` (`src/main/java/mod/test/mymodtest/weapon/transitional/item/TransitionalWeaponItems.java`)
- `mymodtest:fatalis` (`src/main/java/mod/test/mymodtest/weapon/transitional/item/TransitionalWeaponItems.java`)

### Katana tag check
- Tag file: `src/main/resources/data/mymodtest/tags/item/katana.json`
- Included: `moon_glow_katana`, `regret_blade`, `eclipse_blade`, `oblivion_edge`, `nmap_katana`, `acer`, `fatalis`
- Excluded: `velox` (intentional, matches current rule “exclude velox”)

## 2) Per-weapon real spec (mechanics + params)

## `mymodtest:moon_glow_katana`
- Base attributes:
  - `SwordItem.createAttributeModifiers(ToolMaterials.NETHERITE, 3, -2.2f)` (`MoonGlowKatanaItem#createAttributeModifiers`)
  - Durability: `1765` (`KatanaItems.KATANA_MAX_DURABILITY`)
  - Enchantability source: inherited Sword/Netherite path (`MoonGlowKatanaItem` has no `getEnchantability()` override)
- Trigger conditions:
  - Attack callback entry: `MoonTraceHandler#register`
  - Target must be `LivingEntity`, main hand must be Moon Glow
  - Moonlight path: requires `world.isNight()` + `isSkyVisible` + sky light `>= 8` (`MoonTraceConfig.REQUIRE_NIGHT/REQUIRE_MOONLIGHT/SKY_LIGHT_THRESHOLD`)
  - Light path fallback: when moonlight path not satisfied and total light `max(block, sky) >= 12` (`LIGHT_MARK_ENABLED`, `LIGHT_MARK_MIN_LIGHT`)
  - Crit guarantee: `CRIT_GUARANTEES_MARK = true`
  - Per-target mark cooldown: `40 ticks` (`MARK_COOLDOWN`, two independent maps for moonlight/light)
- Effects:
  - On mark apply (`applyInstantEffects`):
    - Instant damage random `[1.0, 2.0]`
    - Slowness I `15 ticks` (`SLOWNESS_AMPLIFIER=0`, `SLOWNESS_DURATION=15`)
    - Glowing `12 ticks`
  - On mark consume (`applyConsumeBonus`):
    - Physical bonus random `[5.0, 9.0]`
    - Armor penetration: normal `50%`, boss `25%`
    - If consuming `LIGHT_MARK`, all consume damage multiplied by `0.70`
    - Extra delayed magic (next tick):
      - Normal: `1.0 + min(maxHP*0.02, 4.0)`
      - Boss: `0.5 + min(maxHP*0.01, 2.0)`
  - Passive while holding in valid moonlight:
    - Speed I + Night Vision refreshed every `40 ticks`, each duration `60 ticks` (`tickSpeedBuff`)
- Boss rule:
  - Boss check is `EnderDragonEntity` / `WitherEntity` (`MoonTraceHandler#isBoss`)
  - Not forbidden (`FORBID_BOSS=false`), but consume scaling is weaker on boss
- Debug/log:
  - `MoonTraceConfig.DEBUG` controls verbose logs
  - Recommendation: keep for balancing; disable in release

## `mymodtest:regret_blade`
- Base attributes:
  - `SwordItem.createAttributeModifiers(ToolMaterials.NETHERITE, 3, -2.2f)` (`RegretBladeItem`)
  - Durability: `1765`
  - Enchantability source: inherited Sword/Netherite path
- Trigger conditions (`LifeCutHandler#register`, `shouldTrigger`):
  - 30% proc (`LifeCutConfig.TRIGGER_CHANCE`)
  - Target HP must be `> 10` (`MIN_HEALTH_TO_TRIGGER`)
  - Per-target cooldown: `60 ticks` (`LIFECUT_TRIGGER_CD_TICKS`)
- Effects (`scheduleLifeCut`):
  - Base cut = current HP * `0.30`
  - Boss multiplier: `0.333`
  - Armor penetration:
    - Normal `35%`
    - Boss `35%`
  - Uses MC armor formula after penetration
  - Cannot kill clamp: leaves at least `1 HP` (`CANNOT_KILL`, `MIN_HEALTH_AFTER_CUT`)
  - Damage applied next tick via queue (`tickDelayedDamage`)
- Boss rule:
  - `ALLOW_BOSS=true`
  - Boss check: dragon/wither
- Debug/log:
  - `LifeCutConfig.DEBUG` controls detailed trigger math logs
  - Recommendation: keep in test, off in release

## `mymodtest:eclipse_blade`
- Base attributes:
  - `SwordItem.createAttributeModifiers(ToolMaterials.NETHERITE, 3, -2.2f)` (`EclipseBladeItem`)
  - Durability: `1765`
  - Enchantability source: inherited Sword/Netherite path
- Trigger conditions (`EclipseHandler#register`, `shouldTrigger`):
  - 40% proc (`EclipseConfig.TRIGGER_CHANCE`)
  - Per-target cooldown: `50 ticks` (`TRIGGER_CD_TICKS`)
- Effects:
  - Applies Eclipse mark (`EclipseManager`) with duration:
    - Normal `60 ticks`
    - Boss `30 ticks` (`BOSS_DURATION_MULTIPLIER=0.5`)
  - Adds `Glowing` plus 2 debuffs selected from weighted combo table (`EclipseHandler` static combos)
  - Penetration state values defined as:
    - Base `15%`
    - Marked `25%`
    - Current implementation note: handler comments declare this state for display/logging (no explicit extra-damage compensation in this handler)
- Boss rule:
  - Boss check: dragon/wither
- Debug/log:
  - `EclipseConfig.DEBUG` controls logs
  - Recommendation: keep in QA; off in release

## `mymodtest:oblivion_edge`
- Base attributes:
  - `SwordItem.createAttributeModifiers(ToolMaterials.NETHERITE, 2, -2.2f)` (`OblivionEdgeItem`)
  - Durability: `1765`
  - Enchantability source: inherited Sword/Netherite path
- Layer 1 / ReadWrite trigger (`OblivionHandler#shouldApplyReadWrite`):
  - 25% proc (`READWRITE_CHANCE`)
  - Per-target cooldown:
    - Normal `100 ticks`
    - Boss `200 ticks` (`BOSS_COOLDOWN_MULTIPLIER=2.0`)
  - Duration:
    - Normal `50 ticks`
    - Boss `25 ticks` (`BOSS_DURATION_MULTIPLIER=0.5`)
- Layer 2 / Debuff:
  - Random 50/50:
    - Weakness II
    - Slowness II
  - Plus Glowing for mark duration
- Layer 3 / Causality (`shouldTriggerCausality` + `applyCausality`):
  - Requires existing ReadWrite mark
  - Player HP ratio `< 50%`
  - Target HP must be `> player HP`
  - Proc chance:
    - Normal `20%`
    - Boss `6.6667%`
  - Player cooldown:
    - Normal `500 ticks` (25s)
    - Boss `900 ticks` (45s)
  - Effect: target HP set to `min(targetHP, playerHP)` (i.e., lowered to player HP when above it)
- Layer 4 / Penetration bonus damage (`applyArmorPenetration`):
  - Only on ReadWrite targets
  - Compensation = `baseDamage(5.0) * min(armor*0.04, 0.8) * penetration`
  - Penetration:
    - Normal `35%`
    - Boss `17.5%`
  - Applied as magic damage if compensation `> 0.3`
- Debug/log:
  - `OblivionConfig.DEBUG` controls logs
  - Recommendation: keep for tuning, disable in release

## `mymodtest:nmap_katana`
- Base attributes:
  - `SwordItem.createAttributeModifiers(ToolMaterials.NETHERITE, 2, -2.2f)` (`NmapKatanaItem`)
  - Durability: `1765`
  - Enchantability source: inherited Sword/Netherite path
- Module A / Host Discovery (`NmapScanHandler`):
  - Scan radius `50` every:
    - Normal `60 ticks`
    - During cooldown `40 ticks`
  - If hostile found and scan not on cooldown:
    - Applies Resistance V (`amplifier=4`) for `60 ticks`
    - Marks shield active
  - On player damaged while shield active (`LivingEntityMixin -> NmapScanHandler#onPlayerDamaged`):
    - Cooldown `1200 ticks` if from hostile monster
    - Cooldown `3600 ticks` otherwise
- Module B / Port Enumeration (`NmapManager`):
  - Window `200 ticks`
  - +5% penetration per unique hostile UUID
  - Cap `35%` (max count 7)
  - Applied in attack handler as magic compensation
- Module C / Vulnerability Scan (`NmapAttackHandler`):
  - Conditions:
    - target armor == 0
    - not dragon/wither
    - crit cooldown ready
  - Bonus: `+50%` of baseDamage(5.0) => `+2.5` player-attack damage
  - Cooldown `60 ticks`
- Module D / Firewall (`NmapFirewallHandler` via `LivingEntityMixin`):
  - Harmful debuff block from hostile source:
    - chance normal `40%`, boss `20%`
    - cooldown `120 ticks`
  - Projectile block from hostile owner:
    - chance normal `35%`, boss `15%`
    - cooldown `120 ticks`
- Debug/log:
  - `NmapConfig.DEBUG` controls module logs
  - Recommendation: keep temporarily; off in release

## `mymodtest:acer` (transitional)
- Base attributes:
  - Damage/speed source: `TransitionalWeaponConstants.ACER_BASE_DAMAGE=6`, `ACER_ATTACK_SPEED=-2.2f` (`AcerItem#createSettings`)
  - Durability: `287` (`ACER_DURABILITY`)
  - Enchantability: override returns `15` (`AcerItem#getEnchantability`)
- Special effect:
  - Critical multiplier modified from 1.5x to 1.7x
  - Injection point: `PlayerEntity#attack`, `AcerCritMixin#transitional$acerCritMultiplier`
  - Only when main hand is Acer and vanilla critical condition matches
- Debug/log:
  - `TransitionalWeaponConstants.DEBUG` toggles diagnostic logs in mixin

## `mymodtest:velox` (transitional, intentionally not in katana tag)
- Base attributes:
  - Damage/speed source: `VELOX_BASE_DAMAGE=5`, `VELOX_ATTACK_SPEED=-1.8f` (`VeloxItem#createSettings`)
  - Durability: `262` (`VELOX_DURABILITY`)
  - Enchantability: override returns `14` (`SWORD_ENCHANTABILITY`)
- Special effect:
  - None (pure stat weapon)
- Debug/log:
  - No dedicated combat effect logger

## `mymodtest:fatalis` (transitional)
- Base attributes:
  - Damage/speed source: `FATALIS_BASE_DAMAGE=10`, `FATALIS_ATTACK_SPEED=-2.2f` (`FatalisItem#createSettings`)
  - Durability: `287` (`FATALIS_DURABILITY`)
  - Enchantability: override returns `15` (`KATANA_ENCHANTABILITY`)
- Special effect:
  - None (pure stat weapon)
- Debug/log:
  - No dedicated combat effect logger

## 3) Sweeping / enchant constraints status (code check)

- Sweeping attack path disabled for items in `mymodtest:katana`:
  - `PlayerEntitySweepMixin` redirects sweep AOE list, sweep sound, and sweep particles.
- Sweeping Edge enchant blocked for `mymodtest:katana`:
  - `EnchantmentMixin` intercepts `isPrimaryItem` and `isAcceptableItem` for `Enchantments.SWEEPING_EDGE`.
- `velox` is outside katana tag, so it keeps vanilla sword sweep behavior.
