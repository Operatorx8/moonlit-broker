package mod.test.mymodtest.registry;

import mod.test.mymodtest.trade.item.GuideScrollItem;
import mod.test.mymodtest.trade.item.MerchantMarkItem;
import mod.test.mymodtest.trade.item.SilverNoteItem;
import mod.test.mymodtest.trade.item.TradeScrollItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Phase 6: 自定义物品注册
 * Phase 8: 解封卷轴系统物品
 * Trade System: 交易系统物品
 */
public final class ModItems {

    public static final String MOD_ID = "mymodtest";
    private static final Logger LOGGER = LoggerFactory.getLogger(ModItems.class);

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

    // ========== Phase 8: 解封卷轴系统物品 ==========

    /** 封印卷轴 - 通过神秘硬币购买获得 */
    public static final Identifier SEALED_LEDGER_ID = Identifier.of(MOD_ID, "sealed_ledger");
    public static final Item SEALED_LEDGER = Registry.register(
            Registries.ITEM,
            SEALED_LEDGER_ID,
            new Item(new Item.Settings()
                    .maxCount(1)
                    .rarity(Rarity.RARE)
            )
    );

    /** 已解封卷轴 - 用于解锁 Katana 隐藏交易 */
    public static final Identifier ARCANE_LEDGER_ID = Identifier.of(MOD_ID, "arcane_ledger");
    public static final Item ARCANE_LEDGER = Registry.register(
            Registries.ITEM,
            ARCANE_LEDGER_ID,
            new Item(new Item.Settings()
                    .maxCount(1)
                    .rarity(Rarity.EPIC)
            )
    );

    /** 解封印记 - 用于解封 Sealed Ledger */
    public static final Identifier SIGIL_ID = Identifier.of(MOD_ID, "sigil");
    public static final Item SIGIL = Registry.register(
            Registries.ITEM,
            SIGIL_ID,
            new Item(new Item.Settings()
                    .maxCount(16)
                    .rarity(Rarity.UNCOMMON)
            )
    );

    /** Sacrifice 材料 - 用于神秘铁砧修复 */
    public static final Identifier SACRIFICE_ID = Identifier.of(MOD_ID, "sacrifice");
    public static final Item SACRIFICE = Registry.register(
            Registries.ITEM,
            SACRIFICE_ID,
            new Item(new Item.Settings())
    );

    // ========== Trade System: 交易系统物品 ==========

    /** 商人印记 - UUID绑定会员标识 */
    public static final Identifier MERCHANT_MARK_ID = Identifier.of(MOD_ID, "merchant_mark");
    public static final Item MERCHANT_MARK = Registry.register(
            Registries.ITEM,
            MERCHANT_MARK_ID,
            new MerchantMarkItem(new Item.Settings()
                    .maxCount(1)
                    .rarity(Rarity.RARE)
            )
    );

    /** 交易卷轴 - 带NBT的消耗品 */
    public static final Identifier TRADE_SCROLL_ID = Identifier.of(MOD_ID, "trade_scroll");
    public static final Item TRADE_SCROLL = Registry.register(
            Registries.ITEM,
            TRADE_SCROLL_ID,
            new TradeScrollItem(new Item.Settings()
                    .maxCount(1)
                    .rarity(Rarity.UNCOMMON)
            )
    );

    /** 银币 - 交易货币 */
    public static final Identifier SILVER_NOTE_ID = Identifier.of(MOD_ID, "silver_note");
    public static final Item SILVER_NOTE = Registry.register(
            Registries.ITEM,
            SILVER_NOTE_ID,
            new SilverNoteItem(new Item.Settings()
                    .maxCount(64)
                    .rarity(Rarity.COMMON)
            )
    );

    /** 指南卷轴 - 首次见面赠送 */
    public static final Identifier GUIDE_SCROLL_ID = Identifier.of(MOD_ID, "guide_scroll");
    public static final Item GUIDE_SCROLL = Registry.register(
            Registries.ITEM,
            GUIDE_SCROLL_ID,
            new GuideScrollItem(new Item.Settings()
                    .maxCount(1)
                    .rarity(Rarity.COMMON)
            )
    );

    /**
     * 注册所有物品
     * 在 ModInitializer.onInitialize() 中调用
     */
    public static void register() {
        LOGGER.info("[Mymodtest] 物品已注册: {}, {}, {}, {}, {}, {}, {}, {}",
                MYSTERIOUS_COIN_ID, SEALED_LEDGER_ID, ARCANE_LEDGER_ID, SIGIL_ID,
                MERCHANT_MARK_ID, TRADE_SCROLL_ID, SILVER_NOTE_ID, GUIDE_SCROLL_ID);
    }
}
