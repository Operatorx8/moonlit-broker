package dev.xqanzd.moonlitbroker.armor.mixin;

import dev.xqanzd.moonlitbroker.armor.effect.OldMarketHandler;
import dev.xqanzd.moonlitbroker.entity.MysteriousMerchantEntity;
import dev.xqanzd.moonlitbroker.trade.KatanaIdUtil;
import dev.xqanzd.moonlitbroker.trade.TradeConfig;
import dev.xqanzd.moonlitbroker.trade.item.TradeScrollItem;
import dev.xqanzd.moonlitbroker.registry.ModItems;
import dev.xqanzd.moonlitbroker.util.KatanaContractUtil;
import dev.xqanzd.moonlitbroker.world.KatanaOwnershipState;
import dev.xqanzd.moonlitbroker.world.MerchantUnlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.TradeOutputSlot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
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

    @Inject(method = "takeStack(I)Lnet/minecraft/item/ItemStack;", at = @At("HEAD"), cancellable = true, require = 1)
    private void armor$blockDuplicateKatanaTake(int amount, CallbackInfoReturnable<ItemStack> cir) {
        if (this.player.getWorld().isClient) {
            return;
        }
        if (!(this.player instanceof ServerPlayerEntity serverPlayer)) {
            return;
        }
        if (!(serverPlayer.getWorld() instanceof ServerWorld serverWorld)) {
            return;
        }

        TradeOffer offer = this.merchantInventory.getTradeOffer();
        ItemStack sell = offer != null ? offer.getSellItem() : this.merchantInventory.getStack(2);
        if (sell.isEmpty()) {
            return;
        }
        String katanaId = KatanaIdUtil.extractCanonicalKatanaId(sell);
        if (!KatanaIdUtil.isSecretKatana(katanaId)) {
            return;
        }
        if (KatanaContractUtil.isReclaimOutput(sell)) {
            return;
        }

        KatanaOwnershipState state = KatanaOwnershipState.getServerState(serverWorld);
        if (!state.hasOwned(serverPlayer.getUuid(), katanaId)) {
            return;
        }

        LOGGER.info("[MoonTrade] MM_KATANA_BLOCK player={} katanaId={} merchant={} offerIndex={} sellItem={} amount={}",
                serverPlayer.getUuid(), katanaId, merchantTag(this.merchant), resolveOfferIndex(offer),
                Registries.ITEM.getId(sell.getItem()), amount);
        cir.setReturnValue(ItemStack.EMPTY);
    }

    @Inject(method = "onTakeItem(Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/item/ItemStack;)V", at = @At("TAIL"), require = 1)
    private void armor$onTakeItem(PlayerEntity player, ItemStack stack, CallbackInfo ci) {
        if (player.getWorld().isClient) {
            return;
        }
        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            return;
        }

        handleKatanaOwnershipOnTake(serverPlayer, stack);

        // Task C: Scroll NBT init — ensure Trade Scrolls from trade output have correct
        // Uses/Grade
        if (stack.getItem() instanceof TradeScrollItem) {
            if (TradeScrollItem.getUses(stack) <= 0) {
                TradeScrollItem.initialize(stack, TradeConfig.GRADE_NORMAL);
                LOGGER.info("[MoonTrade] SCROLL_NBT_INIT player={} grade={} uses={}",
                        serverPlayer.getName().getString(), TradeConfig.GRADE_NORMAL, TradeScrollItem.getUses(stack));
            }
        }

        // Trade System: 声望增加（仅神秘商人）
        if (merchant instanceof MysteriousMerchantEntity) {
            incrementReputation(serverPlayer);
        }

        TradeOffer offer = merchantInventory.getTradeOffer();
        if (offer == null) {
            return;
        }

        int baseXp = offer.getMerchantExperience();
        if (baseXp <= 0) {
            return;
        }

        java.util.UUID merchantId;
        if (merchant instanceof net.minecraft.entity.Entity entity) {
            merchantId = entity.getUuid();
        } else {
            merchantId = new java.util.UUID(0L, 0L);
        }

        int tradeIndex = findTradeIndex(offer);

        long currentTick = serverPlayer.getWorld().getTime();

        int bonusXp = OldMarketHandler.onTradeComplete(serverPlayer, merchantId, tradeIndex, baseXp, currentTick);
        if (bonusXp > 0 && serverPlayer.getWorld() instanceof ServerWorld serverWorld) {
            OldMarketHandler.spawnBonusXp(serverWorld, serverPlayer, bonusXp);
        }
    }

    private void incrementReputation(ServerPlayerEntity player) {
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

    private void handleKatanaOwnershipOnTake(ServerPlayerEntity player, ItemStack taken) {
        String katanaId = KatanaIdUtil.extractCanonicalKatanaId(taken);
        if (!KatanaIdUtil.isSecretKatana(katanaId)) {
            return;
        }
        if (!(player.getWorld() instanceof ServerWorld serverWorld)) {
            return;
        }

        KatanaOwnershipState state = KatanaOwnershipState.getServerState(serverWorld);
        boolean added = state.addOwned(player.getUuid(), katanaId);
        if (added) {
            TradeOffer offer = this.merchantInventory.getTradeOffer();
            LOGGER.info("[MoonTrade] MM_KATANA_OWNED_ADD player={} katanaId={} merchant={} offerIndex={} takenItem={}",
                    player.getUuid(), katanaId, merchantTag(this.merchant), resolveOfferIndex(offer),
                    Registries.ITEM.getId(taken.getItem()));
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

    private int resolveOfferIndex(TradeOffer currentOffer) {
        if (currentOffer == null) {
            return -1;
        }
        var offers = this.merchant.getOffers();
        for (int i = 0; i < offers.size(); i++) {
            if (offers.get(i) == currentOffer) {
                return i;
            }
        }
        return -1;
    }

    private static String merchantTag(Merchant merchant) {
        if (merchant instanceof Entity entity) {
            return entity.getUuid().toString();
        }
        return merchant.getClass().getSimpleName();
    }
}
