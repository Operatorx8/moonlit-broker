package dev.xqanzd.moonlitbroker.katana.effect.nmap;

import dev.xqanzd.moonlitbroker.katana.item.KatanaItems;
import dev.xqanzd.moonlitbroker.katana.sound.ModSounds;
import dev.xqanzd.moonlitbroker.util.KatanaContractUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NmapFirewallHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("Nmap");

    /**
     * Debuff interception (called from Mixin)
     */
    public static boolean shouldBlockDebuff(PlayerEntity player, StatusEffectInstance effect, Entity source) {
        if (!isHoldingNmap(player)) return false;
        // Contract gate: dormant nmap → no firewall
        if (player.getWorld() instanceof net.minecraft.server.world.ServerWorld sw) {
            net.minecraft.item.ItemStack nmapStack = player.getMainHandStack().isOf(KatanaItems.NMAP_KATANA)
                    ? player.getMainHandStack() : player.getOffHandStack();
            if (!KatanaContractUtil.gateOrReturn(sw, player, nmapStack)) return false;
        }
        if (effect.getEffectType().value().getCategory() != StatusEffectCategory.HARMFUL) return false;
        if (source == null || !isHostile(source)) return false;

        long currentTick = player.getWorld().getTime();
        if (!NmapManager.canFirewallDebuff(player, currentTick)) return false;

        boolean isBoss = source instanceof EnderDragonEntity || source instanceof WitherEntity;
        float chance = isBoss ? NmapConfig.FIREWALL_DEBUFF_CHANCE_BOSS : NmapConfig.FIREWALL_DEBUFF_CHANCE;

        if (player.getWorld().getRandom().nextFloat() >= chance) return false;

        NmapManager.setFirewallDebuffCooldown(player, currentTick);

        if (NmapConfig.DEBUG) {
            LOGGER.info("[Nmap] FIREWALL blocked debuff: {}",
                effect.getEffectType().getIdAsString());
        }

        showFirewallEffect(player);
        return true;
    }

    /**
     * Projectile interception (called from Mixin)
     */
    public static boolean shouldBlockProjectile(PlayerEntity player, DamageSource source) {
        if (!isHoldingNmap(player)) return false;
        // Contract gate: dormant nmap → no firewall
        if (player.getWorld() instanceof net.minecraft.server.world.ServerWorld sw) {
            net.minecraft.item.ItemStack nmapStack = player.getMainHandStack().isOf(KatanaItems.NMAP_KATANA)
                    ? player.getMainHandStack() : player.getOffHandStack();
            if (!KatanaContractUtil.gateOrReturn(sw, player, nmapStack)) return false;
        }
        if (!(source.getSource() instanceof ProjectileEntity projectile)) return false;

        Entity owner = projectile.getOwner();
        if (owner == null || !isHostile(owner)) return false;

        long currentTick = player.getWorld().getTime();
        if (!NmapManager.canFirewallProj(player, currentTick)) return false;

        boolean isBoss = owner instanceof EnderDragonEntity || owner instanceof WitherEntity;
        float chance = isBoss ? NmapConfig.FIREWALL_PROJ_CHANCE_BOSS : NmapConfig.FIREWALL_PROJ_CHANCE;

        if (player.getWorld().getRandom().nextFloat() >= chance) return false;

        NmapManager.setFirewallProjCooldown(player, currentTick);

        if (NmapConfig.DEBUG) {
            LOGGER.info("[Nmap] FIREWALL blocked projectile from {}",
                owner.getName().getString());
        }

        showFirewallEffect(player);
        return true;
    }

    private static boolean isHoldingNmap(PlayerEntity player) {
        return player.getMainHandStack().isOf(KatanaItems.NMAP_KATANA) ||
               player.getOffHandStack().isOf(KatanaItems.NMAP_KATANA);
    }

    private static boolean isHostile(Entity entity) {
        return entity instanceof Monster ||
               entity instanceof EnderDragonEntity ||
               entity instanceof WitherEntity;
    }

    private static void showFirewallEffect(PlayerEntity player) {
        if (player instanceof ServerPlayerEntity serverPlayer) {
            serverPlayer.getServerWorld().spawnParticles(
                ParticleTypes.WITCH,
                player.getX(), player.getY() + 1, player.getZ(),
                15, 0.5, 0.5, 0.5, 0.1
            );
        }
        player.playSound(ModSounds.NMAP_FIREWALL, 0.6f, 1.8f);
    }
}
