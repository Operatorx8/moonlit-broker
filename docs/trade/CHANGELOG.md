# Trade System Changelog

## [Unreleased]

### Phase 0 - Scaffolding
- Added docs structure (SPEC, PARAMS, CHANGELOG, TODO, TESTPLAN, BOUNTY_SCHEMA)
- Added TradeConfig constants class
- Audited existing codebase

### Phase 1 - Items + Data Contracts
- Added Merchant Mark item with UUID binding
- Added Trade Scroll item with NBT (uses, grade)
- Added Silver Note currency item
- Added Guide Scroll informational item

### Phase 2 - State + Secret Constraints
- Extended MerchantUnlockState.Progress with reputation field
- Added first-meet guide flag
- Added silver anti-inflation counters
- Added merchant entity NBT: secret_sold, secret_katana_id

### Phase 3 - Paging UI + C2S Validation
- Implemented Normal/Secret page system
- Added C2S packets for page actions
- Server-side validation for all requests
- Cost application with atomic deduction

### Phase 4 - Reputation + Trade Result Hook
- Hooked into trade result taken event
- Reputation increments only on successful purchase

### Phase 5 - Loot/Drop/Bounty/Silver Economy
- Chest loot injection for 4 target categories
- Mob drop with night/surface/elite conditions
- Bounty submit system (2-item input)
- Silver anti-inflation rate limiting
