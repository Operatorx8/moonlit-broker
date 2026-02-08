package mod.test.mymodtest.armor.mixin;

import mod.test.mymodtest.armor.effect.OldMarketHandler;
import mod.test.mymodtest.entity.MysteriousMerchantEntity;
import mod.test.mymodtest.trade.TradeConfig;
import mod.test.mymodtest.world.MerchantUnlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.TradeOutputSlot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.village.Merchant;
import net.minecraft.village.MerchantInventory;
import net.minecraft.village.TradeOffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 交易输出槽 Mixin
 * 处理：
 * - 旧市护甲：交易经验加成
 * - Trade System：声望增加（仅在实际取走结果时）
 */
@Mixin(TradeOutputSlot.class)
public class TradeOutputSlotMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("TradeOutputSlotMixin");

    @Shadow
    @Final
    private MerchantInventory merchantInventory;

    @Shadow
    @Final
    private Merchant merchant;

    /**
     * 在玩家取走交易结果后检查是否应该给予额外经验
     */
    @Inject(method = "onTakeItem", at = @At("TAIL"))
    private void armor$onTakeItem(PlayerEntity player, ItemStack stack, CallbackInfo ci) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            return;
        }

        // 获取当前交易
        TradeOffer offer = merchantInventory.getTradeOffer();
        if (offer == null) {
            return;
        }

        // ========== Trade System: 声望增加 ==========
        // 仅当商人是 MysteriousMerchantEntity 时增加声望
        if (merchant instanceof MysteriousMerchantEntity mysteriousMerchant) {
            incrementReputation(serverPlayer, mysteriousMerchant);
            
            // ========== 3.2 FIX: Secret sale enforcement ==========
            // Check if this is the epic katana offer and mark as sold
            checkAndMarkSecretSold(serverPlayer, mysteriousMerchant, offer, stack);
        }

        // 获取交易经验
        int baseXp = offer.getMerchantExperience();
        if (baseXp <= 0) {
            return;
        }

        // 获取商人 UUID（如果是实体商人）
        java.util.UUID merchantId;
        if (merchant instanceof net.minecraft.entity.Entity entity) {
            merchantId = entity.getUuid();
        } else {
            // 非实体商人（如工作台），使用固定 UUID
            merchantId = new java.util.UUID(0L, 0L);
        }

        // 获取交易索引（通过遍历交易列表找到当前交易）
        int tradeIndex = findTradeIndex(offer);

        long currentTick = serverPlayer.getWorld().getTime();

        // 旧市护甲 - 交易经验加成
        int bonusXp = OldMarketHandler.onTradeComplete(serverPlayer, merchantId, tradeIndex, baseXp, currentTick);
        if (bonusXp > 0 && serverPlayer.getWorld() instanceof ServerWorld serverWorld) {
            OldMarketHandler.spawnBonusXp(serverWorld, serverPlayer, bonusXp);
        }
    }

    /**
     * 增加玩家声望（仅在实际取走交易结果时调用）
     */
    private void incrementReputation(ServerPlayerEntity player, MysteriousMerchantEntity merchant) {
        if (!(player.getWorld() instanceof ServerWorld serverWorld)) {
            return;
        }

        MerchantUnlockState state = MerchantUnlockState.getServerState(serverWorld);
        MerchantUnlockState.Progress progress = state.getOrCreateProgress(player.getUuid());
        
        progress.incrementReputation();
        state.markDirty();
        
        int newRep = progress.getReputation();
        
        if (TradeConfig.TRADE_DEBUG) {
            LOGGER.debug("[MoonTrade] REP_INCREMENT player={} newRep={}", 
                player.getName().getString(), newRep);
        }
        
        // 达到门槛时提示
        if (newRep == TradeConfig.SECRET_REP_THRESHOLD) {
            LOGGER.info("[MoonTrade] REP_THRESHOLD_REACHED player={} rep={}", 
                player.getName().getString(), newRep);
        }
    }

    /**
     * 3.2 FIX: Check if this is the epic katana purchase and mark as sold
     * This prevents the katana from appearing again for this merchant
     */
    private void checkAndMarkSecretSold(ServerPlayerEntity player, MysteriousMerchantEntity merchant, 
                                         TradeOffer offer, ItemStack outputStack) {
        // Marked secret outputs are tagged when the hidden offer is built.
        if (!MysteriousMerchantEntity.isSecretTradeOutput(outputStack)) {
            return;
        }
        String secretId = MysteriousMerchantEntity.getSecretTradeMarkerId(outputStack);
        if (secretId == null || secretId.isEmpty()) {
            LOGGER.warn("[MoonTrade] SECRET_MARKER_MISSING player={} merchant={} output={} action=skip_mark_sold",
                player.getName().getString(), merchant.getUuid().toString().substring(0, 8), outputStack.getItem());
            return;
        }
        
        // Atomically mark as sold - if already sold, this returns false
        boolean marked = merchant.tryMarkSecretSold(secretId);
        
        if (marked) {
            MysteriousMerchantEntity.clearSecretTradeMarker(outputStack);
            LOGGER.info("[MoonTrade] SECRET_KATANA_PURCHASED player={} merchant={} secretId={}", 
                player.getName().getString(), merchant.getUuid().toString().substring(0, 8), secretId);
            
            player.sendMessage(
                Text.literal("[神秘商人] 你获得了珍贵的隐藏神器！")
                    .formatted(Formatting.GOLD, Formatting.BOLD),
                false
            );
        } else {
            // This shouldn't happen if offer generation is correct, but log it
            LOGGER.warn("[MoonTrade] SECRET_ALREADY_SOLD_ON_PURCHASE player={} merchant={} secretId={}", 
                player.getName().getString(), merchant.getUuid().toString().substring(0, 8), secretId);
        }
    }

    /**
     * 查找交易在列表中的索引
     */
    private int findTradeIndex(TradeOffer currentOffer) {
        var offers = merchant.getOffers();
        for (int i = 0; i < offers.size(); i++) {
            if (offers.get(i) == currentOffer) {
                return i;
            }
        }
        return -1;
    }
}
