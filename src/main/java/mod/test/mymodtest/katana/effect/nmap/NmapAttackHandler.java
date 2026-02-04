package mod.test.mymodtest.katana.effect.nmap;

import mod.test.mymodtest.katana.item.KatanaItems;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NmapAttackHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("Nmap");

    public static void register() {
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient()) return ActionResult.PASS;
            if (!(entity instanceof LivingEntity target)) return ActionResult.PASS;
            if (!(player.getMainHandStack().isOf(KatanaItems.NMAP_KATANA))) return ActionResult.PASS;

            long currentTick = world.getTime();

            // Port Enumeration -> Penetration
            float penetration = NmapManager.getCurrentPenetration(player, currentTick);
            if (penetration > 0) {
                applyPenetration(player, target, penetration);
            }

            // Vulnerability Scan -> Crit
            if (shouldVulnCrit(player, target, currentTick)) {
                applyVulnCrit(player, target, currentTick);
            }

            return ActionResult.PASS;
        });

        LOGGER.info("[Nmap] Attack handler registered");
    }

    private static void applyPenetration(PlayerEntity player, LivingEntity target, float penetration) {
        float baseDamage = 5.0f;
        float armor = target.getArmor();
        float armorReduction = Math.min(armor * 0.04f, 0.8f);
        float bonusDamage = baseDamage * armorReduction * penetration;

        if (bonusDamage > 0.3f) {
            target.damage(player.getDamageSources().magic(), bonusDamage);

            if (NmapConfig.DEBUG) {
                LOGGER.info("[Nmap] PENETRATION: {}% -> +{} damage",
                    (int)(penetration * 100), String.format("%.1f", bonusDamage));
            }
        }
    }

    private static boolean shouldVulnCrit(PlayerEntity player, LivingEntity target, long currentTick) {
        if (target.getArmor() > 0) return false;
        if (target instanceof EnderDragonEntity || target instanceof WitherEntity) return false;
        return NmapManager.canVulnCrit(player, currentTick);
    }

    private static void applyVulnCrit(PlayerEntity player, LivingEntity target, long currentTick) {
        float baseDamage = 5.0f;
        float critBonus = baseDamage * (NmapConfig.VULN_CRIT_MULTIPLIER - 1.0f);

        target.damage(player.getDamageSources().playerAttack(player), critBonus);
        NmapManager.setVulnCritCooldown(player, currentTick);

        if (NmapConfig.DEBUG) {
            LOGGER.info("[Nmap] VULN CRIT! Target armor=0 -> +{} damage",
                String.format("%.1f", critBonus));
        }

        if (target.getWorld() instanceof ServerWorld serverWorld) {
            serverWorld.spawnParticles(
                ParticleTypes.CRIT,
                target.getX(), target.getY() + target.getHeight() / 2, target.getZ(),
                15, 0.3, 0.3, 0.3, 0.2
            );
        }

        player.playSound(SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 1.2f);
    }
}
