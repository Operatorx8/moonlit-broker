package dev.xqanzd.moonlitbroker.entity.ai;

import dev.xqanzd.moonlitbroker.entity.MysteriousMerchantEntity;
import dev.xqanzd.moonlitbroker.katana.sound.ModSounds;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;
import java.util.List;

/**
 * Phase 3.2: 自保机制 Goal
 * - 隐身：周围有敌对生物或最近受伤时
 * - 治疗：生命值低于阈值时
 * - 有冷却机制防止刷屏
 */
public class DrinkPotionGoal extends Goal {
    private static final Logger LOGGER = LoggerFactory.getLogger(DrinkPotionGoal.class);

    // ========== 可配置常量 ==========
    /** 隐身冷却时间 (ticks) */
    public static final int INVISIBILITY_COOLDOWN = 100 * 20; // 100秒
    /** 隐身持续时间 (ticks) */
    public static final int INVISIBILITY_DURATION = 40 * 20; // 40秒
    /** 治疗冷却时间 (ticks) */
    public static final int HEAL_COOLDOWN = 45 * 20; // 45秒
    /** 治疗触发的生命值阈值 (百分比) */
    public static final float HEAL_THRESHOLD = 0.6f; // 60%
    /** 检测威胁的范围 */
    public static final double THREAT_RANGE = 12.0;
    /** 受伤后触发隐身的 tick 数 */
    public static final int HURT_TRIGGER_TICKS = 40; // 2秒
    /** 喝药动作持续时间 (ticks) */
    public static final int DRINK_DURATION = 20; // 1秒

    private final MysteriousMerchantEntity merchant;

    // 冷却计时器
    private int invisibilityCooldown;
    private int healCooldown;

    // 当前喝药状态
    private int drinkingTicks;
    private PotionType pendingPotion;

    private enum PotionType {
        INVISIBILITY,
        HEALING
    }

    public DrinkPotionGoal(MysteriousMerchantEntity merchant) {
        this.merchant = merchant;
        this.invisibilityCooldown = 0;
        this.healCooldown = 0;
        this.drinkingTicks = 0;
        this.pendingPotion = null;
        // 不锁定移动，可以边跑边喝
        this.setControls(EnumSet.noneOf(Control.class));
    }

    @Override
    public boolean canStart() {
        // 减少冷却
        if (invisibilityCooldown > 0) invisibilityCooldown--;
        if (healCooldown > 0) healCooldown--;

        // 优先检查是否需要治疗
        if (shouldHeal()) {
            pendingPotion = PotionType.HEALING;
            return true;
        }

        // 其次检查是否需要隐身
        if (shouldGoInvisible()) {
            pendingPotion = PotionType.INVISIBILITY;
            return true;
        }

        return false;
    }

    @Override
    public boolean shouldContinue() {
        // 喝药动作进行中
        return drinkingTicks > 0;
    }

    @Override
    public void start() {
        drinkingTicks = DRINK_DURATION;

        if (MysteriousMerchantEntity.DEBUG_AI) {
            String potionName = pendingPotion == PotionType.HEALING ? "治疗" : "隐身";
            LOGGER.debug("[MerchantAI] 开始喝{}药水", potionName);
        }

        // 播放喝药音效
        merchant.playSound(ModSounds.MERCHANT_DRINK, 1.0f, 1.0f);
    }

    @Override
    public void tick() {
        drinkingTicks--;

        // 生成喝药粒子效果
        if (merchant.getEntityWorld() instanceof ServerWorld serverWorld) {
            // 使用简单的药水飞溅粒子效果
            serverWorld.spawnParticles(
                    ParticleTypes.SPLASH,
                    merchant.getX(),
                    merchant.getY() + merchant.getHeight() * 0.5,
                    merchant.getZ(),
                    5, 0.2, 0.2, 0.2, 0.1
            );
        }

        // 喝完后应用效果
        if (drinkingTicks <= 0) {
            applyPotionEffect();
        }
    }

    @Override
    public void stop() {
        drinkingTicks = 0;
        pendingPotion = null;
    }

    /**
     * 应用药水效果
     */
    private void applyPotionEffect() {
        if (pendingPotion == PotionType.HEALING) {
            // 瞬间治疗 II
            merchant.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.INSTANT_HEALTH, 1, 1
            ));
            // 额外给一个再生效果
            merchant.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.REGENERATION, 5 * 20, 1 // 5秒再生II
            ));
            healCooldown = HEAL_COOLDOWN;

            if (MysteriousMerchantEntity.DEBUG_AI) {
                LOGGER.debug("[MerchantAI] 喝下治疗药水，当前生命: {}/{}",
                        merchant.getHealth(), merchant.getMaxHealth());
            }

        } else if (pendingPotion == PotionType.INVISIBILITY) {
            // 隐身效果
            merchant.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.INVISIBILITY, INVISIBILITY_DURATION, 0
            ));
            invisibilityCooldown = INVISIBILITY_COOLDOWN;

            if (MysteriousMerchantEntity.DEBUG_AI) {
                LOGGER.debug("[MerchantAI] 喝下隐身药水，持续 {} 秒", (INVISIBILITY_DURATION / 20));
            }
        }

        // 播放完成音效
        merchant.playSound(ModSounds.MERCHANT_DRINK, 1.0f, 1.05f);
    }

    /**
     * 检查是否需要治疗
     */
    private boolean shouldHeal() {
        if (healCooldown > 0) return false;

        float healthPercent = merchant.getHealth() / merchant.getMaxHealth();
        return healthPercent < HEAL_THRESHOLD;
    }

    /**
     * 检查是否需要隐身
     */
    private boolean shouldGoInvisible() {
        if (invisibilityCooldown > 0) return false;

        // 已经隐身了就不需要再喝
        if (merchant.hasStatusEffect(StatusEffects.INVISIBILITY)) return false;

        // 条件1：最近受伤
        if (wasRecentlyHurt()) {
            return true;
        }

        // 条件2：附近有敌对生物
        return hasNearbyThreat();
    }

    /**
     * 检查最近是否受伤
     */
    private boolean wasRecentlyHurt() {
        return merchant.hurtTime > 0 ||
               (merchant.getAttacker() != null &&
                merchant.age - merchant.getLastAttackedTime() < HURT_TRIGGER_TICKS);
    }

    /**
     * 检查附近是否有威胁
     */
    private boolean hasNearbyThreat() {
        List<HostileEntity> hostiles = merchant.getEntityWorld().getEntitiesByClass(
                HostileEntity.class,
                merchant.getBoundingBox().expand(THREAT_RANGE),
                entity -> entity.isAlive()
        );
        return !hostiles.isEmpty();
    }
}
