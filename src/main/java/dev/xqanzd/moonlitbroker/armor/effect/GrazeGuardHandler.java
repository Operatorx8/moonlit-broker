package dev.xqanzd.moonlitbroker.armor.effect;

import dev.xqanzd.moonlitbroker.armor.ArmorConfig;
import dev.xqanzd.moonlitbroker.armor.item.ArmorItems;
import dev.xqanzd.moonlitbroker.armor.util.CooldownManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

/**
 * 擦身护胫 - 擦身减伤
 *
 * 机制：
 * - 触发源：玩家作为受害者的 damage 判定
 * - 触发概率：18%
 * - 减伤比例：60%（即本次伤害 ×0.40）
 * - 冷却：12s（240 ticks）
 * - 限制：仅 LivingEntity 直接攻击链路生效（含箭矢来源）
 */
public class GrazeGuardHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("MoonTrace");
    private static final Random RANDOM = new Random();

    /**
     * 在伤害事件中调用
     *
     * @param player      受伤玩家
     * @param source      伤害来源
     * @param amount      当前伤害值
     * @param currentTick 当前 tick
     * @return 修改后的伤害值
     */
    public static float onDamage(ServerPlayerEntity player, DamageSource source, float amount, long currentTick) {
        // 检查是否穿戴该护腿
        if (!isWearing(player)) {
            return amount;
        }

        // 检查伤害来源是否归属 LivingEntity（含箭矢来源）
        if (!isFromLivingEntity(source)) {
            if (ArmorConfig.DEBUG) {
                LOGGER.info("[MoonTrace|Armor|TRIGGER] action=check result=BLOCKED reason=not_living_attacker ctx{{p={} src={}}}",
                        player.getName().getString(), source.getName());
            }
            return amount;
        }

        // 检查冷却
        if (!CooldownManager.isReady(player.getUuid(), ArmorConfig.GRAZE_GUARD_EFFECT_ID, currentTick)) {
            if (ArmorConfig.DEBUG) {
                long cdLeft = CooldownManager.getRemainingTicks(player.getUuid(), ArmorConfig.GRAZE_GUARD_EFFECT_ID, currentTick);
                LOGGER.info("[MoonTrace|Armor|TRIGGER] action=check result=BLOCKED reason=cd_hit cd_total={} cd_left={} ctx{{p={}}}",
                        ArmorConfig.GRAZE_GUARD_COOLDOWN, cdLeft, player.getName().getString());
            }
            return amount;
        }

        // RNG 检查
        float roll = RANDOM.nextFloat();
        if (roll >= ArmorConfig.GRAZE_GUARD_TRIGGER_CHANCE) {
            if (ArmorConfig.DEBUG) {
                LOGGER.info("[MoonTrace|Armor|TRIGGER] action=rng_check result=BLOCKED rng{{roll={} need={} hit=NO}} ctx{{p={}}}",
                        String.format("%.2f", roll), ArmorConfig.GRAZE_GUARD_TRIGGER_CHANCE,
                        player.getName().getString());
            }
            return amount;
        }

        // 应用减伤
        float reduction = ArmorConfig.GRAZE_GUARD_REDUCTION;
        float finalDamage = amount * (1.0f - reduction);

        // 进入冷却
        CooldownManager.setCooldown(player.getUuid(), ArmorConfig.GRAZE_GUARD_EFFECT_ID, currentTick, ArmorConfig.GRAZE_GUARD_COOLDOWN);

        // 获取攻击者名称
        String attackerName = "unknown";
        Entity attacker = source.getAttacker();
        if (attacker != null) {
            attackerName = attacker.getType().getName().getString();
        }

        // 日志
        LOGGER.info("[MoonTrace|Armor|TRIGGER] action=trigger result=OK effect={} rng{{roll={} need={} hit=YES}} ctx{{p={} t={}}}",
                ArmorConfig.GRAZE_GUARD_EFFECT_ID, String.format("%.2f", roll),
                String.format("%.2f", ArmorConfig.GRAZE_GUARD_TRIGGER_CHANCE),
                player.getName().getString(), attackerName);
        LOGGER.info("[MoonTrace|Armor|APPLY] action=damage_modify result=OK effect={} final{{amount={}}} src{{original={}}} ctx{{p={}}}",
                ArmorConfig.GRAZE_GUARD_EFFECT_ID, String.format("%.1f", finalDamage),
                String.format("%.1f", amount), player.getName().getString());

        return finalDamage;
    }

    /**
     * 判断伤害是否来源于 LivingEntity（包括箭矢来源的射击者）
     */
    private static boolean isFromLivingEntity(DamageSource source) {
        // 直接攻击者
        Entity attacker = source.getAttacker();
        if (attacker instanceof LivingEntity) {
            return true;
        }

        // 检查投射物来源
        Entity sourceEntity = source.getSource();
        if (sourceEntity instanceof ProjectileEntity projectile) {
            Entity owner = projectile.getOwner();
            return owner instanceof LivingEntity;
        }

        return false;
    }

    private static boolean isWearing(ServerPlayerEntity player) {
        return player.getEquippedStack(EquipmentSlot.LEGS).isOf(ArmorItems.GRAZE_GUARD_LEGGINGS);
    }
}
