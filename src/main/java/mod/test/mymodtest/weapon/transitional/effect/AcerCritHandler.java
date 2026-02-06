package mod.test.mymodtest.weapon.transitional.effect;

import mod.test.mymodtest.weapon.transitional.TransitionalWeaponConstants;
import mod.test.mymodtest.weapon.transitional.item.TransitionalWeaponItems;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * Acer 暴击加成处理器
 *
 * 机制：
 * - 原版暴击乘数：1.5×
 * - Acer 暴击乘数：1.7×
 * - 额外加成：(1.7 - 1.5) / 1.5 = 13.33% 的暴击伤害提升
 */
public final class AcerCritHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger("MoonTrace");

    /** 排除的伤害源类型 */
    private static final Set<String> EXCLUDED_DAMAGE_TYPES = Set.of(
            "thorns",
            "magic",
            "indirectMagic",
            "onFire",
            "inFire",
            "lava",
            "arrow",
            "trident",
            "fireball",
            "witherSkull",
            "thrown"
    );

    private AcerCritHandler() {}

    /**
     * 计算 Acer 暴击加成后的伤害
     * 在暴击判定成立后调用
     *
     * @param player     攻击者
     * @param target     目标
     * @param baseDamage 基础伤害（暴击前）
     * @param isCrit     是否为暴击
     * @return 修改后的伤害值
     */
    public static float modifyCritDamage(ServerPlayerEntity player, Entity target, float baseDamage, boolean isCrit) {
        // 非暴击不处理
        if (!isCrit) {
            return baseDamage * 1.5f;  // 返回原版暴击伤害
        }

        // 检查主手是否为 Acer
        ItemStack mainHand = player.getMainHandStack();
        if (!mainHand.isOf(TransitionalWeaponItems.ACER)) {
            return baseDamage * 1.5f;
        }

        // 检查目标是否为 LivingEntity
        if (!(target instanceof LivingEntity)) {
            return baseDamage * 1.5f;
        }

        // 应用 Acer 暴击乘数
        float finalDamage = baseDamage * TransitionalWeaponConstants.ACER_CRIT_MULTIPLIER;
        float bonus = finalDamage - (baseDamage * 1.5f);

        if (TransitionalWeaponConstants.DEBUG) {
            LOGGER.info("[TransWeapon] item=acer player={} target={} isCrit=true base={} bonus={} final={} dim={}",
                    player.getUuidAsString().substring(0, 8),
                    target.getUuidAsString().substring(0, 8),
                    String.format("%.1f", baseDamage),
                    String.format("%.2f", bonus),
                    String.format("%.2f", finalDamage),
                    player.getWorld().getRegistryKey().getValue().getPath());
        }

        return finalDamage;
    }

    /**
     * 检查伤害源是否被排除
     */
    public static boolean isExcludedDamageSource(DamageSource source) {
        String typeName = source.getType().msgId();
        return EXCLUDED_DAMAGE_TYPES.contains(typeName);
    }

    /**
     * 检查玩家是否主手持有 Acer
     */
    public static boolean isHoldingAcer(ServerPlayerEntity player) {
        return player.getMainHandStack().isOf(TransitionalWeaponItems.ACER);
    }
}
