package mod.test.mymodtest.armor.effect;

import mod.test.mymodtest.armor.ArmorConfig;
import mod.test.mymodtest.armor.item.ArmorItems;
import mod.test.mymodtest.armor.util.CooldownManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.Angerable;
import net.minecraft.entity.mob.Monster;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 沉默之誓约 - 冷却窗口内首次受伤减伤（仅怪物伤害）
 *
 * 机制：
 * - 仅对敌对生物伤害生效
 * - 原始伤害 >= 2 点才生效
 * - 减免 2 点伤害
 * - 冷却：30s (600 ticks)
 */
public class SilentOathHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("MoonTrace");

    /**
     * 在伤害事件中调用
     * @param player 受伤玩家
     * @param source 伤害来源
     * @param amount 原始伤害值
     * @param currentTick 当前 tick
     * @return 修改后的伤害值
     */
    public static float onDamage(ServerPlayerEntity player, DamageSource source, float amount, long currentTick) {
        // 检查是否穿戴该头盔
        if (!isWearing(player)) {
            return amount;
        }

        // 检查伤害来源是否为敌对生物
        Entity attacker = source.getAttacker();
        if (!isHostileMob(attacker)) {
            if (ArmorConfig.DEBUG) {
                LOGGER.info("[MoonTrace|Armor|TRIGGER] action=check result=BLOCKED reason=damage_not_hostile ctx{{p={} src={}}}",
                        player.getName().getString(), attacker != null ? attacker.getType().getName().getString() : "null");
            }
            return amount;
        }

        // 检查伤害阈值
        if (amount < ArmorConfig.SILENT_OATH_MIN_DAMAGE) {
            if (ArmorConfig.DEBUG) {
                LOGGER.info("[MoonTrace|Armor|TRIGGER] action=check result=BLOCKED reason=damage_too_low damage={} threshold={} ctx{{p={}}}",
                        amount, ArmorConfig.SILENT_OATH_MIN_DAMAGE, player.getName().getString());
            }
            return amount;
        }

        // 检查冷却
        if (!CooldownManager.isReady(player.getUuid(), ArmorConfig.SILENT_OATH_EFFECT_ID, currentTick)) {
            if (ArmorConfig.DEBUG) {
                long cdLeft = CooldownManager.getRemainingTicks(player.getUuid(), ArmorConfig.SILENT_OATH_EFFECT_ID, currentTick);
                LOGGER.info("[MoonTrace|Armor|TRIGGER] action=check result=BLOCKED reason=cd_hit cd_total={} cd_left={} ctx{{p={}}}",
                        ArmorConfig.SILENT_OATH_COOLDOWN, cdLeft, player.getName().getString());
            }
            return amount;
        }

        // 应用减伤
        float reduction = ArmorConfig.SILENT_OATH_REDUCTION;
        float finalDamage = Math.max(0, amount - reduction);

        // 进入冷却
        CooldownManager.setCooldown(player.getUuid(), ArmorConfig.SILENT_OATH_EFFECT_ID, currentTick, ArmorConfig.SILENT_OATH_COOLDOWN);

        // 日志
        LOGGER.info("[MoonTrace|Armor|TRIGGER] action=trigger result=OK effect={} ctx{{p={} t={}}}",
                ArmorConfig.SILENT_OATH_EFFECT_ID, player.getName().getString(),
                attacker != null ? attacker.getType().getName().getString() : "unknown");
        LOGGER.info("[MoonTrace|Armor|APPLY] action=damage_modify result=OK final{{amount={}}} src{{original={}}} reduction={} ctx{{p={}}}",
                finalDamage, amount, reduction, player.getName().getString());

        return finalDamage;
    }

    /**
     * 判断实体是否为敌对生物（包括被激怒的中立生物）
     */
    private static boolean isHostileMob(Entity entity) {
        if (entity == null) return false;

        // 原生敌对生物
        if (entity instanceof Monster) return true;

        // 被激怒的中立生物（末影人、猪灵、僵尸猪人等）
        if (entity instanceof Angerable angerable) {
            return angerable.getAngryAt() != null;
        }

        return false;
    }

    private static boolean isWearing(ServerPlayerEntity player) {
        return player.getEquippedStack(EquipmentSlot.HEAD).isOf(ArmorItems.SILENT_OATH_HELMET);
    }
}
