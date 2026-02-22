package dev.xqanzd.moonlitbroker.trade.loot;

import dev.xqanzd.moonlitbroker.armor.util.CooldownManager;
import dev.xqanzd.moonlitbroker.registry.ModEntityTypeTags;
import dev.xqanzd.moonlitbroker.registry.ModItems;
import dev.xqanzd.moonlitbroker.trade.TradeConfig;
import dev.xqanzd.moonlitbroker.trade.item.BountyContractItem;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.Angerable;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 悬赏进度处理器
 * 监听击杀事件，更新玩家背包中匹配的 BountyContract 进度
 * P0: 扫描 main + offhand；进度提示在 25/50/75% 阈值触发 actionbar，带冷却
 * 完成后自动结算（不掉地上）；背包满则延迟到下次击杀重试。
 */
public class BountyProgressHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(BountyProgressHandler.class);

    private static final String COOLDOWN_KEY = "bounty_progress_hint";

    public static void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register(BountyProgressHandler::onEntityDeath);
        LOGGER.info("[MoonTrade] 悬赏进度处理器已注册");
    }

    private static void onEntityDeath(LivingEntity entity, DamageSource source) {
        // 只处理玩家击杀
        if (!(source.getAttacker() instanceof ServerPlayerEntity player)) {
            return;
        }

        // 收集 main + offhand 中所有契约槽位
        List<ItemStack> candidates = new ArrayList<>();
        for (int i = 0; i < player.getInventory().main.size(); i++) {
            candidates.add(player.getInventory().main.get(i));
        }
        for (int i = 0; i < player.getInventory().offHand.size(); i++) {
            candidates.add(player.getInventory().offHand.get(i));
        }

        boolean newlyCompleted = false;

        for (ItemStack stack : candidates) {
            if (!stack.isOf(ModItems.BOUNTY_CONTRACT))
                continue;
            if (!BountyContractItem.isValidContract(stack))
                continue;
            if (BountyContractItem.isCompleted(stack))
                continue;

            // 检查目标是否匹配
            if (!BountyContractItem.matchesTarget(stack, entity.getType()))
                continue;

            // Neutral gate: contract target in BOUNTY_NEUTRAL_TARGETS requires angry check
            Identifier contractTargetId = Identifier.tryParse(BountyContractItem.getTarget(stack));
            if (contractTargetId != null) {
                EntityType<?> contractTargetType = Registries.ENTITY_TYPE.get(contractTargetId);
                if (contractTargetType != null && contractTargetType.isIn(ModEntityTypeTags.BOUNTY_NEUTRAL_TARGETS)) {
                    if (!isAngeredAtPlayer(entity, player)) {
                        continue; // skip this contract
                    }
                }
            }

            // 更新进度
            int prevProgress = BountyContractItem.getProgress(stack);
            int required = BountyContractItem.getRequired(stack);
            newlyCompleted = BountyContractItem.incrementProgress(stack);
            int progress = BountyContractItem.getProgress(stack);
            String target = BountyContractItem.getTarget(stack);

            LOGGER.info("[MoonTrade] action=BOUNTY_PROGRESS player={} target={} progress={}/{} completed={}",
                    player.getName().getString(), target, progress, required,
                    newlyCompleted || BountyContractItem.isCompleted(stack));

            if (!newlyCompleted) {
                // 里程碑提示：25/50/75%，带冷却
                sendMilestoneHintIfNeeded(player, target, prevProgress, progress, required);
            }
            // newlyCompleted 的提示由下面的 tryAutoSettle 处理

            // 只更新第一张匹配的契约
            break;
        }

        // 自动结算：无论本次击杀是否推进了进度，都尝试结算背包中已完成的契约
        // 这同时覆盖"newlyCompleted"和"重试"两种场景
        if (player.getWorld() instanceof ServerWorld serverWorld) {
            BountySettleHelper.tryAutoSettleIfCompleted(serverWorld, player);
        }
    }

    /**
     * 在 25/50/75% 阈值时发送 actionbar 提示，带冷却节流
     */
    private static void sendMilestoneHintIfNeeded(ServerPlayerEntity player,
            String target, int prevProgress, int newProgress, int required) {
        if (required <= 0) return;

        // 检查是否跨越了 25/50/75% 阈值
        boolean crossed = false;
        for (int pct : new int[]{25, 50, 75}) {
            int threshold = Math.max(1, (required * pct + 99) / 100); // ceil: 避免小 required 时阈值折叠
            if (prevProgress < threshold && newProgress >= threshold) {
                crossed = true;
                break;
            }
        }
        if (!crossed) return;

        // 冷却检查
        long currentTick = player.getWorld().getTime();
        if (!CooldownManager.isReady(player.getUuid(), COOLDOWN_KEY, currentTick)) {
            return;
        }
        CooldownManager.setCooldown(player.getUuid(), COOLDOWN_KEY, currentTick,
                TradeConfig.BOUNTY_PROGRESS_HINT_CD_TICKS);

        Text targetName = resolveTargetName(target);
        int pct = (int) ((long) newProgress * 100 / required);
        player.sendMessage(
                Text.translatable(
                        "actionbar.xqanzd_moonlit_broker.bounty.progress",
                        targetName, newProgress, required, pct
                ).formatted(Formatting.YELLOW),
                true);
    }

    /**
     * 将 entity ID 字符串解析为可读名称
     */
    private static Text resolveTargetName(String target) {
        Identifier targetId = Identifier.tryParse(target);
        if (targetId != null) {
            EntityType<?> entityType = Registries.ENTITY_TYPE.get(targetId);
            if (entityType != null) {
                return entityType.getName();
            }
        }
        return Text.literal(target);
    }

    private static boolean isAngeredAtPlayer(LivingEntity entity, ServerPlayerEntity player) {
        if (entity instanceof MobEntity mobEntity) {
            LivingEntity target = mobEntity.getTarget();
            if (target != null && target.getUuid().equals(player.getUuid())) {
                return true;
            }
        }
        if (entity instanceof Angerable angerable) {
            return player.getUuid().equals(angerable.getAngryAt());
        }
        return false;
    }
}
