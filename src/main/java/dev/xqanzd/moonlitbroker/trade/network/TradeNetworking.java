package dev.xqanzd.moonlitbroker.trade.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 交易系统网络包注册
 */
public class TradeNetworking {
    private static final Logger LOGGER = LoggerFactory.getLogger(TradeNetworking.class);

    /**
     * 注册所有网络包（服务端）
     */
    public static void registerServer() {
        // 注册 C2S 包类型
        PayloadTypeRegistry.playC2S().register(
            TradeActionC2SPacket.ID,
            TradeActionC2SPacket.CODEC
        );
        
        // 注册 C2S 包处理器
        ServerPlayNetworking.registerGlobalReceiver(
            TradeActionC2SPacket.ID,
            TradeActionHandler::handle
        );
        
        LOGGER.info("[MoonTrade] 网络包已注册");
    }

    /**
     * 注册客户端网络包
     */
    public static void registerClient() {
        // 客户端只需要注册包类型用于发送
        // PayloadTypeRegistry 在服务端注册后客户端自动可用
    }
}
