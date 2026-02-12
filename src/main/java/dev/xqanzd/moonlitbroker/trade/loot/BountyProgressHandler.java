package dev.xqanzd.moonlitbroker.trade.loot;

import dev.xqanzd.moonlitbroker.registry.ModItems;
import dev.xqanzd.moonlitbroker.trade.TradeConfig;
import dev.xqanzd.moonlitbroker.trade.item.BountyContractItem;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 悬赏进度处理器
 * 监听击杀事件，更新玩家背包中匹配的 BountyContract 进度
 */
public class BountyProgressHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(BountyProgressHandler.class);

    public static void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register(BountyProgressHandler::onEntityDeath);
        LOGGER.info("[MoonTrade] 悬赏进度处理器已注册");
    }

    private static void onEntityDeath(LivingEntity entity, DamageSource source) {
        // 只处理玩家击杀
        if (!(source.getAttacker() instanceof ServerPlayerEntity player)) {
            return;
        }

        // 扫描玩家背包中的契约
        for (int i = 0; i < player.getInventory().main.size(); i++) {
            ItemStack stack = player.getInventory().main.get(i);
            if (!stack.isOf(ModItems.BOUNTY_CONTRACT))
                continue;
            if (!BountyContractItem.isValidContract(stack))
                continue;
            if (BountyContractItem.isCompleted(stack))
                continue;

            // 检查目标是否匹配
            if (!BountyContractItem.matchesTarget(stack, entity.getType()))
                continue;

            // 更新进度
            boolean newlyCompleted = BountyContractItem.incrementProgress(stack);
            int progress = BountyContractItem.getProgress(stack);
            int required = BountyContractItem.getRequired(stack);
            String target = BountyContractItem.getTarget(stack);

            LOGGER.info("[MoonTrade] action=BOUNTY_PROGRESS player={} target={} progress={}/{} completed={}",
                    player.getName().getString(), target, progress, required,
                    newlyCompleted || BountyContractItem.isCompleted(stack));

            if (newlyCompleted) {
                player.sendMessage(
                        net.minecraft.text.Text.literal("悬赏完成！可以右键商人提交契约")
                                .formatted(net.minecraft.util.Formatting.GREEN),
                        false);
            }

            // 只更新第一张匹配的契约
            break;
        }
    }
}
