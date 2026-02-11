package mod.test.mymodtest.trade.command;

import com.mojang.brigadier.CommandDispatcher;
import mod.test.mymodtest.trade.TradeConfig;
import mod.test.mymodtest.trade.loot.BountyHandler;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * /mm_bounty_submit 命令
 * OP-only，直接发放 Bounty 奖励（用于测试/手工验证）
 */
public final class BountySubmitCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(BountySubmitCommand.class);

    private BountySubmitCommand() {
    }

    /**
     * 注册命令 — 在 ModInitializer.onInitialize() 中调用
     */
    public static void register() {
        CommandRegistrationCallback.EVENT
                .register((dispatcher, registryAccess, environment) -> registerCommand(dispatcher));
    }

    private static void registerCommand(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
                CommandManager.literal("mm_bounty_submit")
                        .requires(source -> source.hasPermissionLevel(2))
                        .executes(context -> execute(context.getSource())));
    }

    private static int execute(ServerCommandSource source) {
        // 服务端权威：客户端不应到达此处，但做防御检查
        if (source.getWorld().isClient()) {
            return 0;
        }

        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("此命令只能由玩家执行"));
            return 0;
        }

        // 发放奖励（复用 BountyHandler）
        BountyHandler.grantRewards(player);

        // 统一日志格式
        LOGGER.info("[MoonTrade] action=BOUNTY_SUBMIT side=S player={} rewardScroll=1 rewardSilver={}",
                player.getName().getString(), TradeConfig.BOUNTY_SILVER_REWARD);

        player.sendMessage(
                Text.literal("悬赏奖励已发放！获得交易卷轴 ×1 + 银币 ×" + TradeConfig.BOUNTY_SILVER_REWARD)
                        .formatted(Formatting.GREEN),
                false);

        return 1;
    }
}
