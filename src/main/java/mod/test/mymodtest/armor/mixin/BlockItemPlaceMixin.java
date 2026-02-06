package mod.test.mymodtest.armor.mixin;

import mod.test.mymodtest.armor.transitional.effect.CargoPantsHandler;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.util.ActionResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * BlockItem 放置 Mixin
 * 用于处理工装裤的火把返还逻辑
 */
@Mixin(BlockItem.class)
public abstract class BlockItemPlaceMixin {

    /**
     * 在 BlockItem.place() 返回时检查是否触发火把返还
     */
    @Inject(
            method = "place(Lnet/minecraft/item/ItemPlacementContext;)Lnet/minecraft/util/ActionResult;",
            at = @At("RETURN")
    )
    private void armor$onPlaceReturn(ItemPlacementContext context, CallbackInfoReturnable<ActionResult> cir) {
        // 1. 只在放置成功时触发
        ActionResult result = cir.getReturnValue();
        if (result == null || !result.isAccepted()) {
            return;
        }

        // 2. 检查放置的是否为 minecraft:torch
        BlockItem self = (BlockItem) (Object) this;
        if (self.getBlock() != Blocks.TORCH) {
            return;
        }

        // 3. 检查是否为玩家操作
        PlayerEntity player = context.getPlayer();
        if (player == null) {
            return;
        }

        // 4. 调用 CargoPantsHandler 处理火把返还逻辑
        //    handStack 在放置成功后已被系统 decrement(1)
        CargoPantsHandler.onTorchPlaced(player, context.getStack());
    }
}
