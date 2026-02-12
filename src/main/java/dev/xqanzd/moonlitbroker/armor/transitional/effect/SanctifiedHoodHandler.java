package dev.xqanzd.moonlitbroker.armor.transitional.effect;

import dev.xqanzd.moonlitbroker.armor.transitional.TransitionalArmorConstants;
import dev.xqanzd.moonlitbroker.armor.transitional.TransitionalArmorItems;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * 祝圣兜帽 - 魔法减伤 15%
 *
 * 机制：
 * - 穿戴于头部槽位时，受到的魔法类伤害 × 0.85
 * - 仅服务端计算
 * - damageType 白名单判定
 */
public final class SanctifiedHoodHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger("MoonTrace");

    private SanctifiedHoodHandler() {}

    /**
     * 魔法伤害类型白名单
     * 基于 1.21.1 Yarn mapping 的 DamageTypes
     */
    private static final Set<Identifier> MAGIC_DAMAGE_TYPES = Set.of(
            Identifier.of("minecraft", "magic"),           // 瞬间伤害药水
            Identifier.of("minecraft", "indirect_magic"),  // 女巫投掷药水、滞留药水
            Identifier.of("minecraft", "sonic_boom"),      // Warden 音波
            Identifier.of("minecraft", "wither")           // 凋零效果
    );

    /**
     * 处理伤害事件
     *
     * @param player 受伤玩家
     * @param source 伤害来源
     * @param amount 原始伤害值
     * @return 修改后的伤害值
     */
    public static float onDamage(ServerPlayerEntity player, DamageSource source, float amount) {
        // 检查是否穿戴祝圣兜帽
        if (!isWearing(player)) {
            return amount;
        }

        // 检查是否为魔法伤害
        if (!isMagicDamage(source)) {
            return amount;
        }

        // 应用减伤
        float finalDamage = amount * TransitionalArmorConstants.SANCTIFIED_MAGIC_REDUCTION_MULT;

        if (TransitionalArmorConstants.DEBUG) {
            Entity attacker = source.getAttacker();
            Entity directEntity = source.getSource();
            LOGGER.info("[TransArmor] item=sanctified_hood player={} damageTypeId={} raw={} out={} attacker={} directEntity={} dim={}",
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
     * 检查伤害是否为魔法类型
     */
    private static boolean isMagicDamage(DamageSource source) {
        // 获取伤害类型 ID
        return source.getTypeRegistryEntry().getKey()
                .map(key -> MAGIC_DAMAGE_TYPES.contains(key.getValue()))
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
     * 检查是否穿戴祝圣兜帽
     */
    private static boolean isWearing(ServerPlayerEntity player) {
        return player.getEquippedStack(EquipmentSlot.HEAD).isOf(TransitionalArmorItems.SANCTIFIED_HOOD);
    }
}
