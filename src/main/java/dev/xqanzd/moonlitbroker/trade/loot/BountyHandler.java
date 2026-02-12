package dev.xqanzd.moonlitbroker.trade.loot;

import dev.xqanzd.moonlitbroker.entity.MysteriousMerchantEntity;
import dev.xqanzd.moonlitbroker.registry.ModItems;
import dev.xqanzd.moonlitbroker.trade.TradeConfig;
import dev.xqanzd.moonlitbroker.trade.item.MerchantMarkItem;
import dev.xqanzd.moonlitbroker.trade.item.TradeScrollItem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 悬赏提交处理器
 * 轻量级2物品提交系统
 * 5 FIX: Now scans full inventory instead of just hands
 */
public class BountyHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(BountyHandler.class);

    // 悬赏物品组合（可配置）
    private static final Item BOUNTY_ITEM_A = Items.ZOMBIE_HEAD;
    private static final Item BOUNTY_ITEM_B = Items.SKELETON_SKULL;
    private static final int REQUIRED_COUNT_A = 1;
    private static final int REQUIRED_COUNT_B = 1;

    /**
     * 尝试提交悬赏
     * 5 FIX: Scans full inventory for required items
     * 
     * @return true 如果提交成功
     */
    public static boolean trySubmitBounty(ServerPlayerEntity player, MysteriousMerchantEntity merchant) {
        PlayerInventory inventory = player.getInventory();

        // 5 FIX: Count items across entire inventory
        int countA = countItemInInventory(inventory, BOUNTY_ITEM_A);
        int countB = countItemInInventory(inventory, BOUNTY_ITEM_B);

        // AUDIT FIX: Log counts found (guarded under TRADE_DEBUG)
        if (TradeConfig.TRADE_DEBUG) {
            LOGGER.debug("[MoonTrade] BOUNTY_COUNT player={} itemA={} countA={} itemB={} countB={}",
                    player.getName().getString(), BOUNTY_ITEM_A, countA, BOUNTY_ITEM_B, countB);
        }

        if (countA < REQUIRED_COUNT_A) {
            player.sendMessage(Text.literal("需要 " + REQUIRED_COUNT_A + " 个 " +
                    BOUNTY_ITEM_A.getName().getString() + " 才能提交悬赏 (当前: " + countA + ")")
                    .formatted(Formatting.RED), true);
            return false;
        }

        if (countB < REQUIRED_COUNT_B) {
            player.sendMessage(Text.literal("需要 " + REQUIRED_COUNT_B + " 个 " +
                    BOUNTY_ITEM_B.getName().getString() + " 才能提交悬赏 (当前: " + countB + ")")
                    .formatted(Formatting.RED), true);
            return false;
        }

        // 5 FIX: Atomically remove items from inventory
        // Pre-verify counts are sufficient before removing
        // AUDIT FIX: Pass player for rollback fail-safe
        if (!removeItemsFromInventory(inventory, BOUNTY_ITEM_A, REQUIRED_COUNT_A,
                BOUNTY_ITEM_B, REQUIRED_COUNT_B, player)) {
            player.sendMessage(Text.literal("提交失败：物品移除错误").formatted(Formatting.RED), true);
            LOGGER.error("[MoonTrade] BOUNTY_REMOVE_FAILED player={}", player.getName().getString());
            return false;
        }

        // 发放奖励 (only after successful removal)
        grantRewards(player);

        player.sendMessage(Text.literal("悬赏已提交！获得交易卷轴和银币")
                .formatted(Formatting.GREEN), false);

        LOGGER.info("[MoonTrade] BOUNTY_SUBMIT player={}", player.getName().getString());
        return true;
    }

    /**
     * 5 FIX: Count how many of an item exist in the entire inventory
     */
    private static int countItemInInventory(PlayerInventory inventory, Item item) {
        int count = 0;
        // Main inventory (includes hotbar)
        for (ItemStack stack : inventory.main) {
            if (stack.isOf(item)) {
                count += stack.getCount();
            }
        }
        // Offhand
        for (ItemStack stack : inventory.offHand) {
            if (stack.isOf(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /**
     * 5 FIX: Atomically remove items from inventory
     * Removes item A first, then item B. If both succeed, returns true.
     * AUDIT FIX: Validates rollback insertion; drops items to world if rollback
     * fails.
     */
    private static boolean removeItemsFromInventory(PlayerInventory inventory,
            Item itemA, int countA,
            Item itemB, int countB,
            ServerPlayerEntity player) {
        // Remove item A
        int removedA = removeItemFromInventory(inventory, itemA, countA);
        if (TradeConfig.TRADE_DEBUG) {
            LOGGER.debug("[MoonTrade] BOUNTY_REMOVE_A player={} requested={} removed={}",
                    player.getName().getString(), countA, removedA);
        }
        if (removedA < countA) {
            // Rollback: give back what we removed (shouldn't happen if counts were
            // verified)
            if (removedA > 0) {
                rollbackWithFailsafe(inventory, player, itemA, removedA);
            }
            return false;
        }

        // Remove item B
        int removedB = removeItemFromInventory(inventory, itemB, countB);
        if (TradeConfig.TRADE_DEBUG) {
            LOGGER.debug("[MoonTrade] BOUNTY_REMOVE_B player={} requested={} removed={}",
                    player.getName().getString(), countB, removedB);
        }
        if (removedB < countB) {
            // Rollback: give back both items
            rollbackWithFailsafe(inventory, player, itemA, removedA);
            if (removedB > 0) {
                rollbackWithFailsafe(inventory, player, itemB, removedB);
            }
            return false;
        }

        return true;
    }

    /**
     * AUDIT FIX: Rollback with fail-safe - if insertStack fails, drop to world and
     * log ERROR.
     */
    private static void rollbackWithFailsafe(PlayerInventory inventory, ServerPlayerEntity player, Item item,
            int count) {
        ItemStack rollbackStack = new ItemStack(item, count);
        if (!inventory.insertStack(rollbackStack)) {
            // Insertion failed - drop remaining items to world as fail-safe
            int remaining = rollbackStack.getCount();
            if (remaining > 0) {
                LOGGER.error("[MoonTrade] BOUNTY_ROLLBACK_FAIL player={} item={} count={} - dropping to world",
                        player.getName().getString(), item, remaining);
                player.dropItem(rollbackStack, false);
            }
        }
    }

    /**
     * 5 FIX: Remove a specific count of an item from inventory
     * 
     * @return the actual number of items removed
     */
    private static int removeItemFromInventory(PlayerInventory inventory, Item item, int count) {
        int remaining = count;

        // Remove from main inventory first
        for (int i = 0; i < inventory.main.size() && remaining > 0; i++) {
            ItemStack stack = inventory.main.get(i);
            if (stack.isOf(item)) {
                int toRemove = Math.min(remaining, stack.getCount());
                stack.decrement(toRemove);
                remaining -= toRemove;
            }
        }

        // Remove from offhand if needed
        for (int i = 0; i < inventory.offHand.size() && remaining > 0; i++) {
            ItemStack stack = inventory.offHand.get(i);
            if (stack.isOf(item)) {
                int toRemove = Math.min(remaining, stack.getCount());
                stack.decrement(toRemove);
                remaining -= toRemove;
            }
        }

        return count - remaining;
    }

    /**
     * AUDIT FIX: Added logging for reward grant/drop outcome (guarded under
     * TRADE_DEBUG)
     */
    public static void grantRewards(ServerPlayerEntity player) {
        final int rewardScroll = 1;
        final int rewardSilver = TradeConfig.BOUNTY_SILVER_REWARD;
        boolean anyDropped = false;

        // 奖励1：交易卷轴 (Grade=NORMAL, Uses=3)
        ItemStack scroll = new ItemStack(ModItems.TRADE_SCROLL, rewardScroll);
        TradeScrollItem.initialize(scroll, TradeConfig.GRADE_NORMAL);
        if (!player.giveItemStack(scroll)) {
            player.dropItem(scroll, false);
            anyDropped = true;
        }

        // 奖励2：银币
        ItemStack silver = new ItemStack(ModItems.SILVER_NOTE, rewardSilver);
        if (!player.giveItemStack(silver)) {
            player.dropItem(silver, false);
            anyDropped = true;
        }

        // 奖励3：如果玩家没有绑定的商人印记，给一个
        if (!MerchantMarkItem.playerHasValidMark(player)) {
            ItemStack mark = new ItemStack(ModItems.MERCHANT_MARK, 1);
            MerchantMarkItem.bindToPlayer(mark, player);
            boolean markGiven = player.giveItemStack(mark);
            if (!markGiven) {
                player.dropItem(mark, false);
                anyDropped = true;
            }
            LOGGER.info("[MoonTrade] action=BOUNTY_REWARD_MARK side=S player={} given={}",
                    player.getName().getString(), markGiven ? "inventory" : "dropped");
            player.sendMessage(Text.literal("获得商人印记！").formatted(Formatting.GOLD), false);
        }

        LOGGER.info("[MoonTrade] action=BOUNTY_REWARD side=S player={} rewardScroll={} rewardSilver={} dropped={}",
                player.getName().getString(), rewardScroll, rewardSilver, anyDropped);

        // 背包满提示
        if (anyDropped) {
            player.sendMessage(
                    Text.literal("背包已满，部分奖励掉落在脚下").formatted(Formatting.YELLOW), false);
        }
    }
}
