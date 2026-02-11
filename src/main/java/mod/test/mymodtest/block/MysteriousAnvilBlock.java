package mod.test.mymodtest.block;

import mod.test.mymodtest.screen.MysteriousAnvilScreenHandler;
import net.minecraft.block.AnvilBlock;
import net.minecraft.block.BlockState;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class MysteriousAnvilBlock extends AnvilBlock {
    public MysteriousAnvilBlock(Settings settings) {
        super(settings);
    }

    @Override
    protected NamedScreenHandlerFactory createScreenHandlerFactory(BlockState state, World world, BlockPos pos) {
        return new SimpleNamedScreenHandlerFactory(
                (syncId, inventory, player) -> new MysteriousAnvilScreenHandler(syncId, inventory, ScreenHandlerContext.EMPTY),
                Text.translatable("container.repair")
        );
    }

    @Override
    public void onLanding(World world, BlockPos pos, BlockState fallingState, BlockState currentState, FallingBlockEntity fallingBlockEntity) {
        super.onLanding(world, pos, fallingState, currentState, fallingBlockEntity);
    }
}
