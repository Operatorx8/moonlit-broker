package mod.test.mymodtest.armor.effect;

import mod.test.mymodtest.armor.ArmorConfig;
import mod.test.mymodtest.armor.item.ArmorItems;
import mod.test.mymodtest.armor.util.CooldownManager;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 回溯者的额饰 - 爆炸致死保护（第二条命）
 *
 * 机制：
 * - 仅对爆炸伤害生效
 * - 仅在会致死时触发 (damage >= currentHealth)
 * - 玩家持有图腾时不触发（图腾优先）
 * - 阻止死亡，设置血量为 1 心 (2.0 HP)
 * - 给予短暂保护 2s (Resistance V)
 * - 播放独特音效/粒子
 * - 冷却：15min (18000 ticks)
 */
public class RetracerHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("MoonTrace");

    /**
     * 在伤害事件中调用
     * @param player 受伤玩家
     * @param source 伤害来源
     * @param amount 原始伤害值
     * @param currentTick 当前 tick
     * @return 修改后的伤害值（如果触发保护则返回 0 并设置血量）
     */
    public static float onDamage(ServerPlayerEntity player, DamageSource source, float amount, long currentTick) {
        // 检查是否穿戴该头盔
        if (!isWearing(player)) {
            return amount;
        }

        // 检查是否为爆炸伤害
        if (!isExplosionDamage(source)) {
            if (ArmorConfig.DEBUG) {
                LOGGER.info("[MoonTrace|Armor|TRIGGER] action=check result=BLOCKED reason=damage_not_explosion dmg_type={} ctx{{p={}}}",
                        source.getType().msgId(), player.getName().getString());
            }
            return amount;
        }

        // 检查是否会致死
        float currentHealth = player.getHealth();
        if (amount < currentHealth) {
            // 不会致死，不触发
            return amount;
        }

        // 检查玩家是否持有图腾（图腾优先）
        if (hasTotem(player)) {
            if (ArmorConfig.DEBUG) {
                LOGGER.info("[MoonTrace|Armor|TRIGGER] action=check result=BLOCKED reason=totem_active ctx{{p={}}}",
                        player.getName().getString());
            }
            return amount;  // 让图腾处理
        }

        // 检查冷却
        if (!CooldownManager.isReady(player.getUuid(), ArmorConfig.RETRACER_EFFECT_ID, currentTick)) {
            if (ArmorConfig.DEBUG) {
                long cdLeft = CooldownManager.getRemainingTicks(player.getUuid(), ArmorConfig.RETRACER_EFFECT_ID, currentTick);
                LOGGER.info("[MoonTrace|Armor|TRIGGER] action=check result=BLOCKED reason=cd_hit cd_total={} cd_left={} ctx{{p={}}}",
                        ArmorConfig.RETRACER_COOLDOWN, cdLeft, player.getName().getString());
            }
            return amount;  // CD 中，正常死亡
        }

        // ===== 触发保护 =====

        // 进入冷却
        CooldownManager.setCooldown(player.getUuid(), ArmorConfig.RETRACER_EFFECT_ID, currentTick, ArmorConfig.RETRACER_COOLDOWN);

        // 设置血量为 1 心
        player.setHealth(ArmorConfig.RETRACER_HEALTH_SET);

        // 给予 Resistance V（2秒无敌）
        player.addStatusEffect(new StatusEffectInstance(
                StatusEffects.RESISTANCE,
                ArmorConfig.RETRACER_RESISTANCE_DURATION,
                ArmorConfig.RETRACER_RESISTANCE_AMPLIFIER,
                false,  // ambient
                true,   // showParticles
                true    // showIcon
        ));

        // 播放音效
        ServerWorld world = player.getServerWorld();
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ITEM_TOTEM_USE, SoundCategory.PLAYERS, 1.0f, 1.0f);

        // 播放粒子效果（复用图腾粒子）
        world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING,
                player.getX(), player.getY() + 1.0, player.getZ(),
                50,  // count
                0.5, 0.5, 0.5,  // offset
                0.5  // speed
        );

        // 日志
        String dmgSrc = source.getAttacker() != null ?
                source.getAttacker().getType().getName().getString() : "unknown";
        LOGGER.info("[MoonTrace|Armor|TRIGGER] action=trigger result=OK effect={} ctx{{p={} dmg_src={} dmg_type=explosion}}",
                ArmorConfig.RETRACER_EFFECT_ID, player.getName().getString(), dmgSrc);
        LOGGER.info("[MoonTrace|Armor|APPLY] action=apply result=OK effect=death_prevention final{{health_set={}}} ctx{{p={}}}",
                ArmorConfig.RETRACER_HEALTH_SET, player.getName().getString());
        LOGGER.info("[MoonTrace|Armor|APPLY] action=apply result=OK effect=resistance final{{dur={} amp={}}} ctx{{p={}}}",
                ArmorConfig.RETRACER_RESISTANCE_DURATION, ArmorConfig.RETRACER_RESISTANCE_AMPLIFIER, player.getName().getString());

        // 返回 0 伤害（伤害已被吸收）
        return 0;
    }

    /**
     * 判断是否为爆炸伤害
     */
    private static boolean isExplosionDamage(DamageSource source) {
        // 检查伤害类型
        if (source.isOf(DamageTypes.EXPLOSION)) return true;
        if (source.isOf(DamageTypes.PLAYER_EXPLOSION)) return true;

        // 备用：检查消息 ID
        String msgId = source.getType().msgId();
        return msgId.contains("explosion");
    }

    /**
     * 检查玩家是否持有不死图腾
     */
    private static boolean hasTotem(ServerPlayerEntity player) {
        return player.getMainHandStack().isOf(Items.TOTEM_OF_UNDYING)
                || player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING);
    }

    private static boolean isWearing(ServerPlayerEntity player) {
        return player.getEquippedStack(EquipmentSlot.HEAD).isOf(ArmorItems.RETRACER_ORNAMENT_HELMET);
    }
}
