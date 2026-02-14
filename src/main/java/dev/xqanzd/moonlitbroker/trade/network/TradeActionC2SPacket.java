package dev.xqanzd.moonlitbroker.trade.network;

import dev.xqanzd.moonlitbroker.trade.TradeAction;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * C2S 交易页面操作包
 * 客户端发送到服务端请求页面操作。
 * pageIndex 为逻辑页索引（0-based），仅 REFRESH 使用，其余动作可传 -1。
 */
public record TradeActionC2SPacket(int action, int merchantId, int pageIndex) implements CustomPayload {
    
    public static final Identifier PACKET_ID = Identifier.of("xqanzd_moonlit_broker", "trade_action");
    public static final Id<TradeActionC2SPacket> ID = new Id<>(PACKET_ID);
    
    public static final PacketCodec<RegistryByteBuf, TradeActionC2SPacket> CODEC = PacketCodec.tuple(
        PacketCodecs.VAR_INT, TradeActionC2SPacket::action,
        PacketCodecs.VAR_INT, TradeActionC2SPacket::merchantId,
        PacketCodecs.VAR_INT, TradeActionC2SPacket::pageIndex,
        TradeActionC2SPacket::new
    );
    
    public TradeAction getAction() {
        return TradeAction.fromOrdinal(action);
    }
    
    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
