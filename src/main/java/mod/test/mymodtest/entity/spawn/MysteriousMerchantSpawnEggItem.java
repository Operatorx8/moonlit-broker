package mod.test.mymodtest.entity.spawn;

import mod.test.mymodtest.entity.MysteriousMerchantEntity;
import mod.test.mymodtest.trade.MoonTradeConfig;
import mod.test.mymodtest.trade.TradeConfig;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Debug-capable merchant spawn egg:
 * - Release: behaves like vanilla fixed-type spawn egg
 * - Debug + DEBUG_EGG_RANDOMIZE: resolves spawn type through climate intrusion table
 */
public class MysteriousMerchantSpawnEggItem extends SpawnEggItem {
    private static final Logger LOGGER = LoggerFactory.getLogger("MoonTrade");
    private static final MysteriousMerchantEntity.MerchantVariant[] DEBUG_RANDOM_ORIGINS = new MysteriousMerchantEntity.MerchantVariant[] {
        MysteriousMerchantEntity.MerchantVariant.STANDARD,
        MysteriousMerchantEntity.MerchantVariant.ARID,
        MysteriousMerchantEntity.MerchantVariant.COLD,
        MysteriousMerchantEntity.MerchantVariant.WET,
        MysteriousMerchantEntity.MerchantVariant.EXOTIC
    };

    private final EntityType<? extends MysteriousMerchantEntity> fixedType;
    private final MysteriousMerchantEntity.MerchantVariant originVariant;
    private final boolean randomOriginVariantEgg;

    public MysteriousMerchantSpawnEggItem(
        EntityType<? extends MysteriousMerchantEntity> fixedType,
        int primaryColor,
        int secondaryColor,
        Item.Settings settings,
        MysteriousMerchantEntity.MerchantVariant originVariant,
        boolean randomOriginVariantEgg
    ) {
        super(fixedType, primaryColor, secondaryColor, settings);
        this.fixedType = fixedType;
        this.originVariant = originVariant == null
            ? MysteriousMerchantEntity.variantOf(fixedType)
            : originVariant;
        this.randomOriginVariantEgg = randomOriginVariantEgg;
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        if (!MoonTradeConfig.DEBUG_EGG_RANDOMIZE) {
            return super.useOnBlock(context);
        }

        World world = context.getWorld();
        BlockPos targetPos = context.getBlockPos();
        BlockState targetState = world.getBlockState(targetPos);
        if (targetState.isOf(Blocks.SPAWNER)) {
            return super.useOnBlock(context);
        }
        if (!(world instanceof ServerWorld serverWorld)) {
            return ActionResult.SUCCESS;
        }

        Direction side = context.getSide();
        BlockPos spawnPos = targetState.getCollisionShape(world, targetPos).isEmpty()
            ? targetPos
            : targetPos.offset(side);

        UUID playerUuid = context.getPlayer() == null ? null : context.getPlayer().getUuid();
        long seed = buildEggSpawnSeed(serverWorld, spawnPos, playerUuid);
        Random random = Random.create(seed);
        MysteriousMerchantEntity.MerchantVariant origin = resolveOriginVariant(random);
        MysteriousMerchantSpawner.VariantRollResult rollResult = MysteriousMerchantSpawner.chooseMerchantVariantForOrigin(
            origin,
            random,
            TradeConfig.SPAWN_DEBUG
        );
        EntityType<MysteriousMerchantEntity> chosenType = MysteriousMerchantSpawner.merchantTypeOfVariant(rollResult.rolledVariant());

        MysteriousMerchantEntity merchant = chosenType.create(serverWorld);
        if (merchant == null) {
            return ActionResult.FAIL;
        }
        merchant.refreshPositionAndAngles(
            spawnPos.getX() + 0.5D,
            spawnPos.getY(),
            spawnPos.getZ() + 0.5D,
            random.nextFloat() * 360.0F,
            0.0F
        );
        if (!serverWorld.spawnEntity(merchant)) {
            return ActionResult.FAIL;
        }

        if (context.getPlayer() == null || !context.getPlayer().isCreative()) {
            context.getStack().decrement(1);
        }

        if (TradeConfig.SPAWN_DEBUG) {
            LOGGER.info("[MoonTrade] MM_EGG_SPAWN origin={} rolled={} seed={} playerUuid={} pos={} roll={}/{} debug={} chosenType={}",
                rollResult.originVariant(),
                rollResult.rolledVariant(),
                seed,
                playerUuid == null ? "none" : playerUuid,
                spawnPos.toShortString(),
                rollResult.roll(),
                rollResult.totalWeight(),
                MoonTradeConfig.DEBUG_EGG_RANDOMIZE ? 1 : 0,
                Registries.ENTITY_TYPE.getId(chosenType));
        }
        return ActionResult.SUCCESS;
    }

    private MysteriousMerchantEntity.MerchantVariant resolveOriginVariant(Random random) {
        if (this.randomOriginVariantEgg) {
            return DEBUG_RANDOM_ORIGINS[random.nextInt(DEBUG_RANDOM_ORIGINS.length)];
        }
        return this.originVariant;
    }

    private long buildEggSpawnSeed(ServerWorld world, BlockPos pos, UUID playerUuid) {
        long seed = world.getSeed()
            ^ world.getTime()
            ^ pos.asLong()
            ^ (long) world.getRegistryKey().getValue().hashCode();
        if (playerUuid != null) {
            seed ^= playerUuid.getMostSignificantBits();
            seed ^= playerUuid.getLeastSignificantBits();
        }
        seed ^= this.originVariant.ordinal() * 31L;
        if (this.randomOriginVariantEgg) {
            seed ^= 0x9E3779B97F4A7C15L;
        }
        return seed;
    }
}
