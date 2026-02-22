package dev.xqanzd.moonlitbroker.trade.loot;

import dev.xqanzd.moonlitbroker.armor.util.CooldownManager;
import dev.xqanzd.moonlitbroker.entity.MysteriousMerchantEntity;
import dev.xqanzd.moonlitbroker.registry.ModItems;
import dev.xqanzd.moonlitbroker.trade.TradeConfig;
import dev.xqanzd.moonlitbroker.trade.item.BountyContractItem;
import dev.xqanzd.moonlitbroker.trade.item.MerchantMarkItem;
import dev.xqanzd.moonlitbroker.trade.item.TradeScrollItem;
import dev.xqanzd.moonlitbroker.world.MerchantUnlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

/**
 * 悬赏提交处理器
 * 轻量级2物品提交系统
 * 5 FIX: Now scans full inventory instead of just hands
 */
public class BountyHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(BountyHandler.class);
    private static final Random RANDOM = new Random();

    /** Coin cooldown hint throttle key + duration */
    private static final String COOLDOWN_COIN_CD_HINT = "bounty_coin_cd_hint";
    private static final long COIN_CD_HINT_TICKS = 2400L;

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
            player.sendMessage(
                    Text.translatable(
                            "error.xqanzd_moonlit_broker.bounty.need_item",
                            REQUIRED_COUNT_A,
                            BOUNTY_ITEM_A.getName(),
                            countA
                    ).formatted(Formatting.RED),
                    true
            );
            return false;
        }

        if (countB < REQUIRED_COUNT_B) {
            player.sendMessage(
                    Text.translatable(
                            "error.xqanzd_moonlit_broker.bounty.need_item",
                            REQUIRED_COUNT_B,
                            BOUNTY_ITEM_B.getName(),
                            countB
                    ).formatted(Formatting.RED),
                    true
            );
            return false;
        }

        // 5 FIX: Atomically remove items from inventory
        // Pre-verify counts are sufficient before removing
        // AUDIT FIX: Pass player for rollback fail-safe
        if (!removeItemsFromInventory(inventory, BOUNTY_ITEM_A, REQUIRED_COUNT_A,
                BOUNTY_ITEM_B, REQUIRED_COUNT_B, player)) {
            player.sendMessage(Text.translatable("error.xqanzd_moonlit_broker.bounty.remove_failed")
                    .formatted(Formatting.RED), true);
            LOGGER.error("[MoonTrade] BOUNTY_REMOVE_FAILED player={}", player.getName().getString());
            return false;
        }

        // 发放奖励 (only after successful removal) — legacy skull path, no contract context
        grantRewards(player, 0, BountyContractItem.TIER_COMMON);

        player.sendMessage(
                Text.translatable(
                        "msg.xqanzd_moonlit_broker.bounty.rewards_granted",
                        new ItemStack(ModItems.TRADE_SCROLL).getName(),
                        1,
                        new ItemStack(ModItems.SILVER_NOTE).getName(),
                        TradeConfig.BOUNTY_SILVER_REWARD
                ).formatted(Formatting.GREEN),
                false
        );

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

    /** Coin roll 结果 */
    public enum CoinRollResult {
        COOLDOWN_ACTIVE, // 冷却中，未 roll
        MISS,            // roll 了但未命中
        HIT              // roll 并命中
    }

    /**
     * 复用的 Coin roll 逻辑（冷却 + 首次保底 + 概率），不发放物品。
     * 调用方根据返回值决定如何处理（直接给 / 写 pending）。
     * 冷却按"尝试"写入，即使 MISS 也会消耗冷却。
     */
    public static CoinRollResult tryRollCoin(ServerWorld world, ServerPlayerEntity player, int required, String tier) {
        MerchantUnlockState state = MerchantUnlockState.getServerState(world);
        MerchantUnlockState.Progress progress = state.getOrCreateProgress(player.getUuid());
        long now = world.getTime();
        long last = progress.getLastCoinBountyTick();

        if (last >= 0 && now - last < TradeConfig.COIN_BOUNTY_CD_TICKS) {
            return CoinRollResult.COOLDOWN_ACTIVE;
        }

        progress.setLastCoinBountyTick(now);
        state.markDirty();

        boolean isEliteOrRare = BountyContractItem.TIER_ELITE.equals(tier)
                || BountyContractItem.TIER_RARE.equals(tier);

        float bonusCoinChance = 0f;
        if (TradeConfig.BOUNTY_ENABLE_REWARD_SCALING && required > 0) {
            if (isEliteOrRare) {
                bonusCoinChance = Math.max(0f, Math.min(
                        (required - 5) * TradeConfig.BOUNTY_COIN_BONUS_PER_REQUIRED_ELITE,
                        TradeConfig.BOUNTY_COIN_BONUS_MAX_ELITE));
            } else {
                bonusCoinChance = Math.max(0f, Math.min(
                        (required - 10) * TradeConfig.BOUNTY_COIN_BONUS_PER_REQUIRED_NORMAL,
                        TradeConfig.BOUNTY_COIN_BONUS_MAX_NORMAL));
            }
        }
        float effectiveCoinChance = Math.min(1f, TradeConfig.COIN_BOUNTY_CHANCE + bonusCoinChance);

        // Tier-based coin chance adjustment
        if (BountyContractItem.TIER_RARE.equals(tier)) {
            effectiveCoinChance = Math.min(TradeConfig.COIN_RARE_CAP,
                    effectiveCoinChance * TradeConfig.COIN_RARE_MULT + TradeConfig.COIN_RARE_ADD);
        } else if (BountyContractItem.TIER_ELITE.equals(tier)) {
            effectiveCoinChance = Math.min(TradeConfig.COIN_ELITE_CAP,
                    effectiveCoinChance * TradeConfig.COIN_ELITE_MULT + TradeConfig.COIN_ELITE_ADD);
        }
        // common: no adjustment (keeps original behavior)

        boolean guaranteeUsed = false;
        if (TradeConfig.COIN_FIRST_BOUNTY_GUARANTEE && !progress.hasEverReceivedBountyCoin()) {
            guaranteeUsed = true;
            progress.setHasEverReceivedBountyCoin(true);
            state.markDirty();
            if (TradeConfig.TRADE_DEBUG) {
                LOGGER.debug("[Bounty] COIN_ROLL tier={} required={} baseChance={} bonus={} tierChance={} cap={} guaranteeUsed=true result=HIT player={}",
                        tier, required, TradeConfig.COIN_BOUNTY_CHANCE, bonusCoinChance, effectiveCoinChance,
                        BountyContractItem.TIER_RARE.equals(tier) ? TradeConfig.COIN_RARE_CAP
                                : BountyContractItem.TIER_ELITE.equals(tier) ? TradeConfig.COIN_ELITE_CAP : "none",
                        player.getName().getString());
            }
            LOGGER.info("[Bounty] COIN_GUARANTEE_FIRST player={} now={}",
                    player.getName().getString(), now);
            return CoinRollResult.HIT;
        }

        float coinRoll = RANDOM.nextFloat();
        CoinRollResult result = coinRoll < effectiveCoinChance ? CoinRollResult.HIT : CoinRollResult.MISS;

        if (TradeConfig.TRADE_DEBUG) {
            LOGGER.debug("[Bounty] COIN_ROLL tier={} required={} baseChance={} bonus={} tierChance={} cap={} roll={} result={} player={}",
                    tier, required, TradeConfig.COIN_BOUNTY_CHANCE, bonusCoinChance, effectiveCoinChance,
                    BountyContractItem.TIER_RARE.equals(tier) ? TradeConfig.COIN_RARE_CAP
                            : BountyContractItem.TIER_ELITE.equals(tier) ? TradeConfig.COIN_ELITE_CAP : "none",
                    coinRoll, result, player.getName().getString());
        }

        return result;
    }

    /**
     * AUDIT FIX: Added logging for reward grant/drop outcome (guarded under
     * TRADE_DEBUG)
     *
     * @param required   contract kill requirement (0 = legacy/unknown, no scaling)
     * @param tier       bounty tier string (common/elite/rare)
     */
    public static void grantRewards(ServerPlayerEntity player, int required, String tier) {
        final int rewardScroll = 1;
        final int rewardSilver = TradeConfig.BOUNTY_SILVER_REWARD;
        boolean anyDropped = false;
        boolean coinCooldownReady = false;
        boolean coinRolled = false;
        boolean coinGranted = false;

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

        // 奖励3：Coin（复用 tryRollCoin 保证公式一致）
        if (player.getWorld() instanceof ServerWorld serverWorld) {
            CoinRollResult coinResult = tryRollCoin(serverWorld, player, required, tier);
            coinRolled = coinResult != CoinRollResult.COOLDOWN_ACTIVE;
            coinCooldownReady = coinRolled;
            coinGranted = coinResult == CoinRollResult.HIT;
            if (coinGranted) {
                ItemStack coin = new ItemStack(ModItems.MYSTERIOUS_COIN, 1);
                if (!player.giveItemStack(coin)) {
                    player.dropItem(coin, false);
                    anyDropped = true;
                }
            }

            // Coin cooldown hint: rate-limited actionbar when on cooldown
            if (coinResult == CoinRollResult.COOLDOWN_ACTIVE) {
                long now = serverWorld.getTime();
                if (CooldownManager.isReady(player.getUuid(), COOLDOWN_COIN_CD_HINT, now)) {
                    CooldownManager.setCooldown(player.getUuid(), COOLDOWN_COIN_CD_HINT, now, COIN_CD_HINT_TICKS);
                    player.sendMessage(
                            Text.translatable("actionbar.xqanzd_moonlit_broker.bounty.coin_cooldown")
                                    .formatted(Formatting.GRAY),
                            true);
                }
            }

            if (TradeConfig.TRADE_DEBUG) {
                LOGGER.info(
                        "[MoonTrade] BOUNTY_COIN_ROLL player={} cdReady={} rolled={} granted={}",
                        player.getName().getString(), coinCooldownReady, coinRolled, coinGranted);
            }
        }

        // 奖励4：如果玩家没有绑定的商人印记，给一个
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
            player.sendMessage(Text.translatable("msg.xqanzd_moonlit_broker.bounty.mark_granted")
                    .formatted(Formatting.GOLD), false);
        }

        LOGGER.info(
                "[MoonTrade] action=BOUNTY_REWARD side=S player={} rewardScroll={} rewardSilver={} coinRolled={} coinGranted={} dropped={}",
                player.getName().getString(), rewardScroll, rewardSilver, coinRolled, coinGranted, anyDropped);

        // 背包满提示
        if (anyDropped) {
            player.sendMessage(
                    Text.translatable("msg.xqanzd_moonlit_broker.bounty.rewards_dropped")
                            .formatted(Formatting.YELLOW),
                    false
            );
        }
    }
}
