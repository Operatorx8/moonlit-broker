package dev.xqanzd.moonlitbroker.trade.item;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.WrittenBookContentComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.WrittenBookItem;
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

    private static final String GUIDE_TITLE = "Moonlit Broker 指南";
    private static final String GUIDE_AUTHOR = "Moonlit Broker";

    public GuideScrollItem(Settings settings) {
        super(settings);
    }

    public static void ensureGuideContent(ItemStack stack) {
        if (stack.get(DataComponentTypes.WRITTEN_BOOK_CONTENT) != null) {
            return;
        }

        List<RawFilteredPair<Text>> pages = List.of(
            RawFilteredPair.of(Text.literal(
                "Moonlit Broker 指南\n\n" +
                "欢迎来到月光掮客。\n" +
                "该商人提供常规交易、刷新与悬赏提交。\n" +
                "本书用于快速查阅核心流程。"
            )),
            RawFilteredPair.of(Text.literal(
                "交易系统\n\n" +
                "1) 右键商人打开交易界面。\n" +
                "2) 常规页可直接购买。\n" +
                "3) 刷新会消耗 Trade Scroll。\n" +
                "4) 特殊页受解锁与条件限制。"
            )),
            RawFilteredPair.of(Text.literal(
                "Bounty / 刷新说明\n\n" +
                "手持悬赏契约（非潜行）右键商人可提交。\n" +
                "完成后会发放 Trade Scroll 与银币。\n" +
                "刷新交易后，滚动条与分页状态会同步更新。"
            )),
            RawFilteredPair.of(Text.literal(
                "FAQ\n\n" +
                "Q: 为什么无法进入隐藏页？\n" +
                "A: 需满足印记/卷轴/进度等条件。\n\n" +
                "Q: 为什么刷新失败？\n" +
                "A: 通常是卷轴不足或当前状态不允许刷新。"
            ))
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
    public TypedActionResult<ItemStack> use(World world, net.minecraft.entity.player.PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        ensureGuideContent(stack);
        return super.use(world, user, hand);
    }

}
