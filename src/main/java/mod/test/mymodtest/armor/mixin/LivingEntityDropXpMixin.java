package mod.test.mymodtest.armor.mixin;

import mod.test.mymodtest.armor.effect.OldMarketHandler;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 怪物掉落经验 Mixin
 * 处理：
 * - 旧市护甲：击杀经验加成
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityDropXpMixin {

    @Shadow
    protected PlayerEntity attackingPlayer;

    @Shadow
    protected abstract int getXpToDrop();

    /**
     * 在掉落经验后检查是否应该给予额外经验
     */
    @Inject(method = "dropXp", at = @At("TAIL"))
    private void armor$onDropXp(CallbackInfo ci) {
        LivingEntity self = (LivingEntity)(Object)this;

        // 检查击杀者是否为玩家
        if (!(attackingPlayer instanceof ServerPlayerEntity player)) {
            return;
        }

        // 获取原始经验值
        int baseXp = getXpToDrop();
        if (baseXp <= 0) {
            return;
        }

        long currentTick = player.getWorld().getTime();

        // 旧市护甲 - 击杀经验加成
        int bonusXp = OldMarketHandler.onKillXp(player, baseXp, currentTick);
        if (bonusXp > 0 && self.getWorld() instanceof ServerWorld serverWorld) {
            OldMarketHandler.spawnBonusXp(serverWorld, player, bonusXp);
        }
    }
}
