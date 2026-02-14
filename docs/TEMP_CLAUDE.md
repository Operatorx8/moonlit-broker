# TEMP_TRADE_ENCHANT_SERVICE.md

## CRITICAL
- Only touch:
  - src/main/java/dev/xqanzd/moonlitbroker/entity/MysteriousMerchantEntity.java
- Do NOT modify A/B anchors, random B, anti-repeat, EPIC cap, Arcane claim logic, debug gate, forbidden-output scan.
- Diff-only output. No whole-file rewrite.

## Goal
Add 6 service trades to Page2 to increase attractiveness:
- 5 enchanted book tiers (player supplies Book) + 1 Name Tag trade.
Keep Page2 total trades = 18 by replacing 6 existing low-value/redundant trades.

## Replace List (Page2)
Replace these 6 offers (or if exact ones don't exist, replace the closest redundant ones):
1) 3E -> Coal x64  => 6E + Book -> EnchBook(Efficiency II)   maxUses=4
2) 6E -> Quartz x32 => 6E + Book -> EnchBook(Unbreaking II)  maxUses=4
3) 4E -> GlowstoneDust x32 => 10E + SilverNote + Book -> EnchBook(Efficiency III) maxUses=2
4) 5E -> Rail x64 => 10E + SilverNote + Book -> EnchBook(Unbreaking III) maxUses=2
5) 4I -> Glass x128 => 16E + 2*SilverNote + Book -> EnchBook(Mending I) maxUses=1
6) 4I -> Planks x256 => 8E -> NameTag x1 maxUses=3

## Implementation Notes
- Enchanted Book must be a real stored-enchantments ItemStack:
  - Item: Items.ENCHANTED_BOOK
  - Add stored enchantments: Efficiency/Unbreaking/Mending at specified level.
- Input Book uses Items.BOOK (player supplies).
- Use existing addBaseOfferWithLoopGuard / dedup-safe add method to add offers.
- maxUses as specified. priceMultiplier can be 0f for stability.
- Ensure no forbidden-output scan triggers (these are safe outputs).

## Verify
./gradlew compileJava
./gradlew runClient
