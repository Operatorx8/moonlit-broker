package mod.test.mymodtest.entity.ai;

import mod.test.mymodtest.entity.MysteriousMerchantEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.NoPenaltyTargeting;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;
import java.util.List;

/**
 * Phase 3.1: 强化逃跑 Goal
 * - 触发条件：最近受伤 或 附近有敌对生物
 * - 行为：更高逃跑速度、更频繁重选路径、远离威胁源
 */
public class EnhancedFleeGoal extends Goal {

    // ========== 可配置常量 ==========
    /** 检测敌对生物的范围 */
    public static final double THREAT_DETECTION_RANGE = 16.0;
    /** 受伤后保持逃跑状态的 tick 数 */
    public static final int HURT_MEMORY_TICKS = 60; // 3秒
    /** 逃跑速度倍率 */
    public static final double FLEE_SPEED_MULTIPLIER = 1.4;
    /** 重新选择路径的间隔 tick */
    public static final int REPATH_INTERVAL = 20; // 1秒
    /** 逃跑目标点距离 */
    public static final int FLEE_DISTANCE = 16;

    private final MysteriousMerchantEntity merchant;
    private final EntityNavigation navigation;
    private final double fleeSpeed;

    private LivingEntity threatEntity;
    private int repathCooldown;
    private int lastHurtTime;

    public EnhancedFleeGoal(MysteriousMerchantEntity merchant, double baseSpeed) {
        this.merchant = merchant;
        this.navigation = merchant.getNavigation();
        this.fleeSpeed = baseSpeed * FLEE_SPEED_MULTIPLIER;
        this.setControls(EnumSet.of(Control.MOVE));
    }

    @Override
    public boolean canStart() {
        // 条件1：最近受伤
        if (wasRecentlyHurt()) {
            threatEntity = merchant.getAttacker();
            if (MysteriousMerchantEntity.DEBUG_AI) {
                System.out.println("[MerchantAI] 逃跑触发：最近受伤");
            }
            return true;
        }

        // 条件2：附近有敌对生物
        LivingEntity nearestHostile = findNearestHostile();
        if (nearestHostile != null) {
            threatEntity = nearestHostile;
            if (MysteriousMerchantEntity.DEBUG_AI) {
                System.out.println("[MerchantAI] 逃跑触发：检测到敌对生物 " + nearestHostile.getType().getUntranslatedName());
            }
            return true;
        }

        return false;
    }

    @Override
    public boolean shouldContinue() {
        // 继续条件：路径未完成 且 （威胁仍存在 或 最近受伤）
        if (navigation.isIdle()) {
            return false;
        }
        return threatEntity != null && threatEntity.isAlive() &&
               merchant.squaredDistanceTo(threatEntity) < THREAT_DETECTION_RANGE * THREAT_DETECTION_RANGE
               || wasRecentlyHurt();
    }

    @Override
    public void start() {
        repathCooldown = 0;
        lastHurtTime = merchant.age;
        findFleePosition();
    }

    @Override
    public void tick() {
        repathCooldown--;

        // 定期重新选择逃跑点，避免卡墙
        if (repathCooldown <= 0) {
            repathCooldown = REPATH_INTERVAL;
            findFleePosition();
        }

        // 更新威胁实体
        if (threatEntity == null || !threatEntity.isAlive()) {
            threatEntity = findNearestHostile();
            if (threatEntity == null) {
                threatEntity = merchant.getAttacker();
            }
        }
    }

    @Override
    public void stop() {
        navigation.stop();
        threatEntity = null;
        if (MysteriousMerchantEntity.DEBUG_AI) {
            System.out.println("[MerchantAI] 逃跑结束");
        }
    }

    /**
     * 寻找逃离威胁的位置并开始导航
     */
    private void findFleePosition() {
        Vec3d fleeTarget = null;

        if (threatEntity != null) {
            // 尝试向远离威胁的方向找点
            fleeTarget = NoPenaltyTargeting.findFrom(merchant, FLEE_DISTANCE, 7, threatEntity.getEyePos());
        }

        if (fleeTarget == null) {
            // 如果找不到，就随便找个点
            fleeTarget = NoPenaltyTargeting.find(merchant, FLEE_DISTANCE, 7);
        }

        if (fleeTarget != null) {
            Path path = navigation.findPathTo(fleeTarget.x, fleeTarget.y, fleeTarget.z, 0);
            if (path != null) {
                navigation.startMovingAlong(path, fleeSpeed);
            }
        }
    }

    /**
     * 检查是否最近受伤
     */
    private boolean wasRecentlyHurt() {
        return merchant.hurtTime > 0 ||
               (merchant.getAttacker() != null &&
                merchant.age - merchant.getLastAttackedTime() < HURT_MEMORY_TICKS);
    }

    /**
     * 查找附近最近的敌对生物
     */
    private LivingEntity findNearestHostile() {
        List<HostileEntity> hostiles = merchant.getEntityWorld().getEntitiesByClass(
                HostileEntity.class,
                merchant.getBoundingBox().expand(THREAT_DETECTION_RANGE),
                entity -> entity.isAlive() && merchant.canSee(entity)
        );

        if (hostiles.isEmpty()) {
            return null;
        }

        // 返回最近的敌对生物
        HostileEntity nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (HostileEntity hostile : hostiles) {
            double dist = merchant.squaredDistanceTo(hostile);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = hostile;
            }
        }
        return nearest;
    }
}
