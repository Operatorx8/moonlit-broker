package mod.test.mymodtest.trade.network;

import mod.test.mymodtest.entity.MysteriousMerchantEntity;
import mod.test.mymodtest.mixin.MerchantScreenHandlerAccessor;
import mod.test.mymodtest.trade.SecretGateValidator;
import mod.test.mymodtest.trade.TradeAction;
import mod.test.mymodtest.trade.TradeConfig;
import mod.test.mymodtest.trade.item.TradeScrollItem;
import mod.test.mymodtest.trade.loot.BountyHandler;
import mod.test.mymodtest.world.MerchantUnlockState;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
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
    
    // Per-session cooldown key: player UUID + merchant UUID
    private static final Map<String, Long> sessionCooldowns = new HashMap<>();
    
    // Track which page each player is on (per merchant session)
    // Key: playerUUID + merchantUUID, Value: true = secret page, false = normal page
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
        
        // 获取商人实体
        Entity entity = player.getServerWorld().getEntityById(packet.merchantId());
        if (!(entity instanceof MysteriousMerchantEntity merchant)) {
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
    
    /**
     * 1.2 FIX: Validate that player has active merchant screen handler bound to this merchant
     * AUDIT FIX: Added explicit handler->merchant identity cross-check
     */
    private static boolean validateMerchantSession(ServerPlayerEntity player, MysteriousMerchantEntity merchant) {
        // Check player has MerchantScreenHandler open
        if (!(player.currentScreenHandler instanceof MerchantScreenHandler handler)) {
            return false;
        }
        
        // AUDIT FIX: Explicit cross-check - handler's merchant must match packet's merchant
        // Use accessor mixin to get the private merchant field
        Merchant handlerMerchant = ((MerchantScreenHandlerAccessor) handler).getMerchant();
        if (handlerMerchant != merchant) {
            LOGGER.warn("[MoonTrade] HANDLER_MERCHANT_MISMATCH player={} packetMerchant={} handlerMerchant={}",
                player.getName().getString(), 
                merchant.getUuid().toString().substring(0, 8),
                handlerMerchant instanceof MysteriousMerchantEntity m ? m.getUuid().toString().substring(0, 8) : "unknown");
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
    private static boolean checkSessionCooldown(ServerPlayerEntity player, MysteriousMerchantEntity merchant, TradeAction action) {
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
        
        // 2 FIX: Actually rebuild offers
        merchant.rebuildOffersForPlayer(player, MysteriousMerchantEntity.OfferBuildSource.OPEN_NORMAL);
        
        // Now consume scroll uses AFTER successful rebuild
        TradeScrollItem.tryConsume(scroll, TradeConfig.COST_OPEN_NORMAL);
        
        // Mark as on normal page
        setOnSecretPage(player, merchant, false);
        
        // Sync offers to client - merchant.setOffers triggers sync via setCustomer
        merchant.sendOffers(player, merchant.getDisplayName(), merchant.getExperience());
        
        LOGGER.info("[MoonTrade] OPEN_NORMAL player={} cost={}", 
            player.getName().getString(), TradeConfig.COST_OPEN_NORMAL);
        
        player.sendMessage(Text.literal("消耗卷轴次数: " + TradeConfig.COST_OPEN_NORMAL).formatted(Formatting.GRAY), true);
    }
    
    private static void handleSwitchSecret(ServerPlayerEntity player, MysteriousMerchantEntity merchant) {
        // 验证门槛
        SecretGateValidator.ValidationResult result = SecretGateValidator.validate(player, merchant);
        
        if (!result.passed()) {
            player.sendMessage(Text.literal("无法进入隐藏交易: " + result.reason()).formatted(Formatting.RED), false);
            LOGGER.info("[MoonTrade] SWITCH_SECRET player={} merchant={} secretSold={} blocked=1 allowed=0 reason={} cost={}",
                player.getUuid(), merchant.getUuid(), merchant.isSecretSold() ? 1 : 0, result.reason(), 0);
            return;
        }
        
        // 找到封印卷轴
        ItemStack scroll = SecretGateValidator.findSealedScroll(player);
        
        // Check scroll has enough uses BEFORE rebuilding
        if (TradeScrollItem.getUses(scroll) < TradeConfig.COST_SWITCH_SECRET) {
            player.sendMessage(Text.literal("卷轴次数不足").formatted(Formatting.RED), true);
            LOGGER.info("[MoonTrade] SWITCH_SECRET player={} merchant={} secretSold={} blocked=1 allowed=0 reason=scroll_uses_low cost={}",
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
        
        LOGGER.info("[MoonTrade] SWITCH_SECRET player={} merchant={} secretSold={} blocked=0 allowed=1 reason=OK cost={}",
            player.getUuid(), merchant.getUuid(), merchant.isSecretSold() ? 1 : 0, TradeConfig.COST_SWITCH_SECRET);
        
        player.sendMessage(Text.literal("已进入隐藏交易页").formatted(Formatting.LIGHT_PURPLE), false);
    }
    
    private static void handleRefresh(ServerPlayerEntity player, MysteriousMerchantEntity merchant) {
        ItemStack scroll = SecretGateValidator.findAnyScroll(player);
        
        if (scroll.isEmpty()) {
            player.sendMessage(Text.literal("需要交易卷轴才能刷新").formatted(Formatting.RED), true);
            return;
        }
        
        // Check scroll has enough uses BEFORE rebuilding
        if (TradeScrollItem.getUses(scroll) < TradeConfig.COST_REFRESH) {
            player.sendMessage(Text.literal("卷轴次数不足").formatted(Formatting.RED), true);
            return;
        }
        
        // P0-B FIX: Capture beforeHash to detect "refresh without change"
        int beforeHash = merchant.snapshotOffersHash();
        
        // P0-2: Refresh offers based on current page
        boolean onSecret = isOnSecretPage(player, merchant);
        String page = onSecret ? "SECRET" : "NORMAL";
        int refreshSeenBefore = -1;
        int refreshSeenAfter = -1;
        String refreshReadSource = "n/a";
        MerchantUnlockState state = null;
        MerchantUnlockState.Progress progress = null;
        java.util.UUID merchantUuid = merchant.getUuid();
        if (merchantUuid == null) {
            LOGGER.warn("[MoonTrade] REFRESH_COUNT_INVALID_UUID playerUuid={} merchantUuid=null page={} source=handler_precheck costApplied=0",
                player.getUuid(), page);
            player.sendMessage(Text.literal("刷新失败：商人标识异常").formatted(Formatting.RED), true);
            return;
        }
        if (onSecret) {
            merchant.rebuildSecretOffersForPlayer(player);
        } else {
            // P0-A: refreshSigilOffers is now a no-op on entity; seed changes via refreshSeenCount
            merchant.refreshSigilOffers();
            if (player.getServerWorld() != null) {
                state = MerchantUnlockState.getServerState(player.getServerWorld());
                progress = state.getOrCreateProgress(player.getUuid());
                MerchantUnlockState.Progress.RefreshCountReadResult readResult = progress.readSigilRefreshSeen(merchantUuid);
                refreshSeenBefore = readResult.count();
                refreshReadSource = readResult.source();
                refreshSeenAfter = refreshSeenBefore + 1;
                LOGGER.debug("[MoonTrade] REFRESH_COUNT_READ playerUuid={} merchantUuid={} page=NORMAL before={} after={} source={} costApplied={}",
                    player.getUuid(), merchantUuid, refreshSeenBefore, refreshSeenBefore, refreshReadSource, 0);
                progress.setSigilRefreshSeen(merchantUuid, refreshSeenAfter);
                state.markDirty();
            }
            merchant.rebuildOffersForPlayer(player, MysteriousMerchantEntity.OfferBuildSource.REFRESH_NORMAL);
        }
        
        // P0-B FIX: Compute afterHash and only charge if offers actually changed
        int afterHash = merchant.snapshotOffersHash();
        boolean changed = (beforeHash != afterHash);
        boolean costApplied = false;
        
        if (changed) {
            // Offers changed - consume scroll uses
            costApplied = TradeScrollItem.tryConsume(scroll, TradeConfig.COST_REFRESH);
            if (!costApplied && !onSecret && progress != null && state != null && refreshSeenBefore >= 0) {
                progress.setSigilRefreshSeen(merchantUuid, refreshSeenBefore);
                refreshSeenAfter = refreshSeenBefore;
                state.markDirty();
                merchant.rebuildOffersForPlayer(player, MysteriousMerchantEntity.OfferBuildSource.ROLLBACK_CONSUME_FAIL);
                afterHash = merchant.snapshotOffersHash();
                player.sendMessage(Text.literal("刷新扣费失败，已回滚").formatted(Formatting.YELLOW), true);
            }
        } else {
            // P0-B: Offers unchanged - do NOT charge; notify player
            if (!onSecret && progress != null && state != null && refreshSeenBefore >= 0) {
                progress.setSigilRefreshSeen(merchantUuid, refreshSeenBefore);
                refreshSeenAfter = refreshSeenBefore;
                state.markDirty();
                merchant.rebuildOffersForPlayer(player, MysteriousMerchantEntity.OfferBuildSource.ROLLBACK_NO_CHANGE);
                afterHash = merchant.snapshotOffersHash();
            }
            player.sendMessage(Text.literal("刷新未产生变化，未扣费").formatted(Formatting.YELLOW), true);
        }

        if (!onSecret && progress != null && state != null && refreshSeenBefore >= 0) {
            LOGGER.debug("[MoonTrade] REFRESH_COUNT_WRITE playerUuid={} merchantUuid={} page=NORMAL before={} after={} source={} costApplied={}",
                player.getUuid(), merchantUuid, refreshSeenBefore, refreshSeenAfter, refreshReadSource, costApplied ? 1 : 0);
        }
        
        // Sync offers to client
        merchant.sendOffers(player, merchant.getDisplayName(), merchant.getExperience());
        
        // P0-B FIX: Structured audit log with per-merchant refreshSeenCount
        LOGGER.info("[MoonTrade] REFRESH_RESULT player={} merchant={} page={} beforeHash={} afterHash={} changed={} costApplied={} refreshSeenCount={} source={}",
            player.getUuid(), merchantUuid,
            page,
            Integer.toHexString(beforeHash), Integer.toHexString(afterHash),
            changed ? 1 : 0, costApplied ? 1 : 0,
            refreshSeenBefore + "->" + refreshSeenAfter,
            refreshReadSource);
        
        if (changed) {
            player.sendMessage(Text.literal("交易已刷新").formatted(Formatting.GREEN), true);
        }
    }
    
    private static void handlePageNav(ServerPlayerEntity player, MysteriousMerchantEntity merchant, TradeAction action) {
        // 页面导航不消耗卷轴
        if (TradeConfig.TRADE_DEBUG) {
            LOGGER.debug("[MoonTrade] PAGE_NAV player={} action={}", 
                player.getName().getString(), action);
        }
        // Page navigation within current offer list - handled by vanilla UI
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
