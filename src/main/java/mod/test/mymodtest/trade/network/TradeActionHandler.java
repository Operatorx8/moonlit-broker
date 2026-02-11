package mod.test.mymodtest.trade.network;

import mod.test.mymodtest.entity.MysteriousMerchantEntity;
import mod.test.mymodtest.mixin.MerchantScreenHandlerAccessor;
import mod.test.mymodtest.registry.ModItems;
import mod.test.mymodtest.trade.SecretGateValidator;
import mod.test.mymodtest.trade.TradeAction;
import mod.test.mymodtest.trade.TradeConfig;
import mod.test.mymodtest.trade.item.TradeScrollItem;
import mod.test.mymodtest.trade.loot.BountyHandler;
import mod.test.mymodtest.world.MerchantUnlockState;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.village.Merchant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * 服务端交易操作包处理器
 */
public class TradeActionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(TradeActionHandler.class);
    private static final int TRADE_PAGE_SIZE = 7;
    private static final int ELIGIBLE_TRADE_COUNT = 15;
    private static final int REFRESH_SCROLL_COST = 1;
    private static final Item REFRESH_SCROLL_ITEM = ModItems.TRADE_SCROLL;

    // Per-session cooldown key: player UUID + merchant UUID
    private static final Map<String, Long> sessionCooldowns = new HashMap<>();

    // Track which page each player is on (per merchant session)
    // Key: playerUUID + merchantUUID, Value: true = secret page, false = normal
    // page
    private static final Map<String, Boolean> playerPageState = new HashMap<>();

    /**
     * 处理客户端发来的交易操作请求
     */
    public static void handle(TradeActionC2SPacket packet, ServerPlayNetworking.Context context) {
        ServerPlayerEntity player = context.player();

        // 1.1 FIX: Reject invalid opcode
        TradeAction action = packet.getAction();
        if (action == null) {
            LOGGER.warn("[MoonTrade] INVALID_OPCODE player={} ordinal={}",
                    player.getName().getString(), packet.action());
            return;
        }

        MysteriousMerchantEntity merchant = resolveMerchantFromPacketOrSession(player, packet.merchantId());
        if (merchant == null) {
            LOGGER.warn("[MoonTrade] INVALID_MERCHANT player={} entityId={}",
                    player.getName().getString(), packet.merchantId());
            return;
        }

        // 1.2 FIX: Validate active merchant UI session
        if (!validateMerchantSession(player, merchant)) {
            LOGGER.warn("[MoonTrade] INVALID_SESSION player={} merchantId={}",
                    player.getName().getString(), packet.merchantId());
            return;
        }

        // 1.3 FIX: Per-session throttling using world time
        // AUDIT FIX: Throttle key now includes action
        if (!checkSessionCooldown(player, merchant, action)) {
            if (TradeConfig.TRADE_DEBUG) {
                LOGGER.debug("[MoonTrade] ACTION_THROTTLED player={} action={}", player.getName().getString(), action);
            }
            return;
        }

        switch (action) {
            case OPEN_NORMAL -> handleOpenNormal(player, merchant);
            case SWITCH_SECRET -> handleSwitchSecret(player, merchant);
            case REFRESH -> handleRefresh(player, merchant);
            case PREV_PAGE, NEXT_PAGE -> handlePageNav(player, merchant, action);
            case SUBMIT_BOUNTY -> BountyHandler.trySubmitBounty(player, merchant);
        }
    }

    private static MysteriousMerchantEntity resolveMerchantFromPacketOrSession(ServerPlayerEntity player,
            int merchantId) {
        if (merchantId >= 0) {
            Entity entity = player.getServerWorld().getEntityById(merchantId);
            if (entity instanceof MysteriousMerchantEntity merchant) {
                return merchant;
            }
        }

        if (player.currentScreenHandler instanceof MerchantScreenHandler handler) {
            Merchant handlerMerchant = ((MerchantScreenHandlerAccessor) handler).getMerchant();
            if (handlerMerchant instanceof MysteriousMerchantEntity merchant) {
                return merchant;
            }
        }
        return null;
    }

    /**
     * 1.2 FIX: Validate that player has active merchant screen handler bound to
     * this merchant
     * AUDIT FIX: Added explicit handler->merchant identity cross-check
     */
    private static boolean validateMerchantSession(ServerPlayerEntity player, MysteriousMerchantEntity merchant) {
        // Check player has MerchantScreenHandler open
        if (!(player.currentScreenHandler instanceof MerchantScreenHandler handler)) {
            return false;
        }

        // AUDIT FIX: Explicit cross-check - handler's merchant must match packet's
        // merchant
        // Use accessor mixin to get the private merchant field
        Merchant handlerMerchant = ((MerchantScreenHandlerAccessor) handler).getMerchant();
        if (handlerMerchant != merchant) {
            LOGGER.warn("[MoonTrade] HANDLER_MERCHANT_MISMATCH player={} packetMerchant={} handlerMerchant={}",
                    player.getName().getString(),
                    merchant.getUuid().toString().substring(0, 8),
                    handlerMerchant instanceof MysteriousMerchantEntity m ? m.getUuid().toString().substring(0, 8)
                            : "unknown");
            return false;
        }

        // Validate the merchant's current customer is this player
        // This ensures the handler is bound to the same merchant entity
        if (merchant.getCustomer() != player) {
            return false;
        }

        // Check distance (8 blocks = 64 squared distance)
        if (player.squaredDistanceTo(merchant) > 64.0) {
            LOGGER.warn("[MoonTrade] TOO_FAR player={} distance={}",
                    player.getName().getString(), Math.sqrt(player.squaredDistanceTo(merchant)));
            return false;
        }

        // Check merchant is alive
        if (!merchant.isAlive()) {
            return false;
        }

        return true;
    }

    /**
     * 1.3 FIX: Per-session cooldown using server tick time
     * AUDIT FIX: Throttle key now includes action (player+merchant+action)
     */
    private static boolean checkSessionCooldown(ServerPlayerEntity player, MysteriousMerchantEntity merchant,
            TradeAction action) {
        String sessionKey = player.getUuid().toString() + ":" + merchant.getUuid().toString() + ":" + action.name();
        long currentTick = player.getServerWorld().getTime();
        Long lastAction = sessionCooldowns.get(sessionKey);

        if (lastAction != null) {
            long cooldownTicks = TradeConfig.PAGE_ACTION_COOLDOWN_TICKS;
            if (currentTick - lastAction < cooldownTicks) {
                return false;
            }
        }

        sessionCooldowns.put(sessionKey, currentTick);
        return true;
    }

    /**
     * Get session key for page state tracking
     */
    private static String getSessionKey(ServerPlayerEntity player, MysteriousMerchantEntity merchant) {
        return player.getUuid().toString() + ":" + merchant.getUuid().toString();
    }

    /**
     * Check if player is on secret page
     */
    private static boolean isOnSecretPage(ServerPlayerEntity player, MysteriousMerchantEntity merchant) {
        return playerPageState.getOrDefault(getSessionKey(player, merchant), false);
    }

    /**
     * Set player page state
     */
    private static void setOnSecretPage(ServerPlayerEntity player, MysteriousMerchantEntity merchant, boolean secret) {
        playerPageState.put(getSessionKey(player, merchant), secret);
    }

    private static void handleOpenNormal(ServerPlayerEntity player, MysteriousMerchantEntity merchant) {
        ItemStack scroll = SecretGateValidator.findAnyScroll(player);

        if (scroll.isEmpty()) {
            player.sendMessage(Text.literal("需要交易卷轴才能打开交易").formatted(Formatting.RED), true);
            return;
        }

        // Check scroll has enough uses BEFORE rebuilding
        if (TradeScrollItem.getUses(scroll) < TradeConfig.COST_OPEN_NORMAL) {
            player.sendMessage(Text.literal("卷轴次数不足").formatted(Formatting.RED), true);
            return;
        }

        MysteriousMerchantEntity.OfferBuildAudit audit = merchant.rebuildOffersForPlayer(player,
                MysteriousMerchantEntity.OfferBuildSource.OPEN_NORMAL);

        // Now consume scroll uses AFTER successful rebuild
        TradeScrollItem.tryConsume(scroll, TradeConfig.COST_OPEN_NORMAL);

        // Mark as on normal page
        setOnSecretPage(player, merchant, false);

        // Sync offers to client - merchant.setOffers triggers sync via setCustomer
        merchant.sendOffers(player, merchant.getDisplayName(), merchant.getExperience());

        LOGGER.info(
                "[MoonTrade] action=OPEN_NORMAL side=S player={} merchant={} cost={} offersTotal={} base={} sigil={} hidden={} offersHash={}",
                playerTag(player), merchantTag(merchant), TradeConfig.COST_OPEN_NORMAL,
                audit.offersTotal(), audit.baseCount(), audit.sigilCount(), audit.hiddenCount(),
                Integer.toHexString(audit.offersHash()));

        player.sendMessage(Text.literal("消耗卷轴次数: " + TradeConfig.COST_OPEN_NORMAL).formatted(Formatting.GRAY), true);
    }

    private static void handleSwitchSecret(ServerPlayerEntity player, MysteriousMerchantEntity merchant) {
        String variantKey = merchant.getVariantKey();
        MerchantUnlockState state = MerchantUnlockState.getServerState(player.getServerWorld());
        MerchantUnlockState.Progress progress = state.getOrCreateProgress(player.getUuid(), variantKey);
        if (!progress.isUnlockedKatanaHidden(variantKey)) {
            player.sendMessage(Text.literal("该商人变体的隐藏页尚未解锁").formatted(Formatting.RED), false);
            LOGGER.info(
                    "[MoonTrade] action=SWITCH_SECRET side=S player={} merchant={} secretSold={} blocked=1 allowed=0 reason=variant_not_unlocked variant={}",
                    player.getUuid(), merchant.getUuid(), merchant.isSecretSold() ? 1 : 0, variantKey);
            return;
        }

        // 验证门槛
        SecretGateValidator.ValidationResult result = SecretGateValidator.validate(player, merchant);

        if (!result.passed()) {
            player.sendMessage(Text.literal("无法进入隐藏交易: " + result.reason()).formatted(Formatting.RED), false);
            LOGGER.info(
                    "[MoonTrade] action=SWITCH_SECRET side=S player={} merchant={} secretSold={} blocked=1 allowed=0 reason={} cost={}",
                    player.getUuid(), merchant.getUuid(), merchant.isSecretSold() ? 1 : 0, result.reason(), 0);
            return;
        }

        // 找到封印卷轴
        ItemStack scroll = SecretGateValidator.findSealedScroll(player);

        // Check scroll has enough uses BEFORE rebuilding
        if (TradeScrollItem.getUses(scroll) < TradeConfig.COST_SWITCH_SECRET) {
            player.sendMessage(Text.literal("卷轴次数不足").formatted(Formatting.RED), true);
            LOGGER.info(
                    "[MoonTrade] action=SWITCH_SECRET side=S player={} merchant={} secretSold={} blocked=1 allowed=0 reason=scroll_uses_low cost={}",
                    player.getUuid(), merchant.getUuid(), merchant.isSecretSold() ? 1 : 0, 0);
            return;
        }

        // 2 FIX: Actually switch to secret page offers (respects secretSold flag)
        merchant.rebuildSecretOffersForPlayer(player);

        // Now consume scroll uses AFTER successful rebuild
        TradeScrollItem.tryConsume(scroll, TradeConfig.COST_SWITCH_SECRET);

        // Mark as on secret page
        setOnSecretPage(player, merchant, true);

        // Sync offers to client
        merchant.sendOffers(player, merchant.getDisplayName(), merchant.getExperience());

        LOGGER.info(
                "[MoonTrade] action=SWITCH_SECRET side=S player={} merchant={} secretSold={} blocked=0 allowed=1 reason=OK cost={}",
                player.getUuid(), merchant.getUuid(), merchant.isSecretSold() ? 1 : 0, TradeConfig.COST_SWITCH_SECRET);

        player.sendMessage(Text.literal("已进入隐藏交易页").formatted(Formatting.LIGHT_PURPLE), false);
    }

    private static void handleRefresh(ServerPlayerEntity player, MysteriousMerchantEntity merchant) {
        boolean onSecret = isOnSecretPage(player, merchant);

        // Task C: Secret refresh rejection — HIDDEN page refresh is not allowed
        if (onSecret) {
            LOGGER.info(
                    "[MoonTrade] REFRESH_SECRET_GUARD player={} merchant={} stable=true reason=refresh_rejected_on_hidden",
                    playerTag(player), merchantTag(merchant));
            player.sendMessage(Text.literal("隐藏交易页不可刷新").formatted(Formatting.RED), true);
            return;
        }

        String source = "REFRESH_NORMAL";
        String unlockState = resolveUnlockState(player, merchant);
        String refreshItemId = Registries.ITEM.getId(REFRESH_SCROLL_ITEM).toString();
        java.util.UUID merchantUuid = merchant.getUuid();
        MysteriousMerchantEntity.OfferCounters beforeCounters = merchant.snapshotOfferCounters();
        int beforeHash = beforeCounters.offersHash();
        int haveBefore = countItem(player, REFRESH_SCROLL_ITEM);

        LOGGER.info(
                "[MoonTrade] action=REFRESH_REQUEST side=S player={} merchant={} source={} beforeHash={} offersTotal={} base={} sigil={} hidden={} pageSize={}",
                playerTag(player), merchantTag(merchant), source,
                Integer.toHexString(beforeHash),
                beforeCounters.totalCount(), beforeCounters.baseCount(), beforeCounters.sigilCount(),
                beforeCounters.hiddenCount(),
                TRADE_PAGE_SIZE);

        if (merchantUuid == null) {
            player.sendMessage(Text.literal("刷新失败：商人标识异常").formatted(Formatting.RED), true);
            LOGGER.warn(
                    "[MoonTrade] action=REFRESH_DENY side=S player={} merchant={} item={} need={} have={} unlock={} source={} reason=merchant_uuid_null",
                    playerTag(player), merchantTag(merchant), refreshItemId, REFRESH_SCROLL_COST, haveBefore,
                    unlockState, source);
            return;
        }

        if (haveBefore < REFRESH_SCROLL_COST) {
            player.sendMessage(
                    Text.translatable("message.mymodtest.trade.refresh.deny",
                            new ItemStack(REFRESH_SCROLL_ITEM).getName(),
                            REFRESH_SCROLL_COST,
                            haveBefore).formatted(Formatting.RED),
                    true);
            LOGGER.info(
                    "[MoonTrade] action=REFRESH_DENY side=S player={} merchant={} item={} need={} have={} unlock={} source={} reason=insufficient_items",
                    playerTag(player), merchantTag(merchant), refreshItemId, REFRESH_SCROLL_COST, haveBefore,
                    unlockState, source);
            return;
        }

        if (!consumeItem(player, REFRESH_SCROLL_ITEM, REFRESH_SCROLL_COST)) {
            int haveNow = countItem(player, REFRESH_SCROLL_ITEM);
            player.sendMessage(
                    Text.translatable("message.mymodtest.trade.refresh.deny",
                            new ItemStack(REFRESH_SCROLL_ITEM).getName(),
                            REFRESH_SCROLL_COST,
                            haveNow).formatted(Formatting.RED),
                    true);
            LOGGER.info(
                    "[MoonTrade] action=REFRESH_DENY side=S player={} merchant={} item={} need={} have={} unlock={} source={} reason=consume_failed",
                    playerTag(player), merchantTag(merchant), refreshItemId, REFRESH_SCROLL_COST, haveNow, unlockState,
                    source);
            return;
        }

        long rebuildStartNanos = System.nanoTime();
        MysteriousMerchantEntity.OfferBuildAudit rebuildAudit = null;
        {
            ServerWorld world = player.getServerWorld();
            MerchantUnlockState state = MerchantUnlockState.getServerState(world);
            MerchantUnlockState.Progress progress = state.getOrCreateProgress(player.getUuid());
            MerchantUnlockState.Progress.RefreshCountReadResult readResult = progress
                    .readSigilRefreshSeen(merchantUuid);
            int refreshSeenBefore = readResult.count();
            int refreshSeenAfter = refreshSeenBefore + 1;
            progress.setSigilRefreshSeen(merchantUuid, refreshSeenAfter);
            state.markDirty();
            if (TradeConfig.TRADE_DEBUG) {
                LOGGER.debug(
                        "[MoonTrade] REFRESH_COUNT_WRITE playerUuid={} merchantUuid={} page=NORMAL before={} after={} source={} costApplied=1",
                        player.getUuid(), merchantUuid, refreshSeenBefore, refreshSeenAfter, readResult.source());
            }
            rebuildAudit = merchant.rebuildOffersForPlayer(player,
                    MysteriousMerchantEntity.OfferBuildSource.REFRESH_NORMAL);
        }
        long durationMs = Math.max(0L, (System.nanoTime() - rebuildStartNanos) / 1_000_000L);

        MysteriousMerchantEntity.OfferCounters afterCounters = merchant.snapshotOfferCounters();
        int afterHash = afterCounters.offersHash();
        int haveAfter = countItem(player, REFRESH_SCROLL_ITEM);

        merchant.sendOffers(player, merchant.getDisplayName(), merchant.getExperience());

        player.sendMessage(
                Text.translatable("message.mymodtest.trade.refresh.success",
                        new ItemStack(REFRESH_SCROLL_ITEM).getName(),
                        REFRESH_SCROLL_COST).formatted(Formatting.GREEN),
                true);

        LOGGER.info(
                "[MoonTrade] action=REFRESH_APPLY side=S player={} merchant={} source={} item={} cost={} haveBefore={} haveAfter={} unlock={} beforeHash={} afterHash={} offersTotal={} base={} sigil={} hidden={} seed={} cache={} refreshSeenCount={} durationMs={} pageSize={}",
                playerTag(player), merchantTag(merchant), source,
                refreshItemId, REFRESH_SCROLL_COST, haveBefore, haveAfter, unlockState,
                Integer.toHexString(beforeHash), Integer.toHexString(afterHash),
                afterCounters.totalCount(), afterCounters.baseCount(), afterCounters.sigilCount(),
                afterCounters.hiddenCount(),
                rebuildAudit == null ? -1L : rebuildAudit.seed(),
                rebuildAudit == null ? "BYPASS" : rebuildAudit.cache(),
                rebuildAudit == null ? -1 : rebuildAudit.refreshSeenCount(),
                durationMs,
                TRADE_PAGE_SIZE);
    }

    private static void handlePageNav(ServerPlayerEntity player, MysteriousMerchantEntity merchant,
            TradeAction action) {
        // 页面导航不消耗卷轴
        if (TradeConfig.TRADE_DEBUG) {
            LOGGER.debug("[MoonTrade] PAGE_NAV player={} action={}",
                    player.getName().getString(), action);
        }
        // Page navigation within current offer list - handled by vanilla UI
    }

    private static String playerTag(ServerPlayerEntity player) {
        return player.getName().getString() + "(" + uuidShort(player.getUuid()) + ")";
    }

    private static String merchantTag(MysteriousMerchantEntity merchant) {
        return uuidShort(merchant.getUuid()) + "#" + merchant.getId();
    }

    private static String uuidShort(java.util.UUID uuid) {
        if (uuid == null) {
            return "null";
        }
        String text = uuid.toString();
        return text.length() >= 8 ? text.substring(0, 8) : text;
    }

    private static int countItem(ServerPlayerEntity player, Item item) {
        int total = 0;
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (stack.isOf(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /**
     * AUDIT FIX 1: Two-pass consume to prevent partial item loss on failure.
     * Pass 1: verify total >= amount (no mutation).
     * Pass 2: deduct (guaranteed to succeed after pass 1).
     */
    private static boolean consumeItem(ServerPlayerEntity player, Item item, int amount) {
        // Pass 1: verify
        int available = 0;
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (stack.isOf(item) && !stack.isEmpty()) {
                available += stack.getCount();
                if (available >= amount)
                    break;
            }
        }
        if (available < amount) {
            return false;
        }
        // Pass 2: deduct (safe - we verified enough exist)
        int remaining = amount;
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            if (remaining <= 0)
                break;
            ItemStack stack = player.getInventory().getStack(slot);
            if (!stack.isOf(item) || stack.isEmpty())
                continue;
            int take = Math.min(remaining, stack.getCount());
            stack.decrement(take);
            remaining -= take;
        }
        player.getInventory().markDirty();
        player.playerScreenHandler.sendContentUpdates();
        return true;
    }

    private static String resolveUnlockState(ServerPlayerEntity player, MysteriousMerchantEntity merchant) {
        if (!(player.getServerWorld() instanceof ServerWorld serverWorld)) {
            return "UNKNOWN";
        }
        MerchantUnlockState state = MerchantUnlockState.getServerState(serverWorld);
        String variantKey = merchant.getVariantKey();
        MerchantUnlockState.Progress progress = state.getOrCreateProgress(player.getUuid(), variantKey);
        if (progress.isUnlockedKatanaHidden(variantKey)) {
            return "UNLOCKED";
        }
        return progress.getTradeCount(variantKey) >= ELIGIBLE_TRADE_COUNT ? "ELIGIBLE" : "LOCKED";
    }

    /**
     * Clean up session state when player closes merchant UI
     * AUDIT FIX: Now clears all action-specific cooldown keys
     */
    public static void onSessionClosed(ServerPlayerEntity player, MysteriousMerchantEntity merchant) {
        String baseKey = getSessionKey(player, merchant);
        // Remove page state
        playerPageState.remove(baseKey);
        // Remove all action-specific cooldowns
        for (TradeAction action : TradeAction.values()) {
            sessionCooldowns.remove(baseKey + ":" + action.name());
        }
    }
}
