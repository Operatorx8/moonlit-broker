package dev.xqanzd.moonlitbroker.katana.effect;

import dev.xqanzd.moonlitbroker.katana.item.KatanaItems;
import dev.xqanzd.moonlitbroker.util.KatanaContractUtil;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 窃念之黯核心事件处理器
 *
 * 四层机制：
 * Layer 1: ReadWrite 标记
 * Layer 2: Debuff（虚弱II/缓慢II）
 * Layer 3: 倒因噬果
 * Layer 4: 护甲穿透
 */
public class OblivionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("Oblivion");

    public static void register() {
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient()) return ActionResult.PASS;
            if (!(entity instanceof LivingEntity target)) return ActionResult.PASS;
            if (!(player.getMainHandStack().isOf(KatanaItems.OBLIVION_EDGE))) return ActionResult.PASS;
            if (world instanceof ServerWorld sw
                    && !KatanaContractUtil.gateOrReturn(sw, player, player.getMainHandStack())) {
                return ActionResult.PASS;
            }

            long currentTick = world.getTime();
            boolean isBoss = isBoss(target);
            boolean hasReadWrite = OblivionManager.hasReadWrite(target, currentTick);

            // 记录玩家命中前的真实生命值（用于倒因噬果）
            float playerHpBeforeHit = player.getHealth();
            float playerMaxHp = player.getMaxHealth();
            float playerHpRatio = playerHpBeforeHit / playerMaxHp;

            if (OblivionConfig.DEBUG) {
                LOGGER.info("[Oblivion] Attack: {} (HP: {}/{} = {}%) -> {} (HP: {}, ReadWrite: {})",
                    player.getName().getString(),
                    String.format("%.1f", playerHpBeforeHit),
                    String.format("%.1f", playerMaxHp),
                    String.format("%.0f", playerHpRatio * 100),
                    target.getName().getString(),
                    String.format("%.1f", target.getHealth()),
                    hasReadWrite);
            }

            // === Layer 4: 护甲穿透（仅对 ReadWrite 目标） ===
            if (hasReadWrite) {
                applyArmorPenetration(player, target, isBoss);
            }

            // === Layer 3: 倒因噬果（条件判定） ===
            if (hasReadWrite && shouldTriggerCausality(player, target, playerHpBeforeHit, playerHpRatio, isBoss, currentTick)) {
                applyCausality(player, target, playerHpBeforeHit, isBoss, currentTick);
            }

            // === Layer 1 & 2: ReadWrite 标记 + Debuff ===
            if (shouldApplyReadWrite(world, target, isBoss, currentTick)) {
                int duration = calculateReadWriteDuration(isBoss);
                int cooldown = calculateReadWriteCooldown(isBoss);

                applyReadWriteEffect(player, target, duration, cooldown, currentTick);
            }

            return ActionResult.PASS;
        });

        LOGGER.info("[Oblivion] Handler registered");
    }

    // ========== Layer 1: ReadWrite 施加判定 ==========

    private static boolean shouldApplyReadWrite(World world, LivingEntity target, boolean isBoss, long currentTick) {
        // 冷却检查
        if (!OblivionManager.canApplyReadWrite(target, currentTick)) {
            return false;
        }

        // 概率判定
        float roll = world.getRandom().nextFloat();
        boolean triggered = roll < OblivionConfig.READWRITE_CHANCE;

        if (OblivionConfig.DEBUG) {
            LOGGER.info("[Oblivion] ReadWrite roll: {} < {} = {}",
                String.format("%.2f", roll), OblivionConfig.READWRITE_CHANCE, triggered);
        }

        return triggered;
    }

    private static int calculateReadWriteDuration(boolean isBoss) {
        int base = OblivionConfig.READWRITE_DURATION_TICKS;
        return isBoss ? (int)(base * OblivionConfig.BOSS_DURATION_MULTIPLIER) : base;
    }

    private static int calculateReadWriteCooldown(boolean isBoss) {
        int base = OblivionConfig.READWRITE_COOLDOWN_TICKS;
        return isBoss ? (int)(base * OblivionConfig.BOSS_COOLDOWN_MULTIPLIER) : base;
    }

    // ========== Layer 1 & 2: 施加 ReadWrite + Debuff ==========

    private static void applyReadWriteEffect(PlayerEntity player, LivingEntity target,
                                              int durationTicks, int cooldownTicks, long currentTick) {
        // 施加 ReadWrite 标记
        OblivionManager.applyReadWrite(target, player, durationTicks, cooldownTicks, currentTick);

        // 施加发光效果（可视化标记）
        target.addStatusEffect(new StatusEffectInstance(
            StatusEffects.GLOWING,
            durationTicks,
            0,
            false, false, true
        ));

        // 随机选择 Debuff：50% 虚弱 II，50% 缓慢 II
        boolean useWeakness = player.getWorld().getRandom().nextBoolean();

        if (useWeakness) {
            target.addStatusEffect(new StatusEffectInstance(
                StatusEffects.WEAKNESS,
                durationTicks,
                OblivionConfig.DEBUFF_AMPLIFIER  // II 级
            ));
            if (OblivionConfig.DEBUG) {
                LOGGER.info("[Oblivion] ★ ReadWrite + Weakness II applied for {} ticks", durationTicks);
            }
        } else {
            target.addStatusEffect(new StatusEffectInstance(
                StatusEffects.SLOWNESS,
                durationTicks,
                OblivionConfig.DEBUFF_AMPLIFIER  // II 级
            ));
            if (OblivionConfig.DEBUG) {
                LOGGER.info("[Oblivion] ★ ReadWrite + Slowness II applied for {} ticks", durationTicks);
            }
        }

        // 粒子效果：思维被读取
        spawnReadWriteParticles(player, target);

        // 音效
        playReadWriteSound(player, target);
    }

    // ========== Layer 3: 倒因噬果 ==========

    private static boolean shouldTriggerCausality(PlayerEntity player, LivingEntity target,
                                                   float playerHp, float playerHpRatio,
                                                   boolean isBoss, long currentTick) {
        // 条件 1：玩家血量 < 50%
        if (playerHpRatio >= OblivionConfig.PLAYER_HP_THRESHOLD) {
            if (OblivionConfig.DEBUG) {
                LOGGER.info("[Oblivion] Causality skip: player HP {}% >= 50%",
                    String.format("%.0f", playerHpRatio * 100));
            }
            return false;
        }

        // 条件 2：目标血量 > 玩家血量
        if (target.getHealth() <= playerHp) {
            if (OblivionConfig.DEBUG) {
                LOGGER.info("[Oblivion] Causality skip: target HP {} <= player HP {}",
                    String.format("%.1f", target.getHealth()), String.format("%.1f", playerHp));
            }
            return false;
        }

        // 条件 3：冷却检查
        if (OblivionManager.isCausalityOnCooldown(player, currentTick)) {
            return false;
        }

        // 概率判定
        float chance = isBoss ? OblivionConfig.CAUSALITY_CHANCE_BOSS : OblivionConfig.CAUSALITY_CHANCE;
        float roll = player.getWorld().getRandom().nextFloat();
        boolean triggered = roll < chance;

        if (OblivionConfig.DEBUG) {
            LOGGER.info("[Oblivion] Causality roll: {} < {} = {} (Boss: {})",
                String.format("%.2f", roll), String.format("%.4f", chance), triggered, isBoss);
        }

        return triggered;
    }

    private static void applyCausality(PlayerEntity player, LivingEntity target,
                                        float playerHp, boolean isBoss, long currentTick) {
        float targetHpBefore = target.getHealth();

        // 核心效果：目标血量 = min(目标血量, 玩家血量)
        float newTargetHp = Math.min(targetHpBefore, playerHp);
        target.setHealth(newTargetHp);

        // 设置冷却
        int cooldown = isBoss ? OblivionConfig.CAUSALITY_COOLDOWN_BOSS_TICKS : OblivionConfig.CAUSALITY_COOLDOWN_TICKS;
        OblivionManager.setCausalityCooldown(player, cooldown, currentTick);

        if (OblivionConfig.DEBUG) {
            LOGGER.info("[Oblivion] ★★★ CAUSALITY TRIGGERED! Target HP: {} -> {} (= player HP)",
                String.format("%.1f", targetHpBefore), String.format("%.1f", newTargetHp));
        }

        // 强烈的视觉反馈
        spawnCausalityParticles(player, target);

        // 震撼音效
        playCausalitySound(player, target);
    }

    // ========== Layer 4: 护甲穿透 ==========

    private static void applyArmorPenetration(PlayerEntity player, LivingEntity target, boolean isBoss) {
        float baseDamage = 5.0f;  // OblivionEdgeItem 基础伤害
        float armor = target.getArmor();
        float armorReduction = Math.min(armor * 0.04f, 0.8f);

        float penetration = isBoss ? OblivionConfig.ARMOR_PENETRATION_BOSS : OblivionConfig.ARMOR_PENETRATION;
        float compensationDamage = baseDamage * armorReduction * penetration;

        if (compensationDamage > 0.3f) {
            target.damage(player.getDamageSources().magic(), compensationDamage);

            if (OblivionConfig.DEBUG) {
                LOGGER.info("[Oblivion] Armor penetration: {}% of {} armor = {} bonus damage (Boss: {})",
                    (int)(penetration * 100), (int)armor,
                    String.format("%.1f", compensationDamage), isBoss);
            }
        }
    }

    // ========== 粒子效果 ==========

    private static void spawnReadWriteParticles(PlayerEntity player, LivingEntity target) {
        if (!(target.getWorld() instanceof ServerWorld serverWorld)) return;

        // 灵魂粒子：思维被窃取
        serverWorld.spawnParticles(
            ParticleTypes.SOUL,
            target.getX(), target.getY() + target.getHeight() + 0.5, target.getZ(),
            8,
            0.3, 0.3, 0.3,
            0.05
        );

        // 附魔粒子：神秘感
        serverWorld.spawnParticles(
            ParticleTypes.ENCHANT,
            target.getX(), target.getY() + target.getHeight() / 2, target.getZ(),
            15,
            0.4, 0.4, 0.4,
            0.5
        );
    }

    private static void spawnCausalityParticles(PlayerEntity player, LivingEntity target) {
        if (!(target.getWorld() instanceof ServerWorld serverWorld)) return;

        // 爆发性粒子：因果翻转的冲击
        serverWorld.spawnParticles(
            ParticleTypes.REVERSE_PORTAL,
            target.getX(), target.getY() + target.getHeight() / 2, target.getZ(),
            50,
            0.8, 0.8, 0.8,
            0.1
        );

        // 末影粒子：时空扭曲
        serverWorld.spawnParticles(
            ParticleTypes.PORTAL,
            target.getX(), target.getY(), target.getZ(),
            30,
            0.5, 1.0, 0.5,
            0.5
        );

        // 灵魂火焰：生命被窃取
        serverWorld.spawnParticles(
            ParticleTypes.SOUL_FIRE_FLAME,
            target.getX(), target.getY() + target.getHeight(), target.getZ(),
            20,
            0.4, 0.4, 0.4,
            0.1
        );

        // 玩家周围也要有粒子（表示能量流向玩家）
        serverWorld.spawnParticles(
            ParticleTypes.ENCHANTED_HIT,
            player.getX(), player.getY() + 1, player.getZ(),
            15,
            0.3, 0.5, 0.3,
            0.1
        );
    }

    // ========== 音效 ==========

    private static void playReadWriteSound(PlayerEntity player, LivingEntity target) {
        // 玩家听到：低语声
        player.playSound(SoundEvents.ENTITY_ALLAY_AMBIENT_WITHOUT_ITEM, 0.6f, 0.5f);

        // 世界听到：神秘音效
        target.getWorld().playSound(
            null,
            target.getX(), target.getY(), target.getZ(),
            SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE,
            SoundCategory.PLAYERS,
            0.5f, 1.5f
        );
    }

    private static void playCausalitySound(PlayerEntity player, LivingEntity target) {
        // 玩家听到：因果翻转的冲击
        player.playSound(SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 0.5f, 1.5f);

        // 世界听到：末影珍珠爆炸 + 凋灵生成
        target.getWorld().playSound(
            null,
            target.getX(), target.getY(), target.getZ(),
            SoundEvents.ENTITY_ENDERMAN_TELEPORT,
            SoundCategory.PLAYERS,
            1.0f, 0.5f
        );

        target.getWorld().playSound(
            null,
            target.getX(), target.getY(), target.getZ(),
            SoundEvents.ENTITY_WITHER_SPAWN,
            SoundCategory.PLAYERS,
            0.3f, 1.5f
        );
    }

    // ========== 辅助方法 ==========

    private static boolean isBoss(LivingEntity entity) {
        return entity instanceof EnderDragonEntity || entity instanceof WitherEntity;
    }
}
