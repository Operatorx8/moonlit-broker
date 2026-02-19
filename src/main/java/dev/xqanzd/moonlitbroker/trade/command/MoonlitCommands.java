package dev.xqanzd.moonlitbroker.trade.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.xqanzd.moonlitbroker.world.MerchantUnlockState;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * /moonlit status          — 玩家自查 MarkBound 状态
 * /moonlit check_mark <player> — 管理员查看指定玩家状态
 * /moonlit reset_mark <player> — 管理员重置指定玩家 MarkBound
 */
public final class MoonlitCommands {
    private static final Logger LOGGER = LoggerFactory.getLogger(MoonlitCommands.class);
    private static final long TICKS_PER_GAME_DAY = 24000L;
    private static final long TICKS_PER_REAL_SECOND = 20L;

    private MoonlitCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT
                .register((dispatcher, registryAccess, environment) -> registerCommands(dispatcher));
    }

    private static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
                CommandManager.literal("moonlit")
                        .then(CommandManager.literal("status")
                                .executes(MoonlitCommands::executeStatus))
                        .then(CommandManager.literal("check_mark")
                                .requires(source -> source.hasPermissionLevel(2))
                                .then(CommandManager.argument("player", EntityArgumentType.player())
                                        .executes(MoonlitCommands::executeCheckMark)))
                        .then(CommandManager.literal("reset_mark")
                                .requires(source -> source.hasPermissionLevel(2))
                                .then(CommandManager.argument("player", EntityArgumentType.player())
                                        .executes(MoonlitCommands::executeResetMark)))
        );
    }

    private static int executeStatus(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();
        ServerWorld world = source.getWorld();

        MerchantUnlockState state = MerchantUnlockState.getServerState(world);
        MerchantUnlockState.Progress progress = state.getOrCreateProgress(player.getUuid());

        if (progress.isMarkBound()) {
            String elapsed = formatElapsed(world, progress.getMarkBoundTick());
            player.sendMessage(
                    Text.translatable("msg.xqanzd_moonlit_broker.mark.status_bound",
                            progress.getMarkBoundVersion(),
                            elapsed)
                            .formatted(Formatting.GREEN),
                    false);
        } else {
            player.sendMessage(
                    Text.translatable("msg.xqanzd_moonlit_broker.mark.status_unbound")
                            .formatted(Formatting.YELLOW),
                    false);
        }
        return 1;
    }

    private static int executeCheckMark(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "player");
        ServerWorld world = source.getWorld();

        MerchantUnlockState state = MerchantUnlockState.getServerState(world);
        MerchantUnlockState.Progress progress = state.getOrCreateProgress(target.getUuid());

        UUID merchantUuid = progress.getMarkBoundMerchantUuid();
        String merchantStr = merchantUuid != null ? merchantUuid.toString().substring(0, 8) : "N/A";

        String elapsed = formatElapsed(world, progress.getMarkBoundTick());

        source.sendFeedback(
                () -> Text.translatable("msg.xqanzd_moonlit_broker.mark.check_result",
                        target.getName().getString(),
                        progress.isMarkBound(),
                        progress.getMarkBoundVersion(),
                        elapsed,
                        merchantStr)
                        .formatted(Formatting.AQUA),
                false);

        LOGGER.info("[MoonTrade] ADMIN_CHECK_MARK admin={} target={} bound={} ver={} tick={} merchant={}",
                source.getName(), target.getName().getString(),
                progress.isMarkBound(), progress.getMarkBoundVersion(),
                progress.getMarkBoundTick(), merchantStr);
        return 1;
    }

    private static int executeResetMark(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "player");
        ServerWorld world = source.getWorld();

        MerchantUnlockState state = MerchantUnlockState.getServerState(world);
        MerchantUnlockState.Progress progress = state.getOrCreateProgress(target.getUuid());

        progress.resetMarkBound();
        state.markDirty();

        source.sendFeedback(
                () -> Text.translatable("msg.xqanzd_moonlit_broker.mark.reset_done",
                        target.getName().getString())
                        .formatted(Formatting.GOLD),
                true);

        LOGGER.info("[MoonTrade] ADMIN_RESET_MARK admin={} target={}",
                source.getName(), target.getName().getString());
        return 1;
    }

    /**
     * 把 boundTick 换算为人类可读的经过时间。
     * 格式："{N}d {H}h {M}m ago (day {gameDay})"
     * boundTick=0 视为 legacy 迁移，显示 "legacy"。
     */
    private static String formatElapsed(ServerWorld world, long boundTick) {
        if (boundTick <= 0) {
            return "legacy";
        }
        long now = world.getServer().getOverworld().getTime();
        long elapsedTicks = Math.max(0, now - boundTick);
        long totalSeconds = elapsedTicks / TICKS_PER_REAL_SECOND;
        long days = totalSeconds / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long gameDay = boundTick / TICKS_PER_GAME_DAY;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0 || days > 0) sb.append(hours).append("h ");
        sb.append(minutes).append("m ago");
        sb.append(" (day ").append(gameDay).append(")");
        return sb.toString();
    }
}
