package mod.test.mymodtest.armor.effect.boots;

import net.minecraft.item.Item;

import java.util.UUID;

/**
 * 靴子 per-player 状态对象
 * 所有字段为 long tick 或 int 计数，不做 NBT 持久化（仅内存状态）
 */
public class BootsPlayerState {

    // ==================== 共用 ====================
    /** 上次攻击 LivingEntity 的 tick */
    public long lastHitLivingTick = Long.MIN_VALUE;
    /** 上次被 LivingEntity 伤害的 tick */
    public long lastHurtByLivingTick = Long.MIN_VALUE;
    /** 上一次穿戴的靴子物品 */
    public Item lastBootsItem = null;

    // ==================== Boot1 - Untraceable Treads ====================
    /** 隐身冷却就绪 tick */
    public long untraceableCdReadyTick = 0;
    /** 隐身效果过期 tick */
    public long invisExpiresTick = 0;

    // ==================== Boot2 - Boundary Walker ====================
    /** 跳跃提升效果过期 tick */
    public long jumpExpiresTick = 0;

    // ==================== Boot3 - Ghost Step ====================
    /** 受击窗口开始 tick */
    public long damageWindowStartTick = 0;
    /** 受击窗口内计数 */
    public int damageWindowCount = 0;
    /** 上次受伤 tick（去抖用） */
    public long lastDamageTick = Long.MIN_VALUE;
    /** 上次攻击者 UUID（去抖用） */
    public UUID lastAttackerUuid = null;
    /** 应急幽灵冷却就绪 tick */
    public long ghostBurstCdReadyTick = 0;
    /** 应急幽灵效果过期 tick */
    public long ghostBurstExpiresTick = 0;
    /** 幽灵碰撞效果 A 是否激活（非战斗时常驻） */
    public boolean ghostStateA_active = false;

    // ==================== Boot4 - Marching Boots ====================
    /** 急行是否激活 */
    public boolean marchActive = false;
    /** 急行开始 tick */
    public long marchStartTick = 0;
    /** 急行冷却就绪 tick */
    public long marchCdReadyTick = 0;
    /** 速度效果过期 tick */
    public long speedExpiresTick = 0;

    // ==================== Boot5 - Gossamer Boots ====================
    /** 蛛网辅助效果过期 tick */
    public long webAssistExpiresTick = 0;
    /** 缓慢降阶效果过期 tick */
    public long slownessAdjustExpiresTick = 0;

    /**
     * 重置所有状态（玩家下线或重生时调用）
     */
    public void reset() {
        lastHitLivingTick = Long.MIN_VALUE;
        lastHurtByLivingTick = Long.MIN_VALUE;
        lastBootsItem = null;

        untraceableCdReadyTick = 0;
        invisExpiresTick = 0;

        jumpExpiresTick = 0;

        damageWindowStartTick = 0;
        damageWindowCount = 0;
        lastDamageTick = Long.MIN_VALUE;
        lastAttackerUuid = null;
        ghostBurstCdReadyTick = 0;
        ghostBurstExpiresTick = 0;
        ghostStateA_active = false;

        marchActive = false;
        marchStartTick = 0;
        marchCdReadyTick = 0;
        speedExpiresTick = 0;

        webAssistExpiresTick = 0;
        slownessAdjustExpiresTick = 0;
    }
}
