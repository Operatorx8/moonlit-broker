package dev.xqanzd.moonlitbroker.armor.effect;

import dev.xqanzd.moonlitbroker.armor.ArmorConfig;
import dev.xqanzd.moonlitbroker.armor.item.ArmorItems;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.AbstractSkeletonEntity;
import net.minecraft.entity.mob.PhantomEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.mob.ZombifiedPiglinEntity;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;
import java.util.Set;

/**
 * 鬼神之铠 - 对亡灵的"减伤 + 负面抗性"
 *
 * 机制：
 * - 亡灵伤害减免：30% 概率使该次伤害 -15%（Boss 15% 概率）
 * - 亡灵 Debuff 免疫：50% 概率免疫 Wither/Hunger/Slowness（Boss 25%）
 */
public class GhostGodHandler {
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
     * 受击时调用（由 ArmorDamageMixin 触发）
     *
     * @param player      受伤玩家
     * @param source      伤害来源
     * @param amount      原始伤害值
     * @param currentTick 当前 tick
     * @return 修改后的伤害值
     */
    public static float onDamage(ServerPlayerEntity player, DamageSource source, float amount, long currentTick) {
        // 检查是否穿戴该胸甲
        if (!isWearing(player)) {
            return amount;
        }

        // 检查攻击者是否为亡灵
        Entity attacker = source.getAttacker();
        if (!isUndead(attacker)) {
            if (ArmorConfig.DEBUG) {
                LOGGER.info("[MoonTrace|Armor|TRIGGER] action=check result=BLOCKED reason=not_undead ctx{{p={} src={}}}",
                        player.getName().getString(), attacker != null ? attacker.getType().getName().getString() : "null");
            }
            return amount;
        }

        // 确定概率（Boss 减半）
        boolean isBoss = isBoss(attacker);
        float chance = isBoss ? ArmorConfig.GHOST_GOD_DAMAGE_REDUCTION_CHANCE_BOSS : ArmorConfig.GHOST_GOD_DAMAGE_REDUCTION_CHANCE;

        // RNG 检查
        float roll = RANDOM.nextFloat();
        if (roll >= chance) {
            if (ArmorConfig.DEBUG) {
                LOGGER.info("[MoonTrace|Armor|TRIGGER] action=check result=BLOCKED reason=rng_fail rng{{roll={} need={} hit=NO}} ctx{{p={}}}",
                        String.format("%.2f", roll), chance, player.getName().getString());
            }
            return amount;
        }

        // 应用减伤
        float reducedAmount = amount * (1.0f - ArmorConfig.GHOST_GOD_DAMAGE_REDUCTION_AMOUNT);

        // 日志
        String attackerName = attacker != null ? attacker.getType().getName().getString() : "unknown";
        LOGGER.info("[MoonTrace|Armor|TRIGGER] action=trigger result=OK effect={} rng{{roll={} need={} hit=YES}} boss={} ctx{{p={} t={}}}",
                ArmorConfig.GHOST_GOD_DAMAGE_EFFECT_ID, String.format("%.2f", roll), chance, isBoss,
                player.getName().getString(), attackerName);
        LOGGER.info("[MoonTrace|Armor|APPLY] action=apply result=OK effect=damage_reduction final{{amount={}}} src{{original={}}} ctx{{p={}}}",
                String.format("%.2f", reducedAmount), String.format("%.2f", amount), player.getName().getString());

        return reducedAmount;
    }

    /**
     * 状态效果添加时调用（由 StatusEffectMixin 触发）
     *
     * @param player      被施加效果的玩家
     * @param effect      效果类型
     * @param source      效果来源实体（可为 null）
     * @param currentTick 当前 tick
     * @return true 表示允许添加效果，false 表示免疫（拒绝添加）
     */
    public static boolean onStatusEffect(ServerPlayerEntity player, RegistryEntry<StatusEffect> effect, Entity source, long currentTick) {
        // 检查是否穿戴该胸甲
        if (!isWearing(player)) {
            return true; // 允许
        }

        // 检查效果类型是否为目标 debuff
        if (!isTargetDebuff(effect)) {
            return true; // 允许
        }

        // 检查来源是否为亡灵
        if (!isUndead(source)) {
            if (ArmorConfig.DEBUG) {
                LOGGER.info("[MoonTrace|Armor|TRIGGER] action=check result=BLOCKED reason=debuff_not_from_undead effect={} ctx{{p={}}}",
                        effect.getIdAsString(), player.getName().getString());
            }
            return true; // 允许
        }

        // 确定概率（Boss 减半）
        boolean isBoss = isBoss(source);
        float chance = isBoss ? ArmorConfig.GHOST_GOD_DEBUFF_IMMUNE_CHANCE_BOSS : ArmorConfig.GHOST_GOD_DEBUFF_IMMUNE_CHANCE;

        // RNG 检查
        float roll = RANDOM.nextFloat();
        if (roll >= chance) {
            if (ArmorConfig.DEBUG) {
                LOGGER.info("[MoonTrace|Armor|TRIGGER] action=check result=BLOCKED reason=rng_fail rng{{roll={} need={} hit=NO}} effect={} ctx{{p={}}}",
                        String.format("%.2f", roll), chance, effect.getIdAsString(), player.getName().getString());
            }
            return true; // 允许
        }

        // 免疫！
        String sourceName = source != null ? source.getType().getName().getString() : "unknown";
        LOGGER.info("[MoonTrace|Armor|TRIGGER] action=trigger result=OK effect={} rng{{roll={} need={} hit=YES}} debuff={} boss={} ctx{{p={} t={}}}",
                ArmorConfig.GHOST_GOD_DEBUFF_EFFECT_ID, String.format("%.2f", roll), chance, effect.getIdAsString(),
                isBoss, player.getName().getString(), sourceName);
        LOGGER.info("[MoonTrace|Armor|APPLY] action=apply result=OK effect=debuff_blocked debuff={} ctx{{p={}}}",
                effect.getIdAsString(), player.getName().getString());

        return false; // 拒绝添加（免疫）
    }

    /**
     * 判断实体是否为亡灵
     */
    public static boolean isUndead(Entity entity) {
        if (entity == null) return false;
        // 检查常见亡灵类型
        return entity instanceof ZombieEntity
                || entity instanceof AbstractSkeletonEntity
                || entity instanceof PhantomEntity
                || entity instanceof WitherEntity
                || entity instanceof ZombifiedPiglinEntity;
    }

    /**
     * 判断实体是否为 Boss
     */
    public static boolean isBoss(Entity entity) {
        if (entity == null) return false;
        return BOSS_ENTITIES.contains(entity.getType());
    }

    /**
     * 判断效果是否为目标 debuff（Wither/Hunger/Slowness）
     */
    private static boolean isTargetDebuff(RegistryEntry<StatusEffect> effect) {
        return effect.matches(StatusEffects.WITHER)
                || effect.matches(StatusEffects.HUNGER)
                || effect.matches(StatusEffects.SLOWNESS);
    }

    private static boolean isWearing(ServerPlayerEntity player) {
        return player.getEquippedStack(EquipmentSlot.CHEST).isOf(ArmorItems.GHOST_GOD_CHESTPLATE);
    }
}
