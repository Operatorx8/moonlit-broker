package dev.xqanzd.moonlitbroker.armor.mixin;

import dev.xqanzd.moonlitbroker.armor.effect.boots.BootsPlayerState;
import dev.xqanzd.moonlitbroker.armor.effect.boots.BootsTickHandler;
import dev.xqanzd.moonlitbroker.armor.item.ArmorItems;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Boot3 - 幽灵步伐 (Ghost Step)
 * Mixin Entity.pushAwayFrom 取消实体推挤
 * pushAwayFrom 定义在 Entity 类上，非 PlayerEntity
 *
 * 判断条件：
 * - 效果 A（非战斗时常驻）：ghostStateA_active == true
 * - 效果 B（受击应急）：ghostBurstExpiresTick > currentTick
 * 任一满足则取消推挤
 */
@Mixin(Entity.class)
public class GhostStepPushMixin {

    @Inject(method = "pushAwayFrom(Lnet/minecraft/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
    private void boots$onPushAwayFrom(Entity entity, CallbackInfo ci) {
        Entity self = (Entity)(Object)this;

        if (!(self instanceof ServerPlayerEntity player)) {
            return;
        }

        // 检查是否穿戴幽灵步伐靴子
        if (player.getEquippedStack(EquipmentSlot.FEET).getItem() != ArmorItems.GHOST_STEP_BOOTS) {
            return;
        }

        BootsPlayerState state = BootsTickHandler.getOrCreateState(player.getUuid());
        long currentTick = player.getServer().getTicks();

        // 效果 A：非战斗时常驻幽灵碰撞
        // 效果 B：受击应急幽灵（即使在战斗中也生效）
        if (state.ghostStateA_active || state.ghostBurstExpiresTick > currentTick) {
            ci.cancel();
        }
    }
}
