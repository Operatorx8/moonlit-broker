package mod.test.mymodtest.trade.network;

import mod.test.mymodtest.entity.MysteriousMerchantEntity;
import mod.test.mymodtest.trade.SecretGateValidator;
import mod.test.mymodtest.trade.TradeAction;
import mod.test.mymodtest.trade.TradeConfig;
import mod.test.mymodtest.trade.item.TradeScrollItem;
import mod.test.mymodtest.trade.loot.BountyHandler;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 服务端交易操作包处理器
 */
public class TradeActionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(TradeActionHandler.class);
    
    // 玩家操作冷却追踪
    private static final Map<UUID, Long> playerCooldowns = new HashMap<>();

    /**
     * 处理客户端发来的交易操作请求
     */
    public static void handle(TradeActionC2SPacket packet, ServerPlayNetworking.Context context) {
        ServerPlayerEntity player = context.player();
        
        // 冷却检查
        if (!checkCooldown(player)) {
            if (TradeConfig.TRADE_DEBUG) {
                LOGGER.debug("[MoonTrade] ACTION_THROTTLED player={}", player.getName().getString());
            }
            return;
        }
        
        // 获取商人实体
        Entity entity = player.getServerWorld().getEntityById(packet.merchantId());
        if (!(entity instanceof MysteriousMerchantEntity merchant)) {
            LOGGER.warn("[MoonTrade] INVALID_MERCHANT player={} entityId={}", 
                player.getName().getString(), packet.merchantId());
            return;
        }
        
        // 检查距离
        if (player.squaredDistanceTo(merchant) > 64.0) { // 8格距离
            LOGGER.warn("[MoonTrade] TOO_FAR player={} distance={}", 
                player.getName().getString(), Math.sqrt(player.squaredDistanceTo(merchant)));
            return;
        }
        
        TradeAction action = packet.getAction();
        
        switch (action) {
            case OPEN_NORMAL -> handleOpenNormal(player, merchant);
            case SWITCH_SECRET -> handleSwitchSecret(player, merchant);
            case REFRESH -> handleRefresh(player, merchant);
            case PREV_PAGE, NEXT_PAGE -> handlePageNav(player, merchant, action);
            case SUBMIT_BOUNTY -> BountyHandler.trySubmitBounty(player, merchant);
        }
    }
    
    private static boolean checkCooldown(ServerPlayerEntity player) {
        long now = System.currentTimeMillis();
        Long lastAction = playerCooldowns.get(player.getUuid());
        
        if (lastAction != null) {
            long cooldownMs = TradeConfig.PAGE_ACTION_COOLDOWN_TICKS * 50L; // ticks to ms
            if (now - lastAction < cooldownMs) {
                return false;
            }
        }
        
        playerCooldowns.put(player.getUuid(), now);
        return true;
    }
    
    private static void handleOpenNormal(ServerPlayerEntity player, MysteriousMerchantEntity merchant) {
        ItemStack scroll = SecretGateValidator.findAnyScroll(player);
        
        if (scroll.isEmpty()) {
            player.sendMessage(Text.literal("需要交易卷轴才能打开交易").formatted(Formatting.RED), true);
            return;
        }
        
        if (!TradeScrollItem.tryConsume(scroll, TradeConfig.COST_OPEN_NORMAL)) {
            player.sendMessage(Text.literal("卷轴次数不足").formatted(Formatting.RED), true);
            return;
        }
        
        LOGGER.info("[MoonTrade] OPEN_NORMAL player={} cost={}", 
            player.getName().getString(), TradeConfig.COST_OPEN_NORMAL);
        
        // 交易界面由原版处理，这里只消耗卷轴
        player.sendMessage(Text.literal("消耗卷轴次数: " + TradeConfig.COST_OPEN_NORMAL).formatted(Formatting.GRAY), true);
    }
    
    private static void handleSwitchSecret(ServerPlayerEntity player, MysteriousMerchantEntity merchant) {
        // 验证门槛
        SecretGateValidator.ValidationResult result = SecretGateValidator.validate(player, merchant);
        
        if (!result.passed()) {
            player.sendMessage(Text.literal("无法进入隐藏交易: " + result.reason()).formatted(Formatting.RED), false);
            LOGGER.info("[MoonTrade] SWITCH_SECRET_DENIED player={} reason={}", 
                player.getName().getString(), result.reason());
            return;
        }
        
        // 找到封印卷轴并消耗
        ItemStack scroll = SecretGateValidator.findSealedScroll(player);
        if (!TradeScrollItem.tryConsume(scroll, TradeConfig.COST_SWITCH_SECRET)) {
            player.sendMessage(Text.literal("卷轴次数不足").formatted(Formatting.RED), true);
            return;
        }
        
        LOGGER.info("[MoonTrade] SWITCH_SECRET_SUCCESS player={} cost={}", 
            player.getName().getString(), TradeConfig.COST_SWITCH_SECRET);
        
        player.sendMessage(Text.literal("已进入隐藏交易页").formatted(Formatting.LIGHT_PURPLE), false);
        // TODO: 实际切换交易列表
    }
    
    private static void handleRefresh(ServerPlayerEntity player, MysteriousMerchantEntity merchant) {
        ItemStack scroll = SecretGateValidator.findAnyScroll(player);
        
        if (scroll.isEmpty()) {
            player.sendMessage(Text.literal("需要交易卷轴才能刷新").formatted(Formatting.RED), true);
            return;
        }
        
        if (!TradeScrollItem.tryConsume(scroll, TradeConfig.COST_REFRESH)) {
            player.sendMessage(Text.literal("卷轴次数不足").formatted(Formatting.RED), true);
            return;
        }
        
        LOGGER.info("[MoonTrade] REFRESH player={} cost={}", 
            player.getName().getString(), TradeConfig.COST_REFRESH);
        
        player.sendMessage(Text.literal("交易已刷新").formatted(Formatting.GREEN), true);
        // TODO: 实际刷新交易列表
    }
    
    private static void handlePageNav(ServerPlayerEntity player, MysteriousMerchantEntity merchant, TradeAction action) {
        // 页面导航不消耗卷轴
        if (TradeConfig.TRADE_DEBUG) {
            LOGGER.debug("[MoonTrade] PAGE_NAV player={} action={}", 
                player.getName().getString(), action);
        }
        // TODO: 实际页面导航
    }
}
