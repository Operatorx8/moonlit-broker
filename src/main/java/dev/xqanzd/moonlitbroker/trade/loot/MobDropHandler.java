package dev.xqanzd.moonlitbroker.trade.loot;

import dev.xqanzd.moonlitbroker.registry.ModEntityTypeTags;
import dev.xqanzd.moonlitbroker.registry.ModItems;
import dev.xqanzd.moonlitbroker.trade.TradeConfig;
import dev.xqanzd.moonlitbroker.trade.item.TradeScrollItem;
import dev.xqanzd.moonlitbroker.world.MerchantUnlockState;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.Angerable;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.Random;

/**
 * 怪物掉落处理器
 * 处理交易卷轴和银币的怪物掉落
 */
public class MobDropHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(MobDropHandler.class);
    private static final Random RANDOM = new Random();
    private static final float SILVER_BASE_DROP_CHANCE = 0.05f;
    private static final float SILVER_LOOTING_BONUS_PER_LEVEL = 0.01f;
    private static final float SILVER_MAX_DROP_CHANCE = 0.25f;
    private static volatile boolean silverTagEmptyWarned = false;

    /**
     * 注册怪物死亡事件
     */
    public static void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register(MobDropHandler::onMobDeath);
        LOGGER.info("[MoonTrade] 怪物掉落处理器已注册");
    }

    private static void onMobDeath(LivingEntity entity, DamageSource source) {
        // 只处理玩家击杀
        if (!(source.getAttacker() instanceof ServerPlayerEntity player)) {
            return;
        }

        if (!(entity.getWorld() instanceof ServerWorld world)) {
            return;
        }

        // Scroll 掉落保留原有规则（hostile + 条件门槛）
        if (entity instanceof HostileEntity && checkDropCondition(entity, world)) {
            // 尝试掉落交易卷轴
            tryDropScroll(entity, player, world);
        }

        // Silver 改为 entity type tag 驱动，不再写死白名单。
        tryDropSilver(entity, player, world);
    }

    /**
     * 检查掉落条件：
     * - (夜晚 + 主世界地表) OR (装备怪) OR (精英怪)
     * 6 FIX: Added elite mob condition
     */
    private static boolean checkDropCondition(LivingEntity entity, ServerWorld world) {
        // 条件1：夜晚 + 主世界地表
        boolean isNight = world.isNight();
        boolean isOverworld = world.getRegistryKey() == World.OVERWORLD;
        boolean isSurface = entity.getY() > world.getSeaLevel() - 10 
            && world.isSkyVisible(entity.getBlockPos());
        
        if (isNight && isOverworld && isSurface) {
            return true;
        }

        // 条件2：装备怪（有装备的怪物）
        if (isEquippedMob(entity)) {
            return true;
        }
        
        // 6 FIX: 条件3：精英怪
        if (isEliteMob(entity)) {
            return true;
        }

        return false;
    }

    /**
     * 检查是否为装备怪
     */
    private static boolean isEquippedMob(LivingEntity entity) {
        // 检查是否有武器或盔甲
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = entity.getEquippedStack(slot);
            if (!stack.isEmpty()) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 6 FIX: 检查是否为精英怪
     * Elite mob definition: has any status effect OR has higher max health than baseline (>20)
     * This is a simple heuristic that catches buffed/special mobs without requiring tags.
     */
    private static boolean isEliteMob(LivingEntity entity) {
        // Check for any active status effects (buffed mobs)
        if (!entity.getStatusEffects().isEmpty()) {
            return true;
        }
        
        // Check for higher than baseline max health (baseline zombie/skeleton = 20)
        double maxHealth = entity.getAttributeValue(EntityAttributes.GENERIC_MAX_HEALTH);
        if (maxHealth > 20.0) {
            return true;
        }
        
        return false;
    }

    /**
     * 尝试掉落交易卷轴
     */
    private static void tryDropScroll(LivingEntity entity, ServerPlayerEntity player, ServerWorld world) {
        if (RANDOM.nextFloat() >= TradeConfig.MOB_SCROLL_CHANCE) {
            return;
        }

        // 创建普通卷轴
        ItemStack scroll = new ItemStack(ModItems.TRADE_SCROLL, 1);
        TradeScrollItem.initialize(scroll, TradeConfig.GRADE_NORMAL);
        
        entity.dropStack(scroll);

        if (TradeConfig.TRADE_DEBUG) {
            LOGGER.debug("[MoonTrade] SCROLL_DROP player= mob={} pos={}", 
                player.getName().getString(),
                entity.getType().getUntranslatedName(),
                entity.getBlockPos());
        }
    }

    /**
     * 尝试掉落银币（tag 驱动 + neutral 仇恨判定 + looting 概率加成 + 限流）
     */
    private static void tryDropSilver(LivingEntity entity, ServerPlayerEntity player, ServerWorld world) {
        Identifier mobId = Registries.ENTITY_TYPE.getId(entity.getType());
        boolean inRegularTag = entity.getType().isIn(ModEntityTypeTags.SILVERNOTE_DROPPERS);
        boolean inNeutralTag = entity.getType().isIn(ModEntityTypeTags.SILVERNOTE_NEUTRAL_DROPPERS);
        if (!inRegularTag && !inNeutralTag) {
            // 护栏 B: release 下只 warn 一次，避免日志洪水
            if (!silverTagEmptyWarned) {
                silverTagEmptyWarned = true;
                LOGGER.warn("[MoonTrade] action=SILVER_TAG_MISS mob={} — 该实体不在 silvernote_droppers/silvernote_neutral_droppers tag 中。" +
                        "若所有怪物都不掉银票，请检查 data/{}/tags/entity_type/ 目录",
                        mobId, ModItems.MOD_ID);
            }
            return;
        }

        // neutral 目标只有“正在对该玩家仇恨/激怒”时才允许掉落
        if (inNeutralTag && !isAngeredAtPlayer(entity, player)) {
            if (TradeConfig.TRADE_DEBUG) {
                LOGGER.info(
                        "[MoonTrade] action=SILVER_DROP_CHECK result=SKIP_NEUTRAL_NOT_ANGRY mob={} player={} dim={}",
                        mobId, player.getName().getString(), world.getRegistryKey().getValue());
            }
            return;
        }

        int lootingLevel = getLootingLevel(world, player);
        float baseChance = Math.min(SILVER_MAX_DROP_CHANCE,
                SILVER_BASE_DROP_CHANCE + lootingLevel * SILVER_LOOTING_BONUS_PER_LEVEL);
        // Elite 倍率：提高概率，不改 cap
        boolean isElite = entity.getType().isIn(ModEntityTypeTags.SILVERNOTE_ELITE_DROPPERS);
        float chance = (TradeConfig.ENABLE_ELITE_DROP_BONUS && isElite)
                ? Math.min(1.0f, baseChance * TradeConfig.SILVER_ELITE_MULTIPLIER)
                : baseChance;
        float roll = RANDOM.nextFloat();
        if (roll >= chance) {
            if (TradeConfig.TRADE_DEBUG) {
                LOGGER.info(
                        "[MoonTrade] action=SILVER_DROP_CHECK result=MISS mob={} roll={} chance={} elite={} looting={} player={} dim={}",
                        mobId, roll, chance, isElite, lootingLevel, player.getName().getString(), world.getRegistryKey().getValue());
            }
            return;
        }

        // 检查限流
        MerchantUnlockState state = MerchantUnlockState.getServerState(world);
        MerchantUnlockState.Progress progress = state.getOrCreateProgress(player.getUuid());

        long currentTime = world.getTime();
        if (!progress.tryRecordSilverDrop(currentTime,
                TradeConfig.SILVER_CAP_WINDOW_TICKS,
                TradeConfig.SILVER_MOB_DROP_CAP)) {
            if (TradeConfig.TRADE_DEBUG) {
                LOGGER.info(
                        "[MoonTrade] action=SILVER_DROP_CHECK result=THROTTLED mob={} roll={} chance={} looting={} player={} dim={}",
                        mobId, roll, chance, lootingLevel, player.getName().getString(), world.getRegistryKey().getValue());
            }
            return;
        }

        state.markDirty();

        // 掉落 1-3 个银币
        int amount = 1 + RANDOM.nextInt(3);
        ItemStack silver = new ItemStack(ModItems.SILVER_NOTE, amount);
        entity.dropStack(silver);

        if (TradeConfig.TRADE_DEBUG) {
            LOGGER.info(
                    "[MoonTrade] action=SILVER_DROP_CHECK result=DROP mob={} roll={} chance={} elite={} looting={} amount={} player={} dim={} dropCount={}",
                    mobId, roll, chance, isElite, lootingLevel, amount, player.getName().getString(),
                    world.getRegistryKey().getValue(), progress.getSilverDropCount());
        }
    }

    private static int getLootingLevel(ServerWorld world, ServerPlayerEntity player) {
        Registry<Enchantment> enchantmentRegistry = world.getRegistryManager().get(RegistryKeys.ENCHANTMENT);
        if (enchantmentRegistry == null) {
            return 0;
        }
        Optional<? extends net.minecraft.registry.entry.RegistryEntry.Reference<Enchantment>> looting = enchantmentRegistry
                .getEntry(Enchantments.LOOTING);
        return looting.map(entry -> EnchantmentHelper.getEquipmentLevel(entry, player)).orElse(0);
    }

    private static boolean isAngeredAtPlayer(LivingEntity entity, ServerPlayerEntity player) {
        if (entity instanceof MobEntity mobEntity) {
            LivingEntity target = mobEntity.getTarget();
            if (target != null && target.getUuid().equals(player.getUuid())) {
                return true;
            }
        }

        if (entity instanceof Angerable angerable) {
            return player.getUuid().equals(angerable.getAngryAt());
        }

        return false;
    }
}
