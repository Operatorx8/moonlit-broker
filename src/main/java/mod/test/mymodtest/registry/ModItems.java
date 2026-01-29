package mod.test.mymodtest.registry;

import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;

/**
 * Phase 6: 自定义物品注册
 */
public final class ModItems {

    public static final String MOD_ID = "mymodtest";

    // ========== 神秘硬币 ==========
    public static final Identifier MYSTERIOUS_COIN_ID = Identifier.of(MOD_ID, "mysterious_coin");

    public static final Item MYSTERIOUS_COIN = Registry.register(
            Registries.ITEM,
            MYSTERIOUS_COIN_ID,
            new Item(new Item.Settings()
                    .maxCount(64)
                    .rarity(Rarity.UNCOMMON)
            )
    );

    /**
     * 注册所有物品
     * 在 ModInitializer.onInitialize() 中调用
     */
    public static void register() {
        System.out.println("[Mymodtest] 物品已注册: " + MYSTERIOUS_COIN_ID);
    }
}
