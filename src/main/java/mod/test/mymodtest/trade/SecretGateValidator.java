package mod.test.mymodtest.trade;

import mod.test.mymodtest.entity.MysteriousMerchantEntity;
import mod.test.mymodtest.trade.item.MerchantMarkItem;
import mod.test.mymodtest.trade.item.TradeScrollItem;
import mod.test.mymodtest.world.MerchantUnlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 隐藏页门槛验证器
 * 服务端验证所有条件
 */
public class SecretGateValidator {
    private static final Logger LOGGER = LoggerFactory.getLogger(SecretGateValidator.class);

    /**
     * 验证结果
     */
    public record ValidationResult(boolean passed, String reason) {
        public static ValidationResult success() {
            return new ValidationResult(true, "OK");
        }
        public static ValidationResult fail(String reason) {
            return new ValidationResult(false, reason);
        }
    }

    /**
     * 验证玩家是否满足进入隐藏页的所有条件
     * 需要同时满足：
     * 1. 持有绑定到自己的 Merchant Mark
     * 2. 持有 SEALED 等级的 Trade Scroll，且 uses >= 2
     * 3. reputation >= 15
     */
    public static ValidationResult validate(PlayerEntity player, MysteriousMerchantEntity merchant) {
        // 条件1：检查 Merchant Mark
        if (!MerchantMarkItem.playerHasValidMark(player)) {
            if (TradeConfig.TRADE_DEBUG) {
                LOGGER.debug("[MoonTrade] GATE_FAIL player={} reason=NO_MARK", 
                    player.getName().getString());
            }
            return ValidationResult.fail("缺少商人印记");
        }

        // 条件2：检查 Trade Scroll
        ItemStack scroll = findSealedScroll(player);
        if (scroll.isEmpty()) {
            if (TradeConfig.TRADE_DEBUG) {
                LOGGER.debug("[MoonTrade] GATE_FAIL player={} reason=NO_SEALED_SCROLL", 
                    player.getName().getString());
            }
            return ValidationResult.fail("缺少封印交易卷轴");
        }
        
        int uses = TradeScrollItem.getUses(scroll);
        if (uses < TradeConfig.SECRET_SCROLL_USES_MIN) {
            if (TradeConfig.TRADE_DEBUG) {
                LOGGER.debug("[MoonTrade] GATE_FAIL player={} reason=SCROLL_USES_LOW uses={}", 
                    player.getName().getString(), uses);
            }
            return ValidationResult.fail("卷轴次数不足 (需要 " + TradeConfig.SECRET_SCROLL_USES_MIN + ")");
        }

        // 条件3：检查 reputation
        if (!(player.getWorld() instanceof ServerWorld serverWorld)) {
            return ValidationResult.fail("服务端验证失败");
        }
        
        MerchantUnlockState state = MerchantUnlockState.getServerState(serverWorld);
        MerchantUnlockState.Progress progress = state.getOrCreateProgress(player.getUuid());
        int reputation = progress.getReputation();
        
        if (reputation < TradeConfig.SECRET_REP_THRESHOLD) {
            if (TradeConfig.TRADE_DEBUG) {
                LOGGER.debug("[MoonTrade] GATE_FAIL player={} reason=REP_LOW rep={}", 
                    player.getName().getString(), reputation);
            }
            return ValidationResult.fail("声望不足 (需要 " + TradeConfig.SECRET_REP_THRESHOLD + ", 当前 " + reputation + ")");
        }

        // 所有条件满足
        LOGGER.info("[MoonTrade] GATE_PASS player={} rep={}", 
            player.getName().getString(), reputation);
        return ValidationResult.success();
    }

    /**
     * 在玩家背包中查找封印卷轴
     */
    public static ItemStack findSealedScroll(PlayerEntity player) {
        // 检查主手
        ItemStack mainHand = player.getMainHandStack();
        if (mainHand.getItem() instanceof TradeScrollItem && TradeScrollItem.isSealed(mainHand)) {
            return mainHand;
        }
        // 检查副手
        ItemStack offHand = player.getOffHandStack();
        if (offHand.getItem() instanceof TradeScrollItem && TradeScrollItem.isSealed(offHand)) {
            return offHand;
        }
        // 检查背包
        for (ItemStack stack : player.getInventory().main) {
            if (stack.getItem() instanceof TradeScrollItem && TradeScrollItem.isSealed(stack)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * 在玩家背包中查找任意卷轴（用于普通页操作）
     */
    public static ItemStack findAnyScroll(PlayerEntity player) {
        // 检查主手
        ItemStack mainHand = player.getMainHandStack();
        if (mainHand.getItem() instanceof TradeScrollItem) {
            return mainHand;
        }
        // 检查副手
        ItemStack offHand = player.getOffHandStack();
        if (offHand.getItem() instanceof TradeScrollItem) {
            return offHand;
        }
        // 检查背包
        for (ItemStack stack : player.getInventory().main) {
            if (stack.getItem() instanceof TradeScrollItem) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }
}
