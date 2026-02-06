package mod.test.mymodtest.armor.effect;

import mod.test.mymodtest.armor.ArmorConfig;
import mod.test.mymodtest.armor.item.ArmorItems;
import mod.test.mymodtest.armor.util.CooldownManager;
import mod.test.mymodtest.registry.ModTags;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * 走私者之胫 - 击杀掉落增益
 *
 * 机制：
 * - 战利品概率：20%（额外一次 loot roll）
 * - 双倍掉落：10%（复制一个已生成掉落）
 * - 冷却：40s（800 ticks），任一子效果触发即进入 CD
 * - 限制：PVP 不触发；Boss/核心资源掉落概率减半
 */
public class SmugglerShinHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("MoonTrace");
    private static final Random RANDOM = new Random();

    /** Boss 实体类型集合 */
    private static final Set<EntityType<?>> BOSS_ENTITIES = Set.of(
            EntityType.ENDER_DRAGON,
            EntityType.WITHER,
            EntityType.ELDER_GUARDIAN,
            EntityType.WARDEN
    );

    /**
     * 处理掉落增益逻辑
     * 在 LivingEntity#dropLoot 后调用
     *
     * @param player      击杀者
     * @param killed      被击杀的实体
     * @param source      伤害来源
     * @param drops       原始掉落物列表
     * @param currentTick 当前 tick
     * @return 是否触发了效果
     */
    public static boolean onEntityDeath(ServerPlayerEntity player, Entity killed, DamageSource source,
                                        List<ItemStack> drops, long currentTick) {
        // 检查是否穿戴该护腿
        if (!isWearing(player)) {
            return false;
        }

        // PVP 排除
        if (killed instanceof ServerPlayerEntity) {
            if (ArmorConfig.DEBUG) {
                LOGGER.info("[MoonTrace|Armor|TRIGGER] action=check result=BLOCKED reason=pvp_excluded ctx{{p={} t={}}}",
                        player.getName().getString(), killed.getName().getString());
            }
            return false;
        }

        // 检查冷却
        if (!CooldownManager.isReady(player.getUuid(), ArmorConfig.SMUGGLER_SHIN_EFFECT_ID, currentTick)) {
            if (ArmorConfig.DEBUG) {
                long cdLeft = CooldownManager.getRemainingTicks(player.getUuid(), ArmorConfig.SMUGGLER_SHIN_EFFECT_ID, currentTick);
                LOGGER.info("[MoonTrace|Armor|TRIGGER] action=check result=BLOCKED reason=cd_hit cd_total={} cd_left={} ctx{{p={}}}",
                        ArmorConfig.SMUGGLER_SHIN_COOLDOWN, cdLeft, player.getName().getString());
            }
            return false;
        }

        // 判断是否为 Boss 或包含核心资源
        boolean isBoss = isBoss(killed);
        boolean hasCoreLoot = containsCoreLoot(drops);
        boolean isSpecial = isBoss || hasCoreLoot;

        float lootBonusChance = isSpecial ? ArmorConfig.SMUGGLER_SHIN_LOOT_BONUS_CHANCE_BOSS : ArmorConfig.SMUGGLER_SHIN_LOOT_BONUS_CHANCE;
        float doubleLootChance = isSpecial ? ArmorConfig.SMUGGLER_SHIN_DOUBLE_LOOT_CHANCE_BOSS : ArmorConfig.SMUGGLER_SHIN_DOUBLE_LOOT_CHANCE;

        boolean triggered = false;

        // 尝试额外掉落
        float lootRoll = RANDOM.nextFloat();
        if (lootRoll < lootBonusChance) {
            // 触发额外掉落：复制一个掉落物并添加到世界
            if (!drops.isEmpty()) {
                ItemStack extraDrop = drops.get(RANDOM.nextInt(drops.size())).copy();
                spawnDropItem(player.getServerWorld(), killed, extraDrop);
                triggered = true;

                LOGGER.info("[MoonTrace|Armor|TRIGGER] action=trigger result=OK effect={} rng{{roll={} need={} hit=YES}} boss={} core={} ctx{{p={} t={}}}",
                        ArmorConfig.SMUGGLER_SHIN_EFFECT_ID, String.format("%.2f", lootRoll),
                        String.format("%.2f", lootBonusChance), isBoss, hasCoreLoot,
                        player.getName().getString(), killed.getType().getName().getString());
                LOGGER.info("[MoonTrace|Armor|APPLY] action=apply result=OK effect=extra_loot_roll item={} ctx{{p={}}}",
                        extraDrop.getItem().getName().getString(), player.getName().getString());
            }
        }

        // 尝试双倍掉落
        float doubleRoll = RANDOM.nextFloat();
        if (doubleRoll < doubleLootChance && !drops.isEmpty()) {
            ItemStack doubleDrop = drops.get(RANDOM.nextInt(drops.size())).copy();
            spawnDropItem(player.getServerWorld(), killed, doubleDrop);
            triggered = true;

            LOGGER.info("[MoonTrace|Armor|TRIGGER] action=trigger result=OK effect=smuggler_double_drop rng{{roll={} need={} hit=YES}} boss={} core={} ctx{{p={} t={}}}",
                    String.format("%.2f", doubleRoll), String.format("%.2f", doubleLootChance),
                    isBoss, hasCoreLoot, player.getName().getString(), killed.getType().getName().getString());
            LOGGER.info("[MoonTrace|Armor|APPLY] action=apply result=OK effect=double_drop item={} ctx{{p={}}}",
                    doubleDrop.getItem().getName().getString(), player.getName().getString());
        }

        // 如果任一效果触发，进入冷却
        if (triggered) {
            CooldownManager.setCooldown(player.getUuid(), ArmorConfig.SMUGGLER_SHIN_EFFECT_ID, currentTick, ArmorConfig.SMUGGLER_SHIN_COOLDOWN);
        }

        return triggered;
    }

    /**
     * 在实体位置生成掉落物
     */
    private static void spawnDropItem(ServerWorld world, Entity entity, ItemStack stack) {
        if (stack.isEmpty()) return;
        ItemEntity itemEntity = new ItemEntity(world, entity.getX(), entity.getY(), entity.getZ(), stack);
        itemEntity.setVelocity(
                (RANDOM.nextFloat() - 0.5f) * 0.1,
                0.2,
                (RANDOM.nextFloat() - 0.5f) * 0.1
        );
        world.spawnEntity(itemEntity);
    }

    /**
     * 判断实体是否为 Boss
     */
    public static boolean isBoss(Entity entity) {
        if (entity == null) return false;
        return BOSS_ENTITIES.contains(entity.getType());
    }

    /**
     * 判断掉落物是否包含核心资源（在 mymodtest:core_loot tag 中）
     */
    private static boolean containsCoreLoot(List<ItemStack> drops) {
        for (ItemStack stack : drops) {
            if (!stack.isEmpty() && stack.isIn(ModTags.Items.CORE_LOOT)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isWearing(ServerPlayerEntity player) {
        return player.getEquippedStack(EquipmentSlot.LEGS).isOf(ArmorItems.SMUGGLER_SHIN_LEGGINGS);
    }
}
