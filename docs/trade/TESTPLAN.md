# Trade System Test Plan

## 1. First Meet
- [ ] Interact with merchant first time -> Guide Scroll granted
- [ ] Re-interact -> no additional Guide Scroll

## 2. Scroll Usage Costs
- [ ] Open Normal page consumes uses -1
- [ ] Refresh current page consumes uses -1
- [ ] Secret switch consumes -2 only when switch succeeds

## 3. Secret Gate
- [ ] Missing Mark -> denied
- [ ] Has Mark + Sealed scroll but uses < 2 -> denied
- [ ] All above but rep < 15 -> denied
- [ ] All 3 satisfied -> allowed

## 4. Reputation Hook
- [ ] Open/select offer without taking result -> no rep gain
- [ ] Complete purchase and take result -> rep +1

## 5. Secret One-Time Limit
- [ ] Buy epic katana from merchant A -> secret_sold=true
- [ ] Try buying another epic from same merchant A -> blocked
- [ ] Merchant B can have independent sale

## 6. Drops/Loot/Bounty
- [ ] Chest scroll appears only in 4 target categories at 15%
- [ ] Mob scroll drop obeys 0.5% + condition
- [ ] Bounty submit always grants 1 Trade Scroll + small Silver

## 7. Silver Anti-Inflation
- [ ] Repeated mob kills under same window -> throttled

## 8. Multiplayer Isolation
- [ ] Reputation per-player
- [ ] Cooldowns per-player
- [ ] Drop limits per-player

## 9. Persistence
- [ ] Save/reload world -> player state valid
- [ ] Save/reload world -> merchant secret flags valid

## 10. Build
- [ ] `./gradlew build` -> BUILD SUCCESSFUL
