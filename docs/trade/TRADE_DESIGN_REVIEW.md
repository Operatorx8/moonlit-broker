# Mysterious Merchant Trade System - Design Review (Supplemental)

This document complements `docs/trade/CLAUDE_CODE_INSTRUCTIONS.md` and focuses on omission/risk review to reduce rework.  
Scope is intentionally **minimal-change friendly** and aligned to the existing phase plan.

## Top 10 Must-Not-Forget Checklist
- [ ] Secret page checks all 3 gates server-side (Mark + Sealed Scroll uses>=2 + rep>=15).
- [ ] Reputation increments only on actual take-result success.
- [ ] Scroll use deduction is atomic and never goes negative.
- [ ] Failed switch/refresh/open does not consume scroll uses.
- [ ] Merchant `secret_sold` and `secret_katana_id` persist across save/reload.
- [ ] One merchant sells only one epic katana total, even under multiplayer contention.
- [ ] C2S paging actions validate current screen handler + merchant entity validity.
- [ ] Packet spam is throttled server-side (light cooldown/rate guard).
- [ ] Chest injection is limited to exactly 4 target categories.
- [ ] `./gradlew build` remains successful after each phase-sized patch.

---

## 1) Persistence & Data Ownership

### 1.1 State split clarity
- Issue: Ambiguous ownership of player vs merchant vs global state.
- Why it matters: Misplaced state causes dupes, cross-player leakage, or reset bugs.
- Recommendation:
  - Player persistent state: reputation, first-meet-guide flag, silver drop throttle window counters.
  - Merchant entity NBT: `secret_sold`, `secret_katana_id`.
  - World persistent state: only if needed for global anti-abuse stats; avoid unless required.
- Acceptance/Check:
  - Relog + world reload keeps each state where expected.
  - One player’s values do not affect another.

### 1.2 Save/load and chunk lifecycle
- Issue: Merchant entity data may be lost on chunk unload/reload if not serialized correctly.
- Why it matters: Secret sale limits can reset, enabling repeat sales.
- Recommendation: Serialize `secret_sold` and `secret_katana_id` in merchant read/write NBT path already used by entity.
- Acceptance/Check:
  - Move far enough to unload chunk, return, verify values unchanged.

### 1.3 Merchant identity binding
- Issue: Binding secret stock to display name or transient references.
- Why it matters: Renames or respawns can invalidate identity rules.
- Recommendation: Bind secret identity to merchant entity instance data (`secret_katana_id` + `secret_sold`) keyed by entity lifecycle/UUID, not display name.
- Acceptance/Check:
  - Name change does not change secret item identity.
  - Same entity keeps same `secret_katana_id`.

### 1.4 Dupe prevention under races
- Issue: Two players buy secret item simultaneously.
- Why it matters: Violates one-per-merchant invariant.
- Recommendation: Guard sale transition with server-side atomic check/update in one critical section:
  - check `secret_sold == false` during purchase finalization
  - set true before final commit returns.
- Acceptance/Check:
  - Concurrent purchase attempt yields exactly one success.

---

## 2) Server-Authoritative UI Paging

### 2.1 Packet validation scope
- Issue: SWITCH_PAGE/REFRESH_PAGE packets may trust client state.
- Why it matters: Client can bypass gates/costs or target wrong merchant.
- Recommendation: Validate on server:
  - sender has merchant screen open
  - active handler is expected merchant screen handler type
  - bound merchant exists, alive, in interaction range
  - requested action/page is allowed.
- Acceptance/Check:
  - Malformed packets are rejected with no state/cost change.

### 2.2 Desync and spam/flood
- Issue: Rapid click/pkt flood can cause out-of-order swaps or duplicated consumption.
- Why it matters: Economic exploits and UX desync.
- Recommendation:
  - Add small per-player action cooldown for page actions (server memory map).
  - Ignore stale requests not matching current handler session token/window id.
- Acceptance/Check:
  - Spam clicking does not consume extra uses or corrupt offers.

### 2.3 Safe offer swap timing
- Issue: Offer list mutation during active transaction can race with result slot actions.
- Why it matters: Ghost offers, wrong item outputs, dupes.
- Recommendation: Swap offers only at controlled handler state transitions; after swap, resync handler properties/slots once.
- Acceptance/Check:
  - Refresh/page switch never produces stale result output.

---

## 3) Reputation Semantics

### 3.1 Increment trigger point
- Issue: Incrementing on open/select/preview instead of take-result.
- Why it matters: Reputation farming without real purchases.
- Recommendation: Hook only at successful “result taken” path where payment was consumed and output transferred.
- Acceptance/Check:
  - Open/select/cancel loops give +0 rep.
  - Real completed purchase gives +1 rep exactly once.

### 3.2 Anti-farming by cancel flows
- Issue: Client can repeatedly trigger partial flows.
- Why it matters: Inflated rep and secret gate bypass.
- Recommendation: Tie rep increment to transaction commit event, not UI actions.
- Acceptance/Check:
  - Interrupting/canceling before take-result never increments rep.

### 3.3 Legacy migration (if old fields exist)
- Issue: Existing “15 trades unlock” fields may conflict.
- Why it matters: Unexpected unlock paths or broken state.
- Recommendation: On load, map old fields once into new reputation semantics, then prefer new source of truth.
- Acceptance/Check:
  - Existing saves load without granting unintended secret access.

---

## 4) Trade Scroll Consumption Rules

### 4.1 Deduction timing correctness
- Issue: Uses decremented before validation or on failed action.
- Why it matters: Item loss and bug reports.
- Recommendation:
  - Normal open: deduct only when server confirms open action success.
  - Secret switch: deduct `-2` only when switch truly succeeds.
  - Refresh: deduct only after refresh success.
- Acceptance/Check:
  - Every failed action leaves uses unchanged.

### 4.2 Negative uses and atomicity
- Issue: Multiple checks + writes can underflow uses.
- Why it matters: Exploit or corrupted item state.
- Recommendation: Single helper method: validate required uses then decrement atomically; clamp at zero forbidden.
- Acceptance/Check:
  - `uses` never < 0 in any path.

### 4.3 Grade consistency
- Issue: `NORMAL` vs `SEALED` behavior mismatch with lore/UI text.
- Why it matters: Player confusion and support overhead.
- Recommendation: Keep one shared source for grade checks and one formatter for display text.
- Acceptance/Check:
  - UI/lore always matches functional gate behavior.

---

## 5) Secret Page One-Per-Merchant Rule

### 5.1 Define “single sale” event
- Issue: Ambiguous whether selecting offer counts as sale.
- Why it matters: Premature lockout or exploit.
- Recommendation: Sale counts only when epic katana trade is fully completed (take-result success).
- Acceptance/Check:
  - Browsing/selecting does not set `secret_sold`.

### 5.2 Post-sale UX behavior
- Issue: No consistent behavior after sold out.
- Why it matters: Confusing UX and repeated failed attempts.
- Recommendation: Minimal change:
  - show “SOLD” placeholder offer or remove epic offer cleanly; keep deterministic behavior.
- Acceptance/Check:
  - After sale, secret page clearly indicates unavailable state.

### 5.3 Persistence consistency
- Issue: secret sold flag may reset after restart.
- Why it matters: Hard economy break.
- Recommendation: Persist both fields and test restart path explicitly.
- Acceptance/Check:
  - Server restart preserves sold-out merchant.

### 5.4 Multiplayer contention
- Issue: Two buyers on same tick.
- Why it matters: duplicate epic output.
- Recommendation: Re-check `secret_sold` inside final transaction path before output grant; fail second buyer gracefully.
- Acceptance/Check:
  - Exactly one epic item generated.

---

## 6) Economy & Inflation Controls (Silver Notes)

### 6.1 Source weighting
- Issue: Too much combat silver makes exploration irrelevant.
- Why it matters: Flattens progression and farming dominates.
- Recommendation: Keep exploration/combat primary split explicit in params; bounty secondary small.
- Acceptance/Check:
  - Early progression feels rewarding but not flood-heavy.

### 6.2 Per-player throttle window
- Issue: Unlimited kill farming in grinders.
- Why it matters: Currency inflation.
- Recommendation: Sliding window cap per player for mob-silver drops (`N per 10m`, configurable).
- Acceptance/Check:
  - Drop rate clearly decreases after cap, recovers after window.

### 6.3 Sinks without complexity
- Issue: No sinks => inflation; too many sinks => friction.
- Why it matters: Economy instability.
- Recommendation: Use light sinks only:
  - optional secret refresh silver fee
  - optional refresh cooldown.
- Acceptance/Check:
  - Economy stabilizes without adding new complex subsystems.

---

## 7) Loot Integration Pitfalls

### 7.1 Chest table scope control
- Issue: Injecting into broad chest pools accidentally.
- Why it matters: Scroll oversupply.
- Recommendation: Whitelist exactly 4 categories (Dungeon, Mineshaft, Stronghold, Shipwreck); deny-by-default all others.
- Acceptance/Check:
  - Spot-check non-target chests: zero scroll injection.

### 7.2 Chance tuning isolation
- Issue: Global loot modifiers affect unrelated content.
- Why it matters: Hidden balance regressions.
- Recommendation: Localized injection hooks for target tables only.
- Acceptance/Check:
  - Existing unrelated loot remains unchanged.

### 7.3 Mob condition abuse
- Issue: Night/surface condition may be gamed by farm setups.
- Why it matters: unintended high throughput.
- Recommendation: Combine condition with elite/equipped checks and silver throttle; keep 0.5% low and server-side.
- Acceptance/Check:
  - Farm throughput remains bounded by rate limits.

---

## 8) Bounty System Pitfalls

### 8.1 Two-item submit correctness
- Issue: Partial removal or desync in inventory deduction.
- Why it matters: dupe/loss exploits.
- Recommendation: Validate both required items first; remove atomically on success; otherwise no removal.
- Acceptance/Check:
  - Missing one item never consumes the other.

### 8.2 Feedback quality
- Issue: Silent failure on submit conditions.
- Why it matters: Player confusion.
- Recommendation: Send explicit server message for success/fail reason.
- Acceptance/Check:
  - Every submit attempt returns clear feedback.

### 8.3 Optional bounty cooldown
- Issue: Immediate repeat turn-ins can become printing press.
- Why it matters: economy abuse.
- Recommendation: Add optional per-player bounty cooldown param (short) if balancing requires; default can be disabled initially.
- Acceptance/Check:
  - Cooldown, if enabled, blocks rapid repeat submits predictably.

---

## 9) Naming / Identity Guidance

### 9.1 Dynamic display names
- Issue: Overloading names with logic identity.
- Why it matters: localization and future styling conflicts.
- Recommendation: Keep display name cosmetic; do not use for logic keys.
- Acceptance/Check:
  - Changing title/prefix has zero gameplay effect.

### 9.2 Katana identity binding
- Issue: Secret katana derived from UI text or transient offer order.
- Why it matters: unstable behavior after updates.
- Recommendation: Bind katana identity to persisted `secret_katana_id` only.
- Acceptance/Check:
  - Offer reorder/localization does not change sold item identity.

### 9.3 Non-blocking style hints
- Issue: Color/profession cues not standardized.
- Why it matters: UX inconsistency.
- Recommendation: Optional small mapping table in docs only; do not block implementation.
- Acceptance/Check:
  - UI remains understandable even without extra style pass.

---

## 10) Logging & Debug

### 10.1 Prefix and structure
- Issue: Mixed prefixes make traceability hard.
- Why it matters: debugging multi-step trade flow is expensive.
- Recommendation: Use a dedicated prefix (example: `[MoonTrade]`) with stable action/result/reason fields.
- Acceptance/Check:
  - One grep query can trace full transaction path.

### 10.2 DEBUG toggle placement
- Issue: hardcoded noisy logs in hot paths.
- Why it matters: performance and log noise.
- Recommendation: One central config toggle (e.g., `TRADE_DEBUG`) read by trade modules.
- Acceptance/Check:
  - DEBUG off: only key INFO logs.
  - DEBUG on: validation branches and drop rolls visible.

### 10.3 Critical events to log
- Issue: Missing logs for exploit-sensitive transitions.
- Why it matters: hard to audit incidents.
- Recommendation: Log at least:
  - gate block reasons
  - scroll use consumption
  - rep increments
  - secret sold transition
  - packet reject reasons
- Acceptance/Check:
  - Security-relevant transitions are reconstructable from logs.

---

## Suggested Invariants
- `reputation` is non-negative integer.
- Scroll `uses` is integer and `uses >= 0`.
- Scroll `grade` is one of `NORMAL` or `SEALED`.
- Secret switch never succeeds unless all 3 gates pass server-side.
- `secret_sold == true` implies epic katana cannot be sold again by same merchant.
- `secret_katana_id` is stable for a merchant entity once assigned.
- Failed action never consumes uses/currency.
- Rep increments only on successful purchase take-result.
- All page/refresh actions require active valid merchant screen handler.

---

## Test Probes (Quick Early Catchers)
- Probe A: Send SWITCH_PAGE packet without merchant screen open -> must reject.
- Probe B: Force invalid merchant reference in packet -> reject, no state change.
- Probe C: Scroll with `uses=1` try secret switch -> reject, uses unchanged.
- Probe D: Open/close/select without take-result -> rep unchanged.
- Probe E: Two players buy secret epic simultaneously -> one success max.
- Probe F: Restart server after secret sale -> still sold.
- Probe G: Spam refresh button -> throttled, no negative uses.
- Probe H: Open random non-target chest tables -> no scroll injection.
- Probe I: Mob farm for extended period -> silver throttling activates.
- Probe J: Bounty submit with missing item -> no partial consume.

---

## Future Extension Hooks (No Implementation Now)
- Hook 1: Secret catalog abstraction for more secret items beyond epic katana.
- Hook 2: Page enum extensibility (Normal/Secret now, optional future pages later).
- Hook 3: Seasonal bounty rule slot (event-based reward multipliers).
- Hook 4: Param-driven economy tuning table (drop caps/fees) with no logic rewrite.

Keep these as lightweight placeholders only; do not build new frameworks now.

---

## Non-goals / Explicitly Out of Scope
- No broad refactor of merchant architecture.
- No new combat gimmicks or unrelated mechanics.
- No client-authoritative shortcuts.
- No quest UI framework for bounty in this scope.
- No major UI redesign beyond required page controls.
- No optimization megaproject; only targeted guards (validation/rate-limit/cooldown).

For implementation sequencing, follow the phase plan in `docs/trade/CLAUDE_CODE_INSTRUCTIONS.md` and patch minimally.
