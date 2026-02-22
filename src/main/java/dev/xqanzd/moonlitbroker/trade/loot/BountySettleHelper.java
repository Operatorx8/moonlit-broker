package dev.xqanzd.moonlitbroker.trade.loot;

import dev.xqanzd.moonlitbroker.armor.util.CooldownManager;
import dev.xqanzd.moonlitbroker.registry.ModItems;
import dev.xqanzd.moonlitbroker.trade.TradeConfig;
import dev.xqanzd.moonlitbroker.trade.item.BountyContractItem;
import dev.xqanzd.moonlitbroker.trade.item.TradeScrollItem;
import dev.xqanzd.moonlitbroker.world.MerchantUnlockState;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 悬赏自动结算助手
 * 击杀最后一个目标时自动发放基础奖励；背包满则延迟；Coin 写 pending。
 * 严禁 dropItem（岩浆/虚空安全）。
 */
public final class BountySettleHelper {
    private BountySettleHelper() {}

    private static final Logger LOGGER = LoggerFactory.getLogger(BountySettleHelper.class);

    /** 背包满提示限频 key */
    private static final String COOLDOWN_DEFER_HINT = "bounty_settle_defer";
    /** 限频冷却 (600 ticks = 30 秒) */
    private static final long DEFER_HINT_CD_TICKS = 600L;
    /** Poison pill NBT key：标记契约曾触发 partial insert，阻止后续自动结算重复发 */
    private static final String NBT_SETTLE_ATTEMPTED = "BountyAutoSettleAttempted";
    /** pendingCoinReward 软上限 */
    private static final int PENDING_COIN_CAP = 64;
    private static boolean pendingCoinCapWarned = false;
    /** Poison pill 引导提示限频 key */
    private static final String COOLDOWN_POISON_HINT = "bounty_settle_poison";
    /** 限频冷却 (6000 ticks = 5 分钟) */
    private static final long POISON_HINT_CD_TICKS = 6000L;
    /** Coin cooldown hint throttle key + duration */
    private static final String COOLDOWN_COIN_CD_HINT = "bounty_coin_cd_hint";
    private static final long COIN_CD_HINT_TICKS = 2400L;

    /**
     * 尝试自动结算背包中第一张已完成的悬赏契约。
     * 调用时机：每次击杀回调（newlyCompleted 或重试分支）。
     *
     * @return true 如果成功结算了一张契约
     */
    public static boolean tryAutoSettleIfCompleted(ServerWorld world, ServerPlayerEntity player) {
        PlayerInventory inv = player.getInventory();

        // 扫描 main + offhand 找到第一张 completed 契约
        ItemStack contractStack = null;
        int contractSlot = -1;
        boolean hasPoisonedContract = false;
        for (int i = 0; i < inv.main.size(); i++) {
            ItemStack stack = inv.main.get(i);
            if (!stack.isOf(ModItems.BOUNTY_CONTRACT)) continue;
            if (!BountyContractItem.isValidContract(stack)) continue;
            if (!BountyContractItem.isCompletedStrict(stack)) continue;
            if (isSettleAttempted(stack)) {
                hasPoisonedContract = true;
                continue;
            }
            contractStack = stack;
            contractSlot = i;
            break;
        }
        if (contractStack == null) {
            for (int i = 0; i < inv.offHand.size(); i++) {
                ItemStack stack = inv.offHand.get(i);
                if (!stack.isOf(ModItems.BOUNTY_CONTRACT)) continue;
                if (!BountyContractItem.isValidContract(stack)) continue;
                if (!BountyContractItem.isCompletedStrict(stack)) continue;
                if (isSettleAttempted(stack)) {
                    hasPoisonedContract = true;
                    continue;
                }
                contractStack = stack;
                contractSlot = -(i + 1); // negative = offhand
                break;
            }
        }
        if (contractStack == null) {
            // 没有可自动结算的契约；若存在 poison pill 契约则限频引导提示
            if (hasPoisonedContract) {
                long currentTick = world.getTime();
                if (CooldownManager.isReady(player.getUuid(), COOLDOWN_POISON_HINT, currentTick)) {
                    CooldownManager.setCooldown(player.getUuid(), COOLDOWN_POISON_HINT, currentTick, POISON_HINT_CD_TICKS);
                    player.sendMessage(
                            Text.translatable("actionbar.xqanzd_moonlit_broker.bounty.settle_anomaly")
                                    .formatted(Formatting.YELLOW),
                            false);
                }
            }
            return false;
        }

        // 准备奖励物品（不修改背包）
        ItemStack rewardScroll = new ItemStack(ModItems.TRADE_SCROLL, 1);
        TradeScrollItem.initialize(rewardScroll, TradeConfig.GRADE_NORMAL);
        ItemStack rewardSilver = new ItemStack(ModItems.SILVER_NOTE, TradeConfig.BOUNTY_SILVER_REWARD);

        // 背包容量预检查：两种奖励都必须能同时放入（考虑空槽竞争）
        if (!hasRoomForBoth(player, rewardScroll, rewardSilver)) {
            // 限频 actionbar 提示
            long currentTick = world.getTime();
            if (CooldownManager.isReady(player.getUuid(), COOLDOWN_DEFER_HINT, currentTick)) {
                CooldownManager.setCooldown(player.getUuid(), COOLDOWN_DEFER_HINT, currentTick, DEFER_HINT_CD_TICKS);
                player.sendMessage(
                        Text.translatable("actionbar.xqanzd_moonlit_broker.bounty.defer_inventory_full")
                                .formatted(Formatting.RED),
                        true);
            }
            return false;
        }

        // 提前快照诊断数据（insertStack 会修改 inventory 状态）
        int preInsertEmpty = countFreeSlots(player);
        int preInsertSilverMerge = countSilverMergeRoom(player, rewardSilver);

        // 发放奖励（insertStack）
        boolean scrollOk = inv.insertStack(rewardScroll) && rewardScroll.isEmpty();
        boolean silverOk = inv.insertStack(rewardSilver) && rewardSilver.isEmpty();
        if (!scrollOk || !silverOk) {
            // 不可达路径：hasRoomForBoth 应已排除。记录全量诊断信息以便定位根因。
            String target = BountyContractItem.getTarget(contractStack);
            int required = BountyContractItem.getRequired(contractStack);
            LOGGER.error("[BountySettle] PARTIAL_INSERT_UNREACHABLE player={} scrollOk={} silverOk={}" +
                            " scrollLeft={} silverLeft={} preInsertEmpty={} preInsertSilverMerge={}" +
                            " postInsertEmpty={} target={} required={}",
                    player.getName().getString(), scrollOk, silverOk,
                    rewardScroll.getCount(), rewardSilver.getCount(),
                    preInsertEmpty, preInsertSilverMerge,
                    countFreeSlots(player), target, required);
            // 在契约 NBT 上写 poison pill，防止后续重试重复发放部分奖励
            markSettleAttempted(contractStack);
            // 不 consume 契约，不 roll Coin
            return false;
        }

        // Coin roll（只在奖励成功发放后执行）
        int required = BountyContractItem.getRequired(contractStack);
        String tier = BountyContractItem.normalizeTier(BountyContractItem.getTier(contractStack));
        boolean coinHit = false;

        BountyHandler.CoinRollResult coinResult = BountyHandler.tryRollCoin(world, player, required, tier);

        // Coin cooldown hint (rate-limited)
        if (coinResult == BountyHandler.CoinRollResult.COOLDOWN_ACTIVE) {
            long now = world.getTime();
            if (CooldownManager.isReady(player.getUuid(), COOLDOWN_COIN_CD_HINT, now)) {
                CooldownManager.setCooldown(player.getUuid(), COOLDOWN_COIN_CD_HINT, now, COIN_CD_HINT_TICKS);
                player.sendMessage(
                        Text.translatable("actionbar.xqanzd_moonlit_broker.bounty.coin_cooldown")
                                .formatted(Formatting.GRAY),
                        true);
            }
        }

        if (coinResult == BountyHandler.CoinRollResult.HIT) {
            MerchantUnlockState state = MerchantUnlockState.getServerState(world);
            MerchantUnlockState.Progress progress = state.getOrCreateProgress(player.getUuid());
            if (progress.getPendingCoinReward() < PENDING_COIN_CAP) {
                coinHit = true;
                progress.incrementPendingCoinReward();
                state.markDirty();
            } else {
                if (!pendingCoinCapWarned) {
                    pendingCoinCapWarned = true;
                    LOGGER.warn("[BountySettle] PENDING_COIN_CAP_REACHED player={} cap={} action=COIN_DISCARDED",
                            player.getName().getString(), PENDING_COIN_CAP);
                }
            }
        }

        // consume 契约（精确移除 1 张）
        contractStack.decrement(1);

        // actionbar 成功提示
        player.sendMessage(
                Text.translatable("actionbar.xqanzd_moonlit_broker.bounty.auto_complete",
                        new ItemStack(ModItems.SILVER_NOTE).getName(),
                        TradeConfig.BOUNTY_SILVER_REWARD,
                        new ItemStack(ModItems.TRADE_SCROLL).getName(),
                        1)
                        .formatted(Formatting.GREEN),
                true);

        // Coin pending 提示
        if (coinHit) {
            player.sendMessage(
                    Text.translatable("actionbar.xqanzd_moonlit_broker.bounty.coin_pending")
                            .formatted(Formatting.GOLD),
                    false);
        }

        String target = BountyContractItem.getTarget(contractStack);
        LOGGER.info("[BountySettle] AUTO_SETTLE player={} target={} required={} tier={} coinRoll={} coinHit={}",
                player.getName().getString(), target, required, tier, coinResult, coinHit);

        return true;
    }

    /**
     * 检查玩家背包是否能容纳指定 ItemStack（不修改背包状态）。
     * 正确处理可堆叠物品的"并入已有堆叠"情况。
     */
    public static boolean hasRoomFor(PlayerEntity player, ItemStack stack) {
        if (stack.isEmpty()) return true;
        PlayerInventory inv = player.getInventory();
        int remaining = stack.getCount();

        for (ItemStack slot : inv.main) {
            if (slot.isEmpty()) {
                return true; // 空槽可以放下全部
            }
            if (ItemStack.areItemsAndComponentsEqual(slot, stack)) {
                int canFit = slot.getMaxCount() - slot.getCount();
                remaining -= canFit;
                if (remaining <= 0) return true;
            }
        }
        return false;
    }

    /**
     * 检查两种奖励是否能同时放入背包。
     * 正确处理空槽竞争：Scroll(maxCount=1) 必须独占一个空槽，
     * Silver 优先 merge 到现有堆叠，剩余部分才占用空槽。
     */
    private static boolean hasRoomForBoth(PlayerEntity player, ItemStack scroll, ItemStack silver) {
        if (scroll.isEmpty() && silver.isEmpty()) return true;
        PlayerInventory inv = player.getInventory();

        int emptySlots = 0;
        int silverMergeRoom = 0;

        for (ItemStack slot : inv.main) {
            if (slot.isEmpty()) {
                emptySlots++;
            } else if (!silver.isEmpty() && ItemStack.areItemsAndComponentsEqual(slot, silver)) {
                silverMergeRoom += slot.getMaxCount() - slot.getCount();
            }
        }

        // Scroll (maxCount=1): always needs 1 empty slot (can never merge)
        int slotsNeeded = scroll.isEmpty() ? 0 : 1;

        // Silver: merge first, remaining needs empty slots
        int silverRemaining = silver.isEmpty() ? 0 : Math.max(0, silver.getCount() - silverMergeRoom);
        if (silverRemaining > 0) {
            slotsNeeded += (silverRemaining + silver.getMaxCount() - 1) / silver.getMaxCount();
        }

        return emptySlots >= slotsNeeded;
    }

    private static int countFreeSlots(PlayerEntity player) {
        int free = 0;
        for (ItemStack stack : player.getInventory().main) {
            if (stack.isEmpty()) free++;
        }
        return free;
    }

    /** 统计 silver 可 merge 进现有堆叠的总容量（用于诊断日志） */
    private static int countSilverMergeRoom(PlayerEntity player, ItemStack silver) {
        if (silver.isEmpty()) return 0;
        int room = 0;
        for (ItemStack slot : player.getInventory().main) {
            if (!slot.isEmpty() && ItemStack.areItemsAndComponentsEqual(slot, silver)) {
                room += slot.getMaxCount() - slot.getCount();
            }
        }
        return room;
    }

    /** 在契约 NBT 上标记 partial insert poison pill */
    private static void markSettleAttempted(ItemStack contractStack) {
        NbtComponent component = contractStack.get(DataComponentTypes.CUSTOM_DATA);
        NbtCompound nbt = component != null ? component.copyNbt() : new NbtCompound();
        nbt.putBoolean(NBT_SETTLE_ATTEMPTED, true);
        contractStack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
    }

    /** 检查契约是否被标记过 partial insert poison pill */
    private static boolean isSettleAttempted(ItemStack contractStack) {
        NbtComponent component = contractStack.get(DataComponentTypes.CUSTOM_DATA);
        if (component == null) return false;
        NbtCompound nbt = component.copyNbt();
        return nbt.contains(NBT_SETTLE_ATTEMPTED) && nbt.getBoolean(NBT_SETTLE_ATTEMPTED);
    }
}
