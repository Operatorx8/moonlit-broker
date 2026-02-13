# MyModTest - Mysterious Merchant Mod

Minecraft Fabric mod that adds a custom NPC entity: Mysterious Merchant.

## Build & Run

```bash
# Build
./gradlew clean build

# Run client (development)
./gradlew runClient
```

## In-Game Testing

After launching with `./gradlew runClient`:

1. Create a new world (Creative mode recommended)
2. Open chat (T key)
3. Summon the entity:
   ```
   /summon mymodtest:mysterious_merchant
   ```
4. Right-click the entity to open trade UI (empty in Phase 1)

## Current Phase: Phase 2 - Trading System (Complete)

### Phase 1 - Entity Bring-Up ✓
- [x] EntityType registration (`ModEntities.java`)
- [x] Attribute registration (HP=20, Speed=0.5)
- [x] Entity class extends `WanderingTraderEntity`
- [x] Client renderer using vanilla `WanderingTraderEntityRenderer`
- [x] Main entrypoint calls `ModEntities.register()`
- [x] Client entrypoint calls `ModEntityRenderers.register()`
- [x] `/summon mymodtest:mysterious_merchant` functional

### Phase 2 - Trading System ✓
- [x] Custom trades: 5 Emerald→Diamond, 10 Emerald→Golden Apple, Diamond→16 Emerald
- [x] Per-player trade tracking (`PlayerTradeData` class)
- [x] Trade callback with SPEED/REGENERATION buffs (100 ticks)
- [x] Secret trade unlock check (>=10 trades)
- [x] NBT persistence using 1.21.x WriteView/ReadView API
- [x] Debug logging to console

### Not Yet Implemented
- [ ] Custom AI behaviors (Phase 3)
- [ ] Spawn conditions (Phase 4)
- [ ] Attack/death penalties (Phase 5)

## Project Structure

```
src/
├── main/java/mod/test/mymodtest/
│   ├── Mymodtest.java                    # Main entrypoint
│   ├── registry/
│   │   └── ModEntities.java              # EntityType + attributes
│   └── entity/
│       ├── MysteriousMerchantEntity.java # Entity with trades & NBT
│       └── data/
│           └── PlayerTradeData.java      # Per-player trade tracking
└── client/java/mod/test/mymodtest/client/
    ├── MymodtestClient.java              # Client entrypoint
    └── registry/
        └── ModEntityRenderers.java       # Renderer registration
```

## Tech Stack

- Minecraft: 1.21.x
- Mod Loader: Fabric
- Language: Java 21

## Next Steps (Phase 3)

1. Implement flee AI goal (when attacked)
2. Implement seek-light AI goal (at night)
3. Implement self-preserve behavior (drink potion when low HP)
