package mod.test.mymodtest.armor.transitional.effect;

import mod.test.mymodtest.armor.transitional.TransitionalArmorConstants;
import mod.test.mymodtest.armor.transitional.TransitionalArmorItems;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 缓冲登山靴 - 摔落减伤 flat -2.0
 *
 * 机制：
 * - 穿戴于 FEET 槽位时，最终摔落伤害 flat -2.0，clamp >= 0
 * - 在 applyDamage 链路处理，此时伤害已过护甲、附魔、抗性计算
 * - 与 Feather Falling 可叠加（FF 先削，本效果再减）
 * - SERVER_ONLY, FEET_ONLY
 */
public final class CushionHikingBootsHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger("MoonTrace");

    private CushionHikingBootsHandler() {}

    /**
     * 处理摔落伤害事件
     *
     * @param player 受伤玩家
     * @param source 伤害来源
     * @param amount 当前伤害值（已过护甲、附魔计算）
     * @return 修改后的伤害值
     */
    public static float onDamage(ServerPlayerEntity player, DamageSource source, float amount) {
        // 检查是否穿戴缓冲登山靴
        if (!isWearing(player)) {
            return amount;
        }

        // 检查是否为摔落伤害
        if (!source.isOf(DamageTypes.FALL)) {
            return amount;
        }

        // 应用减伤
        float reduction = TransitionalArmorConstants.CUSHION_FALL_FLAT_REDUCTION;
        float finalDamage = Math.max(amount - reduction, TransitionalArmorConstants.CUSHION_FALL_CLAMP_MIN);

        if (TransitionalArmorConstants.DEBUG) {
            String damageTypeId = source.getTypeRegistryEntry().getKey()
                    .map(key -> key.getValue().toString())
                    .orElse("unknown");
            LOGGER.info("[TransArmor] item=cushion_hiking_boots player={} damageTypeId={} raw={} out={}",
                    player.getUuidAsString().substring(0, 8),
                    damageTypeId,
                    String.format("%.1f", amount),
                    String.format("%.1f", finalDamage));
        }

        return finalDamage;
    }

    /**
     * 检查是否穿戴缓冲登山靴
     */
    private static boolean isWearing(ServerPlayerEntity player) {
        return player.getEquippedStack(EquipmentSlot.FEET).isOf(TransitionalArmorItems.CUSHION_HIKING_BOOTS);
    }
}
