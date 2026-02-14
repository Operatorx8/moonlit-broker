package dev.xqanzd.moonlitbroker.trade.client;

import dev.xqanzd.moonlitbroker.registry.ModItems;
import dev.xqanzd.moonlitbroker.trade.TradeAction;
import dev.xqanzd.moonlitbroker.trade.TradeConfig;
import dev.xqanzd.moonlitbroker.trade.network.TradeActionC2SPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.MerchantScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.screen.slot.Slot;
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
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Locale;

@Mixin(MerchantScreen.class)
public abstract class MerchantScreenRefreshMixin extends HandledScreen<MerchantScreenHandler> {
    @Unique
    private static final Logger MYMODTEST$LOGGER = LoggerFactory.getLogger("MoonTrade");
    @Unique
    private static final int MYMODTEST$PAGE_SIZE = 7;
    @Unique
    private static final int XQANZD_MOONLIT_BROKER$BOOK_WIDTH = 276;
    @Unique
    private static final int XQANZD_MOONLIT_BROKER$BOOK_HEIGHT = 166;
    @Unique
    private static final int XQANZD_MOONLIT_BROKER$SLOT_SIZE = 18;
    @Unique
    private static final int XQANZD_MOONLIT_BROKER$TRADE_ROW_WIDTH = 88;
    @Unique
    private static final int XQANZD_MOONLIT_BROKER$TRADE_ROW_HEIGHT = 20;
    @Unique
    private static final Identifier XQANZD_MOONLIT_BROKER$BOOK_TEXTURE =
        Identifier.of("xqanzd_moonlit_broker", "textures/gui/merchant/merchant_book.png");
    @Unique
    private static final Identifier XQANZD_MOONLIT_BROKER$SLOT_TEXTURE =
        Identifier.of("xqanzd_moonlit_broker", "textures/gui/merchant/slot_18.png");
    @Unique
    private static final Identifier XQANZD_MOONLIT_BROKER$TRADE_ROW_TEXTURE =
        Identifier.of("xqanzd_moonlit_broker", "textures/gui/merchant/trade_row.png");
    @Unique
    private static final Identifier XQANZD_MOONLIT_BROKER$TRADE_ROW_SELECTED_TEXTURE =
        Identifier.of("xqanzd_moonlit_broker", "textures/gui/merchant/trade_row_selected.png");
    @Unique
    private static final Identifier XQANZD_MOONLIT_BROKER$PREV_TEXTURE =
        Identifier.of("xqanzd_moonlit_broker", "textures/gui/merchant/prev.png");
    @Unique
    private static final Identifier XQANZD_MOONLIT_BROKER$NEXT_TEXTURE =
        Identifier.of("xqanzd_moonlit_broker", "textures/gui/merchant/next.png");
    @Unique
    private static final Identifier XQANZD_MOONLIT_BROKER$REFRESH_TEXTURE =
        Identifier.of("xqanzd_moonlit_broker", "textures/gui/merchant/refresh.png");
    @Unique
    private static final int XQANZD_MOONLIT_BROKER$BUTTON_SIZE_W = 20;
    @Unique
    private static final int XQANZD_MOONLIT_BROKER$BUTTON_SIZE_H = 18;
    @Unique
    private static final int XQANZD_MOONLIT_BROKER$PAGE_LABEL_X = 148;
    @Unique
    private static final int XQANZD_MOONLIT_BROKER$PAGE_LABEL_Y = 9;

    @Shadow
    private int selectedIndex;
    @Shadow
    int indexStartOffset;

    @Unique
    private ButtonWidget xqanzd_moonlit_broker$refreshButton;
    @Unique
    private ButtonWidget xqanzd_moonlit_broker$prevPageButton;
    @Unique
    private ButtonWidget xqanzd_moonlit_broker$nextPageButton;
    @Unique
    private boolean xqanzd_moonlit_broker$isMerchantUi;
    @Unique
    private int xqanzd_moonlit_broker$lastStartOffset = -1;
    @Unique
    private int xqanzd_moonlit_broker$lastSelectedIndex = -1;
    @Unique
    private boolean xqanzd_moonlit_broker$layoutLogged = false;
    @Unique
    private boolean xqanzd_moonlit_broker$texturePresentChecked;
    @Unique
    private boolean xqanzd_moonlit_broker$texturePresent = true;
    @Unique
    private boolean xqanzd_moonlit_broker$textureMissingLogged;

    protected MerchantScreenRefreshMixin(MerchantScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void xqanzd_moonlit_broker$injectControls(CallbackInfo ci) {
        this.xqanzd_moonlit_broker$isMerchantUi = xqanzd_moonlit_broker$isMysteriousMerchantScreen();
        MYMODTEST$LOGGER.info("[MoonTrade] MM_UI_INIT title=\"{}\" syncId={} offers={} merchantUi={}",
            this.title.getString(), this.handler.syncId, this.handler.getRecipes().size(), this.xqanzd_moonlit_broker$isMerchantUi ? 1 : 0);
        xqanzd_moonlit_broker$ensureButtons();
        this.xqanzd_moonlit_broker$lastSelectedIndex = this.selectedIndex;
        this.xqanzd_moonlit_broker$lastStartOffset = this.indexStartOffset;
        this.xqanzd_moonlit_broker$layoutLogged = false;
        if (this.xqanzd_moonlit_broker$isMerchantUi) {
            xqanzd_moonlit_broker$updateControlsAndTooltip();
            xqanzd_moonlit_broker$makeTradeOfferButtonsTransparent();
        }
    }

    @Inject(method = "drawBackground", at = @At("HEAD"), cancellable = true)
    private void xqanzd_moonlit_broker$drawBookBackground(DrawContext context, float delta, int mouseX, int mouseY, CallbackInfo ci) {
        if (!this.xqanzd_moonlit_broker$isMysteriousMerchantScreen()) {
            return;
        }
        if (!xqanzd_moonlit_broker$hasBookTexture()) {
            if (!this.xqanzd_moonlit_broker$textureMissingLogged) {
                this.xqanzd_moonlit_broker$textureMissingLogged = true;
                MYMODTEST$LOGGER.warn("[MoonTrade] Merchant UI skin texture missing: {}", XQANZD_MOONLIT_BROKER$BOOK_TEXTURE);
            }
            context.fill(this.x, this.y, this.x + XQANZD_MOONLIT_BROKER$BOOK_WIDTH, this.y + XQANZD_MOONLIT_BROKER$BOOK_HEIGHT, 0xFF2B2430);
            ci.cancel();
            return;
        }

        context.drawTexture(
            XQANZD_MOONLIT_BROKER$BOOK_TEXTURE,
            this.x,
            this.y,
            0,
            0,
            0,
            XQANZD_MOONLIT_BROKER$BOOK_WIDTH,
            XQANZD_MOONLIT_BROKER$BOOK_HEIGHT,
            XQANZD_MOONLIT_BROKER$BOOK_WIDTH,
            XQANZD_MOONLIT_BROKER$BOOK_HEIGHT
        );
        xqanzd_moonlit_broker$makeTradeOfferButtonsTransparent();
        xqanzd_moonlit_broker$drawTradeOfferRows(context);
        xqanzd_moonlit_broker$drawAllSlotBackgrounds(context);
        ci.cancel();
    }

    /**
     * Intercept mouse clicks on the trade list area: if the click maps to an
     * empty slot (offerIndex >= offers.size()), cancel the event entirely so
     * vanilla never calls setRecipeIndex / switchTo with an invalid index.
     * This prevents the IndexOutOfBoundsException crash on partially-filled pages.
     */
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void xqanzd_moonlit_broker$onMouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (!this.xqanzd_moonlit_broker$isMerchantUi) {
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
        int maxPages = xqanzd_moonlit_broker$ceilDiv(offersTotal, MYMODTEST$PAGE_SIZE);
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
                int page = xqanzd_moonlit_broker$getCurrentPage();
                MYMODTEST$LOGGER.warn("[MoonTrade] action=CLICK_EMPTY_IGNORED side=C row={} offerIndex={} offersTotal={} indexStartOffset={} page={} pageSize={}",
                    row, offerIndex, offersTotal, this.indexStartOffset, page, MYMODTEST$PAGE_SIZE);
                cir.setReturnValue(true);
                cir.cancel();
            }
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void xqanzd_moonlit_broker$afterRender(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        xqanzd_moonlit_broker$ensureButtons();
        if (!this.xqanzd_moonlit_broker$isMerchantUi) {
            return;
        }
        xqanzd_moonlit_broker$clampOffsetToOfferCount();
        xqanzd_moonlit_broker$updateControlsAndTooltip();
        xqanzd_moonlit_broker$drawButtonIcons(context, mouseX, mouseY);

        if (this.selectedIndex != this.xqanzd_moonlit_broker$lastSelectedIndex) {
            xqanzd_moonlit_broker$logSelectTrade();
            this.xqanzd_moonlit_broker$lastSelectedIndex = this.selectedIndex;
        }

        if (this.indexStartOffset != this.xqanzd_moonlit_broker$lastStartOffset) {
            xqanzd_moonlit_broker$logPageChange();
            this.xqanzd_moonlit_broker$lastStartOffset = this.indexStartOffset;
        }
    }

    /**
     * Intercept mouse wheel scroll: for our merchant UI, convert scroll into page jumps
     * instead of the vanilla +1/-1 offset behavior.
     */
    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void xqanzd_moonlit_broker$onMouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount, CallbackInfoReturnable<Boolean> cir) {
        if (!this.xqanzd_moonlit_broker$isMerchantUi) {
            return;
        }
        if (verticalAmount == 0.0D) {
            return;
        }
        xqanzd_moonlit_broker$changePage(verticalAmount < 0.0D ? 1 : -1);
        cir.setReturnValue(true);
        cir.cancel();
    }

    @Inject(method = "drawForeground", at = @At("TAIL"))
    private void xqanzd_moonlit_broker$drawPageLabel(DrawContext context, int mouseX, int mouseY, CallbackInfo ci) {
        if (!this.xqanzd_moonlit_broker$isMerchantUi) {
            return;
        }
        int offersTotal = this.handler.getRecipes().size();
        int totalPages = xqanzd_moonlit_broker$getTotalPages(offersTotal);
        int currentPage = xqanzd_moonlit_broker$getCurrentPage();
        Text pageText = Text.translatable("gui.xqanzd_moonlit_broker.trade.page", currentPage + 1, totalPages);
        context.drawText(
            this.textRenderer,
            pageText,
            XQANZD_MOONLIT_BROKER$PAGE_LABEL_X,
            XQANZD_MOONLIT_BROKER$PAGE_LABEL_Y,
            0x404040,
            false
        );
        // Hide vanilla "Inventory" title to keep the book layout clean.
        int invTextWidth = this.textRenderer.getWidth(this.playerInventoryTitle);
        int invY = this.backgroundHeight - 94;
        context.fill(8, invY - 1, 8 + invTextWidth + 2, invY + 10, 0xFFD7CFAB);
    }

    @Redirect(
        method = "drawForeground",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/DrawContext;drawText(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;IIIZ)I"
        ),
        require = 0
    )
    private int xqanzd_moonlit_broker$redirectForegroundText(
        DrawContext context,
        TextRenderer textRenderer,
        Text text,
        int x,
        int y,
        int color,
        boolean shadow
    ) {
        if (!this.xqanzd_moonlit_broker$isMerchantUi) {
            return context.drawText(textRenderer, text, x, y, color, shadow);
        }
        if (text.getString().equals(this.playerInventoryTitle.getString())) {
            return 0;
        }
        if (text.getString().equals(this.title.getString())) {
            int titleWidth = textRenderer.getWidth(text);
            int buttonsLeftX = this.backgroundWidth - 70;
            int clampedX = Math.min(x, buttonsLeftX - titleWidth - 4);
            clampedX = Math.max(4, clampedX);
            if (!this.xqanzd_moonlit_broker$layoutLogged) {
                this.xqanzd_moonlit_broker$layoutLogged = true;
                MYMODTEST$LOGGER.info("[MoonTrade] MM_UI_LAYOUT titleWidth={} buttonsLeftX={} titleX={} syncId={}",
                    titleWidth, buttonsLeftX, clampedX, this.handler.syncId);
            }
            return context.drawText(textRenderer, text, clampedX, y, color, shadow);
        }
        return context.drawText(textRenderer, text, x, y, color, shadow);
    }

    @Unique
    private void xqanzd_moonlit_broker$ensureButtons() {
        this.xqanzd_moonlit_broker$isMerchantUi = xqanzd_moonlit_broker$isMysteriousMerchantScreen();
        if (!this.xqanzd_moonlit_broker$isMerchantUi) {
            return;
        }
        int top = this.y + 8;
        int prevX = this.x + this.backgroundWidth - 66;
        int nextX = this.x + this.backgroundWidth - 44;
        int refreshX = this.x + this.backgroundWidth - 22;
        boolean restored = false;

        if (xqanzd_moonlit_broker$isButtonMissing(this.xqanzd_moonlit_broker$prevPageButton)) {
            this.xqanzd_moonlit_broker$prevPageButton = this.addDrawableChild(
                ButtonWidget.builder(Text.empty(), button -> xqanzd_moonlit_broker$changePage(-1))
                    .dimensions(prevX, top, XQANZD_MOONLIT_BROKER$BUTTON_SIZE_W, XQANZD_MOONLIT_BROKER$BUTTON_SIZE_H)
                    .build()
            );
            restored = true;
        } else {
            this.xqanzd_moonlit_broker$prevPageButton.setX(prevX);
            this.xqanzd_moonlit_broker$prevPageButton.setY(top);
        }
        this.xqanzd_moonlit_broker$prevPageButton.setAlpha(0.0F);
        this.xqanzd_moonlit_broker$prevPageButton.setMessage(Text.empty());

        if (xqanzd_moonlit_broker$isButtonMissing(this.xqanzd_moonlit_broker$nextPageButton)) {
            this.xqanzd_moonlit_broker$nextPageButton = this.addDrawableChild(
                ButtonWidget.builder(Text.empty(), button -> xqanzd_moonlit_broker$changePage(1))
                    .dimensions(nextX, top, XQANZD_MOONLIT_BROKER$BUTTON_SIZE_W, XQANZD_MOONLIT_BROKER$BUTTON_SIZE_H)
                    .build()
            );
            restored = true;
        } else {
            this.xqanzd_moonlit_broker$nextPageButton.setX(nextX);
            this.xqanzd_moonlit_broker$nextPageButton.setY(top);
        }
        this.xqanzd_moonlit_broker$nextPageButton.setAlpha(0.0F);
        this.xqanzd_moonlit_broker$nextPageButton.setMessage(Text.empty());

        if (xqanzd_moonlit_broker$isButtonMissing(this.xqanzd_moonlit_broker$refreshButton)) {
            this.xqanzd_moonlit_broker$refreshButton = this.addDrawableChild(
                ButtonWidget.builder(Text.empty(), button -> xqanzd_moonlit_broker$sendRefreshRequest())
                    .dimensions(refreshX, top, XQANZD_MOONLIT_BROKER$BUTTON_SIZE_W, XQANZD_MOONLIT_BROKER$BUTTON_SIZE_H)
                    .build()
            );
            restored = true;
        } else {
            this.xqanzd_moonlit_broker$refreshButton.setX(refreshX);
            this.xqanzd_moonlit_broker$refreshButton.setY(top);
        }
        this.xqanzd_moonlit_broker$refreshButton.setAlpha(0.0F);
        this.xqanzd_moonlit_broker$refreshButton.setMessage(Text.empty());

        if (restored) {
            MYMODTEST$LOGGER.info("[MoonTrade] MM_UI_BUTTONS_RESTORED syncId={} offers={}",
                this.handler.syncId, this.handler.getRecipes().size());
        }
    }

    @Unique
    private boolean xqanzd_moonlit_broker$isButtonMissing(ButtonWidget button) {
        return button == null || !this.children().contains(button);
    }

    @Unique
    private void xqanzd_moonlit_broker$sendRefreshRequest() {
        if (this.client == null || this.client.player == null) {
            return;
        }

        ClientPlayNetworking.send(new TradeActionC2SPacket(TradeAction.REFRESH.ordinal(), -1, -1));
        MYMODTEST$LOGGER.info("[MoonTrade] action=REFRESH_REQUEST side=C player={} merchant={} page={} offersHash={}",
            xqanzd_moonlit_broker$playerTag(), xqanzd_moonlit_broker$merchantTag(), xqanzd_moonlit_broker$getCurrentPage(),
            Integer.toHexString(xqanzd_moonlit_broker$computeOfferHash(this.handler.getRecipes())));
    }

    @Unique
    private boolean xqanzd_moonlit_broker$isMysteriousMerchantScreen() {
        String titleText = this.title.getString().toLowerCase(Locale.ROOT);
        if (titleText.contains("mysterious merchant") || titleText.contains("神秘商人")) {
            return true;
        }

        TradeOfferList offers = this.handler.getRecipes();
        for (TradeOffer offer : offers) {
            Identifier sellId = Registries.ITEM.getId(offer.getSellItem().getItem());
            if ("xqanzd_moonlit_broker".equals(sellId.getNamespace())) {
                return true;
            }
        }
        return false;
    }

    @Unique
    private void xqanzd_moonlit_broker$changePage(int deltaPage) {
        int offersTotal = this.handler.getRecipes().size();
        int pages = xqanzd_moonlit_broker$ceilDiv(offersTotal, MYMODTEST$PAGE_SIZE);
        if (pages <= 0) {
            this.indexStartOffset = 0;
            this.selectedIndex = 0;
            xqanzd_moonlit_broker$updateControlsAndTooltip();
            return;
        }
        int currentPage = this.indexStartOffset / MYMODTEST$PAGE_SIZE;
        int targetPage = MathHelper.clamp(currentPage + deltaPage, 0, pages - 1);
        this.indexStartOffset = targetPage * MYMODTEST$PAGE_SIZE;
        xqanzd_moonlit_broker$clampSelectedIndexToPage(offersTotal);
        xqanzd_moonlit_broker$updateControlsAndTooltip();
    }

    @Unique
    private void xqanzd_moonlit_broker$updateControlsAndTooltip() {
        int offersTotal = this.handler.getRecipes().size();
        int totalPages = xqanzd_moonlit_broker$getTotalPages(offersTotal);
        int currentPage = xqanzd_moonlit_broker$getCurrentPage();
        boolean showPager = offersTotal > MYMODTEST$PAGE_SIZE;

        if (this.xqanzd_moonlit_broker$prevPageButton != null) {
            this.xqanzd_moonlit_broker$prevPageButton.visible = showPager;
            this.xqanzd_moonlit_broker$prevPageButton.active = showPager && totalPages > 1 && currentPage > 0;
        }
        if (this.xqanzd_moonlit_broker$nextPageButton != null) {
            this.xqanzd_moonlit_broker$nextPageButton.visible = showPager;
            this.xqanzd_moonlit_broker$nextPageButton.active = showPager && totalPages > 1 && currentPage < totalPages - 1;
        }
        if (this.xqanzd_moonlit_broker$refreshButton != null) {
            this.xqanzd_moonlit_broker$refreshButton.visible = true;
            int need = TradeConfig.COST_REFRESH;
            int have = xqanzd_moonlit_broker$countRefreshScrollsClient();
            this.xqanzd_moonlit_broker$refreshButton.setTooltip(Tooltip.of(Text.translatable(
                "gui.xqanzd_moonlit_broker.trade.refresh.tooltip",
                new ItemStack(ModItems.TRADE_SCROLL).getName(),
                need,
                have
            )));
        }
    }

    @Unique
    private void xqanzd_moonlit_broker$clampOffsetToOfferCount() {
        int offersTotal = this.handler.getRecipes().size();
        int pages = xqanzd_moonlit_broker$ceilDiv(offersTotal, MYMODTEST$PAGE_SIZE);
        int lastStart = Math.max(0, (pages - 1) * MYMODTEST$PAGE_SIZE);
        this.indexStartOffset = MathHelper.clamp(this.indexStartOffset, 0, lastStart);
        this.indexStartOffset = (this.indexStartOffset / MYMODTEST$PAGE_SIZE) * MYMODTEST$PAGE_SIZE;
        xqanzd_moonlit_broker$clampSelectedIndexToPage(offersTotal);
    }

    /**
     * After a page change, if selectedIndex falls outside the visible page range,
     * force it to the first item of the current page.
     */
    @Unique
    private void xqanzd_moonlit_broker$clampSelectedIndexToPage() {
        xqanzd_moonlit_broker$clampSelectedIndexToPage(this.handler.getRecipes().size());
    }

    @Unique
    private void xqanzd_moonlit_broker$clampSelectedIndexToPage(int offersTotal) {
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
    private int xqanzd_moonlit_broker$ceilDiv(int value, int divisor) {
        if (value <= 0) {
            return 0;
        }
        return (value + divisor - 1) / divisor;
    }

    @Unique
    private int xqanzd_moonlit_broker$getCurrentPage() {
        return Math.max(0, this.indexStartOffset / MYMODTEST$PAGE_SIZE);
    }

    @Unique
    private int xqanzd_moonlit_broker$getTotalPages(int offersTotal) {
        return Math.max(1, (offersTotal + MYMODTEST$PAGE_SIZE - 1) / MYMODTEST$PAGE_SIZE);
    }

    @Unique
    private void xqanzd_moonlit_broker$logPageChange() {
        int offersTotal = this.handler.getRecipes().size();
        int page = xqanzd_moonlit_broker$getCurrentPage();
        int totalPages = xqanzd_moonlit_broker$getTotalPages(offersTotal);
        MYMODTEST$LOGGER.info("[MoonTrade] action=PAGE_CHANGE side=C player={} merchant={} page={} pages={} pageSize={} startIndex={} offersTotal={}",
            xqanzd_moonlit_broker$playerTag(), xqanzd_moonlit_broker$merchantTag(), page, totalPages, MYMODTEST$PAGE_SIZE, this.indexStartOffset, offersTotal);
    }

    @Unique
    private void xqanzd_moonlit_broker$logSelectTrade() {
        int offersTotal = this.handler.getRecipes().size();
        int page = xqanzd_moonlit_broker$getCurrentPage();
        int localIndex = this.selectedIndex - this.indexStartOffset;
        MYMODTEST$LOGGER.info("[MoonTrade] action=SELECT_TRADE side=C player={} merchant={} localIndex={} globalIndex={} page={} offersTotal={}",
            xqanzd_moonlit_broker$playerTag(), xqanzd_moonlit_broker$merchantTag(), localIndex, this.selectedIndex, page, offersTotal);
    }

    @Unique
    private String xqanzd_moonlit_broker$playerTag() {
        if (this.client == null || this.client.player == null) {
            return "none";
        }
        String uuid = this.client.player.getUuidAsString();
        String shortUuid = uuid.length() >= 8 ? uuid.substring(0, 8) : uuid;
        return this.client.player.getName().getString() + "(" + shortUuid + ")";
    }

    @Unique
    private String xqanzd_moonlit_broker$merchantTag() {
        return "screen#" + this.handler.syncId;
    }

    @Unique
    private int xqanzd_moonlit_broker$countRefreshScrollsClient() {
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
    private int xqanzd_moonlit_broker$computeOfferHash(TradeOfferList offers) {
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

    @Unique
    private void xqanzd_moonlit_broker$drawAllSlotBackgrounds(DrawContext context) {
        for (Slot slot : this.handler.slots) {
            int slotX = this.x + slot.x - 1;
            int slotY = this.y + slot.y - 1;
            context.drawTexture(
                XQANZD_MOONLIT_BROKER$SLOT_TEXTURE,
                slotX,
                slotY,
                0,
                0,
                XQANZD_MOONLIT_BROKER$SLOT_SIZE,
                XQANZD_MOONLIT_BROKER$SLOT_SIZE,
                XQANZD_MOONLIT_BROKER$SLOT_SIZE,
                XQANZD_MOONLIT_BROKER$SLOT_SIZE
            );
        }
    }

    @Unique
    private void xqanzd_moonlit_broker$drawTradeOfferRows(DrawContext context) {
        int offersTotal = this.handler.getRecipes().size();
        int listX = this.x + 5;
        int listY = this.y + 18;
        for (int row = 0; row < MYMODTEST$PAGE_SIZE; row++) {
            int globalIndex = this.indexStartOffset + row;
            boolean selected = globalIndex == this.selectedIndex && globalIndex < offersTotal;
            Identifier texture = selected ? XQANZD_MOONLIT_BROKER$TRADE_ROW_SELECTED_TEXTURE : XQANZD_MOONLIT_BROKER$TRADE_ROW_TEXTURE;
            int yPos = listY + row * XQANZD_MOONLIT_BROKER$TRADE_ROW_HEIGHT;
            context.drawTexture(
                texture,
                listX,
                yPos,
                0,
                0,
                0,
                XQANZD_MOONLIT_BROKER$TRADE_ROW_WIDTH,
                XQANZD_MOONLIT_BROKER$TRADE_ROW_HEIGHT,
                XQANZD_MOONLIT_BROKER$TRADE_ROW_WIDTH,
                XQANZD_MOONLIT_BROKER$TRADE_ROW_HEIGHT
            );
            if (globalIndex >= offersTotal) {
                context.fill(
                    listX,
                    yPos,
                    listX + XQANZD_MOONLIT_BROKER$TRADE_ROW_WIDTH,
                    yPos + XQANZD_MOONLIT_BROKER$TRADE_ROW_HEIGHT,
                    0x552A2318
                );
            }
        }
    }

    @Unique
    private void xqanzd_moonlit_broker$makeTradeOfferButtonsTransparent() {
        for (Element element : this.children()) {
            if (!(element instanceof ButtonWidget button)) {
                continue;
            }
            if (button.getWidth() == XQANZD_MOONLIT_BROKER$TRADE_ROW_WIDTH
                && button.getHeight() == XQANZD_MOONLIT_BROKER$TRADE_ROW_HEIGHT) {
                button.setAlpha(0.0F);
                button.setMessage(Text.empty());
            }
        }
    }

    @Unique
    private void xqanzd_moonlit_broker$drawButtonIcons(DrawContext context, int mouseX, int mouseY) {
        xqanzd_moonlit_broker$drawIconButton(context, this.xqanzd_moonlit_broker$prevPageButton, XQANZD_MOONLIT_BROKER$PREV_TEXTURE, mouseX, mouseY);
        xqanzd_moonlit_broker$drawIconButton(context, this.xqanzd_moonlit_broker$nextPageButton, XQANZD_MOONLIT_BROKER$NEXT_TEXTURE, mouseX, mouseY);
        xqanzd_moonlit_broker$drawIconButton(context, this.xqanzd_moonlit_broker$refreshButton, XQANZD_MOONLIT_BROKER$REFRESH_TEXTURE, mouseX, mouseY);
    }

    @Unique
    private void xqanzd_moonlit_broker$drawIconButton(DrawContext context, ButtonWidget button, Identifier texture, int mouseX, int mouseY) {
        if (button == null || !button.visible) {
            return;
        }
        int bx = button.getX();
        int by = button.getY();
        int bw = button.getWidth();
        int bh = button.getHeight();
        context.drawTexture(texture, bx, by, 0, 0, bw, bh, bw, bh);
        if (!button.active) {
            context.fill(bx, by, bx + bw, by + bh, 0x88000000);
            return;
        }
        if (button.isMouseOver(mouseX, mouseY)) {
            context.fill(bx, by, bx + bw, by + bh, 0x33FFFFFF);
        }
    }

    @Unique
    private boolean xqanzd_moonlit_broker$hasBookTexture() {
        if (this.xqanzd_moonlit_broker$texturePresentChecked) {
            return this.xqanzd_moonlit_broker$texturePresent;
        }
        this.xqanzd_moonlit_broker$texturePresentChecked = true;
        if (this.client == null) {
            this.xqanzd_moonlit_broker$texturePresent = false;
            return false;
        }
        this.xqanzd_moonlit_broker$texturePresent = this.client.getResourceManager()
            .getResource(XQANZD_MOONLIT_BROKER$BOOK_TEXTURE)
            .isPresent();
        return this.xqanzd_moonlit_broker$texturePresent;
    }
}
