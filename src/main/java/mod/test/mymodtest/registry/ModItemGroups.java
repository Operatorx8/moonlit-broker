package mod.test.mymodtest.registry;

import mod.test.mymodtest.Mymodtest;
import mod.test.mymodtest.armor.item.ArmorItems;
import mod.test.mymodtest.armor.transitional.TransitionalArmorItems;
import mod.test.mymodtest.katana.item.KatanaItems;
import mod.test.mymodtest.weapon.transitional.item.TransitionalWeaponItems;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * 模组创造模式物品栏分组
 */
public final class ModItemGroups {
    private ModItemGroups() {}

    public static final ItemGroup MAIN = Registry.register(
            Registries.ITEM_GROUP,
            Identifier.of(Mymodtest.MOD_ID, "main"),
            FabricItemGroup.builder()
                    .displayName(Text.translatable("itemGroup.mymodtest.main"))
                    .icon(() -> new ItemStack(KatanaItems.MOON_GLOW_KATANA))
                    .entries((ctx, entries) -> {
                        // Currency / Scrolls / Materials
                        entries.add(ModItems.MYSTERIOUS_COIN);
                        entries.add(ModItems.SEALED_LEDGER);
                        entries.add(ModItems.ARCANE_LEDGER);
                        entries.add(ModItems.SIGIL);
                        entries.add(ModItems.SACRIFICE);
                        entries.add(ModItems.MERCHANT_MARK);
                        entries.add(ModItems.TRADE_SCROLL);
                        entries.add(ModItems.SILVER_NOTE);
                        entries.add(ModItems.GUIDE_SCROLL);
                        entries.add(ModBlocks.MYSTERIOUS_ANVIL_ITEM);

                        // Katanas
                        entries.add(KatanaItems.MOON_GLOW_KATANA);
                        entries.add(KatanaItems.REGRET_BLADE);
                        entries.add(KatanaItems.ECLIPSE_BLADE);
                        entries.add(KatanaItems.OBLIVION_EDGE);
                        entries.add(KatanaItems.NMAP_KATANA);

                        // Transitional weapons
                        entries.add(TransitionalWeaponItems.ACER);
                        entries.add(TransitionalWeaponItems.VELOX);
                        entries.add(TransitionalWeaponItems.FATALIS);

                        // Armor - helmets
                        addIfPresent(entries, ArmorItems.SENTINEL_HELMET);
                        addIfPresent(entries, ArmorItems.SILENT_OATH_HELMET);
                        addIfPresent(entries, ArmorItems.EXILE_MASK_HELMET);
                        addIfPresent(entries, ArmorItems.RELIC_CIRCLET_HELMET);
                        addIfPresent(entries, ArmorItems.RETRACER_ORNAMENT_HELMET);

                        // Transitional Armor - helmets (UNCOMMON -> RARE)
                        addIfPresent(entries, TransitionalArmorItems.SCAVENGER_GOGGLES);
                        addIfPresent(entries, TransitionalArmorItems.CAST_IRON_SALLET);
                        addIfPresent(entries, TransitionalArmorItems.SANCTIFIED_HOOD);

                        // Armor - chestplates
                        addIfPresent(entries, ArmorItems.OLD_MARKET_CHESTPLATE);
                        addIfPresent(entries, ArmorItems.BLOOD_PACT_CHESTPLATE);
                        addIfPresent(entries, ArmorItems.GHOST_GOD_CHESTPLATE);
                        addIfPresent(entries, ArmorItems.WINDBREAKER_CHESTPLATE);
                        addIfPresent(entries, ArmorItems.VOID_DEVOURER_CHESTPLATE);

                        // Transitional Armor - chestplates (UNCOMMON -> RARE)
                        addIfPresent(entries, TransitionalArmorItems.PATCHWORK_COAT);
                        addIfPresent(entries, TransitionalArmorItems.RITUAL_ROBE);
                        addIfPresent(entries, TransitionalArmorItems.REACTIVE_BUG_PLATE);

                        // Armor - leggings
                        addIfPresent(entries, ArmorItems.SMUGGLER_SHIN_LEGGINGS);
                        addIfPresent(entries, ArmorItems.SMUGGLER_POUCH_LEGGINGS);
                        addIfPresent(entries, ArmorItems.GRAZE_GUARD_LEGGINGS);
                        addIfPresent(entries, ArmorItems.STEALTH_SHIN_LEGGINGS);
                        addIfPresent(entries, ArmorItems.CLEAR_LEDGER_LEGGINGS);

                        // Armor - boots
                        addIfPresent(entries, ArmorItems.UNTRACEABLE_TREADS_BOOTS);
                        addIfPresent(entries, ArmorItems.BOUNDARY_WALKER_BOOTS);
                        addIfPresent(entries, ArmorItems.GHOST_STEP_BOOTS);
                        addIfPresent(entries, ArmorItems.MARCHING_BOOTS);
                        addIfPresent(entries, ArmorItems.GOSSAMER_BOOTS);
                    })
                    .build()
    );

    public static void init() {
        // trigger class loading
    }

    private static void addIfPresent(ItemGroup.Entries entries, Item item) {
        if (item != null) {
            entries.add(item);
        }
    }
}
