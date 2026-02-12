package dev.xqanzd.moonlitbroker.armor.mixin;

import dev.xqanzd.moonlitbroker.armor.effect.GhostGodHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 状态效果 Mixin
 * 处理：
 * - 鬼神之铠：亡灵施加的 Wither/Hunger/Slowness 免疫
 */
@Mixin(LivingEntity.class)
public class StatusEffectMixin {

    /**
     * 在添加状态效果前检查是否应该免疫
     */
    @Inject(method = "addStatusEffect(Lnet/minecraft/entity/effect/StatusEffectInstance;Lnet/minecraft/entity/Entity;)Z",
            at = @At("HEAD"),
            cancellable = true)
    private void armor$onAddStatusEffect(StatusEffectInstance effect, Entity source, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity)(Object)this;

        if (!(self instanceof ServerPlayerEntity player)) {
            return;
        }

        long currentTick = player.getWorld().getTime();

        // 鬼神之铠 - 亡灵 Debuff 免疫
        boolean allowEffect = GhostGodHandler.onStatusEffect(player, effect.getEffectType(), source, currentTick);
        if (!allowEffect) {
            // 免疫！取消效果添加
            cir.setReturnValue(false);
        }
    }
}
