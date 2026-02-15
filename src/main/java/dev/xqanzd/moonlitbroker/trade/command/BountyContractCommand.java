package dev.xqanzd.moonlitbroker.trade.command;

import com.mojang.brigadier.CommandDispatcher;
import dev.xqanzd.moonlitbroker.registry.ModItems;
import dev.xqanzd.moonlitbroker.trade.item.BountyContractItem;
import net.minecraft.entity.EntityType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

/**
 * /mm_bounty_contract 命令
 * 给玩家一张随机悬赏契约（debug/测试用）
 */
public final class BountyContractCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(BountyContractCommand.class);
    private static final Random RANDOM = new Random();

    // 悬赏目标池: { entityId, required }
    private static final String[][] BOUNTY_POOL = {
            { "minecraft:zombie", "5" },
            { "minecraft:skeleton", "5" },
            { "minecraft:spider", "3" },
            { "minecraft:creeper", "3" },
    };

    private BountyContractCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT
                .register((dispatcher, registryAccess, environment) -> registerCommand(dispatcher));
    }

    private static void registerCommand(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
                CommandManager.literal("mm_bounty_contract")
                        .requires(source -> source.hasPermissionLevel(2))
                        .executes(context -> execute(context.getSource())));
    }

    private static int execute(ServerCommandSource source) {
        if (source.getWorld().isClient()) {
            return 0;
        }

        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.translatable("error.xqanzd_moonlit_broker.command.player_only"));
            return 0;
        }

        // 随机选一个悬赏
        String[] entry = BOUNTY_POOL[RANDOM.nextInt(BOUNTY_POOL.length)];
        String target = entry[0];
        int required = Integer.parseInt(entry[1]);

        // 创建契约
        ItemStack contract = new ItemStack(ModItems.BOUNTY_CONTRACT, 1);
        BountyContractItem.initialize(contract, target, required);

        // 给予玩家
        if (!player.giveItemStack(contract)) {
            player.dropItem(contract, false);
        }

        LOGGER.info("[MoonTrade] action=BOUNTY_CONTRACT_GIVE player={} target={} required={}",
                player.getName().getString(), target, required);

        Text targetName = resolveTargetName(target);
        player.sendMessage(
                Text.translatable(
                        "msg.xqanzd_moonlit_broker.command.bounty_contract.granted",
                        contract.getName(),
                        targetName,
                        required
                )
                        .formatted(Formatting.GOLD),
                false);

        return 1;
    }

    private static Text resolveTargetName(String target) {
        Identifier id = Identifier.tryParse(target);
        if (id != null) {
            EntityType<?> entityType = Registries.ENTITY_TYPE.get(id);
            if (entityType != null) {
                return entityType.getName();
            }
        }
        return Text.literal(target);
    }
}
