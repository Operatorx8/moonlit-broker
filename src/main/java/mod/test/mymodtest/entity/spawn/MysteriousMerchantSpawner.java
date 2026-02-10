package mod.test.mymodtest.entity.spawn;

import mod.test.mymodtest.entity.MysteriousMerchantEntity;
import mod.test.mymodtest.registry.ModEntities;
import mod.test.mymodtest.trade.TradeConfig;
import mod.test.mymodtest.world.MerchantSpawnerState;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.StructureTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.village.VillagerType;
import net.minecraft.world.Heightmap;
import net.minecraft.world.SpawnHelper;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Phase 4: 神秘商人自然生成管理器
 *
 * 生成条件：
 * 1. 村庄附近 128 格（8 区块）内
 * 2. 正在下雨（可选）
 * 3. 每次检查有一定概率生成（per-check 概率，非 per-day）
 * 4. 全局冷却时间
 * 5. 活跃商人追踪（防重复生成）
 *
 * 概率说明：
 * - SPAWN_CHANCE_PER_CHECK 是每次检查的概率
 * - 每天检查次数 = 24000 / NORMAL_CHECK_INTERVAL
 * - 每天生成概率 ≈ 1 - (1 - pCheck)^checksPerDay
 *
 * 使用方式：
 * - 在 ServerTickEvents.END_WORLD_TICK 中调用 trySpawn()
 *
 * 状态持久化：
 * - 使用 MerchantSpawnerState (PersistentState) 保存生成状态
 * - 活跃商人 UUID 持久化追踪，即使商人在未加载区块也不会重复生成
 */
public class MysteriousMerchantSpawner {
    private static final Logger LOGGER = LoggerFactory.getLogger(MysteriousMerchantSpawner.class);

    // ========== 调试开关 ==========
    /** 发布版默认关闭；开启后使用极短的时间参数和详细日志 */
    public static final boolean DEBUG = TradeConfig.SPAWN_DEBUG;

    // ========== 生成常量 ==========
    /**
     * 检测村庄的范围（区块数）
     * locateStructure 的 radius 参数单位是 chunk，不是 block
     */
    private static final int VILLAGE_DETECTION_RANGE_CHUNKS = 8;  // 8 chunks = 128 blocks
    /** 检测村庄的范围（格）- 用于二次距离验证 */
    private static final int VILLAGE_DETECTION_RANGE_BLOCKS = VILLAGE_DETECTION_RANGE_CHUNKS * 16;  // 128 blocks
    /**
     * 每次检查的生成概率 (0.0 ~ 1.0)
     *
     * 概率换算：
     * - checksPerDay = 24000 / NORMAL_CHECK_INTERVAL = 24000 / 3600 ≈ 6.67 次/天
     * - dailyChance ≈ 1 - (1 - SPAWN_CHANCE_PER_CHECK)^checksPerDay
     * - 当前值 0.02 → dailyChance ≈ 1 - 0.98^6.67 ≈ 12.7%/天（满足其他条件时）
     * - 考虑下雨概率约 15%，实际约 1.9%/天
     */
    private static final float SPAWN_CHANCE_PER_CHECK = 0.02f;
    /** 调试模式：每 600 ticks (30秒) 检查一次 */
    private static final int DEBUG_CHECK_INTERVAL = 600;
    /** 正常模式：每 3600 ticks (3分钟) 检查一次 */
    private static final int NORMAL_CHECK_INTERVAL = 3600;
    /** 是否需要下雨才能生成（发布版保持 true 增加稀有感） */
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
    /** 正常模式：生成后冷却 15 分钟 (18000 ticks) */
    private static final long NORMAL_COOLDOWN_TICKS = 18000;
    /** 调试模式：商人预期存活时间 60 秒 (1200 ticks) */
    private static final long DEBUG_EXPECTED_LIFETIME = 1200;
    /** 正常模式：商人预期存活时间 5 天 (120000 ticks) */
    private static final long NORMAL_EXPECTED_LIFETIME = 120000;

    private static final class WeightedVariantEntry {
        private final MysteriousMerchantEntity.MerchantVariant variant;
        private final int weight;

        private WeightedVariantEntry(MysteriousMerchantEntity.MerchantVariant variant, int weight) {
            this.variant = variant;
            this.weight = Math.max(0, weight);
        }
    }

    public record VariantRollResult(
        MysteriousMerchantEntity.MerchantVariant originVariant,
        MysteriousMerchantEntity.MerchantVariant rolledVariant,
        int roll,
        int totalWeight
    ) {
    }

    // ========== 内存状态（仅用于检查间隔，不影响持久化） ==========
    private long lastCheckTick = 0;

    /**
     * 尝试生成神秘商人
     * 应在 ServerTickEvents.END_WORLD_TICK 中调用
     *
     * @param world 服务端世界
     */
    public void trySpawn(ServerWorld world) {
        if (world.getRegistryKey() != World.OVERWORLD) {
            return;
        }

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
            LOGGER.debug("[Spawner] CHECK_START worldTime={} state={{{}}}", currentTick, state.toDebugString());
        }

        // 2. 检查每日上限（使用持久化状态）
        if (!state.canSpawnToday(world, MAX_MERCHANTS_PER_DAY)) {
            if (DEBUG) {
                LOGGER.debug("[Spawner] SKIP_DAILY_LIMIT spawnCountToday={} max={} lastSpawnDay={}",
                    state.getSpawnCountToday(), MAX_MERCHANTS_PER_DAY, state.getLastSpawnDay());
            }
            return;
        }

        // 3. 检查全局冷却（使用持久化状态）
        if (!state.isCooldownExpired(world)) {
            if (DEBUG) {
                long remaining = state.getRemainingCooldown(world);
                LOGGER.debug("[Spawner] SKIP_COOLDOWN cooldownUntil={} remaining={}ticks({}s) worldTime={}",
                    state.getCooldownUntil(), remaining, (remaining / 20), currentTick);
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
                    LOGGER.debug("[Spawner] SKIP_ACTIVE_MERCHANT_EXISTS uuid={} pos={} worldTime={}",
                        activeUuid, existingMerchant.getBlockPos().toShortString(), currentTick);
                }
                return;
            } else if (existingMerchant != null && !existingMerchant.isAlive()) {
                // 商人存在但已死亡，清除追踪（异常情况，保留日志）
                LOGGER.warn("[Spawner] ACTIVE_MERCHANT_DEAD uuid={} clearing",
                    activeUuid.toString().substring(0, 8));
                state.clearActiveMerchant();
            } else {
                // 商人不在已加载区块中，依赖持久化追踪
                if (DEBUG) {
                    LOGGER.debug("[Spawner] SKIP_ACTIVE_MERCHANT_UNLOADED uuid={} expireAt={} worldTime={}",
                        activeUuid, state.getActiveMerchantExpireAt(), currentTick);
                }
                return;
            }
        }

        // 5. 备用检查：扫描已加载区块中的商人（兜底机制，检查所有 5 种类型）
        // 注意：必须用 isAlive() 过滤，因为 discard() 后实体仍在列表中直到 tick 结束
        int existingCount = world.getEntitiesByType(
            TypeFilter.instanceOf(MysteriousMerchantEntity.class),
            entity -> entity.isAlive()
        ).size();
        if (existingCount > 0) {
            if (DEBUG) {
                LOGGER.debug("[Spawner] SKIP_EXISTING_LOADED existingMerchants={} worldTime={}",
                    existingCount, currentTick);
            }
            return;
        }

        // 6. 检查天气条件
        if (REQUIRE_RAIN && !(DEBUG && DEBUG_SKIP_RAIN_CHECK)) {
            if (!world.isRaining()) {
                if (DEBUG) {
                    LOGGER.debug("[Spawner] SKIP_NO_RAIN worldTime={}", currentTick);
                }
                return;
            }
        }

        // 7. 随机概率检查（per-check 概率）
        Random random = world.getRandom();
        float roll = random.nextFloat();
        boolean passed = roll <= SPAWN_CHANCE_PER_CHECK;
        if (DEBUG) {
            LOGGER.debug("[Spawner] CHANCE_CHECK chancePerCheck={} roll={} passed={} worldTime={}",
                SPAWN_CHANCE_PER_CHECK, String.format("%.3f", roll), passed, currentTick);
        }
        if (!passed) {
            return;
        }

        // 8. 获取随机玩家作为生成中心
        var players = world.getPlayers();
        if (players.isEmpty()) {
            if (DEBUG) {
                LOGGER.debug("[Spawner] SKIP_NO_PLAYERS worldTime={}", currentTick);
            }
            return;
        }

        var targetPlayer = players.get(random.nextInt(players.size()));
        BlockPos playerPos = targetPlayer.getBlockPos();

        // 9. 检查玩家附近是否有村庄
        if (!isNearVillage(world, playerPos)) {
            if (DEBUG) {
                LOGGER.debug("[Spawner] SKIP_NO_VILLAGE player={} pos={}",
                    targetPlayer.getName().getString(), playerPos.toShortString());
            }
            return;
        }

        // 10. 尝试在玩家附近生成商人
        BlockPos spawnPos = findSpawnPosition(world, playerPos, random);
        if (spawnPos == null) {
            if (DEBUG) {
                LOGGER.debug("[Spawner] SKIP_NO_VALID_POS attempts={} searchRange={}",
                    MAX_SPAWN_ATTEMPTS, SPAWN_SEARCH_RANGE);
            }
            return;
        }

        // 11. 按 VillagerType 映射选择商人变体
        RegistryEntry<Biome> biomeEntry = world.getBiome(spawnPos);
        VillagerType villagerType = VillagerType.forBiome(biomeEntry);
        VariantRollResult variantRoll = chooseMerchantVariantForVillagerType(villagerType, random, DEBUG);
        EntityType<MysteriousMerchantEntity> chosenType = merchantTypeOfVariant(variantRoll.rolledVariant());

        // 12. 生成商人
        MysteriousMerchantEntity merchant = chosenType.create(world);
        if (merchant != null) {
            merchant.refreshPositionAndAngles(
                    spawnPos.getX() + 0.5,
                    spawnPos.getY(),
                    spawnPos.getZ() + 0.5,
                    random.nextFloat() * 360.0f,
                    0.0f
            );

            if (world.spawnEntity(merchant)) {
                // 13. 记录到持久化状态（包含商人 UUID）
                long cooldownTicks = DEBUG ? DEBUG_COOLDOWN_TICKS : NORMAL_COOLDOWN_TICKS;
                long expectedLifetime = DEBUG ? DEBUG_EXPECTED_LIFETIME : NORMAL_EXPECTED_LIFETIME;
                UUID merchantUuid = merchant.getUuid();
                state.recordSpawn(world, cooldownTicks, merchantUuid, expectedLifetime);

                if (DEBUG) {
                    LOGGER.info("[Spawner] action=MM_SPAWN_VARIANT villagerType={} originVariant={} rolledVariant={} roll={}/{} chosen={} pos={} uuid={}... nearPlayer={} totalSpawned={}",
                        villagerType, variantRoll.originVariant(), variantRoll.rolledVariant(), variantRoll.roll(), variantRoll.totalWeight(),
                        net.minecraft.registry.Registries.ENTITY_TYPE.getId(chosenType),
                        spawnPos.toShortString(),
                        merchantUuid.toString().substring(0, 8),
                        targetPlayer.getName().getString(),
                        state.getTotalSpawnedCount());
                }
                if (DEBUG) {
                    LOGGER.debug("[Spawner]   └─ cooldownTicks={} worldTime={}", cooldownTicks, currentTick);
                }
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
                VILLAGE_DETECTION_RANGE_CHUNKS,  // 单位：区块
                false
        );

        if (villagePos != null) {
            double distance = Math.sqrt(pos.getSquaredDistance(villagePos));
            if (DEBUG) {
                LOGGER.debug("[Spawner] VILLAGE_FOUND pos={} distance={} maxRange={}",
                    villagePos.toShortString(), (int) distance, VILLAGE_DETECTION_RANGE_BLOCKS);
            }
            return distance <= VILLAGE_DETECTION_RANGE_BLOCKS;  // 单位：格
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

        // Use base MYSTERIOUS_MERCHANT type for spawn check (all 5 share same dimensions)
        return SpawnHelper.isClearForSpawn(world, pos, world.getBlockState(pos),
                world.getFluidState(pos), ModEntities.MYSTERIOUS_MERCHANT);
    }

    public static MysteriousMerchantEntity.MerchantVariant originVariantForVillagerType(VillagerType villagerType) {
        if (villagerType == VillagerType.DESERT || villagerType == VillagerType.SAVANNA) {
            return MysteriousMerchantEntity.MerchantVariant.ARID;
        }
        if (villagerType == VillagerType.SNOW) {
            return MysteriousMerchantEntity.MerchantVariant.COLD;
        }
        if (villagerType == VillagerType.SWAMP) {
            return MysteriousMerchantEntity.MerchantVariant.WET;
        }
        if (villagerType == VillagerType.JUNGLE) {
            return MysteriousMerchantEntity.MerchantVariant.EXOTIC;
        }
        return MysteriousMerchantEntity.MerchantVariant.STANDARD;
    }

    public static VariantRollResult chooseMerchantVariantForVillagerType(VillagerType villagerType, Random random, boolean debug) {
        MysteriousMerchantEntity.MerchantVariant originVariant = originVariantForVillagerType(villagerType);
        return chooseMerchantVariantForOrigin(originVariant, random, debug);
    }

    public static VariantRollResult chooseMerchantVariantForOrigin(
        MysteriousMerchantEntity.MerchantVariant originVariant,
        Random random,
        boolean debug
    ) {
        WeightedVariantEntry[] table = switch (originVariant) {
            case ARID -> new WeightedVariantEntry[]{
                new WeightedVariantEntry(MysteriousMerchantEntity.MerchantVariant.ARID, 70),
                new WeightedVariantEntry(MysteriousMerchantEntity.MerchantVariant.STANDARD, 10),
                new WeightedVariantEntry(MysteriousMerchantEntity.MerchantVariant.COLD, 10),
                new WeightedVariantEntry(MysteriousMerchantEntity.MerchantVariant.WET, 8),
                new WeightedVariantEntry(MysteriousMerchantEntity.MerchantVariant.EXOTIC, 2)
            };
            case COLD -> new WeightedVariantEntry[]{
                new WeightedVariantEntry(MysteriousMerchantEntity.MerchantVariant.COLD, 70),
                new WeightedVariantEntry(MysteriousMerchantEntity.MerchantVariant.STANDARD, 10),
                new WeightedVariantEntry(MysteriousMerchantEntity.MerchantVariant.ARID, 10),
                new WeightedVariantEntry(MysteriousMerchantEntity.MerchantVariant.WET, 8),
                new WeightedVariantEntry(MysteriousMerchantEntity.MerchantVariant.EXOTIC, 2)
            };
            case WET -> new WeightedVariantEntry[]{
                new WeightedVariantEntry(MysteriousMerchantEntity.MerchantVariant.WET, 70),
                new WeightedVariantEntry(MysteriousMerchantEntity.MerchantVariant.STANDARD, 10),
                new WeightedVariantEntry(MysteriousMerchantEntity.MerchantVariant.ARID, 10),
                new WeightedVariantEntry(MysteriousMerchantEntity.MerchantVariant.COLD, 8),
                new WeightedVariantEntry(MysteriousMerchantEntity.MerchantVariant.EXOTIC, 2)
            };
            case EXOTIC -> new WeightedVariantEntry[]{
                new WeightedVariantEntry(MysteriousMerchantEntity.MerchantVariant.EXOTIC, 70),
                new WeightedVariantEntry(MysteriousMerchantEntity.MerchantVariant.STANDARD, 10),
                new WeightedVariantEntry(MysteriousMerchantEntity.MerchantVariant.ARID, 10),
                new WeightedVariantEntry(MysteriousMerchantEntity.MerchantVariant.COLD, 8),
                new WeightedVariantEntry(MysteriousMerchantEntity.MerchantVariant.WET, 2)
            };
            case STANDARD -> new WeightedVariantEntry[]{
                new WeightedVariantEntry(MysteriousMerchantEntity.MerchantVariant.STANDARD, 70),
                new WeightedVariantEntry(MysteriousMerchantEntity.MerchantVariant.ARID, 10),
                new WeightedVariantEntry(MysteriousMerchantEntity.MerchantVariant.COLD, 10),
                new WeightedVariantEntry(MysteriousMerchantEntity.MerchantVariant.WET, 8),
                new WeightedVariantEntry(MysteriousMerchantEntity.MerchantVariant.EXOTIC, 2)
            };
        };

        int totalWeight = 0;
        for (WeightedVariantEntry entry : table) {
            totalWeight += entry.weight;
        }
        if (totalWeight <= 0) {
            return new VariantRollResult(
                originVariant,
                MysteriousMerchantEntity.MerchantVariant.STANDARD,
                0,
                1
            );
        }

        int roll = random.nextInt(totalWeight);
        int cursor = roll;
        MysteriousMerchantEntity.MerchantVariant rolled = table[table.length - 1].variant;
        for (WeightedVariantEntry entry : table) {
            if (cursor < entry.weight) {
                rolled = entry.variant;
                break;
            }
            cursor -= entry.weight;
        }

        if (debug && DEBUG) {
            LOGGER.debug("[Spawner] MM_VARIANT_ROLL originVariant={} rolledVariant={} roll={}/{}",
                originVariant, rolled, roll, totalWeight);
        }
        return new VariantRollResult(originVariant, rolled, roll, totalWeight);
    }

    public static EntityType<MysteriousMerchantEntity> merchantTypeOfVariant(MysteriousMerchantEntity.MerchantVariant variant) {
        return switch (variant) {
            case ARID -> ModEntities.MYSTERIOUS_MERCHANT_ARID;
            case COLD -> ModEntities.MYSTERIOUS_MERCHANT_COLD;
            case WET -> ModEntities.MYSTERIOUS_MERCHANT_WET;
            case EXOTIC -> ModEntities.MYSTERIOUS_MERCHANT_EXOTIC;
            case STANDARD -> ModEntities.MYSTERIOUS_MERCHANT;
        };
    }

    public static EntityType<MysteriousMerchantEntity> chooseMerchantTypeForVillagerType(VillagerType villagerType, Random random) {
        VariantRollResult roll = chooseMerchantVariantForVillagerType(villagerType, random, DEBUG);
        return merchantTypeOfVariant(roll.rolledVariant());
    }
}
