# Sigil Trade Replacements

Location: `src/main/java/dev/xqanzd/moonlitbroker/entity/MysteriousMerchantEntity.java` (`createSigilOfferPool`)

## Replacement List (old -> new)

1. `A1`
- Old: `64 Emerald -> Sigil x1 (maxUses=1)`
- New: `8 Emerald + 1 SilverNote -> Mysterious Anvil x1 (maxUses=2)`

2. `A2`
- Old: `8 Diamond -> Sigil x1 (maxUses=1)`
- New: `2 Diamond + 2 SilverNote -> Sacrifice x1 (maxUses=4)`

3. `A3`
- Kept unchanged: `1 Netherite Ingot -> Sigil x1 (maxUses=1)`

## Notes

- Total Sigil pool entry count is unchanged.
- Existing dedupe/shuffle/release-sanitize framework is unchanged.
- `merchantXp` remains forced to `0` by existing normalization/sanitize flow.
