package mod.test.mymodtest.armor.mixin;

import mod.test.mymodtest.armor.effect.OldMarketHandler;
import mod.test.mymodtest.entity.MysteriousMerchantEntity;
import mod.test.mymodtest.trade.TradeConfig;
import mod.test.mymodtest.world.KatanaOwnershipState;
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

    @Shadow
    @Final
    private PlayerEntity player;

    @Inject(method = "takeStack", at = @At("HEAD"), cancellable = true)
    private void armor$blockDuplicateKatanaTake(int amount, CallbackInfoReturnable<ItemStack> cir) {
        if (!(this.player instanceof ServerPlayerEntity serverPlayer)) {
            return;
        }
        if (!(this.merchant instanceof MysteriousMerchantEntity mysteriousMerchant)) {
            return;
        }
        if (!(serverPlayer.getWorld() instanceof ServerWorld serverWorld)) {
            return;
        }

        TradeOffer offer = this.merchantInventory.getTradeOffer();
        if (offer == null) {
            return;
        }
        String katanaId = MysteriousMerchantEntity.getKatanaIdFromKatanaStack(offer.getSellItem());
        if (katanaId.isEmpty()) {
            return;
        }

        KatanaOwnershipState state = KatanaOwnershipState.getServerState(serverWorld);
        if (!state.has(serverPlayer.getUuid(), katanaId)) {
            return;
        }

        offer.disable();
        mysteriousMerchant.tryMarkSecretSold(katanaId);
        mysteriousMerchant.sendOffers(serverPlayer, mysteriousMerchant.getDisplayName(), mysteriousMerchant.getExperience());
        serverPlayer.sendMessage(
            Text.literal("[神秘商人] 你已拥有该神器，无法重复购买。").formatted(Formatting.RED),
            true
        );
        LOGGER.info("[MoonTrade] MM_KATANA_BLOCK player={} katanaId={} merchant={}",
            serverPlayer.getUuid(), katanaId, mysteriousMerchant.getUuid());
        cir.setReturnValue(ItemStack.EMPTY);
    }

    /**
     * 在玩家取走交易结果后检查是否应该给予额外经验
     */
    @Inject(method = "onTakeItem", at = @At("TAIL"))
    private void armor$onTakeItem(PlayerEntity player, ItemStack stack, CallbackInfo ci) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            return;
        }

        // ========== Trade System: 声望增加 ==========
        // 仅当商人是 MysteriousMerchantEntity 时增加声望
        if (merchant instanceof MysteriousMerchantEntity mysteriousMerchant) {
            incrementReputation(serverPlayer, mysteriousMerchant);
            handleKatanaOwnershipOnTake(serverPlayer, mysteriousMerchant, stack);
        }

        // 获取当前交易
        TradeOffer offer = merchantInventory.getTradeOffer();
        if (offer == null) {
            return;
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

    private void handleKatanaOwnershipOnTake(ServerPlayerEntity player, MysteriousMerchantEntity merchant, ItemStack outputStack) {
        String katanaId = MysteriousMerchantEntity.getKatanaIdFromKatanaStack(outputStack);
        if (katanaId.isEmpty()) {
            return;
        }
        if (!(player.getWorld() instanceof ServerWorld serverWorld)) {
            return;
        }

        KatanaOwnershipState state = KatanaOwnershipState.getServerState(serverWorld);
        boolean added = state.add(player.getUuid(), katanaId);
        merchant.tryMarkSecretSold(katanaId);
        MysteriousMerchantEntity.clearSecretTradeMarker(outputStack);
        merchant.sendOffers(player, merchant.getDisplayName(), merchant.getExperience());

        if (added) {
            LOGGER.info("[MoonTrade] MM_KATANA_OWNED_ADD player={} katanaId={} merchant={}",
                player.getUuid(), katanaId, merchant.getUuid());
            LOGGER.info("[MoonTrade] MM_OWNERSHIP_ADD player={} katanaId={} merchant={}",
                player.getUuid(), katanaId, merchant.getUuid());
            LOGGER.info("[MoonTrade] MM_PURCHASED player={} katanaId={} merchant={}",
                player.getUuid(), katanaId, merchant.getUuid());
        } else if (TradeConfig.TRADE_DEBUG) {
            LOGGER.info("[MoonTrade] MM_KATANA_OWNED_ADD player={} katanaId={} merchant={} added=0",
                player.getUuid(), katanaId, merchant.getUuid());
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
