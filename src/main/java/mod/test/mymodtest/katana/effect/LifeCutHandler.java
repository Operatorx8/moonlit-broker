package mod.test.mymodtest.katana.effect;

import mod.test.mymodtest.katana.item.KatanaItems;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.mob.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 残念之刃（Regret Blade）- LifeCut 效果处理器
 *
 * 核心机制：
 * 1. baseCut = currentHP * 0.30（Boss 再乘 1/3）
 * 2. 护甲穿透： effectiveArmor = armor * (1 - penetration)
 * 3. finalCut = DamageUtil.getDamageLeft(baseCut, effectiveArmor, toughness)
 * 4. 使用 setHealth 直接扣血，确保伤害精确可控
 */
public class LifeCutHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("LifeCut");

    // 每目标冷却追踪：targetUUID -> 下次可触发的 tick
    private static final Map<UUID, Long> targetCooldowns = new ConcurrentHashMap<>();

    // 延迟伤害队列：下一 tick 结算，避免无敌帧吞伤害
    private static final List<PendingLifeCutDamage> pendingDamage = new ArrayList<>();

    private record PendingLifeCutDamage(UUID targetUuid, UUID playerUuid, float damage, boolean isBoss) {}

    public static void register() {
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            // 仅服务端执行
            if (world.isClient()) return ActionResult.PASS;
            if (!(entity instanceof LivingEntity target)) return ActionResult.PASS;
            if (!(player.getMainHandStack().isOf(KatanaItems.REGRET_BLADE))) return ActionResult.PASS;

            float currentHp = target.getHealth();
            float maxHp = target.getMaxHealth();
            float armor = target.getArmor();
            float toughness = getArmorToughness(target);

            if (LifeCutConfig.DEBUG) {
                LOGGER.info("[LifeCut] Attack: {} -> {} (HP: {}/{}, Armor: {}, Toughness: {})",
                    player.getName().getString(),
                    target.getName().getString(),
                    String.format("%.1f", currentHp),
                    String.format("%.1f", maxHp),
                    String.format("%.1f", armor),
                    String.format("%.1f", toughness));
            }

            // 冷却检查
            long currentTick = world.getTime();
            Long cooldownExpire = targetCooldowns.get(target.getUuid());
            if (cooldownExpire != null && currentTick < cooldownExpire) {
                if (LifeCutConfig.DEBUG) {
                    LOGGER.info("[LifeCut] Skip: target on cooldown ({} ticks remain)",
                        cooldownExpire - currentTick);
                }
                return ActionResult.PASS;
            }

            // 判定是否触发
            if (shouldTrigger(world, target, currentHp)) {
                // 计算并排入延迟队列
                scheduleLifeCut(world, player, target, currentHp, armor, toughness);
            }

            return ActionResult.PASS;
        });

        LOGGER.info("[LifeCut] Handler registered");
    }

    /**
     * 获取目标的护甲韧性（Armor Toughness）
     */
    private static float getArmorToughness(LivingEntity target) {
        var toughnessAttr = target.getAttributeInstance(EntityAttributes.GENERIC_ARMOR_TOUGHNESS);
        return toughnessAttr != null ? (float) toughnessAttr.getValue() : 0.0f;
    }

    /**
     * 判定是否触发 LifeCut
     */
    private static boolean shouldTrigger(World world, LivingEntity target, float currentHp) {
        // 血量检查：太低不触发
        if (currentHp < LifeCutConfig.MIN_HEALTH_TO_TRIGGER) {
            if (LifeCutConfig.DEBUG) {
                LOGGER.info("[LifeCut] Skip: HP {} < threshold {} (hp too low)",
                    String.format("%.1f", currentHp),
                    String.format("%.1f", LifeCutConfig.MIN_HEALTH_TO_TRIGGER));
            }
            return false;
        }

        // 亡灵检查（如果开启）
        if (LifeCutConfig.ONLY_UNDEAD && !isUndead(target)) {
            if (LifeCutConfig.DEBUG) LOGGER.info("[LifeCut] Skip: not undead");
            return false;
        }

        // 概率判定
        float roll = world.getRandom().nextFloat();
        boolean triggered = roll < LifeCutConfig.TRIGGER_CHANCE;

        if (LifeCutConfig.DEBUG) {
            LOGGER.info("[LifeCut] Roll: {} < {} = {}",
                String.format("%.3f", roll),
                String.format("%.3f", LifeCutConfig.TRIGGER_CHANCE),
                triggered);
        }

        return triggered;
    }

    /**
     * 计算 LifeCut 伤害并排入延迟队列
     */
    private static void scheduleLifeCut(World world, PlayerEntity player, LivingEntity target,
                                        float currentHp, float armor, float toughness) {
        boolean isBoss = isBoss(target);

        // === Step 1: 计算 baseCut ===
        float baseCut = currentHp * LifeCutConfig.HEALTH_CUT_RATIO;

        // Boss 效果减弱（baseCut * 1/3）
        if (isBoss && LifeCutConfig.ALLOW_BOSS) {
            float originalBaseCut = baseCut;
            baseCut *= LifeCutConfig.BOSS_EFFECT_MULTIPLIER;
            if (LifeCutConfig.DEBUG) {
                LOGGER.info("[LifeCut] Boss penalty: baseCut {} -> {} (x{})",
                    String.format("%.1f", originalBaseCut),
                    String.format("%.1f", baseCut),
                    String.format("%.3f", LifeCutConfig.BOSS_EFFECT_MULTIPLIER));
            }
        }

        // === Step 2: 计算护甲穿透 ===
        float armorPenetration = isBoss
            ? LifeCutConfig.ARMOR_PENETRATION_BOSS
            : LifeCutConfig.ARMOR_PENETRATION_NORMAL;

        // effectiveArmor = armor * (1 - penetration)
        float effectiveArmor = armor * (1.0f - armorPenetration);

        // === Step 3: 使用 MC 护甲减伤公式计算 finalCut ===
        float finalCut = calculateDamageAfterArmor(baseCut, effectiveArmor, toughness);

        // === Step 4: cannotKill 检查 ===
        float newHp = currentHp - finalCut;
        boolean clamped = false;
        if (LifeCutConfig.CANNOT_KILL && newHp < LifeCutConfig.MIN_HEALTH_AFTER_CUT) {
            float adjustedFinalCut = currentHp - LifeCutConfig.MIN_HEALTH_AFTER_CUT;
            if (adjustedFinalCut < 0) adjustedFinalCut = 0;

            if (LifeCutConfig.DEBUG) {
                LOGGER.info("[LifeCut] CannotKill: finalCut {} -> {} (clamped to preserve {} HP)",
                    String.format("%.1f", finalCut),
                    String.format("%.1f", adjustedFinalCut),
                    String.format("%.1f", LifeCutConfig.MIN_HEALTH_AFTER_CUT));
            }
            finalCut = adjustedFinalCut;
            clamped = true;
        }

        // === Step 5: 排入延迟队列（下一 tick 结算）===
        if (finalCut > 0) {
            pendingDamage.add(new PendingLifeCutDamage(
                target.getUuid(),
                player.getUuid(),
                finalCut,
                isBoss
            ));

            // 设置每目标冷却
            long cooldownExpire = world.getTime() + LifeCutConfig.LIFECUT_TRIGGER_CD_TICKS;
            targetCooldowns.put(target.getUuid(), cooldownExpire);

            // Debug 日志：入队
            if (LifeCutConfig.DEBUG) {
                LOGGER.info("[LifeCut] Schedule: target={} damage={} boss={}",
                    target.getName().getString(),
                    String.format("%.1f", finalCut),
                    isBoss);
                LOGGER.info("[LifeCut]   BaseCut: {} ({}% of {} HP{})",
                    String.format("%.1f", baseCut),
                    (int)(LifeCutConfig.HEALTH_CUT_RATIO * 100),
                    String.format("%.1f", currentHp),
                    isBoss ? ", Boss penalty applied" : "");
                LOGGER.info("[LifeCut]   ArmorPenetration: {}% ({}) | EffectiveArmor: {} (orig {}, tough {})",
                    (int)(armorPenetration * 100),
                    isBoss ? "Boss" : "Normal",
                    String.format("%.1f", effectiveArmor),
                    String.format("%.1f", armor),
                    String.format("%.1f", toughness));
                LOGGER.info("[LifeCut]   FinalCut: {}{}",
                    String.format("%.1f", finalCut),
                    clamped ? " (clamped to preserve 1.0 HP)" : "");
            }

            // 播放效果（立即播放，让玩家有视觉反馈）
            playEffects(player, target, finalCut);
        } else {
            if (LifeCutConfig.DEBUG) {
                LOGGER.info("[LifeCut] Skip: finalCut <= 0 (cannotKill, target already at min HP)");
            }
        }
    }

    /**
     * 处理延迟伤害队列（每 tick 调用一次）
     * 使用 target.damage() 结算伤害，更兼容原版机制
     */
    public static void tickDelayedDamage(ServerWorld world) {
        if (pendingDamage.isEmpty()) return;

        Iterator<PendingLifeCutDamage> iterator = pendingDamage.iterator();
        while (iterator.hasNext()) {
            PendingLifeCutDamage pending = iterator.next();
            iterator.remove();  // 立即移除，避免重复处理

            // 通过 UUID 查找目标实体
            var targetEntity = world.getEntity(pending.targetUuid());
            if (!(targetEntity instanceof LivingEntity target)) {
                if (LifeCutConfig.DEBUG) {
                    LOGGER.info("[LifeCut] Apply: target=<not found> amount={} ok=false",
                        String.format("%.1f", pending.damage()));
                }
                continue;
            }

            // 目标已死亡，跳过
            if (!target.isAlive()) {
                if (LifeCutConfig.DEBUG) {
                    LOGGER.info("[LifeCut] Apply: target={} amount={} ok=false (dead)",
                        target.getName().getString(),
                        String.format("%.1f", pending.damage()));
                }
                continue;
            }

            // 查找玩家（用于 DamageSource）
            var playerEntity = world.getPlayerByUuid(pending.playerUuid());

            // 使用 playerAttack 伤害源，更接近原版攻击
            if (playerEntity != null) {
                target.damage(playerEntity.getDamageSources().playerAttack(playerEntity), pending.damage());
            } else {
                // 玩家离线，使用通用攻击伤害源
                target.damage(world.getDamageSources().generic(), pending.damage());
            }

            if (LifeCutConfig.DEBUG) {
                LOGGER.info("[LifeCut] Apply: target={} amount={} ok=true",
                    target.getName().getString(),
                    String.format("%.1f", pending.damage()));
            }
        }
    }

    /**
     * 清理过期的冷却记录
     */
    public static void cleanupCooldowns(long currentTick) {
        targetCooldowns.entrySet().removeIf(entry -> entry.getValue() < currentTick);
    }

    /**
     * 播放视觉和音效
     */
    private static void playEffects(PlayerEntity player, LivingEntity target, float damage) {
        // 粒子效果：暗红血雾
        if (target.getWorld() instanceof ServerWorld serverWorld) {
            serverWorld.spawnParticles(
                ParticleTypes.DAMAGE_INDICATOR,
                target.getX(), target.getY() + target.getHeight() / 2, target.getZ(),
                (int)(damage * 2),  // 伤害越高粒子越多
                0.4, 0.4, 0.4,
                0.1
            );
            // 额外的灵魂粒子（表达"残念"）
            serverWorld.spawnParticles(
                ParticleTypes.SOUL,
                target.getX(), target.getY() + target.getHeight(), target.getZ(),
                3,
                0.2, 0.2, 0.2,
                0.02
            );
        }

        // 音效：沉闷的斩击
        player.playSound(SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, 1.0f, 0.8f);
        // 世界隐约听到
        target.getWorld().playSound(
            null,
            target.getX(), target.getY(), target.getZ(),
            SoundEvents.ENTITY_WARDEN_HEARTBEAT,
            SoundCategory.PLAYERS,
            0.3f, 0.6f
        );
    }

    private static boolean isBoss(LivingEntity entity) {
        return entity instanceof EnderDragonEntity || entity instanceof WitherEntity;
    }

    private static boolean isUndead(LivingEntity entity) {
        // 检查常见亡灵类型
        return entity instanceof ZombieEntity
            || entity instanceof SkeletonEntity
            || entity instanceof AbstractSkeletonEntity
            || entity instanceof PhantomEntity
            || entity instanceof WitherEntity
            || entity instanceof ZombifiedPiglinEntity;
    }

    /**
     * Minecraft 护甲减伤公式
     * 公式：damageAfter = damage * (1 - min(20, max(armor/5, armor - damage/(2+toughness/4))) / 25)
     *
     * @param damage    原始伤害
     * @param armor     护甲值（已经过穿透计算的 effectiveArmor）
     * @param toughness 护甲韧性
     * @return 经护甲减免后的实际伤害
     */
    private static float calculateDamageAfterArmor(float damage, float armor, float toughness) {
        // 如果护甲为 0，直接返回原始伤害
        if (armor <= 0) {
            return damage;
        }

        // MC 护甲公式
        // defensePoints = max(armor/5, armor - damage/(2 + toughness/4))
        // cappedDefense = min(20, defensePoints)
        // damageMultiplier = 1 - cappedDefense/25
        // finalDamage = damage * damageMultiplier

        float defensePoints = Math.max(
            armor / 5.0f,
            armor - damage / (2.0f + toughness / 4.0f)
        );
        float cappedDefense = Math.min(20.0f, defensePoints);
        float damageMultiplier = 1.0f - cappedDefense / 25.0f;

        return damage * damageMultiplier;
    }
}
