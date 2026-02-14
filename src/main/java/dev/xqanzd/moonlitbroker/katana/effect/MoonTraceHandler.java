package dev.xqanzd.moonlitbroker.katana.effect;

import dev.xqanzd.moonlitbroker.katana.item.KatanaItems;
import dev.xqanzd.moonlitbroker.katana.sound.ModSounds;
import dev.xqanzd.moonlitbroker.util.KatanaContractUtil;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MoonTraceHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("MoonTrace");

    // Moonlight Mark 冷却追踪：EntityId -> 下次可 mark 的 tick
    private static final Map<Integer, Long> moonlightMarkCooldowns = new ConcurrentHashMap<>();

    // Light Mark 冷却追踪：EntityId -> 下次可 mark 的 tick
    private static final Map<Integer, Long> lightMarkCooldowns = new ConcurrentHashMap<>();

    // Speed buff 刷新追踪：PlayerId -> 上次刷新 tick
    private static final Map<Integer, Long> speedBuffLastRefresh = new ConcurrentHashMap<>();

    // 延迟魔法伤害队列：下一 tick 结算，避免无敌帧吞伤害
    private static final List<PendingMagicDamage> pendingMagicDamage = new ArrayList<>();

    private record PendingMagicDamage(UUID targetUuid, UUID playerUuid, float damage, boolean boss) {}

    public static void register() {
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient()) return ActionResult.PASS;
            if (!(entity instanceof LivingEntity target)) return ActionResult.PASS;
            if (!(player.getMainHandStack().isOf(KatanaItems.MOON_GLOW_KATANA))) return ActionResult.PASS;
            if (world instanceof net.minecraft.server.world.ServerWorld sw
                    && !KatanaContractUtil.gateOrReturn(sw, player, player.getMainHandStack())) {
                return ActionResult.PASS;
            }

            if (MoonTraceConfig.DEBUG) {
                LOGGER.info("[MoonTrace] Attack: {} -> {}",
                    player.getName().getString(), target.getName().getString());
            }

            // 1. 尝试消耗已有月痕
            var consumed = MoonTraceManager.getAndConsume(target, player);
            if (consumed.isPresent()) {
                applyConsumeBonus(player, target, consumed.get().markType());
                return ActionResult.PASS;
            }

            // 2. 尝试触发新月痕（优先月光路径；仅在月光条件不满足时尝试光照路径）
            BlockPos targetPos = target.getBlockPos();
            int blockLight = world.getLightLevel(LightType.BLOCK, targetPos);
            int skyLight = world.getLightLevel(LightType.SKY, targetPos);
            int totalLight = getTotalLight(blockLight, skyLight);

            boolean moonlightPathSatisfied = isMoonlightPathSatisfied(world, targetPos);

            if (moonlightPathSatisfied && shouldTriggerMoonlight(world, player, target, targetPos)) {
                applyInstantEffects(player, target);
                MoonTraceManager.applyMoonlightMark(target, player, MoonTraceConfig.MARK_DURATION);

                // 设置 moonlight mark 冷却
                long cooldownExpire = world.getTime() + MoonTraceConfig.MARK_COOLDOWN;
                moonlightMarkCooldowns.put(target.getId(), cooldownExpire);

                if (MoonTraceConfig.DEBUG) {
                    LOGGER.info("[MoonTrace] APPLY {} allowed=moonlight_path skyVisible={} skyLight={} blockLight={} totalLight={} threshold={}",
                        MoonTraceManager.MarkType.MOONLIGHT_MARK,
                        world.isSkyVisible(targetPos),
                        skyLight,
                        blockLight,
                        totalLight,
                        MoonTraceConfig.SKY_LIGHT_THRESHOLD);
                }
            } else if (!moonlightPathSatisfied && shouldTriggerLight(world, player, target, totalLight)) {
                applyInstantEffects(player, target);
                MoonTraceManager.applyLightMark(target, player, MoonTraceConfig.MARK_DURATION);

                // 设置 light mark 冷却
                long cooldownExpire = world.getTime() + MoonTraceConfig.MARK_COOLDOWN;
                lightMarkCooldowns.put(target.getId(), cooldownExpire);

                if (MoonTraceConfig.DEBUG) {
                    LOGGER.info("[MoonTrace] APPLY {} allowed=light_path skyVisible={} skyLight={} blockLight={} totalLight={} threshold={}",
                        MoonTraceManager.MarkType.LIGHT_MARK,
                        world.isSkyVisible(targetPos),
                        skyLight,
                        blockLight,
                        totalLight,
                        MoonTraceConfig.LIGHT_MARK_MIN_LIGHT);
                }
            }

            return ActionResult.PASS;
        });

        LOGGER.info("[MoonTrace] Attack handler registered");
    }

    /**
     * 每 tick 调用：检查并刷新 Speed buff
     */
    public static void tickSpeedBuff(World world) {
        if (world.isClient()) return;

        long currentTick = world.getTime();

        for (var player : world.getPlayers()) {
            if (!(player instanceof ServerPlayerEntity serverPlayer)) continue;

            // 检查主手是否持有月之光芒
            if (!serverPlayer.getMainHandStack().isOf(KatanaItems.MOON_GLOW_KATANA)) {
                continue;
            }
            // Contract gate: dormant katana → no passive buff
            if (world instanceof net.minecraft.server.world.ServerWorld sw
                    && !KatanaContractUtil.gateOrReturn(sw, serverPlayer, serverPlayer.getMainHandStack())) {
                continue;
            }

            // 检查是否满足月光条件
            if (!isNight(world) || !isMoonlit(world, serverPlayer.getBlockPos())) {
                continue;
            }

            // 检查刷新间隔
            Long lastRefresh = speedBuffLastRefresh.get(serverPlayer.getId());
            if (lastRefresh != null && currentTick - lastRefresh < MoonTraceConfig.SPEED_BUFF_REFRESH_INTERVAL) {
                continue;
            }

            // 刷新 Speed buff
            serverPlayer.addStatusEffect(new StatusEffectInstance(
                StatusEffects.SPEED,
                MoonTraceConfig.SPEED_BUFF_DURATION,
                MoonTraceConfig.SPEED_BUFF_LEVEL,
                false,  // ambient
                true,   // show particles
                true    // show icon
            ));

            // 刷新 Night Vision buff（与 Speed 同步）
            serverPlayer.addStatusEffect(new StatusEffectInstance(
                StatusEffects.NIGHT_VISION,
                MoonTraceConfig.NIGHT_VISION_DURATION,
                0,      // Night Vision 只有 I 级
                false,  // ambient
                false,  // hide particles (夜视粒子遮挡视野)
                true    // show icon
            ));

            speedBuffLastRefresh.put(serverPlayer.getId(), currentTick);

            if (MoonTraceConfig.DEBUG) {
                LOGGER.info("[MoonTrace] Speed + Night Vision refreshed for {}", serverPlayer.getName().getString());
            }
        }
    }

    // ==================== 条件判定函数 ====================

    /**
     * 夜晚判定
     */
    public static boolean isNight(World world) {
        return world.isNight();
    }

    /**
     * 月光判定：天空可见 + 天空光照足够
     */
    public static boolean isMoonlit(World world, BlockPos pos) {
        if (!world.isSkyVisible(pos)) return false;
        int skyLight = world.getLightLevel(LightType.SKY, pos);
        return skyLight >= MoonTraceConfig.SKY_LIGHT_THRESHOLD;
    }

    public static int getTotalLight(int blockLight, int skyLight) {
        return Math.max(blockLight, skyLight);
    }

    /**
     * 获取月相倍率（0-7 对应 8 个月相）
     * 0 = 满月，4 = 新月
     * 返回值用于乘以基础概率
     */
    public static float getMoonPhaseFactor(World world) {
        int moonPhase = world.getMoonPhase();  // 0-7

        // 满月(0) -> FULL, 新月(4) -> NEW, 其他线性插值
        float fullFactor = MoonTraceConfig.MOON_PHASE_FULL;
        float newFactor = MoonTraceConfig.MOON_PHASE_NEW;

        // 计算与满月的"距离"（0-4，4是新月）
        int distanceFromFull = Math.min(moonPhase, 8 - moonPhase);

        // 线性插值：满月时 factor=FULL，新月时 factor=NEW
        float t = distanceFromFull / 4.0f;  // 0.0 ~ 1.0
        return fullFactor + (newFactor - fullFactor) * t;
    }

    /**
     * 计算最终 mark 概率
     * finalChance = clamp(BASE + SCALE * moonFactor, MIN, MAX)
     */
    public static float getMarkProbability(World world) {
        float moonFactor = getMoonPhaseFactor(world);
        float rawChance = MoonTraceConfig.CHANCE_BASE + MoonTraceConfig.CHANCE_MOON_SCALE * moonFactor;
        return Math.max(MoonTraceConfig.CHANCE_MIN, Math.min(MoonTraceConfig.CHANCE_MAX, rawChance));
    }

    /**
     * 判断本次攻击是否为暴击
     * MC 暴击条件：玩家下落中 + 未在地面 + 未在梯子/水中 + 未骑乘 + 未失明
     */
    private static boolean isCriticalHit(PlayerEntity player) {
        return player.fallDistance > 0.0f
            && !player.isOnGround()
            && !player.isClimbing()
            && !player.isTouchingWater()
            && !player.hasVehicle()
            && !player.hasStatusEffect(StatusEffects.BLINDNESS);
    }

    // ==================== 触发判定 ====================

    private static boolean isMoonlightPathSatisfied(World world, BlockPos targetPos) {
        if (MoonTraceConfig.REQUIRE_NIGHT && !isNight(world)) {
            return false;
        }
        if (MoonTraceConfig.REQUIRE_MOONLIGHT && !isMoonlit(world, targetPos)) {
            return false;
        }
        return true;
    }

    private static boolean shouldTriggerMoonlight(World world, PlayerEntity player, LivingEntity target, BlockPos targetPos) {
        boolean boss = isBoss(target);

        // 夜晚检查
        if (MoonTraceConfig.REQUIRE_NIGHT && !isNight(world)) {
            if (MoonTraceConfig.DEBUG) LOGGER.info("[MoonTrace] Blocked: not night");
            return false;
        }

        // 月光判定（天空可见 + 光照）
        if (MoonTraceConfig.REQUIRE_MOONLIGHT && !isMoonlit(world, targetPos)) {
            if (MoonTraceConfig.DEBUG) {
                int skyLight = world.getLightLevel(LightType.SKY, targetPos);
                LOGGER.info("[MoonTrace] Blocked: moonlight (skyVisible={}, skyLight={}, threshold={})",
                    world.isSkyVisible(targetPos), skyLight, MoonTraceConfig.SKY_LIGHT_THRESHOLD);
            }
            return false;
        }

        // Boss 检查（如果开启禁止 Boss）
        if (MoonTraceConfig.FORBID_BOSS && boss) {
            if (MoonTraceConfig.DEBUG) LOGGER.info("[MoonTrace] Blocked: boss target forbidden");
            return false;
        }

        // Mark 冷却检查（暴击也要遵守冷却）
        Long cooldownExpire = moonlightMarkCooldowns.get(target.getId());
        if (cooldownExpire != null && world.getTime() < cooldownExpire) {
            if (MoonTraceConfig.DEBUG) {
                LOGGER.info("[MoonTrace] Blocked: cooldown ({} ticks remain)",
                    cooldownExpire - world.getTime());
            }
            return false;
        }

        // 暴击必触发检查（跳过概率 roll）
        boolean isCrit = isCriticalHit(player);
        if (MoonTraceConfig.CRIT_GUARANTEES_MARK && isCrit) {
            if (MoonTraceConfig.DEBUG) {
                LOGGER.info("[MoonTrace] CRIT TRIGGER (skip roll) | target={}",
                    boss ? "Boss" : "Normal");
            }
            return true;
        }

        // 概率检查：finalChance = clamp(BASE + SCALE * moonFactor, MIN, MAX)
        int moonPhase = world.getMoonPhase();
        float moonFactor = getMoonPhaseFactor(world);
        float finalChance = getMarkProbability(world);
        float roll = world.getRandom().nextFloat();
        boolean triggered = roll < finalChance;

        if (MoonTraceConfig.DEBUG) {
            LOGGER.info("[MoonTrace] Roll: phase={}, factor={}, chance=clamp({}+{}*{}, {}, {})={}, roll={}, result={}",
                moonPhase,
                String.format("%.2f", moonFactor),
                MoonTraceConfig.CHANCE_BASE,
                MoonTraceConfig.CHANCE_MOON_SCALE,
                String.format("%.2f", moonFactor),
                MoonTraceConfig.CHANCE_MIN,
                MoonTraceConfig.CHANCE_MAX,
                String.format("%.3f", finalChance),
                String.format("%.3f", roll),
                triggered ? "TRIGGERED" : "miss");
        }

        return triggered;
    }

    private static boolean shouldTriggerLight(World world, PlayerEntity player, LivingEntity target, int totalLight) {
        boolean boss = isBoss(target);

        if (!MoonTraceConfig.LIGHT_MARK_ENABLED) {
            if (MoonTraceConfig.DEBUG) LOGGER.info("[MoonTrace] Light mark blocked: disabled");
            return false;
        }

        if (totalLight < MoonTraceConfig.LIGHT_MARK_MIN_LIGHT) {
            if (MoonTraceConfig.DEBUG) {
                LOGGER.info("[MoonTrace] Light mark blocked: totalLight={} < threshold={}",
                    totalLight, MoonTraceConfig.LIGHT_MARK_MIN_LIGHT);
            }
            return false;
        }

        if (MoonTraceConfig.FORBID_BOSS && boss) {
            if (MoonTraceConfig.DEBUG) LOGGER.info("[MoonTrace] Light mark blocked: boss target forbidden");
            return false;
        }

        Long cooldownExpire = lightMarkCooldowns.get(target.getId());
        if (cooldownExpire != null && world.getTime() < cooldownExpire) {
            if (MoonTraceConfig.DEBUG) {
                LOGGER.info("[MoonTrace] Light mark blocked: cooldown ({} ticks remain)",
                    cooldownExpire - world.getTime());
            }
            return false;
        }

        boolean isCrit = isCriticalHit(player);
        if (MoonTraceConfig.CRIT_GUARANTEES_MARK && isCrit) {
            if (MoonTraceConfig.DEBUG) {
                LOGGER.info("[MoonTrace] Light mark CRIT TRIGGER (skip roll) | target={}",
                    boss ? "Boss" : "Normal");
            }
            return true;
        }

        float finalChance = getMarkProbability(world);
        float roll = world.getRandom().nextFloat();
        boolean triggered = roll < finalChance;

        if (MoonTraceConfig.DEBUG) {
            LOGGER.info("[MoonTrace] Light mark roll: chance={}, roll={}, result={}",
                String.format("%.3f", finalChance),
                String.format("%.3f", roll),
                triggered ? "TRIGGERED" : "miss");
        }

        return triggered;
    }

    private static boolean isBoss(LivingEntity entity) {
        return entity instanceof EnderDragonEntity || entity instanceof WitherEntity;
    }

    // ==================== 效果应用 ====================

    private static void applyInstantEffects(PlayerEntity player, LivingEntity target) {
        // 即时伤害
        float damage = randomRange(MoonTraceConfig.INSTANT_DAMAGE_MIN,
                                   MoonTraceConfig.INSTANT_DAMAGE_MAX,
                                   player.getRandom().nextFloat());
        target.damage(player.getDamageSources().playerAttack(player), damage);

        // 减速
        target.addStatusEffect(new StatusEffectInstance(
            StatusEffects.SLOWNESS,
            MoonTraceConfig.SLOWNESS_DURATION,
            MoonTraceConfig.SLOWNESS_LEVEL
        ));

        // 发光效果
        target.addStatusEffect(new StatusEffectInstance(
            StatusEffects.GLOWING,
            MoonTraceConfig.GLOWING_DURATION,
            0,
            false,
            false,
            true
        ));

        // 月痕施加音效
        var world = target.getWorld();
        float pitch = 0.8f + world.getRandom().nextFloat() * 0.4f;

        player.playSound(ModSounds.MOONTRACE_MARK, 1.8f, pitch);
        world.playSound(null, target.getX(), target.getY(), target.getZ(),
            ModSounds.MOONTRACE_MARK, SoundCategory.BLOCKS, 0.8f, pitch);

        if (MoonTraceConfig.DEBUG) {
            LOGGER.info("[MoonTrace] MARK applied: damage={}, slowness={}t, glowing={}t",
                String.format("%.1f", damage),
                MoonTraceConfig.SLOWNESS_DURATION,
                MoonTraceConfig.GLOWING_DURATION);
        }
    }

    /**
     * 消耗月痕时的增伤效果
     * 包含：护甲穿透物理伤害 + 魔法补偿（BASE + min(maxHP * PERCENT, CAP)）
     */
    private static void applyConsumeBonus(PlayerEntity player, LivingEntity target, MoonTraceManager.MarkType markType) {
        boolean boss = isBoss(target);
        float maxHp = target.getMaxHealth();
        float damageMultiplier = markType == MoonTraceManager.MarkType.LIGHT_MARK
            ? MoonTraceConfig.LIGHT_MARK_DAMAGE_MULT
            : 1.0f;

        // === 物理伤害（带护甲穿透）===
        float baseRawBonus = randomRange(
            MoonTraceConfig.CONSUME_DAMAGE_MIN,
            MoonTraceConfig.CONSUME_DAMAGE_MAX,
            player.getRandom().nextFloat()
        );
        float rawBonus = baseRawBonus * damageMultiplier;

        float armor = target.getArmor();
        float toughness = (float) target.getAttributeValue(EntityAttributes.GENERIC_ARMOR_TOUGHNESS);
        float pen = boss ? MoonTraceConfig.ARMOR_PEN_BOSS : MoonTraceConfig.ARMOR_PEN_NORMAL;
        float effectiveArmor = armor * (1.0f - pen);
        float finalBonus = calculateDamageAfterArmor(rawBonus, effectiveArmor, toughness);

        target.damage(player.getDamageSources().playerAttack(player), finalBonus);

        // === 魔法补偿：magicBonus = BASE + min(maxHP * PERCENT, CAP) ===
        // 延迟到下一 tick 结算，避免无敌帧吞掉魔法伤害
        float baseMagic = boss ? MoonTraceConfig.MAGIC_BASE_BOSS : MoonTraceConfig.MAGIC_BASE_NORMAL;
        float percentHp = boss ? MoonTraceConfig.MAGIC_PERCENT_HP_BOSS : MoonTraceConfig.MAGIC_PERCENT_HP_NORMAL;
        float percentCap = boss ? MoonTraceConfig.MAGIC_PERCENT_CAP_BOSS : MoonTraceConfig.MAGIC_PERCENT_CAP_NORMAL;

        float percentMagic = Math.min(maxHp * percentHp, percentCap);
        float totalMagic = (baseMagic + percentMagic) * damageMultiplier;

        // 加入延迟队列，下一 tick 结算
        pendingMagicDamage.add(new PendingMagicDamage(
            target.getUuid(),
            player.getUuid(),
            totalMagic,
            boss
        ));

        // === DEBUG 日志 ===
        if (MoonTraceConfig.DEBUG) {
            LOGGER.info("[MoonTrace] CONSUMED {} mult={} Physical: rawBase={} rawScaled={} -> final={} | pen={}% | armor={} eff={} tough={}",
                markType,
                String.format("%.2f", damageMultiplier),
                String.format("%.1f", baseRawBonus),
                String.format("%.1f", rawBonus),
                String.format("%.1f", finalBonus),
                String.format("%.0f", pen * 100f),
                String.format("%.1f", armor),
                String.format("%.1f", effectiveArmor),
                String.format("%.1f", toughness));

            LOGGER.info("[MoonTrace] Schedule magic damage next tick: target={} amount={} boss={}",
                target.getType().getName().getString(),
                String.format("%.2f", totalMagic),
                boss);
        }

        // === 音效 ===
        // 物理暴击音效
        target.getWorld().playSound(null, target.getX(), target.getY(), target.getZ(),
            ModSounds.MOONTRACE_CONSUME_CRIT, SoundCategory.PLAYERS, 1.0f, 1.0f);

        // 月痕消耗音效
        float pitch = 0.8f + player.getRandom().nextFloat() * 0.4f;
        player.playSound(ModSounds.MOONTRACE_MARK, 1.8f, pitch);
        target.getWorld().playSound(null, target.getX(), target.getY(), target.getZ(),
            ModSounds.MOONTRACE_MARK, SoundCategory.PLAYERS, 0.8f, pitch);
    }

    private static float randomRange(float min, float max, float random) {
        return min + (max - min) * random;
    }

    /**
     * Minecraft 护甲减伤公式
     */
    private static float calculateDamageAfterArmor(float damage, float armor, float toughness) {
        if (armor <= 0) {
            return damage;
        }

        float defensePoints = Math.max(
            armor / 5.0f,
            armor - damage / (2.0f + toughness / 4.0f)
        );
        float cappedDefense = Math.min(20.0f, defensePoints);
        float damageMultiplier = 1.0f - cappedDefense / 25.0f;

        return damage * damageMultiplier;
    }

    /**
     * 清理过期的冷却记录
     */
    public static void cleanupCooldowns(long currentTick) {
        moonlightMarkCooldowns.entrySet().removeIf(entry -> entry.getValue() < currentTick);
        lightMarkCooldowns.entrySet().removeIf(entry -> entry.getValue() < currentTick);
    }

    /**
     * 处理延迟魔法伤害（每 tick 调用一次）
     * 在下一 tick 结算魔法伤害，避免无敌帧问题
     */
    public static void tickDelayedMagic(net.minecraft.server.world.ServerWorld world) {
        if (pendingMagicDamage.isEmpty()) return;

        Iterator<PendingMagicDamage> iterator = pendingMagicDamage.iterator();
        while (iterator.hasNext()) {
            PendingMagicDamage pending = iterator.next();
            iterator.remove();  // 立即移除，避免重复处理

            // 通过 UUID 查找目标实体
            var targetEntity = world.getEntity(pending.targetUuid());
            if (!(targetEntity instanceof LivingEntity target)) {
                if (MoonTraceConfig.DEBUG) {
                    LOGGER.info("[MoonTrace] Apply delayed magic damage: target=<not found> amount={} ok=false",
                        String.format("%.2f", pending.damage()));
                }
                continue;
            }

            // 目标已死亡，跳过
            if (!target.isAlive()) {
                if (MoonTraceConfig.DEBUG) {
                    LOGGER.info("[MoonTrace] Apply delayed magic damage: target={} amount={} ok=false (dead)",
                        target.getType().getName().getString(),
                        String.format("%.2f", pending.damage()));
                }
                continue;
            }

            // 查找玩家（用于 DamageSource）
            var playerEntity = world.getPlayerByUuid(pending.playerUuid());
            if (playerEntity == null) {
                // 玩家离线，使用通用魔法伤害源
                target.damage(world.getDamageSources().magic(), pending.damage());
            } else {
                target.damage(playerEntity.getDamageSources().magic(), pending.damage());
            }

            if (MoonTraceConfig.DEBUG) {
                LOGGER.info("[MoonTrace] Apply delayed magic damage: target={} amount={} ok=true",
                    target.getType().getName().getString(),
                    String.format("%.2f", pending.damage()));
            }
        }
    }
}
