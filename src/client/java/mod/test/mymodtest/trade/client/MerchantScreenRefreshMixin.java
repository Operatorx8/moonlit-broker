package mod.test.mymodtest.trade.client;

import mod.test.mymodtest.registry.ModItems;
import mod.test.mymodtest.trade.TradeAction;
import mod.test.mymodtest.trade.TradeConfig;
import mod.test.mymodtest.trade.network.TradeActionC2SPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.MerchantScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Locale;

@Mixin(MerchantScreen.class)
public abstract class MerchantScreenRefreshMixin extends HandledScreen<MerchantScreenHandler> {
    @Unique
    private static final Logger MYMODTEST$LOGGER = LoggerFactory.getLogger("MoonTrade");
    @Unique
    private static final int MYMODTEST$PAGE_SIZE = 7;

    @Shadow
    private int selectedIndex;
    @Shadow
    int indexStartOffset;

    @Unique
    private ButtonWidget mymodtest$refreshButton;
    @Unique
    private ButtonWidget mymodtest$prevPageButton;
    @Unique
    private ButtonWidget mymodtest$nextPageButton;
    @Unique
    private boolean mymodtest$isMerchantUi;
    @Unique
    private int mymodtest$lastStartOffset = -1;
    @Unique
    private int mymodtest$lastSelectedIndex = -1;

    protected MerchantScreenRefreshMixin(MerchantScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void mymodtest$injectControls(CallbackInfo ci) {
        this.mymodtest$isMerchantUi = mymodtest$isMysteriousMerchantScreen();
        if (!this.mymodtest$isMerchantUi) {
            return;
        }

        int top = this.y + 4;
        this.mymodtest$prevPageButton = this.addDrawableChild(
            ButtonWidget.builder(Text.literal("<"), button -> mymodtest$changePage(-1))
                .dimensions(this.x + this.backgroundWidth - 66, top, 20, 18)
                .build()
        );
        this.mymodtest$nextPageButton = this.addDrawableChild(
            ButtonWidget.builder(Text.literal(">"), button -> mymodtest$changePage(1))
                .dimensions(this.x + this.backgroundWidth - 44, top, 20, 18)
                .build()
        );
        this.mymodtest$refreshButton = this.addDrawableChild(
            ButtonWidget.builder(Text.literal("⟳"), button -> mymodtest$sendRefreshRequest())
                .dimensions(this.x + this.backgroundWidth - 22, top, 18, 18)
                .build()
        );
        this.mymodtest$lastSelectedIndex = this.selectedIndex;
        this.mymodtest$lastStartOffset = this.indexStartOffset;
        mymodtest$updateControlsAndTooltip();
    }

    /**
     * Intercept mouse clicks on the trade list area: if the click maps to an
     * empty slot (offerIndex >= offers.size()), cancel the event entirely so
     * vanilla never calls setRecipeIndex / switchTo with an invalid index.
     * This prevents the IndexOutOfBoundsException crash on partially-filled pages.
     */
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void mymodtest$onMouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (!this.mymodtest$isMerchantUi) {
            return;
        }

        // --- Strict clamp of indexStartOffset & selectedIndex every click ---
        int offersTotal = this.handler.getRecipes().size();
        if (offersTotal <= 0) {
            this.indexStartOffset = 0;
            this.selectedIndex = 0;
            // No offers at all -- swallow any click on the trade list
            cir.setReturnValue(true);
            cir.cancel();
            return;
        }
        int maxPages = mymodtest$ceilDiv(offersTotal, MYMODTEST$PAGE_SIZE);
        int lastValidStart = Math.max(0, (maxPages - 1) * MYMODTEST$PAGE_SIZE);
        this.indexStartOffset = MathHelper.clamp(this.indexStartOffset, 0, lastValidStart);
        this.indexStartOffset = (this.indexStartOffset / MYMODTEST$PAGE_SIZE) * MYMODTEST$PAGE_SIZE;
        this.selectedIndex = MathHelper.clamp(this.selectedIndex, 0, offersTotal - 1);

        // --- Detect click inside the trade-list widget area ---
        // Vanilla trade list: x=[this.x+4, this.x+4+101], y=[this.y+18, this.y+18 + 7*20]
        int listLeft = this.x + 4;
        int listTop = this.y + 18;
        int listRight = listLeft + 101;
        int listBottom = listTop + MYMODTEST$PAGE_SIZE * 20;

        if (mouseX >= listLeft && mouseX < listRight && mouseY >= listTop && mouseY < listBottom) {
            int row = ((int) mouseY - listTop) / 20;
            row = MathHelper.clamp(row, 0, MYMODTEST$PAGE_SIZE - 1);
            int offerIndex = this.indexStartOffset + row;

            if (offerIndex >= offersTotal) {
                // --- Empty slot click: log and swallow ---
                int page = mymodtest$getCurrentPage();
                MYMODTEST$LOGGER.warn("[MoonTrade] action=CLICK_EMPTY_IGNORED side=C row={} offerIndex={} offersTotal={} indexStartOffset={} page={} pageSize={}",
                    row, offerIndex, offersTotal, this.indexStartOffset, page, MYMODTEST$PAGE_SIZE);
                cir.setReturnValue(true);
                cir.cancel();
            }
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void mymodtest$afterRender(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!this.mymodtest$isMerchantUi) {
            return;
        }
        mymodtest$clampOffsetToOfferCount();
        mymodtest$updateControlsAndTooltip();

        if (this.selectedIndex != this.mymodtest$lastSelectedIndex) {
            mymodtest$logSelectTrade();
            this.mymodtest$lastSelectedIndex = this.selectedIndex;
        }

        if (this.indexStartOffset != this.mymodtest$lastStartOffset) {
            mymodtest$logPageChange();
            this.mymodtest$lastStartOffset = this.indexStartOffset;
        }
    }

    /**
     * Intercept mouse wheel scroll: for our merchant UI, convert scroll into page jumps
     * instead of the vanilla +1/-1 offset behavior.
     */
    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void mymodtest$onMouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount, CallbackInfoReturnable<Boolean> cir) {
        if (!this.mymodtest$isMerchantUi) {
            return;
        }
        if (verticalAmount == 0.0D) {
            return;
        }
        mymodtest$changePage(verticalAmount < 0.0D ? 1 : -1);
        cir.setReturnValue(true);
        cir.cancel();
    }

    @Inject(method = "drawForeground", at = @At("TAIL"))
    private void mymodtest$drawPageLabel(DrawContext context, int mouseX, int mouseY, CallbackInfo ci) {
        if (!this.mymodtest$isMerchantUi) {
            return;
        }
        int offersTotal = this.handler.getRecipes().size();
        int totalPages = mymodtest$getTotalPages(offersTotal);
        int currentPage = mymodtest$getCurrentPage();
        Text pageText = Text.translatable("gui.mymodtest.trade.page", currentPage + 1, totalPages);
        context.drawText(
            this.textRenderer,
            pageText,
            this.backgroundWidth - 98,
            8,
            0x404040,
            false
        );
    }

    @Unique
    private void mymodtest$sendRefreshRequest() {
        if (this.client == null || this.client.player == null) {
            return;
        }

        ClientPlayNetworking.send(new TradeActionC2SPacket(TradeAction.REFRESH.ordinal(), -1));
        MYMODTEST$LOGGER.info("[MoonTrade] action=REFRESH_REQUEST side=C player={} merchant={} page={} offersHash={}",
            mymodtest$playerTag(), mymodtest$merchantTag(), mymodtest$getCurrentPage(),
            Integer.toHexString(mymodtest$computeOfferHash(this.handler.getRecipes())));
    }

    @Unique
    private boolean mymodtest$isMysteriousMerchantScreen() {
        String titleText = this.title.getString().toLowerCase(Locale.ROOT);
        if (titleText.contains("mysterious merchant") || titleText.contains("神秘商人")) {
            return true;
        }

        TradeOfferList offers = this.handler.getRecipes();
        for (TradeOffer offer : offers) {
            Identifier sellId = Registries.ITEM.getId(offer.getSellItem().getItem());
            if ("mymodtest".equals(sellId.getNamespace())) {
                return true;
            }
        }
        return false;
    }

    @Unique
    private void mymodtest$changePage(int deltaPage) {
        int offersTotal = this.handler.getRecipes().size();
        int pages = mymodtest$ceilDiv(offersTotal, MYMODTEST$PAGE_SIZE);
        if (pages <= 0) {
            this.indexStartOffset = 0;
            this.selectedIndex = 0;
            mymodtest$updateControlsAndTooltip();
            return;
        }
        int currentPage = this.indexStartOffset / MYMODTEST$PAGE_SIZE;
        int targetPage = MathHelper.clamp(currentPage + deltaPage, 0, pages - 1);
        this.indexStartOffset = targetPage * MYMODTEST$PAGE_SIZE;
        mymodtest$clampSelectedIndexToPage(offersTotal);
        mymodtest$updateControlsAndTooltip();
    }

    @Unique
    private void mymodtest$updateControlsAndTooltip() {
        int offersTotal = this.handler.getRecipes().size();
        int totalPages = mymodtest$getTotalPages(offersTotal);
        int currentPage = mymodtest$getCurrentPage();

        if (this.mymodtest$prevPageButton != null) {
            this.mymodtest$prevPageButton.active = totalPages > 1 && currentPage > 0;
        }
        if (this.mymodtest$nextPageButton != null) {
            this.mymodtest$nextPageButton.active = totalPages > 1 && currentPage < totalPages - 1;
        }
        if (this.mymodtest$refreshButton != null) {
            int need = TradeConfig.COST_REFRESH;
            int have = mymodtest$countRefreshScrollsClient();
            this.mymodtest$refreshButton.setTooltip(Tooltip.of(Text.translatable(
                "gui.mymodtest.trade.refresh.tooltip",
                new ItemStack(ModItems.TRADE_SCROLL).getName(),
                need,
                have
            )));
        }
    }

    @Unique
    private void mymodtest$clampOffsetToOfferCount() {
        int offersTotal = this.handler.getRecipes().size();
        int pages = mymodtest$ceilDiv(offersTotal, MYMODTEST$PAGE_SIZE);
        int lastStart = Math.max(0, (pages - 1) * MYMODTEST$PAGE_SIZE);
        this.indexStartOffset = MathHelper.clamp(this.indexStartOffset, 0, lastStart);
        this.indexStartOffset = (this.indexStartOffset / MYMODTEST$PAGE_SIZE) * MYMODTEST$PAGE_SIZE;
        mymodtest$clampSelectedIndexToPage(offersTotal);
    }

    /**
     * After a page change, if selectedIndex falls outside the visible page range,
     * force it to the first item of the current page.
     */
    @Unique
    private void mymodtest$clampSelectedIndexToPage() {
        mymodtest$clampSelectedIndexToPage(this.handler.getRecipes().size());
    }

    @Unique
    private void mymodtest$clampSelectedIndexToPage(int offersTotal) {
        if (offersTotal == 0) {
            this.selectedIndex = 0;
            return;
        }
        int pageStart = this.indexStartOffset;
        int pageEnd = Math.min(pageStart + MYMODTEST$PAGE_SIZE, offersTotal) - 1;
        if (this.selectedIndex < pageStart || this.selectedIndex > pageEnd) {
            this.selectedIndex = pageStart;
        }
        this.selectedIndex = MathHelper.clamp(this.selectedIndex, 0, offersTotal - 1);
    }

    @Unique
    private int mymodtest$ceilDiv(int value, int divisor) {
        if (value <= 0) {
            return 0;
        }
        return (value + divisor - 1) / divisor;
    }

    @Unique
    private int mymodtest$getCurrentPage() {
        return Math.max(0, this.indexStartOffset / MYMODTEST$PAGE_SIZE);
    }

    @Unique
    private int mymodtest$getTotalPages(int offersTotal) {
        return Math.max(1, (offersTotal + MYMODTEST$PAGE_SIZE - 1) / MYMODTEST$PAGE_SIZE);
    }

    @Unique
    private void mymodtest$logPageChange() {
        int offersTotal = this.handler.getRecipes().size();
        int page = mymodtest$getCurrentPage();
        int totalPages = mymodtest$getTotalPages(offersTotal);
        MYMODTEST$LOGGER.info("[MoonTrade] action=PAGE_CHANGE side=C player={} merchant={} page={} pages={} pageSize={} startIndex={} offersTotal={}",
            mymodtest$playerTag(), mymodtest$merchantTag(), page, totalPages, MYMODTEST$PAGE_SIZE, this.indexStartOffset, offersTotal);
    }

    @Unique
    private void mymodtest$logSelectTrade() {
        int offersTotal = this.handler.getRecipes().size();
        int page = mymodtest$getCurrentPage();
        int localIndex = this.selectedIndex - this.indexStartOffset;
        MYMODTEST$LOGGER.info("[MoonTrade] action=SELECT_TRADE side=C player={} merchant={} localIndex={} globalIndex={} page={} offersTotal={}",
            mymodtest$playerTag(), mymodtest$merchantTag(), localIndex, this.selectedIndex, page, offersTotal);
    }

    @Unique
    private String mymodtest$playerTag() {
        if (this.client == null || this.client.player == null) {
            return "none";
        }
        String uuid = this.client.player.getUuidAsString();
        String shortUuid = uuid.length() >= 8 ? uuid.substring(0, 8) : uuid;
        return this.client.player.getName().getString() + "(" + shortUuid + ")";
    }

    @Unique
    private String mymodtest$merchantTag() {
        return "screen#" + this.handler.syncId;
    }

    @Unique
    private int mymodtest$countRefreshScrollsClient() {
        if (this.client == null || this.client.player == null) {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < this.client.player.getInventory().size(); i++) {
            ItemStack stack = this.client.player.getInventory().getStack(i);
            if (stack.isOf(ModItems.TRADE_SCROLL)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    @Unique
    private int mymodtest$computeOfferHash(TradeOfferList offers) {
        int hash = 1;
        for (TradeOffer offer : offers) {
            Identifier firstId = Registries.ITEM.getId(offer.getOriginalFirstBuyItem().getItem());
            Identifier sellId = Registries.ITEM.getId(offer.getSellItem().getItem());
            hash = 31 * hash + firstId.hashCode();
            hash = 31 * hash + sellId.hashCode();
            hash = 31 * hash + offer.getOriginalFirstBuyItem().getCount();
            hash = 31 * hash + offer.getSellItem().getCount();
        }
        return hash;
    }
}
