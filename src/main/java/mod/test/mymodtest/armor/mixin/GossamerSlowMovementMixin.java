package mod.test.mymodtest.armor.mixin;

import mod.test.mymodtest.armor.BootsEffectConstants;
import mod.test.mymodtest.armor.effect.boots.BootsPlayerState;
import mod.test.mymodtest.armor.effect.boots.BootsTickHandler;
import mod.test.mymodtest.armor.item.ArmorItems;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Boot5 - 轻灵之靴 (Gossamer Boots)
 * Mixin Entity.slowMovement 修改蛛网减速系数
 *
 * 原版蛛网调用 entity.slowMovement(state, Vec3d(0.25, 0.05, 0.25))
 * 穿戴轻灵之靴时，将减速系数从 0.25 改为 0.25 + (1-0.25)*0.70 ≈ 0.775
 * 即保留更多速度
 */
@Mixin(Entity.class)
public class GossamerSlowMovementMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("MoonTrace");

    @Inject(method = "slowMovement(Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/Vec3d;)V", at = @At("HEAD"), cancellable = true)
    private void boots$onSlowMovement(BlockState state, Vec3d multiplier, CallbackInfo ci) {
        Entity self = (Entity)(Object)this;

        if (!(self instanceof ServerPlayerEntity player)) {
            return;
        }

        // 检查是否穿戴轻灵之靴
        if (player.getEquippedStack(EquipmentSlot.FEET).getItem() != ArmorItems.GOSSAMER_BOOTS) {
            return;
        }

        // 仅对蛛网生效
        if (!state.isOf(Blocks.COBWEB)) {
            return;
        }

        long now = player.getServer().getTicks();
        BootsPlayerState bootsState = BootsTickHandler.getOrCreateState(player.getUuid());
        String bootId = Registries.ITEM.getId(ArmorItems.GOSSAMER_BOOTS).toString();

        if (bootsState.webAssistExpiresTick <= now) {
            LOGGER.info("[MoonTrace|Armor|BOOT] action=enter player={} bootId={} nowTick={} expiresTick={}",
                    player.getName().getString(),
                    bootId,
                    now,
                    now + BootsEffectConstants.GOSSAMER_REFRESH_TICKS);
        }
        bootsState.webAssistExpiresTick = now + BootsEffectConstants.GOSSAMER_REFRESH_TICKS;

        // Slowness 降阶：如果有 Slowness II+ → 替换为 Slowness I
        StatusEffectInstance slowness = player.getStatusEffect(StatusEffects.SLOWNESS);
        if (slowness != null && slowness.getAmplifier() >= BootsEffectConstants.GOSSAMER_SLOWNESS_DOWNGRADE) {
            int remainingDuration = slowness.getDuration();
            player.removeStatusEffect(StatusEffects.SLOWNESS);
            player.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.SLOWNESS,
                    remainingDuration,
                    0, // amplifier 0 = I级
                    slowness.isAmbient(),
                    slowness.shouldShowParticles(),
                    slowness.shouldShowIcon()
            ));
        } else if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[MoonTrace|Armor|BOOT] action=blocked reason=no_slowness_or_low_level player={} bootId={} nowTick={}",
                    player.getName().getString(),
                    bootId,
                    now);
        }

        // 计算新的减速系数
        // 原版 multiplier = (0.25, 0.05, 0.25)
        // 新系数 = old + (1 - old) * GOSSAMER_WEB_SLOW_REDUCE
        double newX = multiplier.x + (1.0 - multiplier.x) * BootsEffectConstants.GOSSAMER_WEB_SLOW_REDUCE;
        double newY = multiplier.y + (1.0 - multiplier.y) * BootsEffectConstants.GOSSAMER_WEB_SLOW_REDUCE;
        double newZ = multiplier.z + (1.0 - multiplier.z) * BootsEffectConstants.GOSSAMER_WEB_SLOW_REDUCE;

        // 取消原始调用，手动应用修改后的减速
        ci.cancel();

        // 复制原版 slowMovement 的逻辑，但使用修改后的 multiplier
        player.setVelocity(player.getVelocity().multiply(newX, newY, newZ));
    }
}
