package mod.test.mymodtest.armor.effect.boots;

import mod.test.mymodtest.armor.BootsEffectConstants;
import mod.test.mymodtest.armor.item.ArmorItems;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.UUID;

/**
 * 靴子效果 Tick Handler
 * 注册到 ServerTickEvents.END_SERVER_TICK
 * 每 tick 遍历所有在线玩家，每 20t 执行一次靴子扫描
 */
public class BootsTickHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("MoonTrace");

    /** per-player 状态 */
    private static final HashMap<UUID, BootsPlayerState> STATE_MAP = new HashMap<>();

    // ==================== 公共 API ====================

    public static BootsPlayerState getOrCreateState(UUID uuid) {
        return STATE_MAP.computeIfAbsent(uuid, k -> new BootsPlayerState());
    }

    public static void removeState(UUID uuid) {
        STATE_MAP.remove(uuid);
    }

    public static void onPlayerLogout(ServerPlayerEntity player) {
        removeState(player.getUuid());
    }

    public static void onPlayerRespawn(ServerPlayerEntity player) {
        BootsPlayerState state = STATE_MAP.get(player.getUuid());
        if (state != null) {
            state.reset();
        }
    }

    // ==================== Tick 入口 ====================

    public static void tick(ServerWorld world, long currentTick) {
        if (currentTick % BootsEffectConstants.BOOT_SCAN_INTERVAL_TICKS != 0) {
            return;
        }

        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.isSpectator() || !player.isAlive()) continue;

            ItemStack feetStack = player.getEquippedStack(EquipmentSlot.FEET);
            BootsPlayerState existingState = STATE_MAP.get(player.getUuid());

            if (feetStack.isEmpty()) {
                if (existingState != null) {
                    existingState.reset();
                    STATE_MAP.remove(player.getUuid());
                }
                continue;
            }

            Item feetItem = feetStack.getItem();
            boolean isBoots =
                    feetItem == ArmorItems.UNTRACEABLE_TREADS_BOOTS
                    || feetItem == ArmorItems.BOUNDARY_WALKER_BOOTS
                    || feetItem == ArmorItems.GHOST_STEP_BOOTS
                    || feetItem == ArmorItems.MARCHING_BOOTS
                    || feetItem == ArmorItems.GOSSAMER_BOOTS;

            if (!isBoots) {
                if (existingState != null) {
                    existingState.reset();
                    STATE_MAP.remove(player.getUuid());
                }
                continue;
            }

            BootsPlayerState state = (existingState != null) ? existingState : getOrCreateState(player.getUuid());
            if (state.lastBootsItem != feetItem) {
                state.reset();
                state.lastBootsItem = feetItem;
            }
            if (state.lastHitLivingTick == Long.MIN_VALUE) {
                state.lastHitLivingTick = currentTick;
            }
            if (state.lastHurtByLivingTick == Long.MIN_VALUE) {
                state.lastHurtByLivingTick = currentTick;
            }

            String bootId = Registries.ITEM.getId(feetItem).toString();

            if (feetItem == ArmorItems.UNTRACEABLE_TREADS_BOOTS) {
                tickUntraceable(player, state, currentTick);
            } else if (feetItem == ArmorItems.BOUNDARY_WALKER_BOOTS) {
                tickBoundaryWalker(player, state, currentTick, bootId);
            } else if (feetItem == ArmorItems.GHOST_STEP_BOOTS) {
                tickGhostStep(player, state, currentTick, bootId);
            } else if (feetItem == ArmorItems.MARCHING_BOOTS) {
                tickMarchingBoots(player, state, currentTick, bootId);
            } else if (feetItem == ArmorItems.GOSSAMER_BOOTS) {
                tickGossamerBoots(player, state, currentTick, bootId);
            }
        }
    }

    // ==================== Boot1: Untraceable Treads ====================

    private static void tickUntraceable(ServerPlayerEntity player, BootsPlayerState state, long now) {
        if (state.invisExpiresTick > 0 && now >= state.invisExpiresTick) {
            LOGGER.info("[MoonTrace|Armor|BOOT] action=exit player={} bootId={} nowTick={} expiresTick={}",
                    player.getName().getString(),
                    Registries.ITEM.getId(ArmorItems.UNTRACEABLE_TREADS_BOOTS),
                    now,
                    state.invisExpiresTick);
            state.invisExpiresTick = 0;
        }

        // 判定：脱战 + CD 就绪
        if ((now - state.lastHitLivingTick >= BootsEffectConstants.UNTRACEABLE_IDLE_WINDOW_TICKS)
                && (now - state.lastHurtByLivingTick >= BootsEffectConstants.UNTRACEABLE_IDLE_WINDOW_TICKS)
                && (now >= state.untraceableCdReadyTick)) {

            // 给予隐身
            player.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.INVISIBILITY,
                    BootsEffectConstants.UNTRACEABLE_INVIS_TICKS,
                    0,    // amplifier 0
                    true, // ambient
                    false, // showParticles
                    true   // showIcon
            ));

            state.invisExpiresTick = now + BootsEffectConstants.UNTRACEABLE_INVIS_TICKS;
            state.untraceableCdReadyTick = now + BootsEffectConstants.UNTRACEABLE_CD_TICKS;
            LOGGER.info("[MoonTrace|Armor|BOOT] action=enter player={} bootId={} nowTick={} expiresTick={} cdUntil={}",
                    player.getName().getString(),
                    Registries.ITEM.getId(ArmorItems.UNTRACEABLE_TREADS_BOOTS),
                    now,
                    state.invisExpiresTick,
                    state.untraceableCdReadyTick);
        } else if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[MoonTrace|Armor|BOOT] action=blocked reason=recent_hit_or_hurt_or_cd player={} bootId={} nowTick={} cdUntil={} lastHitTick={} lastHurtTick={}",
                    player.getName().getString(),
                    Registries.ITEM.getId(ArmorItems.UNTRACEABLE_TREADS_BOOTS),
                    now,
                    state.untraceableCdReadyTick,
                    state.lastHitLivingTick,
                    state.lastHurtByLivingTick);
        }
    }

    // ==================== Boot2: Boundary Walker ====================

    private static void tickBoundaryWalker(ServerPlayerEntity player, BootsPlayerState state, long now, String bootId) {
        World world = player.getWorld();

        // 仅主世界
        if (world.getRegistryKey() != World.OVERWORLD) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[MoonTrace|Armor|BOOT] action=blocked reason=not_overworld player={} bootId={} nowTick={}",
                        player.getName().getString(), bootId, now);
            }
            return;
        }

        BlockPos pos = player.getBlockPos();

        // 必须露天
        if (!world.isSkyVisible(pos)) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[MoonTrace|Armor|BOOT] action=blocked reason=no_sky_visible player={} bootId={} nowTick={}",
                        player.getName().getString(), bootId, now);
            }
            return;
        }

        // 条件：下雨/打雷/下雪/夜晚
        boolean condition = false;

        if (world.isRaining()) {
            // 下雨或打雷时生效
            condition = true;
        } else if (world.isNight()) {
            condition = true;
        }

        // 额外检查：如果没下雨但是雪地生物群系且正在降水
        if (!condition && world.isRaining()) {
            RegistryEntry<Biome> biomeEntry = world.getBiome(pos);
            Biome biome = biomeEntry.value();
            if (biome.getPrecipitation(pos) == Biome.Precipitation.SNOW) {
                condition = true;
            }
        }

        if (!condition) {
            if (state.jumpExpiresTick > 0 && now >= state.jumpExpiresTick) {
                LOGGER.info("[MoonTrace|Armor|BOOT] action=exit player={} bootId={} nowTick={} expiresTick={}",
                        player.getName().getString(), bootId, now, state.jumpExpiresTick);
                state.jumpExpiresTick = 0;
            } else if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[MoonTrace|Armor|BOOT] action=blocked reason=weather_or_night_required player={} bootId={} nowTick={}",
                        player.getName().getString(), bootId, now);
            }
            return;
        }

        // 给予 Jump Boost
        if (now >= state.jumpExpiresTick) {
            LOGGER.info("[MoonTrace|Armor|BOOT] action=enter player={} bootId={} nowTick={} expiresTick={}",
                    player.getName().getString(), bootId, now, now + BootsEffectConstants.BOUNDARY_REFRESH_TICKS);
        }
        player.addStatusEffect(new StatusEffectInstance(
                StatusEffects.JUMP_BOOST,
                BootsEffectConstants.BOUNDARY_REFRESH_TICKS,
                BootsEffectConstants.BOUNDARY_JUMP_LEVEL - 1, // amplifier 0 = I级
                true,  // ambient
                false, // showParticles
                true   // showIcon
        ));

        state.jumpExpiresTick = now + BootsEffectConstants.BOUNDARY_REFRESH_TICKS;
    }

    // ==================== Boot3: Ghost Step ====================

    private static void tickGhostStep(ServerPlayerEntity player, BootsPlayerState state, long now, String bootId) {
        // 6a. 战斗锁判定
        boolean inCombat = (now - state.lastHitLivingTick < BootsEffectConstants.GHOST_COMBAT_LOCK_TICKS);

        // 效果 A：非战斗时常驻幽灵碰撞
        boolean prevActive = state.ghostStateA_active;
        state.ghostStateA_active = !inCombat;
        if (state.ghostStateA_active != prevActive) {
            LOGGER.info("[MoonTrace|Armor|BOOT] action={} player={} bootId={} nowTick={}",
                    state.ghostStateA_active ? "enter" : "exit",
                    player.getName().getString(),
                    bootId,
                    now);
        } else if (inCombat && LOGGER.isDebugEnabled()) {
            LOGGER.debug("[MoonTrace|Armor|BOOT] action=blocked reason=in_combat player={} bootId={} nowTick={} lastHitTick={}",
                    player.getName().getString(), bootId, now, state.lastHitLivingTick);
        }

        // 6c. 效果 B：受击应急幽灵
        if (state.damageWindowCount >= BootsEffectConstants.GHOST_HIT_COUNT_THRESHOLD
                && now >= state.ghostBurstCdReadyTick) {
            state.ghostBurstExpiresTick = now + BootsEffectConstants.GHOST_BURST_TICKS;
            state.ghostBurstCdReadyTick = now + BootsEffectConstants.GHOST_BURST_CD_TICKS;
            LOGGER.info("[MoonTrace|Armor|BOOT] action=enter player={} bootId={} nowTick={} expiresTick={} cdUntil={}",
                    player.getName().getString(),
                    bootId,
                    now,
                    state.ghostBurstExpiresTick,
                    state.ghostBurstCdReadyTick);
        } else if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[MoonTrace|Armor|BOOT] action=blocked reason=hit_window_or_cd player={} bootId={} nowTick={} hitCount={} cdUntil={}",
                    player.getName().getString(),
                    bootId,
                    now,
                    state.damageWindowCount,
                    state.ghostBurstCdReadyTick);
        }

        if (state.ghostBurstExpiresTick > 0 && now >= state.ghostBurstExpiresTick) {
            LOGGER.info("[MoonTrace|Armor|BOOT] action=exit player={} bootId={} nowTick={} expiresTick={}",
                    player.getName().getString(),
                    bootId,
                    now,
                    state.ghostBurstExpiresTick);
            state.ghostBurstExpiresTick = 0;
        }

        // 重置窗口计数
        state.damageWindowCount = 0;
        state.damageWindowStartTick = now;
    }

    // ==================== Boot4: Marching Boots ====================

    private static void tickMarchingBoots(ServerPlayerEntity player, BootsPlayerState state, long now, String bootId) {
        if (!state.marchActive) {
            // 尝试进入急行
            if ((now - state.lastHitLivingTick >= BootsEffectConstants.MARCH_NO_HIT_TICKS)
                    && (now - state.lastHurtByLivingTick >= BootsEffectConstants.MARCH_NO_HURT_TICKS)
                    && (now >= state.marchCdReadyTick)) {

                state.marchActive = true;
                state.marchStartTick = now;
                LOGGER.info("[MoonTrace|Armor|BOOT] action=enter player={} bootId={} nowTick={} expiresTick={} cdUntil={}",
                        player.getName().getString(),
                        bootId,
                        now,
                        now + BootsEffectConstants.MARCH_MAX_DURATION_TICKS,
                        state.marchCdReadyTick);

                // 给予 Speed II
                player.addStatusEffect(new StatusEffectInstance(
                        StatusEffects.SPEED,
                        BootsEffectConstants.BOUNDARY_REFRESH_TICKS, // 25t 滚动刷新
                        BootsEffectConstants.MARCH_SPEED_LEVEL - 1,  // amplifier 1 = II级
                        true, false, true
                ));
                state.speedExpiresTick = now + BootsEffectConstants.BOUNDARY_REFRESH_TICKS;
            } else if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[MoonTrace|Armor|BOOT] action=blocked reason=recent_hit_or_hurt_or_cd player={} bootId={} nowTick={} lastHitTick={} lastHurtTick={} cdUntil={}",
                        player.getName().getString(),
                        bootId,
                        now,
                        state.lastHitLivingTick,
                        state.lastHurtByLivingTick,
                        state.marchCdReadyTick);
            }
        } else {
            // 维持急行
            boolean stillValid =
                    (now - state.lastHitLivingTick >= BootsEffectConstants.MARCH_NO_HIT_TICKS)
                    && (now - state.lastHurtByLivingTick >= BootsEffectConstants.MARCH_NO_HURT_TICKS);

            boolean expired = (now - state.marchStartTick >= BootsEffectConstants.MARCH_MAX_DURATION_TICKS);

            if (!stillValid || expired) {
                // 退出急行
                LOGGER.info("[MoonTrace|Armor|BOOT] action=exit player={} bootId={} nowTick={} expiresTick={} cdUntil={}",
                        player.getName().getString(),
                        bootId,
                        now,
                        state.marchStartTick + BootsEffectConstants.MARCH_MAX_DURATION_TICKS,
                        now + BootsEffectConstants.MARCH_CD_TICKS);
                exitMarch(state, now);
            } else {
                // 刷新 Speed II
                player.addStatusEffect(new StatusEffectInstance(
                        StatusEffects.SPEED,
                        BootsEffectConstants.BOUNDARY_REFRESH_TICKS,
                        BootsEffectConstants.MARCH_SPEED_LEVEL - 1,
                        true, false, true
                ));
                state.speedExpiresTick = now + BootsEffectConstants.BOUNDARY_REFRESH_TICKS;
            }
        }
    }

    /** 退出急行（供 tick 和 attack 事件调用） */
    public static void exitMarch(BootsPlayerState state, long now) {
        state.marchActive = false;
        state.marchCdReadyTick = now + BootsEffectConstants.MARCH_CD_TICKS;
    }

    // ==================== Boot5: Gossamer Boots ====================

    private static void tickGossamerBoots(ServerPlayerEntity player, BootsPlayerState state, long now, String bootId) {
        // 禁用扫描路线：仅由 slowMovement mixin 触发进入
        if (state.webAssistExpiresTick > 0 && now >= state.webAssistExpiresTick) {
            LOGGER.info("[MoonTrace|Armor|BOOT] action=exit player={} bootId={} nowTick={} expiresTick={}",
                    player.getName().getString(),
                    bootId,
                    now,
                    state.webAssistExpiresTick);
            state.webAssistExpiresTick = 0;
        }
    }
}
