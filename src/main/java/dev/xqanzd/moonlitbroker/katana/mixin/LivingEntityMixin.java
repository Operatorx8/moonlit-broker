package dev.xqanzd.moonlitbroker.katana.mixin;

import dev.xqanzd.moonlitbroker.katana.effect.nmap.NmapFirewallHandler;
import dev.xqanzd.moonlitbroker.katana.effect.nmap.NmapScanHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = LivingEntity.class, priority = 1100)
public class LivingEntityMixin {

    @Inject(method = "addStatusEffect(Lnet/minecraft/entity/effect/StatusEffectInstance;Lnet/minecraft/entity/Entity;)Z",
            at = @At("HEAD"), cancellable = true)
    private void katana$onAddStatusEffect(StatusEffectInstance effect, Entity source,
                                          CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity)(Object)this;

        if (self instanceof PlayerEntity player) {
            if (NmapFirewallHandler.shouldBlockDebuff(player, effect, source)) {
                cir.setReturnValue(false);
            }
        }
    }

    @Inject(method = "damage", at = @At("HEAD"), cancellable = true)
    private void katana$onDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity)(Object)this;

        if (self instanceof ServerPlayerEntity player) {
            // Projectile interception
            if (NmapFirewallHandler.shouldBlockProjectile(player, source)) {
                cir.setReturnValue(false);
                return;
            }

            // Shield break detection
            boolean fromHostile = source.getAttacker() instanceof Monster;
            NmapScanHandler.onPlayerDamaged(player, fromHostile, player.getWorld().getTime());
        }
    }
}
