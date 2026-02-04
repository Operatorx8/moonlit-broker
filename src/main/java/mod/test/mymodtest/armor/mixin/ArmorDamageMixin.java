package mod.test.mymodtest.armor.mixin;

import mod.test.mymodtest.armor.effect.RetracerHandler;
import mod.test.mymodtest.armor.effect.SilentOathHandler;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 盔甲系统伤害事件 Mixin
 * 处理沉默之誓约（减伤）和回溯者额饰（爆炸保命）
 */
@Mixin(LivingEntity.class)
public class ArmorDamageMixin {

    /**
     * 修改伤害值
     * 在伤害计算早期介入，处理减伤和保命逻辑
     */
    @ModifyVariable(method = "damage", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float armor$modifyDamage(float amount, DamageSource source) {
        LivingEntity self = (LivingEntity)(Object)this;

        if (!(self instanceof ServerPlayerEntity player)) {
            return amount;
        }

        long currentTick = player.getWorld().getTime();

        // 1. 回溯者的额饰 - 爆炸致死保护（优先检查，因为会完全取消伤害）
        float afterRetracer = RetracerHandler.onDamage(player, source, amount, currentTick);
        if (afterRetracer == 0 && amount > 0) {
            // 回溯额饰触发，伤害被取消
            return 0;
        }

        // 2. 沉默之誓约 - 首次受伤减伤
        float finalAmount = SilentOathHandler.onDamage(player, source, afterRetracer, currentTick);

        return finalAmount;
    }
}
