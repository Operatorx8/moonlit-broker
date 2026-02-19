package dev.xqanzd.moonlitbroker.armor.mixin;

import dev.xqanzd.moonlitbroker.armor.effect.OldMarketHandler;
import dev.xqanzd.moonlitbroker.trade.KatanaIdUtil;
import dev.xqanzd.moonlitbroker.trade.TradeConfig;
import dev.xqanzd.moonlitbroker.trade.item.TradeScrollItem;
import dev.xqanzd.moonlitbroker.registry.ModItems;
import dev.xqanzd.moonlitbroker.util.KatanaContractUtil;
import dev.xqanzd.moonlitbroker.world.KatanaOwnershipState;
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
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TradeOutputSlot.class)
public class TradeOutputSlotMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("TradeOutputSlotMixin");
    /** Rate-limit deny hints: key = playerUUID:katanaId, value = last hint tick */
    private static final java.util.Map<String, Long> DENY_HINT_COOLDOWN = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long DENY_HINT_INTERVAL_TICKS = 100; // 5 seconds

    @Shadow
    @Final
    private MerchantInventory merchantInventory;

    @Shadow
    @Final
    private Merchant merchant;

    @Shadow
    @Final
    private PlayerEntity player;

    /** P0-2: Tracks whether this takeStack call wrote a pending claim that needs commit/rollback. */
    @Unique
    private boolean xqanzd_pendingKatanaClaim = false;

    @Unique
    private String xqanzd_pendingKatanaId = null;

    @Inject(method = "takeStack(I)Lnet/minecraft/item/ItemStack;", at = @At("HEAD"), cancellable = true, require = 1)
    private void armor$blockDuplicateKatanaTake(int amount, CallbackInfoReturnable<ItemStack> cir) {
        xqanzd_pendingKatanaClaim = false;
        xqanzd_pendingKatanaId = null;

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
        long now = KatanaOwnershipState.getOverworldTick(serverWorld);

        // Already owned — block delivery, rate-limited deny hint
        if (state.hasOwned(serverPlayer.getUuid(), katanaId)) {
            LOGGER.info("[MoonTrade] MM_KATANA_BLOCK player={} katanaId={} merchant={} offerIndex={} sellItem={} amount={}",
                    serverPlayer.getUuid(), katanaId, merchantTag(this.merchant), resolveOfferIndex(offer),
                    Registries.ITEM.getId(sell.getItem()), amount);

            String hintKey = serverPlayer.getUuid() + ":" + katanaId;
            Long lastHint = DENY_HINT_COOLDOWN.get(hintKey);
            if (lastHint == null || now - lastHint > DENY_HINT_INTERVAL_TICKS) {
                DENY_HINT_COOLDOWN.put(hintKey, now);
                serverPlayer.sendMessage(
                        net.minecraft.text.Text.translatable("error.xqanzd_moonlit_broker.katana.already_owned")
                                .formatted(net.minecraft.util.Formatting.RED),
                        true);
            }
            cir.setReturnValue(ItemStack.EMPTY);
            return;
        }

        // Any pending (same or different type) — block until previous commit/rollback/TTL
        if (state.hasAnyPending(serverPlayer.getUuid(), now)) {
            LOGGER.info("[MoonTrade] MM_KATANA_PENDING_BLOCK player={} requestedKatana={}", serverPlayer.getUuid(), katanaId);
            cir.setReturnValue(ItemStack.EMPTY);
            return;
        }

        // P0-2: Write pending claim (NOT ownership). Will commit on success, rollback on failure.
        boolean wrote = state.setPending(serverPlayer.getUuid(), katanaId, now);
        if (!wrote) {
            // setPending failed (should not happen after hasAnyPending check, but defensive)
            LOGGER.warn("[MoonTrade] MM_KATANA_PENDING_FAIL player={} katanaId={}", serverPlayer.getUuid(), katanaId);
            cir.setReturnValue(ItemStack.EMPTY);
            return;
        }
        xqanzd_pendingKatanaClaim = true;
        xqanzd_pendingKatanaId = katanaId;
        LOGGER.info("[MoonTrade] MM_KATANA_PENDING player={} katanaId={} merchant={} offerIndex={}",
                serverPlayer.getUuid(), katanaId, merchantTag(this.merchant), resolveOfferIndex(offer));
        // Allow take to proceed
    }

    @Inject(method = "takeStack(I)Lnet/minecraft/item/ItemStack;", at = @At("RETURN"), require = 1)
    private void armor$commitOrRollbackKatanaClaim(int amount, CallbackInfoReturnable<ItemStack> cir) {
        if (!xqanzd_pendingKatanaClaim) {
            return;
        }
        xqanzd_pendingKatanaClaim = false;

        if (!(this.player instanceof ServerPlayerEntity serverPlayer)) {
            return;
        }
        if (!(serverPlayer.getWorld() instanceof ServerWorld serverWorld)) {
            return;
        }

        KatanaOwnershipState state = KatanaOwnershipState.getServerState(serverWorld);
        ItemStack result = cir.getReturnValue();

        if (result != null && !result.isEmpty()) {
            // Verify the delivered item actually matches the pending katanaId
            String resultKatanaId = KatanaIdUtil.extractCanonicalKatanaId(result);
            if (!xqanzd_pendingKatanaId.equals(resultKatanaId)) {
                // Result is a different item than what we pended — rollback, don't commit wrong type
                state.clearPending(serverPlayer.getUuid());
                LOGGER.warn("[MoonTrade] MM_KATANA_RESULT_MISMATCH player={} pendingId={} resultId={} resultItem={} merchant={}",
                        serverPlayer.getUuid(), xqanzd_pendingKatanaId, resultKatanaId,
                        Registries.ITEM.getId(result.getItem()), merchantTag(this.merchant));
            } else {
                // Result matches pending — commit ownership
                boolean committed = state.commitPending(serverPlayer.getUuid(), xqanzd_pendingKatanaId);
                if (committed) {
                    LOGGER.info("[MoonTrade] MM_KATANA_COMMIT player={} katanaId={} merchant={}",
                            serverPlayer.getUuid(), xqanzd_pendingKatanaId, merchantTag(this.merchant));
                } else {
                    // P1-1: Assertion — take succeeded with matching katana but commitPending returned false.
                    // Pending was TTL-expired, already committed, or mismatched internally.
                    LOGGER.error("[MoonTrade] MM_KATANA_COMMIT_MISS player={} katanaId={} resultItem={} merchant={} — " +
                                    "item delivered but pending commit failed; ownership may not be recorded!",
                            serverPlayer.getUuid(), xqanzd_pendingKatanaId,
                            Registries.ITEM.getId(result.getItem()), merchantTag(this.merchant));
                }
            }
        } else {
            // Take failed (inventory full, etc.) — rollback pending
            state.clearPending(serverPlayer.getUuid());
            LOGGER.info("[MoonTrade] MM_KATANA_ROLLBACK player={} katanaId={} merchant={}",
                    serverPlayer.getUuid(), xqanzd_pendingKatanaId, merchantTag(this.merchant));
        }
        xqanzd_pendingKatanaId = null;
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

        // Scroll NBT init — ensure Trade Scrolls from trade output have correct Uses/Grade
        if (stack.getItem() instanceof TradeScrollItem) {
            if (TradeScrollItem.getUses(stack) <= 0) {
                TradeScrollItem.initialize(stack, TradeConfig.GRADE_NORMAL);
                LOGGER.info("[MoonTrade] SCROLL_NBT_INIT player={} grade={} uses={}",
                        serverPlayer.getName().getString(), TradeConfig.GRADE_NORMAL, TradeScrollItem.getUses(stack));
            }
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
