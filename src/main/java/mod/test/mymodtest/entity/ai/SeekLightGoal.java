package mod.test.mymodtest.entity.ai;

import mod.test.mymodtest.entity.MysteriousMerchantEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;
import java.util.List;

/**
 * Phase 3.3: 趋光性 Goal
 * - 仅夜晚生效
 * - 仅非交易状态时执行
 * - 仅非恐慌/非战斗时执行
 * - 周期性扫描附近寻找亮度更高的位置
 */
public class SeekLightGoal extends Goal {
    private static final Logger LOGGER = LoggerFactory.getLogger(SeekLightGoal.class);

    // ========== 可配置常量 ==========
    /** 扫描光源的范围 (格) */
    public static final int SCAN_RADIUS = 16;
    /** 扫描间隔 (ticks) */
    public static final int SCAN_INTERVAL = 100; // 5秒
    /** 当前位置光照需要低于此值才触发寻光 */
    public static final int LOW_LIGHT_THRESHOLD = 7;
    /** 目标位置光照需要高于当前至少这么多才移动 */
    public static final int LIGHT_DIFFERENCE_THRESHOLD = 4;
    /** 移动速度 */
    public static final double WALK_SPEED = 0.6;
    /** 检测威胁的范围（如果有威胁则不执行） */
    public static final double THREAT_CHECK_RANGE = 20.0;

    private final MysteriousMerchantEntity merchant;
    private final EntityNavigation navigation;

    private int scanCooldown;
    private BlockPos targetLightPos;

    public SeekLightGoal(MysteriousMerchantEntity merchant) {
        this.merchant = merchant;
        this.navigation = merchant.getNavigation();
        this.scanCooldown = 0;
        this.targetLightPos = null;
        this.setControls(EnumSet.of(Control.MOVE));
    }

    @Override
    public boolean canStart() {
        // 1. 只在夜晚生效
        World world = merchant.getEntityWorld();
        if (!world.isNight()) {
            return false;
        }

        // 2. 交易中不执行
        if (merchant.hasCustomer()) {
            return false;
        }

        // 3. 附近有威胁时不执行（让逃跑优先）
        if (hasNearbyThreat()) {
            return false;
        }

        // 4. 最近受伤时不执行
        if (wasRecentlyHurt()) {
            return false;
        }

        // 5. 检查当前位置光照
        BlockPos currentPos = merchant.getBlockPos();
        int currentLight = world.getLightLevel(LightType.BLOCK, currentPos);

        // 只有当前光照较低时才寻光
        if (currentLight >= LOW_LIGHT_THRESHOLD) {
            return false;
        }

        // 6. 扫描间隔
        scanCooldown--;
        if (scanCooldown > 0) {
            return false;
        }
        scanCooldown = SCAN_INTERVAL;

        // 7. 扫描寻找更亮的位置
        targetLightPos = findBrighterPosition(currentPos, currentLight);

        return targetLightPos != null;
    }

    @Override
    public boolean shouldContinue() {
        // 继续条件：未到达目标 且 路径有效
        if (navigation.isIdle()) {
            return false;
        }

        // 交易中停止
        if (merchant.hasCustomer()) {
            return false;
        }

        // 有威胁时停止
        if (hasNearbyThreat() || wasRecentlyHurt()) {
            return false;
        }

        return true;
    }

    @Override
    public void start() {
        if (targetLightPos != null) {
            Path path = navigation.findPathTo(targetLightPos, 0);
            if (path != null) {
                navigation.startMovingAlong(path, WALK_SPEED);

                if (MysteriousMerchantEntity.DEBUG_AI) {
                    int targetLight = merchant.getEntityWorld().getLightLevel(LightType.BLOCK, targetLightPos);
                    LOGGER.debug("[MerchantAI] 趋光：向 {} 移动 (光照等级 {})",
                            targetLightPos.toShortString(), targetLight);
                }
            }
        }
    }

    @Override
    public void stop() {
        navigation.stop();
        targetLightPos = null;

        if (MysteriousMerchantEntity.DEBUG_AI) {
            LOGGER.debug("[MerchantAI] 趋光结束");
        }
    }

    /**
     * 寻找比当前位置更亮的可达位置
     */
    private BlockPos findBrighterPosition(BlockPos currentPos, int currentLight) {
        World world = merchant.getEntityWorld();
        BlockPos bestPos = null;
        int bestLight = currentLight + LIGHT_DIFFERENCE_THRESHOLD;

        // 扫描周围方块
        for (int x = -SCAN_RADIUS; x <= SCAN_RADIUS; x++) {
            for (int z = -SCAN_RADIUS; z <= SCAN_RADIUS; z++) {
                // 跳过距离过远的点（使用圆形范围）
                if (x * x + z * z > SCAN_RADIUS * SCAN_RADIUS) {
                    continue;
                }

                for (int y = -4; y <= 4; y++) {
                    BlockPos checkPos = currentPos.add(x, y, z);

                    // 检查是否可以站立
                    if (!isValidStandingPosition(world, checkPos)) {
                        continue;
                    }

                    // 检查光照等级
                    int lightLevel = world.getLightLevel(LightType.BLOCK, checkPos);
                    if (lightLevel > bestLight) {
                        // 检查路径是否可达
                        Path testPath = navigation.findPathTo(checkPos, 0);
                        if (testPath != null && testPath.reachesTarget()) {
                            bestLight = lightLevel;
                            bestPos = checkPos;
                        }
                    }
                }
            }
        }

        return bestPos;
    }

    /**
     * 检查某个位置是否可以站立
     */
    private boolean isValidStandingPosition(World world, BlockPos pos) {
        // 脚下需要是固体方块
        BlockPos below = pos.down();
        if (!world.getBlockState(below).isSolidBlock(world, below)) {
            return false;
        }

        // 头部和身体位置需要可通过
        if (!world.getBlockState(pos).isAir() ||
            !world.getBlockState(pos.up()).isAir()) {
            return false;
        }

        return true;
    }

    /**
     * 检查附近是否有威胁
     */
    private boolean hasNearbyThreat() {
        List<HostileEntity> hostiles = merchant.getEntityWorld().getEntitiesByClass(
                HostileEntity.class,
                merchant.getBoundingBox().expand(THREAT_CHECK_RANGE),
                entity -> entity.isAlive()
        );
        return !hostiles.isEmpty();
    }

    /**
     * 检查最近是否受伤
     */
    private boolean wasRecentlyHurt() {
        return merchant.hurtTime > 0 ||
               (merchant.getAttacker() != null &&
                merchant.age - merchant.getLastAttackedTime() < 60);
    }
}
