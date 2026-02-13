package dev.xqanzd.moonlitbroker.trade;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradedItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 三页交易清单定义
 * <p>
 * Page1 (Normal)  — 杂货与生存 (24 base + 1 A anchor)
 * Page2 (Normal)  — 工具/战斗/实用 (16 base)
 * Page3 (Arcane)  — 奥术市场 (12 base + B anchor + random B + katana/reclaim)
 */
public final class TradePageOffers {

    private TradePageOffers() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("MoonTrace");

    // ==================== 通用常量 ====================
    public static final int MAX_USES_CONSUMABLE = 16;
    public static final int MAX_USES_TOOL = 8;
    public static final int MAX_USES_SPECIAL = 3;
    public static final int MAX_USES_ARMOR = 2;

    // ==================== Page 1: Supplies ====================

    /**
     * 构建 Page1 基础交易（24 条）
     */
    public static List<TradeOffer> buildPage1Base() {
        List<TradeOffer> offers = new ArrayList<>();

        // --- 绿宝石换食物/材料 ---
        offers.add(simpleOffer(Items.EMERALD, 2, Items.BREAD, 16, MAX_USES_CONSUMABLE));
        offers.add(simpleOffer(Items.EMERALD, 3, Items.COOKED_BEEF, 8, MAX_USES_CONSUMABLE));
        offers.add(simpleOffer(Items.EMERALD, 2, Items.BAKED_POTATO, 16, MAX_USES_CONSUMABLE));
        offers.add(simpleOffer(Items.EMERALD, 2, Items.CARROT, 16, MAX_USES_CONSUMABLE));
        offers.add(simpleOffer(Items.EMERALD, 2, Items.APPLE, 16, MAX_USES_CONSUMABLE));

        // --- 绿宝石换实用方块 ---
        offers.add(simpleOffer(Items.EMERALD, 3, Items.TORCH, 16, MAX_USES_CONSUMABLE));
        offers.add(simpleOffer(Items.EMERALD, 3, Items.LADDER, 16, MAX_USES_CONSUMABLE));
        offers.add(simpleOffer(Items.EMERALD, 2, Items.COBBLESTONE, 32, MAX_USES_CONSUMABLE));
        offers.add(simpleOffer(Items.EMERALD, 3, Items.GLASS, 16, MAX_USES_CONSUMABLE));
        offers.add(simpleOffer(Items.EMERALD, 3, Items.SAND, 16, MAX_USES_CONSUMABLE));
        offers.add(simpleOffer(Items.EMERALD, 3, Items.OAK_LOG, 16, MAX_USES_CONSUMABLE));
        offers.add(simpleOffer(Items.EMERALD, 2, Items.OAK_PLANKS, 16, MAX_USES_CONSUMABLE));

        // --- 绿宝石换杂项 ---
        offers.add(simpleOffer(Items.EMERALD, 4, Items.PAPER, 16, MAX_USES_CONSUMABLE));
        offers.add(simpleOffer(Items.EMERALD, 6, Items.BOOK, 4, MAX_USES_CONSUMABLE));
        offers.add(simpleOffer(Items.EMERALD, 4, Items.COMPASS, 1, MAX_USES_TOOL));
        offers.add(simpleOffer(Items.EMERALD, 4, Items.CLOCK, 1, MAX_USES_TOOL));
        offers.add(simpleOffer(Items.EMERALD, 5, Items.OAK_BOAT, 1, MAX_USES_TOOL));
        offers.add(simpleOffer(Items.EMERALD, 5, Items.WHITE_BED, 1, MAX_USES_TOOL));

        // --- 铁换便利（铁不换稀缺，不换绿宝石） ---
        offers.add(simpleOffer(Items.IRON_INGOT, 3, Items.TORCH, 32, MAX_USES_CONSUMABLE));
        offers.add(simpleOffer(Items.IRON_INGOT, 4, Items.ARROW, 32, MAX_USES_CONSUMABLE));
        offers.add(simpleOffer(Items.IRON_INGOT, 5, Items.RAIL, 16, MAX_USES_CONSUMABLE));
        offers.add(simpleOffer(Items.IRON_INGOT, 3, Items.BUCKET, 1, MAX_USES_TOOL));
        offers.add(simpleOffer(Items.IRON_INGOT, 4, Items.LANTERN, 4, MAX_USES_CONSUMABLE));
        offers.add(simpleOffer(Items.IRON_INGOT, 2, Items.SHEARS, 1, MAX_USES_TOOL));

        return offers;
    }

    // ==================== Page 2: Tools / Combat ====================

    /**
     * 构建 Page2 基础交易（16 条）
     */
    public static List<TradeOffer> buildPage2Base() {
        List<TradeOffer> offers = new ArrayList<>();

        // --- 绿宝石+铁 换工具/武器 ---
        offers.add(twoInputOffer(Items.EMERALD, 6, Items.IRON_INGOT, 1, Items.IRON_SWORD, 1, MAX_USES_TOOL));
        offers.add(twoInputOffer(Items.EMERALD, 7, Items.IRON_INGOT, 1, Items.IRON_PICKAXE, 1, MAX_USES_TOOL));
        offers.add(twoInputOffer(Items.EMERALD, 7, Items.IRON_INGOT, 1, Items.IRON_AXE, 1, MAX_USES_TOOL));
        offers.add(twoInputOffer(Items.EMERALD, 6, Items.IRON_INGOT, 1, Items.IRON_SHOVEL, 1, MAX_USES_TOOL));
        offers.add(twoInputOffer(Items.EMERALD, 5, Items.IRON_INGOT, 1, Items.SHIELD, 1, MAX_USES_TOOL));

        // --- 绿宝石换远程 ---
        offers.add(simpleOffer(Items.EMERALD, 5, Items.BOW, 1, MAX_USES_TOOL));
        offers.add(twoInputOffer(Items.EMERALD, 7, Items.IRON_INGOT, 1, Items.CROSSBOW, 1, MAX_USES_TOOL));
        offers.add(simpleOffer(Items.EMERALD, 3, Items.ARROW, 16, MAX_USES_CONSUMABLE));

        // --- 绿宝石换红石/杂项 ---
        offers.add(simpleOffer(Items.EMERALD, 4, Items.REDSTONE, 16, MAX_USES_CONSUMABLE));
        offers.add(simpleOffer(Items.EMERALD, 4, Items.LAPIS_LAZULI, 16, MAX_USES_CONSUMABLE));
        offers.add(simpleOffer(Items.EMERALD, 8, Items.ENDER_PEARL, 2, MAX_USES_SPECIAL));

        // --- 药水（用 ItemStack 直接给，后续可加 NBT） ---
        offers.add(simpleOffer(Items.EMERALD, 6, Items.POTION, 1, MAX_USES_SPECIAL));
        offers.add(simpleOffer(Items.EMERALD, 6, Items.POTION, 1, MAX_USES_SPECIAL));
        offers.add(simpleOffer(Items.EMERALD, 6, Items.POTION, 1, MAX_USES_SPECIAL));

        // --- 经验瓶 + 铁砧 ---
        offers.add(simpleOffer(Items.EMERALD, 5, Items.EXPERIENCE_BOTTLE, 8, MAX_USES_CONSUMABLE));
        offers.add(simpleOffer(Items.EMERALD, 6, Items.ANVIL, 1, MAX_USES_TOOL));

        return offers;
    }

    // ==================== Page 3: Arcane ====================

    /**
     * 构建 Page3 基础交易（不含 B anchor / random B / katana / reclaim）
     */
    public static List<TradeOffer> buildPage3Base() {
        List<TradeOffer> offers = new ArrayList<>();

        // --- 钻石作为消耗输入的附魔书 ---
        offers.add(twoInputOffer(Items.EMERALD, 12, Items.DIAMOND, 1, Items.ENCHANTED_BOOK, 1, MAX_USES_SPECIAL));
        offers.add(twoInputOffer(Items.EMERALD, 12, Items.DIAMOND, 1, Items.ENCHANTED_BOOK, 1, MAX_USES_SPECIAL));
        offers.add(twoInputOffer(Items.EMERALD, 12, Items.DIAMOND, 1, Items.ENCHANTED_BOOK, 1, MAX_USES_SPECIAL));
        offers.add(twoInputOffer(Items.EMERALD, 16, Items.DIAMOND, 2, Items.ENCHANTED_BOOK, 1, MAX_USES_SPECIAL));

        // --- Arcane 便利 ---
        offers.add(simpleOffer(Items.EMERALD, 10, Items.ENDER_PEARL, 4, MAX_USES_SPECIAL));
        offers.add(simpleOffer(Items.EMERALD, 10, Items.BLAZE_POWDER, 4, MAX_USES_SPECIAL));
        offers.add(simpleOffer(Items.EMERALD, 12, Items.TOTEM_OF_UNDYING, 1, MAX_USES_SPECIAL));
        offers.add(simpleOffer(Items.EMERALD, 8, Items.GOLDEN_APPLE, 1, MAX_USES_SPECIAL));

        return offers;
    }

    // ==================== Debug Trades ====================

    /**
     * 仅在 MASTER_DEBUG 时出现的交易
     */
    public static List<TradeOffer> buildDebugTrades() {
        List<TradeOffer> offers = new ArrayList<>();
        offers.add(simpleOffer(Items.EMERALD, 5, Items.DIAMOND, 1, MAX_USES_CONSUMABLE));
        offers.add(simpleOffer(Items.EMERALD, 24, Items.NETHERITE_INGOT, 1, MAX_USES_SPECIAL));
        return offers;
    }

    // ==================== 去重工具 ====================

    /**
     * 对 offers 列表做 sell-item 去重（同一 item id + count 不重复）
     */
    public static List<TradeOffer> deduplicateOffers(List<TradeOffer> offers) {
        Set<String> seen = new HashSet<>();
        List<TradeOffer> result = new ArrayList<>();
        for (TradeOffer offer : offers) {
            ItemStack sell = offer.getSellItem();
            String key = itemKey(sell);
            if (seen.add(key)) {
                result.add(offer);
            }
        }
        return result;
    }

    private static String itemKey(ItemStack stack) {
        return net.minecraft.registry.Registries.ITEM.getId(stack.getItem()).toString()
                + ":" + stack.getCount();
    }

    // ==================== 工厂方法 ====================

    private static TradeOffer simpleOffer(Item buyItem, int buyCount, Item sellItem, int sellCount, int maxUses) {
        return new TradeOffer(
                new TradedItem(buyItem, buyCount),
                new ItemStack(sellItem, sellCount),
                maxUses,
                1,    // xp
                0.05f // priceMultiplier
        );
    }

    private static TradeOffer twoInputOffer(
            Item buy1, int count1,
            Item buy2, int count2,
            Item sellItem, int sellCount,
            int maxUses) {
        return new TradeOffer(
                new TradedItem(buy1, count1),
                Optional.of(new TradedItem(buy2, count2)),
                new ItemStack(sellItem, sellCount),
                maxUses,
                1,    // xp
                0.05f // priceMultiplier
        );
    }
}
