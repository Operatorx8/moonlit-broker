# Trade System TODO

## High Priority
- [ ] Implement Trade Scroll NBT helper methods
- [ ] Add Merchant Mark UUID binding logic
- [ ] Implement secret gate validator
- [ ] Add C2S packet for page actions
- [ ] Hook trade result taken for reputation

## Medium Priority
- [ ] Chest loot table injection
- [ ] Mob drop handler with conditions
- [ ] Silver anti-inflation limiter
- [ ] Bounty submit handler

## Low Priority
- [ ] Optional secret refresh silver fee
- [ ] Optional refresh cooldown for secret page
- [ ] UI polish and feedback messages

## Known Risks
- Race condition on secret sale (multiplayer)
- Scroll uses underflow protection
- Packet spam throttling

## Deferred
- Secret catalog abstraction for more items
- Page enum extensibility
- Seasonal bounty rules
