package mod.test.mymodtest.entity.spawn;

import mod.test.mymodtest.entity.MysteriousMerchantEntity;
import mod.test.mymodtest.registry.ModEntities;
import net.minecraft.entity.SpawnReason;
import net.minecraft.registry.tag.StructureTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.SpawnHelper;

/**
 * Phase 4: 神秘商人自然生成管理器
 *
 * 生成条件：
 * 1. 村庄附近100格内
 * 2. 正在下雨（可选）
 * 3. 每天有一定概率生成
 *
 * 使用方式：
 * - 在 ServerTickEvents.END_WORLD_TICK 中调用 trySpawn()
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

    // ========== 状态跟踪 ==========
    private long lastCheckTick = 0;
    private int merchantsSpawnedToday = 0;
    /** 每天最多生成的商人数量 */
    private static final int MAX_MERCHANTS_PER_DAY = 1;

    /**
     * 尝试生成神秘商人
     * 应在 ServerTickEvents.END_WORLD_TICK 中调用
     *
     * @param world 服务端世界
     */
    public void trySpawn(ServerWorld world) {
        long currentTick = world.getTime();
        int checkInterval = DEBUG ? DEBUG_CHECK_INTERVAL : NORMAL_CHECK_INTERVAL;

        // 新的一天重置计数
        if (currentTick / NORMAL_CHECK_INTERVAL > lastCheckTick / NORMAL_CHECK_INTERVAL) {
            merchantsSpawnedToday = 0;
        }

        // 检查是否到达检查间隔
        if (currentTick - lastCheckTick < checkInterval) {
            return;
        }
        lastCheckTick = currentTick;

        // 检查今天是否已达到生成上限
        if (merchantsSpawnedToday >= MAX_MERCHANTS_PER_DAY) {
            return;
        }

        // 检查天气条件
        if (REQUIRE_RAIN && !DEBUG_SKIP_RAIN_CHECK) {
            if (!world.isRaining()) {
                if (DEBUG) {
                    System.out.println("[MysteriousMerchantSpawner] 未下雨，跳过生成检查");
                }
                return;
            }
        }

        // 随机概率检查
        Random random = world.getRandom();
        if (random.nextFloat() > SPAWN_CHANCE_PER_DAY) {
            if (DEBUG) {
                System.out.println("[MysteriousMerchantSpawner] 概率检查未通过");
            }
            return;
        }

        // 获取随机玩家作为生成中心
        var players = world.getPlayers();
        if (players.isEmpty()) {
            return;
        }

        var targetPlayer = players.get(random.nextInt(players.size()));
        BlockPos playerPos = targetPlayer.getBlockPos();

        // 检查玩家附近是否有村庄
        if (!isNearVillage(world, playerPos)) {
            if (DEBUG) {
                System.out.println("[MysteriousMerchantSpawner] 玩家 " + targetPlayer.getName().getString() +
                        " 附近没有村庄");
            }
            return;
        }

        // 尝试在玩家附近生成商人
        BlockPos spawnPos = findSpawnPosition(world, playerPos, random);
        if (spawnPos == null) {
            if (DEBUG) {
                System.out.println("[MysteriousMerchantSpawner] 找不到合适的生成位置");
            }
            return;
        }

        // 生成商人
        MysteriousMerchantEntity merchant = ModEntities.MYSTERIOUS_MERCHANT.create(world, SpawnReason.EVENT);
        if (merchant != null) {
            merchant.refreshPositionAndAngles(
                    spawnPos.getX() + 0.5,
                    spawnPos.getY(),
                    spawnPos.getZ() + 0.5,
                    random.nextFloat() * 360.0f,
                    0.0f
            );

            if (world.spawnEntity(merchant)) {
                merchantsSpawnedToday++;
                System.out.println("[MysteriousMerchantSpawner] 成功生成神秘商人于 " +
                        spawnPos.toShortString() + " (玩家: " + targetPlayer.getName().getString() + ")");
            }
        }
    }

    /**
     * 检查指定位置附近是否有村庄
     */
    private boolean isNearVillage(ServerWorld world, BlockPos pos) {
        // 使用 locateStructure 检测村庄
        // locateStructure 返回的是 BlockPos（村庄位置）
        BlockPos villagePos = world.locateStructure(
                StructureTags.VILLAGE,
                pos,
                VILLAGE_DETECTION_RANGE / 16,  // 转换为区块半径
                false  // 不跳过已探索的区块
        );

        if (villagePos != null) {
            double distance = Math.sqrt(pos.getSquaredDistance(villagePos));
            if (DEBUG) {
                System.out.println("[MysteriousMerchantSpawner] 找到村庄于 " +
                        villagePos.toShortString() + "，距离: " + (int) distance);
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

            // 获取地表高度
            int y = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z);
            BlockPos testPos = new BlockPos(x, y, z);

            // 检查是否可以生成
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
        // 检查下方是否有固体方块
        if (!world.getBlockState(pos.down()).isSolidBlock(world, pos.down())) {
            return false;
        }

        // 检查当前位置和上方是否为空
        if (!world.getBlockState(pos).isAir()) {
            return false;
        }
        if (!world.getBlockState(pos.up()).isAir()) {
            return false;
        }

        // 使用 SpawnHelper 检查光照等条件
        return SpawnHelper.isClearForSpawn(world, pos, world.getBlockState(pos),
                world.getFluidState(pos), ModEntities.MYSTERIOUS_MERCHANT);
    }
}
