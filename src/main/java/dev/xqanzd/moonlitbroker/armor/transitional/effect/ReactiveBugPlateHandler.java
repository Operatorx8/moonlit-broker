package dev.xqanzd.moonlitbroker.armor.transitional.effect;

import dev.xqanzd.moonlitbroker.armor.transitional.TransitionalArmorConstants;
import dev.xqanzd.moonlitbroker.armor.transitional.TransitionalArmorItems;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * 反应Bug装甲板 - 节肢类近战减伤 flat -1.0
 *
 * 机制：
 * - 穿戴于胸部槽位时，受到节肢类实体的近战物理伤害 -1.0（clamp >= 0）
 * - 节肢类定义：Spider, Cave Spider, Endermite, Silverfish, Bee
 * - 近战物理定义：排除 projectile、magic、thorns、fire、explosion
 * - 仅服务端计算
 */
public final class ReactiveBugPlateHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger("MoonTrace");

    /**
     * 节肢类实体类型集合
     * 在 1.21.1 中 EntityGroup 已移除，使用 EntityType 直接判定
     */
    private static final Set<EntityType<?>> ARTHROPOD_TYPES = Set.of(
            EntityType.SPIDER,
            EntityType.CAVE_SPIDER,
            EntityType.ENDERMITE,
            EntityType.SILVERFISH,
            EntityType.BEE
    );

    private ReactiveBugPlateHandler() {}

    /**
     * 处理伤害事件
     *
     * @param player 受伤玩家
     * @param source 伤害来源
     * @param amount 原始伤害值
     * @return 修改后的伤害值
     */
    public static float onDamage(ServerPlayerEntity player, DamageSource source, float amount) {
        // 检查是否穿戴反应Bug装甲板
        if (!isWearing(player)) {
            return amount;
        }

        // 获取攻击者
        Entity attacker = source.getAttacker();
        if (!(attacker instanceof LivingEntity livingAttacker)) {
            return amount;
        }

        // 检查攻击者是否为节肢类
        if (!isArthropod(livingAttacker)) {
            return amount;
        }

        // 检查是否为近战物理伤害
        if (!isMeleeDamage(source)) {
            return amount;
        }

        // 应用减伤
        float reduction = TransitionalArmorConstants.REACTIVE_BUG_FLAT_REDUCTION;
        float finalDamage = Math.max(amount - reduction, TransitionalArmorConstants.REACTIVE_BUG_CLAMP_MIN);

        if (TransitionalArmorConstants.DEBUG) {
            Entity directEntity = source.getSource();
            LOGGER.info("[TransArmor] item=reactive_bug_plate player={} damageTypeId={} raw={} out={} attacker={} directEntity={} dim={}",
                    player.getUuidAsString().substring(0, 8),
                    getDamageTypeId(source),
                    String.format("%.1f", amount),
                    String.format("%.1f", finalDamage),
                    entityDebugName(attacker),
                    entityDebugName(directEntity),
                    player.getWorld().getRegistryKey().getValue().getPath());
        }

        return finalDamage;
    }

    /**
     * 检查实体是否为节肢类
     */
    private static boolean isArthropod(LivingEntity entity) {
        return ARTHROPOD_TYPES.contains(entity.getType());
    }

    /**
     * 检查是否为近战物理伤害
     * 排除：projectile、magic、thorns、fire、explosion
     */
    private static boolean isMeleeDamage(DamageSource source) {
        // 排除投射物
        if (source.isIn(DamageTypeTags.IS_PROJECTILE)) {
            return false;
        }

        // 排除爆炸
        if (source.isIn(DamageTypeTags.IS_EXPLOSION)) {
            return false;
        }

        // 排除火焰
        if (source.isIn(DamageTypeTags.IS_FIRE)) {
            return false;
        }

        // 排除摔落
        if (source.isIn(DamageTypeTags.IS_FALL)) {
            return false;
        }

        // 排除溺水
        if (source.isIn(DamageTypeTags.IS_DROWNING)) {
            return false;
        }

        // 排除冰冻
        if (source.isIn(DamageTypeTags.IS_FREEZING)) {
            return false;
        }

        // 显式排除 magic / indirect_magic / thorns
        if (isDamageType(source, "magic")
                || isDamageType(source, "indirect_magic")
                || isDamageType(source, "thorns")) {
            return false;
        }

        // 仅允许直接近战：direct entity == attacker
        Entity attacker = source.getAttacker();
        Entity directEntity = source.getSource();
        if (attacker == null || directEntity == null) {
            return false;
        }
        return attacker == directEntity;
    }

    private static boolean isDamageType(DamageSource source, String path) {
        return source.getTypeRegistryEntry().getKey()
                .map(key -> key.getValue().equals(Identifier.of("minecraft", path)))
                .orElse(false);
    }

    private static String getDamageTypeId(DamageSource source) {
        return source.getTypeRegistryEntry().getKey()
                .map(key -> key.getValue().toString())
                .orElse("unknown");
    }

    private static String entityDebugName(Entity entity) {
        if (entity == null) {
            return "null";
        }
        return entity.getType().getUntranslatedName();
    }

    /**
     * 检查是否穿戴反应Bug装甲板
     */
    private static boolean isWearing(ServerPlayerEntity player) {
        return player.getEquippedStack(EquipmentSlot.CHEST).isOf(TransitionalArmorItems.REACTIVE_BUG_PLATE);
    }
}
