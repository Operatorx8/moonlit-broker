package dev.xqanzd.moonlitbroker.armor.effect;

import dev.xqanzd.moonlitbroker.armor.ArmorConfig;
import dev.xqanzd.moonlitbroker.armor.item.ArmorItems;
import dev.xqanzd.moonlitbroker.armor.util.CooldownManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * 虚空之噬 - 低频"虚空咬合"真实伤害
 *
 * 机制：
 * - 对任意目标造成攻击伤害时：附加 4% 真实伤害
 * - CD：5s（100 ticks）
 * - Boss：仅附加 2% 真实伤害
 */
public class VoidDevourerHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("MoonTrace");

    /** Boss 实体类型集合 */
    private static final Set<EntityType<?>> BOSS_ENTITIES = Set.of(
            EntityType.ENDER_DRAGON,
            EntityType.WITHER,
            EntityType.ELDER_GUARDIAN,
            EntityType.WARDEN
    );

    /**
     * 玩家攻击时调用（由 PlayerAttackMixin 触发）
     *
     * @param player      攻击者
     * @param target      攻击目标
     * @param baseDamage  本次攻击的基础伤害
     * @param currentTick 当前 tick
     * @return 应该附加的真实伤害（0 表示未触发）
     */
    public static float onAttack(ServerPlayerEntity player, Entity target, float baseDamage, long currentTick) {
        // 检查是否穿戴该胸甲
        if (!isWearing(player)) {
            return 0;
        }

        // 检查目标是否为 LivingEntity
        if (!(target instanceof LivingEntity livingTarget)) {
            if (ArmorConfig.DEBUG) {
                LOGGER.info("[MoonTrace|Armor|TRIGGER] action=check result=BLOCKED reason=target_not_living ctx{{p={} t={}}}",
                        player.getName().getString(), target.getType().getName().getString());
            }
            return 0;
        }

        // 检查冷却
        if (!CooldownManager.isReady(player.getUuid(), ArmorConfig.VOID_DEVOURER_EFFECT_ID, currentTick)) {
            if (ArmorConfig.DEBUG) {
                long cdLeft = CooldownManager.getRemainingTicks(player.getUuid(), ArmorConfig.VOID_DEVOURER_EFFECT_ID, currentTick);
                LOGGER.info("[MoonTrace|Armor|TRIGGER] action=check result=BLOCKED reason=cd_hit cd_total={} cd_left={} ctx{{p={}}}",
                        ArmorConfig.VOID_DEVOURER_COOLDOWN, cdLeft, player.getName().getString());
            }
            return 0;
        }

        // 确定真伤比例
        boolean isBoss = isBoss(target);
        float ratio = isBoss ? ArmorConfig.VOID_DEVOURER_TRUE_DAMAGE_RATIO_BOSS : ArmorConfig.VOID_DEVOURER_TRUE_DAMAGE_RATIO;

        // 计算真实伤害
        float trueDamage = baseDamage * ratio;

        // 进入冷却
        CooldownManager.setCooldown(player.getUuid(), ArmorConfig.VOID_DEVOURER_EFFECT_ID, currentTick, ArmorConfig.VOID_DEVOURER_COOLDOWN);

        // 日志
        String bossModifier = isBoss ? " boss_modifier=true" : "";
        LOGGER.info("[MoonTrace|Armor|TRIGGER] action=trigger result=OK effect={}{} ctx{{p={} t={} base_dmg={}}}",
                ArmorConfig.VOID_DEVOURER_EFFECT_ID, bossModifier,
                player.getName().getString(), target.getType().getName().getString(), String.format("%.1f", baseDamage));
        LOGGER.info("[MoonTrace|Armor|APPLY] action=apply result=OK effect=true_damage final{{amount={} ratio={}}} ctx{{p={} t={}}}",
                String.format("%.2f", trueDamage), ratio, player.getName().getString(), target.getType().getName().getString());

        return trueDamage;
    }

    /**
     * 对目标造成真实伤害
     * 使用绕过护甲的魔法伤害源
     */
    public static void applyTrueDamage(ServerPlayerEntity player, LivingEntity target, float trueDamage) {
        if (trueDamage <= 0) return;

        // 使用魔法伤害源（绕过护甲）
        DamageSource magicSource = player.getDamageSources().magic();
        target.damage(magicSource, trueDamage);
    }

    /**
     * 判断实体是否为 Boss
     */
    public static boolean isBoss(Entity entity) {
        if (entity == null) return false;
        return BOSS_ENTITIES.contains(entity.getType());
    }

    private static boolean isWearing(ServerPlayerEntity player) {
        return player.getEquippedStack(EquipmentSlot.CHEST).isOf(ArmorItems.VOID_DEVOURER_CHESTPLATE);
    }
}
