package mod.test.mymodtest.armor.mixin;

import mod.test.mymodtest.armor.effect.BloodPactHandler;
import mod.test.mymodtest.armor.effect.VoidDevourerHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 玩家攻击 Mixin
 * 处理：
 * - 流血契约：攻击时释放血契池伤害
 * - 虚空之噬：攻击时追加真实伤害
 */
@Mixin(PlayerEntity.class)
public class PlayerAttackMixin {

    /**
     * 在攻击后追加额外伤害
     */
    @Inject(method = "attack", at = @At("TAIL"))
    private void armor$onAttack(Entity target, CallbackInfo ci) {
        PlayerEntity self = (PlayerEntity)(Object)this;

        if (!(self instanceof ServerPlayerEntity player)) {
            return;
        }

        if (!(target instanceof LivingEntity livingTarget)) {
            return;
        }

        // 检查目标是否还活着（避免对已死亡目标造成伤害）
        if (!livingTarget.isAlive()) {
            return;
        }

        long currentTick = player.getWorld().getTime();

        // 1. 流血契约 - 释放血契池
        float bloodPactDamage = BloodPactHandler.onAttack(player, target, currentTick);
        if (bloodPactDamage > 0) {
            // 血契伤害作为物理伤害
            livingTarget.damage(player.getDamageSources().playerAttack(player), bloodPactDamage);
        }

        // 2. 虚空之噬 - 追加真实伤害
        // 获取玩家攻击伤害作为基础
        float baseDamage = (float) player.getAttributeValue(net.minecraft.entity.attribute.EntityAttributes.GENERIC_ATTACK_DAMAGE);
        float voidDamage = VoidDevourerHandler.onAttack(player, target, baseDamage, currentTick);
        if (voidDamage > 0) {
            VoidDevourerHandler.applyTrueDamage(player, livingTarget, voidDamage);
        }
    }
}
