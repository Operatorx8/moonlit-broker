package dev.xqanzd.moonlitbroker.katana.effect;

import dev.xqanzd.moonlitbroker.katana.item.KatanaItems;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 暗月之蚀核心效果处理器
 *
 * 触发条件：40% 概率，每目标 2.5秒冷却
 * 效果：
 * - 施加月蚀标记（3秒，Boss减半）
 * - 随机施加 2 种 Debuff（强控互斥：darkness/blindness 最多一个）
 * - 护甲穿透仅用于展示：未标记 15%，标记 25%（不做额外伤害补偿）
 */
public class EclipseHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("Eclipse");

    // === 每目标触发冷却（防 debuff 常驻）===
    private static final Map<UUID, Long> TRIGGER_COOLDOWNS = new HashMap<>();

    // Debuff 组合定义（强控互斥版）
    private static final List<DebuffCombo> COMBOS = new ArrayList<>();
    private static int TOTAL_WEIGHT = 0;

    static {
        // 初始化组合权重表（强控互斥：darkness 和 blindness 不同时出现）
        // 原 darkness+blindness 权重 30 → 分配给 darkness+weakness 和 blindness+weakness
        addCombo(StatusEffects.DARKNESS, StatusEffects.WEAKNESS, EclipseConfig.WEIGHT_DARKNESS_WEAKNESS + 15);
        addCombo(StatusEffects.DARKNESS, StatusEffects.SLOWNESS, EclipseConfig.WEIGHT_DARKNESS_SLOWNESS);
        addCombo(StatusEffects.BLINDNESS, StatusEffects.WEAKNESS, EclipseConfig.WEIGHT_BLINDNESS_WEAKNESS + 15);
        addCombo(StatusEffects.BLINDNESS, StatusEffects.SLOWNESS, EclipseConfig.WEIGHT_BLINDNESS_SLOWNESS);
        addCombo(StatusEffects.WEAKNESS, StatusEffects.SLOWNESS, EclipseConfig.WEIGHT_WEAKNESS_SLOWNESS);
        addCombo(StatusEffects.DARKNESS, StatusEffects.WITHER, EclipseConfig.WEIGHT_DARKNESS_WITHER);
        addCombo(StatusEffects.BLINDNESS, StatusEffects.WITHER, EclipseConfig.WEIGHT_BLINDNESS_WITHER);
        addCombo(StatusEffects.WEAKNESS, StatusEffects.WITHER, EclipseConfig.WEIGHT_WEAKNESS_WITHER);
        addCombo(StatusEffects.SLOWNESS, StatusEffects.WITHER, EclipseConfig.WEIGHT_SLOWNESS_WITHER);
    }

    private static void addCombo(RegistryEntry<StatusEffect> a, RegistryEntry<StatusEffect> b, int weight) {
        COMBOS.add(new DebuffCombo(a, b, weight));
        TOTAL_WEIGHT += weight;
    }

    record DebuffCombo(RegistryEntry<StatusEffect> effect1, RegistryEntry<StatusEffect> effect2, int weight) {}

    public static void register() {
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient()) return ActionResult.PASS;
            if (!(entity instanceof LivingEntity target)) return ActionResult.PASS;
            if (!(player.getMainHandStack().isOf(KatanaItems.ECLIPSE_BLADE))) return ActionResult.PASS;

            // 检查目标是否已有月蚀标记（用于日志）
            boolean hadMark = EclipseManager.hasMark(target);

            if (EclipseConfig.DEBUG) {
                float ratio = hadMark ? EclipseConfig.MARKED_ARMOR_PENETRATION : EclipseConfig.BASE_ARMOR_PENETRATION;
                LOGGER.info("[Eclipse] Attack: {} -> {} (marked: {}, pen: {}%)",
                    player.getName().getString(), target.getName().getString(), hadMark, (int)(ratio * 100));
            }

            // 尝试触发新的月蚀效果（含每目标冷却检查）
            if (shouldTrigger(world, target)) {
                int duration = calculateDuration(target);
                applyEclipseEffect(player, target, duration);
                EclipseManager.applyMark(target, player, duration);

                // 记录触发时间（冷却）
                TRIGGER_COOLDOWNS.put(target.getUuid(), world.getTime());
                // 简单清理：map 过大时 clear
                if (TRIGGER_COOLDOWNS.size() > 2048) {
                    TRIGGER_COOLDOWNS.clear();
                    TRIGGER_COOLDOWNS.put(target.getUuid(), world.getTime());
                }
            }

            return ActionResult.PASS;
        });

        LOGGER.info("[Eclipse] Handler registered");
    }

    private static boolean shouldTrigger(World world, LivingEntity target) {
        // 1. 每目标冷却检查
        Long lastTrigger = TRIGGER_COOLDOWNS.get(target.getUuid());
        if (lastTrigger != null) {
            long elapsed = world.getTime() - lastTrigger;
            if (elapsed < EclipseConfig.TRIGGER_CD_TICKS) {
                if (EclipseConfig.DEBUG) {
                    LOGGER.info("[Eclipse] Trigger blocked: cooldown ({} ticks remain)",
                        EclipseConfig.TRIGGER_CD_TICKS - elapsed);
                }
                return false;
            }
        }

        // 2. 概率 roll
        float roll = world.getRandom().nextFloat();
        boolean triggered = roll < EclipseConfig.TRIGGER_CHANCE;

        if (EclipseConfig.DEBUG) {
            LOGGER.info("[Eclipse] Trigger roll: {} < {} = {}",
                String.format("%.2f", roll), EclipseConfig.TRIGGER_CHANCE, triggered);
        }

        return triggered;
    }

    private static int calculateDuration(LivingEntity target) {
        int baseDuration = EclipseConfig.MARK_DURATION_TICKS;

        if (isBoss(target)) {
            int reduced = (int)(baseDuration * EclipseConfig.BOSS_DURATION_MULTIPLIER);
            if (EclipseConfig.DEBUG) {
                LOGGER.info("[Eclipse] Boss duration: {} -> {} ticks", baseDuration, reduced);
            }
            return reduced;
        }

        return baseDuration;
    }

    private static void applyEclipseEffect(PlayerEntity player, LivingEntity target, int durationTicks) {
        // 1. 施加发光效果
        target.addStatusEffect(new StatusEffectInstance(
            StatusEffects.GLOWING,
            durationTicks,
            0,
            false,  // ambient
            false,  // showParticles
            true    // showIcon
        ));

        // 2. 随机选择 Debuff 组合
        DebuffCombo combo = selectRandomCombo(player.getWorld());

        // 3. 施加两个 Debuff
        int amplifier1 = getAmplifier(combo.effect1());
        int amplifier2 = getAmplifier(combo.effect2());

        target.addStatusEffect(new StatusEffectInstance(combo.effect1(), durationTicks, amplifier1));
        target.addStatusEffect(new StatusEffectInstance(combo.effect2(), durationTicks, amplifier2));

        if (EclipseConfig.DEBUG) {
            LOGGER.info("[Eclipse] ★ TRIGGERED! Applied {} + {} for {} ticks",
                combo.effect1().getIdAsString(), combo.effect2().getIdAsString(), durationTicks);
        }

        // 4. 粒子效果：暗紫色虚空粒子
        spawnParticles(player, target);

        // 5. 音效
        playSound(player, target);
    }

    private static DebuffCombo selectRandomCombo(World world) {
        int roll = world.getRandom().nextInt(TOTAL_WEIGHT);
        int cumulative = 0;

        for (DebuffCombo combo : COMBOS) {
            cumulative += combo.weight();
            if (roll < cumulative) {
                return combo;
            }
        }

        // 保底返回第一个
        return COMBOS.get(0);
    }

    private static int getAmplifier(RegistryEntry<StatusEffect> effect) {
        // 根据效果返回对应等级
        if (effect == StatusEffects.WEAKNESS) return EclipseConfig.WEAKNESS_AMPLIFIER;
        if (effect == StatusEffects.WITHER) return EclipseConfig.WITHER_AMPLIFIER;
        if (effect == StatusEffects.SLOWNESS) return EclipseConfig.SLOWNESS_AMPLIFIER;
        if (effect == StatusEffects.BLINDNESS) return EclipseConfig.BLINDNESS_AMPLIFIER;
        if (effect == StatusEffects.DARKNESS) return EclipseConfig.DARKNESS_AMPLIFIER;
        return 0;
    }

    private static void spawnParticles(PlayerEntity player, LivingEntity target) {
        if (!(target.getWorld() instanceof ServerWorld serverWorld)) return;

        // 暗紫色虚空粒子
        serverWorld.spawnParticles(
            ParticleTypes.REVERSE_PORTAL,
            target.getX(), target.getY() + target.getHeight() / 2, target.getZ(),
            25,
            0.5, 0.5, 0.5,
            0.05
        );

        // 灵魂粒子（表达诅咒）
        serverWorld.spawnParticles(
            ParticleTypes.SOUL_FIRE_FLAME,
            target.getX(), target.getY() + target.getHeight(), target.getZ(),
            8,
            0.3, 0.3, 0.3,
            0.02
        );

        // 烟雾粒子（黑暗氛围）
        serverWorld.spawnParticles(
            ParticleTypes.LARGE_SMOKE,
            target.getX(), target.getY() + 0.5, target.getZ(),
            10,
            0.4, 0.2, 0.4,
            0.01
        );
    }

    private static void playSound(PlayerEntity player, LivingEntity target) {
        // A) 玩家听清：幽灵般的低语
        player.playSound(SoundEvents.ENTITY_VEX_AMBIENT, 0.8f, 0.5f);

        // B) 世界听到：沉闷的诅咒声
        target.getWorld().playSound(
            null,
            target.getX(), target.getY(), target.getZ(),
            SoundEvents.ENTITY_WARDEN_AMBIENT,
            SoundCategory.PLAYERS,
            0.4f, 0.3f
        );

        // C) 额外的黑暗音效
        target.getWorld().playSound(
            null,
            target.getX(), target.getY(), target.getZ(),
            SoundEvents.AMBIENT_CAVE.value(),
            SoundCategory.AMBIENT,
            0.5f, 0.8f
        );
    }

    private static boolean isBoss(LivingEntity entity) {
        return entity instanceof EnderDragonEntity || entity instanceof WitherEntity;
    }
}
