package dev.xqanzd.moonlitbroker.trade.item;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.WrittenBookContentComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.WrittenBookItem;
import net.minecraft.network.packet.s2c.play.OpenWrittenBookS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.Stats;
import net.minecraft.text.RawFilteredPair;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;

/**
 * 指南卷轴物品
 * 首次见面赠送，仅提供信息
 */
public class GuideScrollItem extends WrittenBookItem {

    private static final String GUIDE_TITLE = "Moonlit Broker";
    private static final String GUIDE_AUTHOR = "Moonlit Broker";

    public GuideScrollItem(Settings settings) {
        super(settings);
    }

    public static void ensureGuideContent(ItemStack stack) {
        if (stack.get(DataComponentTypes.WRITTEN_BOOK_CONTENT) != null) {
            return;
        }

        List<RawFilteredPair<Text>> pages = List.of(
                RawFilteredPair.of(Text.translatable("guide.xqanzd_moonlit_broker.page.1")),
                RawFilteredPair.of(Text.translatable("guide.xqanzd_moonlit_broker.page.2")),
                RawFilteredPair.of(Text.translatable("guide.xqanzd_moonlit_broker.page.3")),
                RawFilteredPair.of(Text.translatable("guide.xqanzd_moonlit_broker.page.4")),
                RawFilteredPair.of(Text.translatable("guide.xqanzd_moonlit_broker.page.5")),
                RawFilteredPair.of(Text.translatable("guide.xqanzd_moonlit_broker.page.6")),
                RawFilteredPair.of(Text.translatable("guide.xqanzd_moonlit_broker.page.7")),
                RawFilteredPair.of(Text.translatable("guide.xqanzd_moonlit_broker.page.8")),
                RawFilteredPair.of(Text.translatable("guide.xqanzd_moonlit_broker.page.9")),
                RawFilteredPair.of(Text.translatable("guide.xqanzd_moonlit_broker.page.10"))
        );

        WrittenBookContentComponent content = new WrittenBookContentComponent(
            RawFilteredPair.of(GUIDE_TITLE),
            GUIDE_AUTHOR,
            0,
            pages,
            true
        );

        stack.set(DataComponentTypes.WRITTEN_BOOK_CONTENT, content);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        ensureGuideContent(stack);

        if (user instanceof ServerPlayerEntity serverPlayer) {
            // Vanilla only opens Items.WRITTEN_BOOK here, so custom written-book items
            // must resolve + send the open-book packet manually.
            WrittenBookItem.resolve(stack, serverPlayer.getCommandSource(), serverPlayer);
            serverPlayer.networkHandler.sendPacket(new OpenWrittenBookS2CPacket(hand));
        }

        user.incrementStat(Stats.USED.getOrCreateStat(this));
        return TypedActionResult.success(stack, world.isClient());
    }

}
