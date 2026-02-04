package mod.test.mymodtest.block;

import mod.test.mymodtest.katana.item.KatanaItems;
import mod.test.mymodtest.registry.ModItems;
import net.minecraft.block.AnvilBlock;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class MysteriousAnvilBlock extends AnvilBlock {
    private static final int EXPERIENCE_COST = 5;
    private static final int REPAIR_AMOUNT = 250;

    public MysteriousAnvilBlock(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos,
                              PlayerEntity player, BlockHitResult hit) {
        if (world.isClient()) {
            return ActionResult.SUCCESS;
        }

        ItemStack mainStack = player.getMainHandStack();
        ItemStack offhandStack = player.getOffHandStack();

        boolean canRepair = KatanaItems.isKatana(mainStack)
            && offhandStack.isOf(ModItems.SACRIFICE)
            && mainStack.isDamaged()
            && player.experienceLevel >= EXPERIENCE_COST;

        if (canRepair) {
            offhandStack.decrement(1);
            player.addExperienceLevels(-EXPERIENCE_COST);
            mainStack.setDamage(Math.max(0, mainStack.getDamage() - REPAIR_AMOUNT));
            world.playSound(
                null,
                pos,
                SoundEvents.BLOCK_ANVIL_USE,
                SoundCategory.BLOCKS,
                1.0f,
                1.0f
            );
        }

        return ActionResult.SUCCESS;
    }
}
