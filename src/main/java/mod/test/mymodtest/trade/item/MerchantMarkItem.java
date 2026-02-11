package mod.test.mymodtest.trade.item;

import mod.test.mymodtest.trade.TradeConfig;
import mod.test.mymodtest.world.MerchantSpawnerState;
import mod.test.mymodtest.world.MerchantUnlockState;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * 商人印记物品
 * 绑定到玩家UUID，作为会员标识
 */
public class MerchantMarkItem extends Item {
    private static final Logger LOGGER = LoggerFactory.getLogger(MerchantMarkItem.class);

    public MerchantMarkItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        PlayerEntity player = context.getPlayer();
        if (player == null) {
            return ActionResult.PASS;
        }

        World world = context.getWorld();
        if (!world.getBlockState(context.getBlockPos()).isOf(Blocks.BELL)) {
            return ActionResult.PASS;
        }

        ItemStack markStack = context.getStack();
        if (!isBoundTo(markStack, player)) {
            if (!world.isClient) {
                logBlocked(player, "MARK_NOT_BOUND_SELF", "stack_unbound_or_other_owner");
                player.sendMessage(Text.literal("此商人印记未绑定到你本人。"), true);
            }
            return ActionResult.FAIL;
        }

        if (world.isClient) {
            return ActionResult.SUCCESS;
        }

        ServerWorld serverWorld = (ServerWorld) world;
        if (serverWorld.getRegistryKey() != World.OVERWORLD) {
            logBlocked(player, "NOT_OVERWORLD", "dimension=" + serverWorld.getRegistryKey().getValue());
            player.sendMessage(Text.literal("只能在主世界的钟上发起召唤预约。"), true);
            return ActionResult.FAIL;
        }

        MerchantSpawnerState spawnerState = MerchantSpawnerState.getServerState(serverWorld);
        MerchantUnlockState unlockState = MerchantUnlockState.getServerState(serverWorld);
        MerchantUnlockState.Progress progress = unlockState.getOrCreateProgress(player.getUuid());

        if (spawnerState.hasActiveMerchant(serverWorld)) {
            UUID activeUuid = spawnerState.getActiveMerchantUuid();
            logBlocked(player, "ACTIVE_MERCHANT_LOCK", "activeMerchant=" + shortUuid(activeUuid));
            player.sendMessage(Text.literal("当前已有活跃神秘商人，暂时无法召唤。"), true);
            return ActionResult.FAIL;
        }

        if (spawnerState.hasSummonRequest()) {
            long scheduledTick = spawnerState.getSummonScheduledTick();
            logBlocked(player, "PENDING_REQUEST", "scheduledTick=" + scheduledTick);
            player.sendMessage(Text.literal("已有召唤预约在排队，请稍后。"), true);
            return ActionResult.FAIL;
        }

        long currentTick = serverWorld.getTime();
        long lastSummonTick = progress.getLastSummonTick();
        if (lastSummonTick >= 0L) {
            long elapsed = currentTick - lastSummonTick;
            if (elapsed < TradeConfig.SUMMON_COOLDOWN_TICKS) {
                long remaining = TradeConfig.SUMMON_COOLDOWN_TICKS - elapsed;
                logBlocked(player, "PLAYER_COOLDOWN", "remainingTicks=" + remaining);
                player.sendMessage(Text.literal("召唤冷却中，剩余 " + (remaining / 20) + " 秒。"), true);
                return ActionResult.FAIL;
            }
        }

        int requiredNotes = TradeConfig.SUMMON_SILVER_NOTE_COST;
        int noteCount = countSilverNotes(player);
        if (noteCount < requiredNotes) {
            logBlocked(player, "INSUFFICIENT_SILVER_NOTE", "need=" + requiredNotes + ", have=" + noteCount);
            player.sendMessage(Text.literal("银票不足，需要 " + requiredNotes + " 张。"), true);
            return ActionResult.FAIL;
        }

        long scheduledTick = computeScheduledTick(serverWorld, currentTick);

        int consumed = consumeSilverNotes(player, requiredNotes);
        if (consumed < requiredNotes) {
            logBlocked(player, "SILVER_NOTE_CONSUME_FAILED", "need=" + requiredNotes + ", consumed=" + consumed);
            player.sendMessage(Text.literal("扣除银票失败，请重试。"), true);
            return ActionResult.FAIL;
        }

        spawnerState.summonRequest(player.getUuid(), context.getBlockPos(), scheduledTick);
        progress.setLastSummonTick(currentTick);
        unlockState.markDirty();

        long delayTicks = Math.max(0L, scheduledTick - currentTick);
        player.sendMessage(Text.literal("召唤预约已创建，预计 " + (delayTicks / 20) + " 秒后现身。"), true);
        return ActionResult.SUCCESS;
    }

    private static String shortUuid(UUID uuid) {
        return uuid == null ? "null" : uuid.toString().substring(0, 8);
    }

    private static void logBlocked(PlayerEntity player, String reason, String detail) {
        LOGGER.info("SUMMON_BLOCKED(reason={}) player={} detail={}", reason, shortUuid(player.getUuid()), detail);
    }

    private static long computeScheduledTick(ServerWorld world, long currentTick) {
        long dayTime = Math.floorMod(world.getTimeOfDay(), 24000L);
        if (dayTime >= TradeConfig.SUMMON_DUSK_START_TICK) {
            return currentTick + TradeConfig.SUMMON_AFTER_DUSK_DELAY_TICKS;
        }
        return currentTick + (TradeConfig.SUMMON_DUSK_START_TICK - dayTime);
    }

    private static boolean isSilverNoteStack(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof SilverNoteItem;
    }

    private static int countSilverNotes(PlayerEntity player) {
        int count = 0;
        for (ItemStack stack : player.getInventory().main) {
            if (isSilverNoteStack(stack)) {
                count += stack.getCount();
            }
        }
        for (ItemStack stack : player.getInventory().offHand) {
            if (isSilverNoteStack(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static int consumeSilverNotes(PlayerEntity player, int amount) {
        int remaining = amount;

        for (ItemStack stack : player.getInventory().main) {
            if (!isSilverNoteStack(stack)) {
                continue;
            }
            int take = Math.min(remaining, stack.getCount());
            stack.decrement(take);
            remaining -= take;
            if (remaining <= 0) {
                return amount;
            }
        }

        for (ItemStack stack : player.getInventory().offHand) {
            if (!isSilverNoteStack(stack)) {
                continue;
            }
            int take = Math.min(remaining, stack.getCount());
            stack.decrement(take);
            remaining -= take;
            if (remaining <= 0) {
                return amount;
            }
        }

        return amount - Math.max(0, remaining);
    }

    /**
     * 获取绑定的玩家UUID
     */
    public static UUID getOwnerUUID(ItemStack stack) {
        NbtComponent component = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (component == null) return null;
        NbtCompound nbt = component.copyNbt();
        if (!nbt.containsUuid(TradeConfig.NBT_MARK_OWNER_UUID)) return null;
        return nbt.getUuid(TradeConfig.NBT_MARK_OWNER_UUID);
    }

    /**
     * 绑定到玩家
     */
    public static void bindToPlayer(ItemStack stack, PlayerEntity player) {
        NbtCompound nbt = getOrCreateNbt(stack);
        nbt.putUuid(TradeConfig.NBT_MARK_OWNER_UUID, player.getUuid());
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
    }

    /**
     * 检查是否已绑定
     */
    public static boolean isBound(ItemStack stack) {
        return getOwnerUUID(stack) != null;
    }

    /**
     * 检查是否绑定到指定玩家
     */
    public static boolean isBoundTo(ItemStack stack, PlayerEntity player) {
        UUID owner = getOwnerUUID(stack);
        return owner != null && owner.equals(player.getUuid());
    }

    /**
     * 检查玩家是否持有有效的印记（绑定到自己的）
     */
    public static boolean playerHasValidMark(PlayerEntity player) {
        // 检查主手
        ItemStack mainHand = player.getMainHandStack();
        if (mainHand.getItem() instanceof MerchantMarkItem && isBoundTo(mainHand, player)) {
            return true;
        }
        // 检查副手
        ItemStack offHand = player.getOffHandStack();
        if (offHand.getItem() instanceof MerchantMarkItem && isBoundTo(offHand, player)) {
            return true;
        }
        // 检查背包
        for (ItemStack stack : player.getInventory().main) {
            if (stack.getItem() instanceof MerchantMarkItem && isBoundTo(stack, player)) {
                return true;
            }
        }
        return false;
    }

    private static NbtCompound getOrCreateNbt(ItemStack stack) {
        NbtComponent component = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (component != null) {
            return component.copyNbt();
        }
        return new NbtCompound();
    }

}
