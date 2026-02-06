package mod.test.mymodtest.armor.mixin;

import mod.test.mymodtest.armor.effect.BloodPactHandler;
import mod.test.mymodtest.armor.effect.GhostGodHandler;
import mod.test.mymodtest.armor.effect.GrazeGuardHandler;
import mod.test.mymodtest.armor.effect.RetracerHandler;
import mod.test.mymodtest.armor.effect.SilentOathHandler;
import mod.test.mymodtest.armor.effect.StealthShinHandler;
import mod.test.mymodtest.armor.effect.boots.BootsPlayerState;
import mod.test.mymodtest.armor.effect.boots.BootsTickHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 盔甲系统伤害事件 Mixin
 * 处理：
 * - 头盔：沉默之誓约（减伤）、回溯者额饰（爆炸保命）
 * - 胸甲：鬼神之铠（亡灵减伤）、流血契约（受击储能）
 * - 护腿：擦身护胫（减伤）、潜行之胫（摔落减伤）
 * - 靴子：更新 lastHurtByLivingTick，Ghost Step 受击计数
 */
@Mixin(LivingEntity.class)
public class ArmorDamageMixin {

    /**
     * 修改伤害值
     * 在伤害计算早期介入，处理减伤、保命和储能逻辑
     */
    @ModifyVariable(method = "damage", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float armor$modifyDamage(float amount, DamageSource source) {
        LivingEntity self = (LivingEntity)(Object)this;

        if (!(self instanceof ServerPlayerEntity player)) {
            return amount;
        }

        long currentTick = player.getServer().getTicks();

        // 1. 回溯者的额饰 - 爆炸致死保护（优先检查，因为会完全取消伤害）
        float afterRetracer = RetracerHandler.onDamage(player, source, amount, currentTick);
        if (afterRetracer == 0 && amount > 0) {
            // 回溯额饰触发，伤害被取消
            return 0;
        }

        // 2. 沉默之誓约 - 首次受伤减伤（头盔）
        float afterSilentOath = SilentOathHandler.onDamage(player, source, afterRetracer, currentTick);

        // 3. 鬼神之铠 - 亡灵伤害减免（胸甲）
        float afterGhostGod = GhostGodHandler.onDamage(player, source, afterSilentOath, currentTick);

        // 4. 流血契约 - 受击储能（胸甲，可能增加伤害）
        float afterBloodPact = BloodPactHandler.onDamage(player, source, afterGhostGod, currentTick);

        // 5. 擦身护胫 - 概率减伤（护腿）
        float afterGrazeGuard = GrazeGuardHandler.onDamage(player, source, afterBloodPact, currentTick);

        // 6. 潜行之胫 - 摔落减伤（护腿）
        float finalAmount = StealthShinHandler.onFallDamage(player, source, afterGrazeGuard, currentTick);

        // 7. 靴子 - 更新受伤状态（供 Ghost Step / Marching Boots 使用）
        Entity attacker = source.getAttacker();
        if (attacker instanceof LivingEntity livingAttacker) {
            BootsPlayerState bootsState = BootsTickHandler.getOrCreateState(player.getUuid());
            bootsState.lastHurtByLivingTick = currentTick;

            // Boot3 Ghost Step 去抖逻辑：不同 tick 或不同攻击者才计数
            if (currentTick != bootsState.lastDamageTick
                    || !livingAttacker.getUuid().equals(bootsState.lastAttackerUuid)) {
                bootsState.damageWindowCount++;
            }
            bootsState.lastDamageTick = currentTick;
            bootsState.lastAttackerUuid = livingAttacker.getUuid();
        }

        return finalAmount;
    }
}
