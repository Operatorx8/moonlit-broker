# Claude Code Implementation Instructions: Mysterious Merchant Trade System

## Goal
Implement the merchant trade system below with **minimal refactor**, **server-authoritative logic**, and **compilation-safe changes**.

System must match:
- Merchant Mark (UUID-bound membership)
- Trade Scroll (NBT: `uses` = 3 or 5, `grade` = `NORMAL`/`SEALED`)
- Silver Note (currency)
- Guide Scroll (first meet only, does not unlock)
- One merchant UI with paging: Normal / Secret; buttons Prev / Next / Refresh (C2S -> server validate -> swap offers)
- Secret gate requires all 3:
  1. has Merchant Mark
  2. has Sealed Trade Scroll with `uses >= 2`
  3. reputation >= 15 (increment only when purchase result is actually taken)
- Costs:
  - Open Normal page: scroll uses `-1`
  - Switch to Secret page: scroll uses `-2` (only on successful switch)
  - Refresh current page: scroll uses `-1` (Secret may add Silver fee/cooldown if easy)
- Secret limit per merchant entity:
  - exactly ONE epic katana ever sold (`secret_sold` persisted)
  - merchant stores `secret_katana_id` persisted
- Scroll sources:
  - chest loot 15% only for: Dungeon, Mineshaft, Stronghold, Shipwreck
  - mob drop 0.5% with condition: night+overworld surface OR equipped/elite mob
  - bounty submit: 100% one Trade Scroll (lightweight 2-item submit, no quest UI)
  - first meet gives Guide Scroll once/player
- Silver sources:
  - primary: exploration/combat (chest bundles, elite/night coins)
  - secondary: bounty small amount
  - anti-inflation: per-player mob silver drop rate limit (e.g. N per 10m)

---

## Required Docs Structure
Create these files under `docs/trade/`:

```text
docs/trade/
  SPEC.md
  PARAMS.md
  CHANGELOG.md
  TODO.md
  TESTPLAN.md
  BOUNTY_SCHEMA.md
  CLAUDE_CODE_INSTRUCTIONS.md
```

Keep each doc short and operational:
- `SPEC.md`: behavior rules + invariants
- `PARAMS.md`: all tunables, defaults, units
- `CHANGELOG.md`: versioned change notes
- `TODO.md`: unresolved tasks/risks
- `TESTPLAN.md`: reproducible manual cases
- `BOUNTY_SCHEMA.md`: submit inputs/outputs, validation, anti-abuse

---

## Minimal-Change Search/Hook Strategy
Before editing, locate existing code and patch in place:
1. Find merchant entity class (placeholder examples: `*Merchant*Entity*`, custom villager-like entity).
2. Find current trade opening hook and offer construction path.
3. Find current networking packet registration (C2S/S2C), screen handler, and merchant screen classes.
4. Find current persistent player state and world/entity NBT patterns already used in project.

Use code search first and reuse existing pipelines. Do not introduce parallel systems.

---

## Implementation Phases (0..5)

### Phase 0: Audit + Scaffolding
Tasks:
- Confirm concrete class names/paths for merchant entity, trade UI, packet registration, and persistent state.
- Add docs skeleton files listed above.
- Add central config constants (trade costs, gate thresholds, drop rates, silver cap window).

Acceptance:
- Build passes.
- No behavior change yet.
- Docs structure exists.

### Phase 1: Items + Data Contracts
Tasks:
- Add/verify 4 items: Merchant Mark, Trade Scroll, Silver Note, Guide Scroll.
- Implement Trade Scroll NBT contract:
  - `uses` (int; allowed initial values 3 or 5)
  - `grade` (string enum `NORMAL`/`SEALED`)
- Implement Merchant Mark UUID binding to holder player.
- Ensure Guide Scroll is informational only.

Acceptance:
- Items registered and obtainable.
- NBT reads/writes stable after relog.
- Build passes.

### Phase 2: State + Secret Constraints
Tasks:
- Player state:
  - reputation integer
  - first-meet guide-given flag
  - optional silver anti-inflation counters/window
- Merchant entity persisted NBT:
  - `secret_sold` (bool)
  - `secret_katana_id` (string/id)
- Initialize `secret_katana_id` once per merchant entity lifecycle.
- Enforce one-time epic katana sale per merchant entity.

Acceptance:
- Merchant `secret_sold` and `secret_katana_id` survive save/load.
- One merchant cannot sell second epic katana after first secret sale.
- Build passes.

### Phase 3: Paging UI + C2S Validation
Tasks:
- One merchant screen with pages: Normal / Secret.
- Buttons: Prev / Next / Refresh.
- Client button clicks send C2S packet with action + page context.
- Server validates all requests; server swaps offers and returns synced state.
- Cost application:
  - open Normal page -> scroll uses `-1`
  - successful switch to Secret -> uses `-2`
  - refresh current page -> uses `-1`
- Secret gate requires all 3 conditions before switch.
- If conditions fail: reject request, do not consume unintended costs.

Acceptance:
- Buttons operate through server-authoritative path only.
- Gate and costs strictly enforced.
- Failed switch does not consume Secret switch cost.
- Build passes.

### Phase 4: Reputation + Trade Result Hook
Tasks:
- Increment reputation **only** on successful purchase take-result event.
- Hook into actual result-taken path (not preview/select).
- Keep increment atomic and server-side.

Acceptance:
- Selecting offer alone does not increase reputation.
- Taking result increases by exactly 1 per completed purchase.
- Gate `reputation >= 15` behaves correctly.
- Build passes.

### Phase 5: Loot/Drop/Bounty/Silver Economy
Tasks:
- Trade Scroll chest loot 15% only in 4 categories:
  - Dungeon, Mineshaft, Stronghold, Shipwreck
- Mob drop 0.5% scroll with condition:
  - (night + overworld surface) OR (equipped/elite mob)
- Add lightweight bounty submit (2-item submit, no quest UI):
  - always grants 1 Trade Scroll
  - grants small Silver
- Silver acquisition:
  - chest bundles
  - elite/night combat coins
  - anti-inflation per-player rate limiting window (N per 10 min or configured equivalent)

Acceptance:
- Loot/drop rules match exactly and are testable.
- Bounty flow works without new quest system.
- Silver mob-drop limiter prevents farm inflation.
- Build passes.

---

## Minimal Module Checklist (Add/Modify)
Use existing names where available; otherwise create small focused modules.

### Items
- Add/modify item registration for:
  - Merchant Mark
  - Trade Scroll
  - Silver Note
  - Guide Scroll
- Add utility for Trade Scroll NBT and Mark ownership checks.

### Persistent State
- Player persistent data adapter (reputation, first-meet flag, silver rate-limit window).
- Merchant entity NBT extension (`secret_sold`, `secret_katana_id`).

### Trading / Offers
- Merchant offer builder/updater that is page-aware (Normal/Secret).
- Secret gate validator.
- Cost applier for scroll uses and optional Secret refresh fee/cooldown.

### Networking / UI
- C2S packet(s): page switch, prev/next, refresh.
- Server handlers: validate input, auth, state checks, apply costs, sync updated offers/state.
- Screen handler + client screen button wiring.

### Hooks
- Trade-result taken hook for reputation increment.
- First-meet hook to grant Guide Scroll once/player.

### Economy
- Loot table injection/registration (chests categories only).
- Mob drop handler (0.5% + conditions).
- Bounty submit handler (2-item input, deterministic output).
- Silver anti-inflation limiter.

---

## Manual Validation Steps (Must Execute)
1. First meet:
- Interact with merchant first time -> Guide Scroll granted once only.
- Re-interact -> no additional Guide Scroll.

2. Scroll usage costs:
- Open Normal page consumes uses `-1`.
- Refresh current page consumes uses `-1`.
- Secret switch consumes `-2` only when switch succeeds.

3. Secret gate:
- Missing Mark -> denied.
- Has Mark + Sealed scroll but `uses < 2` -> denied.
- All above but rep < 15 -> denied.
- All 3 satisfied -> allowed.

4. Reputation hook:
- Open/select offer without taking result -> no rep gain.
- Complete purchase and take result -> rep +1.

5. Secret one-time limit:
- Buy epic katana from merchant A secret page -> `secret_sold=true`.
- Try buying another epic from same merchant A -> blocked.
- Merchant B can have independent `secret_katana_id` and one-time sale.

6. Drops/loot/bounty:
- Chest scroll appears only in 4 target categories at expected chance.
- Mob scroll drop obeys 0.5% + condition.
- Bounty submit always grants 1 Trade Scroll + small Silver.

7. Silver anti-inflation:
- Force repeated mob kills under same window -> silver drop throttled by per-player limit.

8. Multiplayer isolation:
- Reputation, cooldowns, drop limits are per-player and do not leak between players.

9. Persistence:
- Save/reload world; verify player state and merchant secret flags remain valid.

10. Build:
- `./gradlew build` must be `BUILD SUCCESSFUL`.

---

## Logging & DEBUG Guidance
- Add `TRADE_DEBUG` toggle in config/constants.
- Default logs at INFO for critical state transitions:
  - gate pass/fail reason
  - page switch success/fail
  - cost consumption
  - reputation increment
  - secret sold transition
- DEBUG logs (only when toggle true):
  - packet payload/action
  - validator branch details
  - drop roll diagnostics
  - silver limiter counters/window
- Keep logs structured and grep-friendly; avoid spam in hot loops.

---

## DO NOT Constraints (Hard Rules)
- Do not refactor unrelated systems.
- Do not redesign combat mechanics or add gimmick effects.
- Do not trust client for validation, costs, or gate checks.
- Do not increment reputation on preview/selection.
- Do not bypass persisted merchant secret limit.
- Do not introduce broad architecture rewrites.
- Do not leave compile broken; final state must pass build.

---

## Patching Placeholders (Project-Specific Resolution)
Because class names may differ, resolve these by search and patch minimally:
- `<MERCHANT_ENTITY_CLASS>`
- `<MERCHANT_SCREEN_HANDLER_CLASS>`
- `<MERCHANT_SCREEN_CLASS>`
- `<TRADE_OPEN_HOOK>`
- `<OFFER_BUILD_METHOD>`
- `<PACKET_REGISTRATION_CLASS>`
- `<PLAYER_PERSISTENT_STATE_CLASS>`
- `<TRADE_RESULT_TAKE_HOOK>`

Instruction:
- Search existing codebase for each placeholder.
- Reuse existing entrypoints/events/components.
- Add narrow helper methods instead of moving large blocks.
- Keep diffs local and incremental per phase.
