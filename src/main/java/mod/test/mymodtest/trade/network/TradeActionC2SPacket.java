package mod.test.mymodtest.trade.network;

import mod.test.mymodtest.trade.TradeAction;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * C2S 交易页面操作包
 * 客户端发送到服务端请求页面操作
 */
public record TradeActionC2SPacket(int action, int merchantId) implements CustomPayload {
    
    public static final Identifier PACKET_ID = Identifier.of("mymodtest", "trade_action");
    public static final Id<TradeActionC2SPacket> ID = new Id<>(PACKET_ID);
    
    public static final PacketCodec<RegistryByteBuf, TradeActionC2SPacket> CODEC = PacketCodec.tuple(
        PacketCodecs.VAR_INT, TradeActionC2SPacket::action,
        PacketCodecs.VAR_INT, TradeActionC2SPacket::merchantId,
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
