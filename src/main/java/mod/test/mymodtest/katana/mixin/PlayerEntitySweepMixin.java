package mod.test.mymodtest.katana.mixin;

import mod.test.mymodtest.registry.ModTags;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;

import java.util.Collections;
import java.util.List;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntitySweepMixin {

    @Redirect(
            method = "attack(Lnet/minecraft/entity/Entity;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/World;getNonSpectatingEntities(Ljava/lang/Class;Lnet/minecraft/util/math/Box;)Ljava/util/List;"
            ),
            require = 1
    )
    @SuppressWarnings({"rawtypes", "unchecked"})
    private List katana$skipSweepAoe(World world, Class entityClass, Box box) {
        if (katana$isKatanaMainHand()) {
            return Collections.emptyList();
        }
        return world.getNonSpectatingEntities(entityClass, box);
    }

    @Redirect(
            method = "attack(Lnet/minecraft/entity/Entity;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/World;playSound(Lnet/minecraft/entity/player/PlayerEntity;DDDLnet/minecraft/sound/SoundEvent;Lnet/minecraft/sound/SoundCategory;FF)V"
            ),
            slice = @Slice(
                    from = @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/world/World;getNonSpectatingEntities(Ljava/lang/Class;Lnet/minecraft/util/math/Box;)Ljava/util/List;"
                    ),
                    to = @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/entity/player/PlayerEntity;spawnSweepAttackParticles()V"
                    )
            ),
            require = 1
    )
    private void katana$skipSweepSound(World world, PlayerEntity soundPlayer, double x, double y, double z,
                                       SoundEvent sound, SoundCategory category, float volume, float pitch) {
        PlayerEntity instance = (PlayerEntity) (Object) this;
        if (instance == null) {
            return;
        }
        if (katana$isKatanaMainHand() && sound == SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP) {
            SoundEvent replacement = instance.getAttackCooldownProgress(0.5F) > 0.9F
                    ? SoundEvents.ENTITY_PLAYER_ATTACK_STRONG
                    : SoundEvents.ENTITY_PLAYER_ATTACK_WEAK;
            world.playSound(soundPlayer, x, y, z, replacement, category, volume, pitch);
            return;
        }
        world.playSound(soundPlayer, x, y, z, sound, category, volume, pitch);
    }

    @Redirect(
            method = "attack(Lnet/minecraft/entity/Entity;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/player/PlayerEntity;spawnSweepAttackParticles()V"
            ),
            require = 1
    )
    private void katana$skipSweepParticles(PlayerEntity player) {
        if (katana$isKatanaMainHand()) {
            return;
        }
        player.spawnSweepAttackParticles();
    }

    private boolean katana$isKatanaMainHand() {
        return ((PlayerEntity) (Object) this).getMainHandStack().isIn(ModTags.Items.KATANA);
    }
}
