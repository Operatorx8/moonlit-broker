package dev.xqanzd.moonlitbroker.armor.mixin;

import dev.xqanzd.moonlitbroker.armor.effect.ClearLedgerHandler;
import dev.xqanzd.moonlitbroker.armor.effect.SmugglerShinHandler;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Box;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 生物死亡事件 Mixin
 * 处理：
 * - 走私者之胫：击杀掉落增益
 * - 清账步态：击杀给速度
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityDeathMixin {

    @Shadow
    protected PlayerEntity attackingPlayer;

    /**
     * 记录 dropLoot 前周围已存在的 ItemEntity ID
     */
    @Unique
    private final Set<Integer> armor$beforeDropIds = new HashSet<>();

    /**
     * 临时存储捕获的掉落物列表（用于走私者之胫）
     */
    @Unique
    private final List<ItemStack> armor$capturedDrops = new ArrayList<>();

    /**
     * 在 dropLoot 开始前记录周围已存在的 ItemEntity
     */
    @Inject(method = "dropLoot", at = @At("HEAD"))
    private void armor$captureDropsStart(DamageSource source, boolean causedByPlayer, CallbackInfo ci) {
        armor$capturedDrops.clear();
        armor$beforeDropIds.clear();

        LivingEntity self = (LivingEntity)(Object)this;
        if (!causedByPlayer || !(attackingPlayer instanceof ServerPlayerEntity)) {
            return;
        }

        // 记录当前周围所有 ItemEntity 的 ID
        Box scanBox = new Box(
                self.getX() - 3, self.getY() - 3, self.getZ() - 3,
                self.getX() + 3, self.getY() + 3, self.getZ() + 3
        );
        List<ItemEntity> existingItems = self.getWorld().getEntitiesByClass(
                ItemEntity.class, scanBox, e -> true
        );
        for (ItemEntity item : existingItems) {
            armor$beforeDropIds.add(item.getId());
        }
    }

    /**
     * 在掉落物生成后处理
     */
    @Inject(method = "dropLoot", at = @At("TAIL"))
    private void armor$onDropLoot(DamageSource source, boolean causedByPlayer, CallbackInfo ci) {
        LivingEntity self = (LivingEntity)(Object)this;

        // 获取击杀者
        if (!causedByPlayer) {
            return;
        }

        if (!(attackingPlayer instanceof ServerPlayerEntity player)) {
            return;
        }

        // 获取 server tick（统一时钟）
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        long nowTick = server.getTicks();

        // 扫描周围新生成的 ItemEntity（不在 before 集合中）
        Box scanBox = new Box(
                self.getX() - 3, self.getY() - 3, self.getZ() - 3,
                self.getX() + 3, self.getY() + 3, self.getZ() + 3
        );
        List<ItemEntity> afterItems = self.getWorld().getEntitiesByClass(
                ItemEntity.class, scanBox, e -> true
        );

        // 筛选新生成的掉落物（ID 不在 before 集合中）
        for (ItemEntity item : afterItems) {
            if (!armor$beforeDropIds.contains(item.getId())) {
                armor$capturedDrops.add(item.getStack().copy());
            }
        }

        // 走私者之胫 - 击杀掉落增益
        SmugglerShinHandler.onEntityDeath(player, self, source, armor$capturedDrops, nowTick);

        // 清理
        armor$beforeDropIds.clear();
        armor$capturedDrops.clear();
    }

    /**
     * 在实体死亡时处理
     * 处理清账步态的击杀速度效果
     */
    @Inject(method = "onDeath", at = @At("HEAD"))
    private void armor$onDeath(DamageSource source, CallbackInfo ci) {
        LivingEntity self = (LivingEntity)(Object)this;

        // 获取击杀者
        if (!(attackingPlayer instanceof ServerPlayerEntity player)) {
            return;
        }

        // 获取 server tick（统一时钟）
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        long nowTick = server.getTicks();

        // 清账步态 - 击杀给速度
        ClearLedgerHandler.onKill(player, self, nowTick);
    }
}
