package mod.test.mymodtest.weapon.transitional.mixin;

import mod.test.mymodtest.weapon.transitional.TransitionalWeaponConstants;
import mod.test.mymodtest.weapon.transitional.item.TransitionalWeaponItems;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.entity.effect.StatusEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Slice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Acer 暴击加成 Mixin
 *
 * 在 PlayerEntity.attack() 的暴击乘算中，
 * 仅当主手持 Acer 且满足原版暴击条件时，将 1.5x 替换为 1.7x。
 */
@Mixin(PlayerEntity.class)
public class AcerCritMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("MoonTrace");
    private static final long DIAG_SAMPLE_INTERVAL_TICKS = 40L;
    private static final Map<UUID, Long> LAST_DIAG_SAMPLE_TICK = new ConcurrentHashMap<>();

    @ModifyConstant(
            method = "attack(Lnet/minecraft/entity/Entity;)V",
            constant = @Constant(floatValue = 1.5F),
            require = 1,
            slice = @Slice(
                    from = @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/entity/player/PlayerEntity;getAttackCooldownProgress(F)F"
                    ),
                    to = @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/entity/Entity;damage(Lnet/minecraft/entity/damage/DamageSource;F)Z"
                    )
            )
    )
    private float transitional$acerCritMultiplier(float original, Entity target) {
        PlayerEntity self = (PlayerEntity) (Object) this;

        // 仅服务端处理
        if (!(self instanceof ServerPlayerEntity player)) return original;

        // 检查是否主手持有 Acer
        if (!player.getMainHandStack().isOf(TransitionalWeaponItems.ACER)) return original;

        // 一次性诊断日志：区分“mixin 未应用”与“条件未命中”
        if (TransitionalWeaponConstants.DEBUG) {
            logDiagnosticSample(player, target);
        }

        // 复刻原版暴击条件，避免误改非暴击路径。
        if (!isVanillaCritical(player, target)) return original;

        float out = TransitionalWeaponConstants.ACER_CRIT_MULTIPLIER;
        if (TransitionalWeaponConstants.DEBUG) {
            LOGGER.info("[TransWeapon|AcerCrit|HIT] player={} worldClient={} mult={} targetClass={}",
                    player.getName().getString(),
                    player.getWorld().isClient(),
                    out,
                    target.getClass().getSimpleName());
        }
        return out;
    }

    private static boolean isVanillaCritical(PlayerEntity player, Entity target) {
        if (player.getAttackCooldownProgress(0.5F) <= 0.9F) return false;
        if (player.fallDistance <= 0.0F) return false;
        if (player.isOnGround()) return false;
        if (player.isClimbing()) return false;
        if (player.isTouchingWater()) return false;
        if (player.hasVehicle()) return false;
        if (player.hasStatusEffect(StatusEffects.BLINDNESS)) return false;
        if (player.isSprinting()) return false;
        return target instanceof LivingEntity;
    }

    private static void logDiagnosticSample(ServerPlayerEntity player, Entity target) {
        long now = player.getServerWorld().getTime();
        UUID playerId = player.getUuid();
        Long last = LAST_DIAG_SAMPLE_TICK.get(playerId);
        if (last != null && now - last < DIAG_SAMPLE_INTERVAL_TICKS) {
            return;
        }
        LAST_DIAG_SAMPLE_TICK.put(playerId, now);

        LOGGER.info(
                "[TransWeapon|AcerCrit|ENTRY] player={} worldClient={} fallDistance={} onGround={} sprinting={} inWater={} climbing={} blindness={} vehicle={} attackCooldownProgress={} targetClass={}",
                player.getName().getString(),
                player.getWorld().isClient(),
                player.fallDistance,
                player.isOnGround(),
                player.isSprinting(),
                player.isTouchingWater(),
                player.isClimbing(),
                player.hasStatusEffect(StatusEffects.BLINDNESS),
                player.hasVehicle(),
                player.getAttackCooldownProgress(0.5F),
                target.getClass().getSimpleName()
        );
    }
}
