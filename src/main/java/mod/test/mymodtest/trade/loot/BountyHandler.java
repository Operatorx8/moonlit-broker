package mod.test.mymodtest.trade.loot;

import mod.test.mymodtest.entity.MysteriousMerchantEntity;
import mod.test.mymodtest.registry.ModItems;
import mod.test.mymodtest.trade.TradeConfig;
import mod.test.mymodtest.trade.item.TradeScrollItem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 悬赏提交处理器
 * 轻量级2物品提交系统
 */
public class BountyHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(BountyHandler.class);

    // 悬赏物品组合（可配置）
    private static final Item BOUNTY_ITEM_A = Items.ZOMBIE_HEAD;
    private static final Item BOUNTY_ITEM_B = Items.SKELETON_SKULL;

    /**
     * 尝试提交悬赏
     * @return true 如果提交成功
     */
    public static boolean trySubmitBounty(ServerPlayerEntity player, MysteriousMerchantEntity merchant) {
        // 检查主手和副手
        ItemStack mainHand = player.getMainHandStack();
        ItemStack offHand = player.getOffHandStack();

        boolean hasA = mainHand.isOf(BOUNTY_ITEM_A) || offHand.isOf(BOUNTY_ITEM_A);
        boolean hasB = mainHand.isOf(BOUNTY_ITEM_B) || offHand.isOf(BOUNTY_ITEM_B);

        if (!hasA) {
            player.sendMessage(Text.literal("需要 " + BOUNTY_ITEM_A.getName().getString() + " 才能提交悬赏")
                .formatted(Formatting.RED), true);
            return false;
        }

        if (!hasB) {
            player.sendMessage(Text.literal("需要 " + BOUNTY_ITEM_B.getName().getString() + " 才能提交悬赏")
                .formatted(Formatting.RED), true);
            return false;
        }

        // 原子性移除物品
        if (!removeItems(player, mainHand, offHand)) {
            player.sendMessage(Text.literal("提交失败").formatted(Formatting.RED), true);
            return false;
        }

        // 发放奖励
        grantRewards(player);

        player.sendMessage(Text.literal("悬赏已提交！获得交易卷轴和银币")
            .formatted(Formatting.GREEN), false);

        LOGGER.info("[MoonTrade] BOUNTY_SUBMIT player={}", player.getName().getString());
        return true;
    }

    private static boolean removeItems(PlayerEntity player, ItemStack mainHand, ItemStack offHand) {
        // 确定哪个手持哪个物品
        ItemStack stackA = null;
        ItemStack stackB = null;

        if (mainHand.isOf(BOUNTY_ITEM_A)) {
            stackA = mainHand;
        } else if (offHand.isOf(BOUNTY_ITEM_A)) {
            stackA = offHand;
        }

        if (mainHand.isOf(BOUNTY_ITEM_B)) {
            stackB = mainHand;
        } else if (offHand.isOf(BOUNTY_ITEM_B)) {
            stackB = offHand;
        }

        if (stackA == null || stackB == null) {
            return false;
        }

        // 原子性移除
        stackA.decrement(1);
        stackB.decrement(1);
        return true;
    }

    private static void grantRewards(ServerPlayerEntity player) {
        // 奖励1：交易卷轴
        ItemStack scroll = new ItemStack(ModItems.TRADE_SCROLL, 1);
        TradeScrollItem.initialize(scroll, TradeConfig.GRADE_NORMAL);
        if (!player.giveItemStack(scroll)) {
            player.dropItem(scroll, false);
        }

        // 奖励2：银币
        ItemStack silver = new ItemStack(ModItems.SILVER_NOTE, TradeConfig.BOUNTY_SILVER_REWARD);
        if (!player.giveItemStack(silver)) {
            player.dropItem(silver, false);
        }
    }
}
