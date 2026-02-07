package mod.test.mymodtest.armor.mixin;

import mod.test.mymodtest.armor.effect.BloodPactHandler;
import mod.test.mymodtest.armor.effect.VoidDevourerHandler;
import mod.test.mymodtest.armor.effect.boots.BootsPlayerState;
import mod.test.mymodtest.armor.effect.boots.BootsTickHandler;
import mod.test.mymodtest.armor.item.ArmorItems;
import mod.test.mymodtest.util.ModLog;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import net.minecraft.entity.damage.DamageSource;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 玩家攻击 Mixin
 * 处理：
 * - 流血契约：攻击时释放血契池伤害
 * - 虚空之噬：攻击时追加真实伤害
 * - 靴子：更新 lastHitLivingTick，急行之靴攻击退出
 */
@Mixin(PlayerEntity.class)
public class PlayerAttackMixin {

    /**
     * 仅当原版伤害成功时，才触发附加效果与靴子计时
     */
    @Redirect(
            method = "attack",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/Entity;damage(Lnet/minecraft/entity/damage/DamageSource;F)Z",
                    ordinal = 0
            )
    )
    private boolean armor$redirectDamage(Entity target, DamageSource source, float amount) {
        PlayerEntity self = (PlayerEntity)(Object)this;
        boolean success = target.damage(source, amount);

        if (!success || !(self instanceof ServerPlayerEntity player)) {
            return success;
        }

        if (!(target instanceof LivingEntity livingTarget) || !livingTarget.isAlive()) {
            return success;
        }

        long currentTick = player.getServer().getTicks();

        // 1. 流血契约 - 释放血契池
        float bloodPactDamage = BloodPactHandler.onAttack(player, target, currentTick);
        if (bloodPactDamage > 0) {
            // 血契伤害作为物理伤害
            livingTarget.damage(player.getDamageSources().playerAttack(player), bloodPactDamage);
        }

        // 2. 虚空之噬 - 追加真实伤害
        float baseDamage = (float) player.getAttributeValue(net.minecraft.entity.attribute.EntityAttributes.GENERIC_ATTACK_DAMAGE);
        float voidDamage = VoidDevourerHandler.onAttack(player, target, baseDamage, currentTick);
        if (voidDamage > 0) {
            VoidDevourerHandler.applyTrueDamage(player, livingTarget, voidDamage);
        }

        // 3. 靴子 - 更新 lastHitLivingTick + 急行之靴攻击退出
        BootsPlayerState bootsState = BootsTickHandler.getOrCreateState(player.getUuid());
        bootsState.lastHitLivingTick = currentTick;

        if (bootsState.marchActive
                && player.getEquippedStack(EquipmentSlot.FEET).getItem() == ArmorItems.MARCHING_BOOTS) {
            long exitCdUntil = currentTick + mod.test.mymodtest.armor.BootsEffectConstants.MARCH_CD_TICKS;
            org.slf4j.LoggerFactory.getLogger(ModLog.MOD_TAG).info(
                    ModLog.armorBootPrefix() + " action=exit player={} bootId={} nowTick={} expiresTick={} cdUntil={}",
                    player.getName().getString(),
                    net.minecraft.registry.Registries.ITEM.getId(ArmorItems.MARCHING_BOOTS),
                    currentTick,
                    bootsState.marchStartTick + mod.test.mymodtest.armor.BootsEffectConstants.MARCH_MAX_DURATION_TICKS,
                    exitCdUntil
            );
            BootsTickHandler.exitMarch(bootsState, currentTick);
        }

        return success;
    }
}
