package dev.xqanzd.moonlitbroker.screen;

import dev.xqanzd.moonlitbroker.registry.ModItems;
import dev.xqanzd.moonlitbroker.registry.ModBlocks;
import dev.xqanzd.moonlitbroker.registry.ModTags;
import dev.xqanzd.moonlitbroker.util.KatanaContractUtil;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MysteriousAnvilScreenHandler extends AnvilScreenHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(MysteriousAnvilScreenHandler.class);
    private static final float REPAIR_PERCENT_PER_SACRIFICE = 0.25f;
    private static final boolean DEBUG = Boolean.getBoolean("mm.anvil.debug");

    private final ScreenHandlerContext context;
    private int sacrificeUsage;

    public MysteriousAnvilScreenHandler(int syncId, PlayerInventory inventory) {
        this(syncId, inventory, ScreenHandlerContext.EMPTY);
    }

    public MysteriousAnvilScreenHandler(int syncId, PlayerInventory inventory, ScreenHandlerContext context) {
        super(syncId, inventory, context);
        this.context = context;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return canUse(this.context, player, ModBlocks.MYSTERIOUS_ANVIL);
    }

    @Override
    protected boolean canTakeOutput(PlayerEntity player, boolean present) {
        ItemStack right = this.input.getStack(1);
        if (!right.isOf(ModItems.SACRIFICE)) {
            this.sacrificeUsage = 0;
            return super.canTakeOutput(player, present);
        }

        if (!present) {
            return false;
        }
        ItemStack left = this.input.getStack(0);
        if (!isAllowedRepairTarget(left)) {
            return false;
        }
        // Reject dormant katana
        if (this.player.getWorld() instanceof ServerWorld sw
                && KatanaContractUtil.isDormant(sw, this.player, left)) {
            return false;
        }
        return this.sacrificeUsage > 0 && right.getCount() >= this.sacrificeUsage;
    }

    @Override
    protected void onTakeOutput(PlayerEntity player, ItemStack stack) {
        ItemStack left = this.input.getStack(0);
        ItemStack right = this.input.getStack(1);
        if (!right.isOf(ModItems.SACRIFICE)) {
            this.sacrificeUsage = 0;
            super.onTakeOutput(player, stack);
            return;
        }

        if (this.player.getWorld() instanceof ServerWorld sw
                && KatanaContractUtil.isDormant(sw, this.player, left)) {
            clearResultState();
            return;
        }
        if (!isAllowedRepairTarget(left) || this.sacrificeUsage <= 0) {
            clearResultState();
            return;
        }

        this.input.setStack(0, ItemStack.EMPTY);
        if (right.getCount() > this.sacrificeUsage) {
            ItemStack remainder = right.copy();
            remainder.decrement(this.sacrificeUsage);
            this.input.setStack(1, remainder);
        } else {
            this.input.setStack(1, ItemStack.EMPTY);
        }
        this.context.run((world, pos) -> world.playSound(
                null,
                pos,
                SoundEvents.BLOCK_ANVIL_USE,
                SoundCategory.BLOCKS,
                1.0F,
                1.0F));
        clearResultState();
    }

    @Override
    public void updateResult() {
        ItemStack left = this.input.getStack(0);
        ItemStack right = this.input.getStack(1);

        if (!right.isOf(ModItems.SACRIFICE)) {
            this.sacrificeUsage = 0;
            super.updateResult();
            return;
        }

        if (left.isEmpty()) {
            reject("left_empty", left, right);
            return;
        }
        if (!isAllowedRepairTarget(left)) {
            reject("left_not_allowed", left, right);
            return;
        }
        // Reject dormant (contract-expired) katanas from repair
        if (this.player.getWorld() instanceof ServerWorld sw
                && KatanaContractUtil.isDormant(sw, this.player, left)) {
            reject("dormant_contract", left, right);
            return;
        }
        if (!left.isDamageable()) {
            reject("left_not_damageable", left, right);
            return;
        }

        int currentDamage = left.getDamage();
        if (currentDamage <= 0) {
            reject("already_full_durability", left, right);
            return;
        }

        int repairPerSacrifice = Math.max(1, Math.round(left.getMaxDamage() * REPAIR_PERCENT_PER_SACRIFICE));
        int needSacrifice = ceilDiv(currentDamage, repairPerSacrifice);
        if (right.getCount() < needSacrifice) {
            reject("insufficient_sacrifice", left, right);
            return;
        }

        int repairedDamage = Math.max(0, currentDamage - needSacrifice * repairPerSacrifice);
        ItemStack result = left.copy();
        result.setDamage(repairedDamage);

        this.output.setStack(0, result);
        this.sacrificeUsage = needSacrifice;
        this.setProperty(0, 0);
        this.sendContentUpdates();

        if (shouldLog()) {
            LOGGER.info(
                    "[Anvil] apply: item={}, repaired={}, costSacrifice={}, xp={}",
                    itemId(left),
                    currentDamage - repairedDamage,
                    needSacrifice,
                    0
            );
        }
    }

    private boolean isAllowedRepairTarget(ItemStack stack) {
        if (stack.isEmpty() || !stack.isDamageable()) {
            return false;
        }
        if (stack.isIn(ModTags.Items.KATANA)) {
            return true;
        }
        Identifier id = Registries.ITEM.getId(stack.getItem());
        return id != null && ModItems.MOD_ID.equals(id.getNamespace());
    }

    private void reject(String reason, ItemStack left, ItemStack right) {
        this.output.setStack(0, ItemStack.EMPTY);
        this.sacrificeUsage = 0;
        this.setProperty(0, 0);
        this.sendContentUpdates();
        if (shouldLog()) {
            LOGGER.info(
                    "[Anvil] reject: reason={}, left={}, right={}",
                    reason,
                    describe(left),
                    describe(right)
            );
        }
    }

    private void clearResultState() {
        this.output.setStack(0, ItemStack.EMPTY);
        this.sacrificeUsage = 0;
        this.setProperty(0, 0);
        this.sendContentUpdates();
    }

    private boolean shouldLog() {
        return DEBUG && !this.player.getWorld().isClient();
    }

    private static String describe(ItemStack stack) {
        if (stack.isEmpty()) {
            return "empty";
        }
        return itemId(stack) + "x" + stack.getCount() + "(damage=" + stack.getDamage() + "/" + stack.getMaxDamage() + ")";
    }

    private static String itemId(ItemStack stack) {
        Identifier id = Registries.ITEM.getId(stack.getItem());
        return id == null ? "unknown" : id.toString();
    }

    private static int ceilDiv(int numerator, int denominator) {
        return (numerator + denominator - 1) / denominator;
    }
}
