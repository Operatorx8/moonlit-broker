package mod.test.mymodtest.entity.spawn;

import mod.test.mymodtest.entity.MysteriousMerchantEntity;
import mod.test.mymodtest.registry.ModEntities;
import mod.test.mymodtest.world.MerchantSpawnerState;
import net.minecraft.registry.tag.StructureTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.SpawnHelper;

import java.util.UUID;

/**
 * Phase 4: 神秘商人自然生成管理器
 *
 * 生成条件：
 * 1. 村庄附近100格内
 * 2. 正在下雨（可选）
 * 3. 每天有一定概率生成
 * 4. 全局冷却时间
 * 5. 活跃商人追踪（防重复生成）
 *
 * 使用方式：
 * - 在 ServerTickEvents.END_WORLD_TICK 中调用 trySpawn()
 *
 * 状态持久化：
 * - 使用 MerchantSpawnerState (PersistentState) 保存生成状态
 * - 活跃商人 UUID 持久化追踪，即使商人在未加载区块也不会重复生成
 */
public class MysteriousMerchantSpawner {

    // ========== 调试开关 ==========
    public static final boolean DEBUG = true;

    // ========== 生成常量 ==========
    /** 检测村庄的范围（格） */
    private static final int VILLAGE_DETECTION_RANGE = 100;
    /** 每天检查生成的概率 (0.0 ~ 1.0) */
    private static final float SPAWN_CHANCE_PER_DAY = 0.3f;
    /** 调试模式：每 600 ticks (30秒) 检查一次 */
    private static final int DEBUG_CHECK_INTERVAL = 600;
    /** 正常模式：每天检查一次（24000 ticks） */
    private static final int NORMAL_CHECK_INTERVAL = 24000;
    /** 是否需要下雨才能生成 */
    private static final boolean REQUIRE_RAIN = true;
    /** 调试模式下是否跳过下雨检测 */
    private static final boolean DEBUG_SKIP_RAIN_CHECK = true;
    /** 生成尝试的最大次数 */
    private static final int MAX_SPAWN_ATTEMPTS = 10;
    /** 生成位置搜索范围 */
    private static final int SPAWN_SEARCH_RANGE = 48;
    /** 每天最多生成的商人数量 */
    private static final int MAX_MERCHANTS_PER_DAY = 1;
    /** 调试模式：生成后冷却 2 分钟 (2400 ticks) */
    private static final long DEBUG_COOLDOWN_TICKS = 2400;
    /** 正常模式：生成后冷却 1 天 (24000 ticks) */
    private static final long NORMAL_COOLDOWN_TICKS = 24000;
    /** 调试模式：商人预期存活时间 60 秒 (1200 ticks) */
    private static final long DEBUG_EXPECTED_LIFETIME = 1200;
    /** 正常模式：商人预期存活时间 30 天 (720000 ticks) */
    private static final long NORMAL_EXPECTED_LIFETIME = 720000;

    // ========== 内存状态（仅用于检查间隔，不影响持久化） ==========
    private long lastCheckTick = 0;

    /**
     * 尝试生成神秘商人
     * 应在 ServerTickEvents.END_WORLD_TICK 中调用
     *
     * @param world 服务端世界
     */
    public void trySpawn(ServerWorld world) {
        long currentTick = world.getTime();
        int checkInterval = DEBUG ? DEBUG_CHECK_INTERVAL : NORMAL_CHECK_INTERVAL;

        // 检查是否到达检查间隔（内存级别，用于减少检查频率）
        if (currentTick - lastCheckTick < checkInterval) {
            return;
        }
        lastCheckTick = currentTick;

        // 1. 获取持久化状态
        MerchantSpawnerState state = MerchantSpawnerState.getServerState(world);

        if (DEBUG) {
            System.out.println("[Spawner] CHECK_START worldTime=" + currentTick + " state={" + state.toDebugString() + "}");
        }

        // 2. 检查每日上限（使用持久化状态）
        if (!state.canSpawnToday(world, MAX_MERCHANTS_PER_DAY)) {
            if (DEBUG) {
                System.out.println("[Spawner] SKIP_DAILY_LIMIT spawnCountToday=" + state.getSpawnCountToday() +
                    " max=" + MAX_MERCHANTS_PER_DAY +
                    " lastSpawnDay=" + state.getLastSpawnDay());
            }
            return;
        }

        // 3. 检查全局冷却（使用持久化状态）
        if (!state.isCooldownExpired(world)) {
            if (DEBUG) {
                long remaining = state.getRemainingCooldown(world);
                System.out.println("[Spawner] SKIP_COOLDOWN cooldownUntil=" + state.getCooldownUntil() +
                    " remaining=" + remaining + "ticks(" + (remaining/20) + "s)" +
                    " worldTime=" + currentTick);
            }
            return;
        }

        // 4. 检查活跃商人追踪（防重复生成核心 - 持久化级别）
        if (state.hasActiveMerchant(world)) {
            UUID activeUuid = state.getActiveMerchantUuid();
            // 尝试在已加载区块中查找该商人
            var existingMerchant = world.getEntity(activeUuid);
            if (existingMerchant != null && existingMerchant.isAlive()) {
                // 商人确实存在且存活
                if (DEBUG) {
                    System.out.println("[Spawner] SKIP_ACTIVE_MERCHANT_EXISTS uuid=" + activeUuid +
                        " pos=" + existingMerchant.getBlockPos().toShortString() +
                        " worldTime=" + currentTick);
                }
                return;
            } else if (existingMerchant != null && !existingMerchant.isAlive()) {
                // 商人存在但已死亡，清除追踪
                System.out.println("[Spawner] ACTIVE_MERCHANT_DEAD uuid=" + activeUuid + " clearing");
                state.clearActiveMerchant();
            } else {
                // 商人不在已加载区块中，依赖持久化追踪
                if (DEBUG) {
                    System.out.println("[Spawner] SKIP_ACTIVE_MERCHANT_UNLOADED uuid=" + activeUuid +
                        " expireAt=" + state.getActiveMerchantExpireAt() +
                        " worldTime=" + currentTick);
                }
                return;
            }
        }

        // 5. 备用检查：扫描已加载区块中的商人（兜底机制）
        // 注意：必须用 isAlive() 过滤，因为 discard() 后实体仍在列表中直到 tick 结束
        int existingCount = world.getEntitiesByType(
            TypeFilter.instanceOf(MysteriousMerchantEntity.class),
            entity -> entity.isAlive()
        ).size();
        if (existingCount > 0) {
            if (DEBUG) {
                System.out.println("[Spawner] SKIP_EXISTING_LOADED existingMerchants=" + existingCount +
                    " worldTime=" + currentTick);
            }
            return;
        }

        // 6. 检查天气条件
        if (REQUIRE_RAIN && !DEBUG_SKIP_RAIN_CHECK) {
            if (!world.isRaining()) {
                if (DEBUG) {
                    System.out.println("[Spawner] SKIP_NO_RAIN worldTime=" + currentTick);
                }
                return;
            }
        }

        // 7. 随机概率检查
        Random random = world.getRandom();
        float roll = random.nextFloat();
        boolean passed = roll <= SPAWN_CHANCE_PER_DAY;
        if (DEBUG) {
            System.out.println("[Spawner] CHANCE_CHECK chance=" + SPAWN_CHANCE_PER_DAY +
                " roll=" + String.format("%.3f", roll) +
                " passed=" + passed +
                " worldTime=" + currentTick);
        }
        if (!passed) {
            return;
        }

        // 8. 获取随机玩家作为生成中心
        var players = world.getPlayers();
        if (players.isEmpty()) {
            if (DEBUG) {
                System.out.println("[Spawner] SKIP_NO_PLAYERS worldTime=" + currentTick);
            }
            return;
        }

        var targetPlayer = players.get(random.nextInt(players.size()));
        BlockPos playerPos = targetPlayer.getBlockPos();

        // 9. 检查玩家附近是否有村庄
        if (!isNearVillage(world, playerPos)) {
            if (DEBUG) {
                System.out.println("[Spawner] SKIP_NO_VILLAGE player=" + targetPlayer.getName().getString() +
                    " pos=" + playerPos.toShortString());
            }
            return;
        }

        // 10. 尝试在玩家附近生成商人
        BlockPos spawnPos = findSpawnPosition(world, playerPos, random);
        if (spawnPos == null) {
            if (DEBUG) {
                System.out.println("[Spawner] SKIP_NO_VALID_POS attempts=" + MAX_SPAWN_ATTEMPTS +
                    " searchRange=" + SPAWN_SEARCH_RANGE);
            }
            return;
        }

        // 11. 生成商人
        MysteriousMerchantEntity merchant = ModEntities.MYSTERIOUS_MERCHANT.create(world);
        if (merchant != null) {
            merchant.refreshPositionAndAngles(
                    spawnPos.getX() + 0.5,
                    spawnPos.getY(),
                    spawnPos.getZ() + 0.5,
                    random.nextFloat() * 360.0f,
                    0.0f
            );

            if (world.spawnEntity(merchant)) {
                // 12. 记录到持久化状态（包含商人 UUID）
                long cooldownTicks = DEBUG ? DEBUG_COOLDOWN_TICKS : NORMAL_COOLDOWN_TICKS;
                long expectedLifetime = DEBUG ? DEBUG_EXPECTED_LIFETIME : NORMAL_EXPECTED_LIFETIME;
                UUID merchantUuid = merchant.getUuid();
                state.recordSpawn(world, cooldownTicks, merchantUuid, expectedLifetime);

                System.out.println("[Spawner] SPAWN_SUCCESS pos=" + spawnPos.toShortString() +
                    " uuid=" + merchantUuid +
                    " nearPlayer=" + targetPlayer.getName().getString() +
                    " spawnCountToday=" + state.getSpawnCountToday() +
                    " totalSpawned=" + state.getTotalSpawnedCount() +
                    " cooldownTicks=" + cooldownTicks +
                    " worldTime=" + currentTick);
            }
        }
    }

    /**
     * 检查指定位置附近是否有村庄
     */
    private boolean isNearVillage(ServerWorld world, BlockPos pos) {
        BlockPos villagePos = world.locateStructure(
                StructureTags.VILLAGE,
                pos,
                VILLAGE_DETECTION_RANGE / 16,
                false
        );

        if (villagePos != null) {
            double distance = Math.sqrt(pos.getSquaredDistance(villagePos));
            if (DEBUG) {
                System.out.println("[Spawner] VILLAGE_FOUND pos=" + villagePos.toShortString() +
                    " distance=" + (int)distance + " maxRange=" + VILLAGE_DETECTION_RANGE);
            }
            return distance <= VILLAGE_DETECTION_RANGE;
        }

        return false;
    }

    /**
     * 在指定位置附近找到合适的生成位置
     */
    private BlockPos findSpawnPosition(ServerWorld world, BlockPos center, Random random) {
        for (int attempt = 0; attempt < MAX_SPAWN_ATTEMPTS; attempt++) {
            int x = center.getX() + random.nextInt(SPAWN_SEARCH_RANGE * 2) - SPAWN_SEARCH_RANGE;
            int z = center.getZ() + random.nextInt(SPAWN_SEARCH_RANGE * 2) - SPAWN_SEARCH_RANGE;

            int y = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z);
            BlockPos testPos = new BlockPos(x, y, z);

            if (isValidSpawnPosition(world, testPos)) {
                return testPos;
            }
        }
        return null;
    }

    /**
     * 检查位置是否适合生成
     */
    private boolean isValidSpawnPosition(ServerWorld world, BlockPos pos) {
        if (!world.getBlockState(pos.down()).isSolidBlock(world, pos.down())) {
            return false;
        }

        if (!world.getBlockState(pos).isAir()) {
            return false;
        }
        if (!world.getBlockState(pos.up()).isAir()) {
            return false;
        }

        return SpawnHelper.isClearForSpawn(world, pos, world.getBlockState(pos),
                world.getFluidState(pos), ModEntities.MYSTERIOUS_MERCHANT);
    }
}
