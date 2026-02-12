package dev.xqanzd.moonlitbroker.armor.effect;

import dev.xqanzd.moonlitbroker.armor.ArmorConfig;
import dev.xqanzd.moonlitbroker.armor.item.ArmorItems;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 流血契约之胸铠 - 受击"血契储能" → 强化下一击
 *
 * 机制：
 * - 受击时 50% 概率触发血契
 * - 触发时额外扣血：min(本次伤害 × 50%, 2♥)
 * - 储能池上限：4♥ 等价伤害（8.0 damage）
 * - 下一击窗口：10s（超时清空）
 * - 玩家下一次有效近战命中消耗储能，转为额外物理伤害
 */
public class BloodPactHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("MoonTrace");
    private static final Random RANDOM = new Random();

    /** 玩家血契池 */
    private static final Map<UUID, Float> bloodPools = new ConcurrentHashMap<>();

    /** 玩家血契池过期 tick */
    private static final Map<UUID, Long> poolExpireTicks = new ConcurrentHashMap<>();

    /** 用于防止递归触发的标记 */
    private static final ThreadLocal<Boolean> processingBloodPact = ThreadLocal.withInitial(() -> false);

    /**
     * 受击时调用（由 ArmorDamageMixin 触发）
     *
     * @param player      受伤玩家
     * @param source      伤害来源
     * @param amount      原始伤害值
     * @param currentTick 当前 tick
     * @return 修改后的伤害值（可能包含额外血契扣血）
     */
    public static float onDamage(ServerPlayerEntity player, DamageSource source, float amount, long currentTick) {
        // 防止递归
        if (processingBloodPact.get()) {
            return amount;
        }

        // 检查是否穿戴该胸甲
        if (!isWearing(player)) {
            return amount;
        }

        // 验证伤害来源
        if (!isValidSource(source)) {
            if (ArmorConfig.DEBUG) {
                LOGGER.info("[MoonTrace|Armor|TRIGGER] action=check result=BLOCKED reason=invalid_damage_source ctx{{p={} src={}}}",
                        player.getName().getString(), source.getName());
            }
            return amount;
        }

        // RNG 检查
        float roll = RANDOM.nextFloat();
        if (roll >= ArmorConfig.BLOOD_PACT_CHARGE_CHANCE) {
            if (ArmorConfig.DEBUG) {
                LOGGER.info("[MoonTrace|Armor|TRIGGER] action=check result=BLOCKED reason=rng_fail rng{{roll={} need={} hit=NO}} ctx{{p={}}}",
                        String.format("%.2f", roll), ArmorConfig.BLOOD_PACT_CHARGE_CHANCE, player.getName().getString());
            }
            return amount;
        }

        // 计算额外扣血
        float extraDamage = Math.min(amount * ArmorConfig.BLOOD_PACT_EXTRA_DAMAGE_RATIO, ArmorConfig.BLOOD_PACT_EXTRA_DAMAGE_CAP);

        // 累加到血契池
        float currentPool = bloodPools.getOrDefault(player.getUuid(), 0f);
        float newPool = Math.min(currentPool + extraDamage, ArmorConfig.BLOOD_PACT_POOL_CAP);
        bloodPools.put(player.getUuid(), newPool);

        // 设置过期时间
        poolExpireTicks.put(player.getUuid(), currentTick + ArmorConfig.BLOOD_PACT_POOL_WINDOW);

        // 日志
        Entity attacker = source.getAttacker();
        String attackerName = attacker != null ? attacker.getType().getName().getString() : "unknown";
        LOGGER.info("[MoonTrace|Armor|TRIGGER] action=trigger result=OK effect={} rng{{roll={} need={} hit=YES}} ctx{{p={} t={}}}",
                ArmorConfig.BLOOD_PACT_EFFECT_ID, String.format("%.2f", roll), ArmorConfig.BLOOD_PACT_CHARGE_CHANCE,
                player.getName().getString(), attackerName);
        LOGGER.info("[MoonTrace|Armor|APPLY] action=apply result=OK effect=blood_charge extra_dmg={} pool{{before={} after={} cap={}}} ctx{{p={}}}",
                String.format("%.1f", extraDamage), String.format("%.1f", currentPool),
                String.format("%.1f", newPool), String.format("%.1f", ArmorConfig.BLOOD_PACT_POOL_CAP),
                player.getName().getString());

        // 返回修改后的伤害（额外扣血通过增加 amount 实现，避免递归）
        return amount + extraDamage;
    }

    /**
     * 玩家攻击时调用（由 PlayerAttackMixin 触发）
     *
     * @param player      攻击者
     * @param target      攻击目标
     * @param currentTick 当前 tick
     * @return 血契池中的额外伤害（0 表示无池或已过期）
     */
    public static float onAttack(ServerPlayerEntity player, Entity target, long currentTick) {
        // 检查是否穿戴该胸甲
        if (!isWearing(player)) {
            return 0;
        }

        // 检查目标是否为 LivingEntity
        if (!(target instanceof LivingEntity)) {
            return 0;
        }

        // 获取血契池
        Float pool = bloodPools.get(player.getUuid());
        if (pool == null || pool <= 0) {
            return 0;
        }

        // 检查是否过期
        Long expireTick = poolExpireTicks.get(player.getUuid());
        if (expireTick == null || currentTick >= expireTick) {
            // 过期，清空池
            bloodPools.remove(player.getUuid());
            poolExpireTicks.remove(player.getUuid());
            if (ArmorConfig.DEBUG) {
                LOGGER.info("[MoonTrace|Armor|TRIGGER] action=check result=BLOCKED reason=pool_expired ctx{{p={}}}",
                        player.getName().getString());
            }
            return 0;
        }

        // 消耗池并返回伤害
        float bonusDamage = pool;
        bloodPools.remove(player.getUuid());
        poolExpireTicks.remove(player.getUuid());

        // 日志
        LOGGER.info("[MoonTrace|Armor|TRIGGER] action=trigger result=OK effect=blood_pact_release ctx{{p={} t={} pool={}}}",
                player.getName().getString(), target.getType().getName().getString(), String.format("%.1f", bonusDamage));
        LOGGER.info("[MoonTrace|Armor|APPLY] action=apply result=OK effect=bonus_damage final{{amount={}}} pool_cleared=true ctx{{p={}}}",
                String.format("%.1f", bonusDamage), player.getName().getString());

        return bonusDamage;
    }

    /**
     * 验证伤害来源是否有效
     * 必须：attacker 是 LivingEntity
     * 排除：爆炸、火焰、魔法、虚空、持续伤害
     */
    private static boolean isValidSource(DamageSource source) {
        Entity attacker = source.getAttacker();
        if (!(attacker instanceof LivingEntity)) {
            return false;
        }

        // 排除非直接伤害
        if (source.isIn(net.minecraft.registry.tag.DamageTypeTags.IS_EXPLOSION)) {
            return false;
        }
        if (source.isIn(net.minecraft.registry.tag.DamageTypeTags.IS_FIRE)) {
            return false;
        }
        if (source.getName().equals("magic") || source.getName().equals("indirectMagic")) {
            return false;
        }
        if (source.isIn(net.minecraft.registry.tag.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }

        return true;
    }

    private static boolean isWearing(ServerPlayerEntity player) {
        return player.getEquippedStack(EquipmentSlot.CHEST).isOf(ArmorItems.BLOOD_PACT_CHESTPLATE);
    }

    /**
     * 玩家下线时清理
     */
    public static void onPlayerLogout(ServerPlayerEntity player) {
        bloodPools.remove(player.getUuid());
        poolExpireTicks.remove(player.getUuid());
    }

    /**
     * 获取当前血契池值（用于调试/显示）
     */
    public static float getCurrentPool(UUID playerId) {
        return bloodPools.getOrDefault(playerId, 0f);
    }
}
