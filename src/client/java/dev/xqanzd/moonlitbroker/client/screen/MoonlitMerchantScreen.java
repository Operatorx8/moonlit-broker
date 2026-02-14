package dev.xqanzd.moonlitbroker.client.screen;

import dev.xqanzd.moonlitbroker.registry.ModItems;
import dev.xqanzd.moonlitbroker.screen.MoonlitMerchantScreenHandler;
import dev.xqanzd.moonlitbroker.trade.TradeAction;
import dev.xqanzd.moonlitbroker.trade.TradeConfig;
import dev.xqanzd.moonlitbroker.trade.network.TradeActionC2SPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.SelectMerchantTradeC2SPacket;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Book UI v2 for the custom moonlit merchant handler.
 * Uses a fixed 320x200 canvas with 1:1 texture rendering.
 */
public class MoonlitMerchantScreen extends HandledScreen<MoonlitMerchantScreenHandler> {

    private static final Logger LOGGER = LoggerFactory.getLogger("MoonTrade");

    private static final int BOOK_WIDTH = 320;
    private static final int BOOK_HEIGHT = 200;
    private static final int LOGICAL_PAGE_SIZE = 18;
    private static final int VISIBLE_ROWS = 7;
    private static final int MAX_LOGICAL_PAGES = 4;

    private static final Identifier SLOT_TEXTURE = Identifier.of(
            "xqanzd_moonlit_broker", "textures/gui/merchant/slot_18.png");
    private static final Identifier TRADE_STRIP_TEXTURE = Identifier.of(
            "xqanzd_moonlit_broker", "textures/gui/merchant/trade_strip.png");
    private static final Identifier PREV_TEXTURE = Identifier.of(
            "xqanzd_moonlit_broker", "textures/gui/merchant/prev.png");
    private static final Identifier NEXT_TEXTURE = Identifier.of(
            "xqanzd_moonlit_broker", "textures/gui/merchant/next.png");
    private static final Identifier REFRESH_TEXTURE = Identifier.of(
            "xqanzd_moonlit_broker", "textures/gui/merchant/refresh.png");

    // Left-side trade list
    private static final int LIST_X = 10;
    private static final int LIST_Y = 28;
    private static final int LIST_ROW_WIDTH = 100;
    private static final int LIST_ROW_HEIGHT = 20;

    // Top-right controls
    private static final int BTN_W = 20;
    private static final int BTN_H = 18;
    private static final int BTN_Y = 8;
    private static final int BTN_PREV_X = 252;
    private static final int BTN_NEXT_X = 274;
    private static final int BTN_REFRESH_X = 296;

    private static final int PAGE_LABEL_X = 148;
    private static final int PAGE_LABEL_Y = 9;

    private int selectedIndex;
    private int indexStartOffset;

    private ButtonWidget prevButton;
    private ButtonWidget nextButton;
    private ButtonWidget refreshButton;
    private final List<ButtonWidget> tradeRowButtons = new ArrayList<>();
    private boolean initialOfferSyncDone;
    private int lastOfferCount = -1;
    private boolean hasManualTradeSelection;

    public MoonlitMerchantScreen(MoonlitMerchantScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = BOOK_WIDTH;
        this.backgroundHeight = BOOK_HEIGHT;
    }

    @Override
    protected void init() {
        super.init();

        this.prevButton = addDrawableChild(
                ButtonWidget.builder(Text.empty(), btn -> changePage(-1))
                        .dimensions(this.x + BTN_PREV_X, this.y + BTN_Y, BTN_W, BTN_H)
                        .build());
        this.prevButton.setAlpha(0.0F);

        this.nextButton = addDrawableChild(
                ButtonWidget.builder(Text.empty(), btn -> changePage(1))
                        .dimensions(this.x + BTN_NEXT_X, this.y + BTN_Y, BTN_W, BTN_H)
                        .build());
        this.nextButton.setAlpha(0.0F);

        this.refreshButton = addDrawableChild(
                ButtonWidget.builder(Text.empty(), btn -> sendRefreshRequest())
                        .dimensions(this.x + BTN_REFRESH_X, this.y + BTN_Y, BTN_W, BTN_H)
                        .build());
        this.refreshButton.setAlpha(0.0F);

        this.tradeRowButtons.clear();
        for (int row = 0; row < VISIBLE_ROWS; row++) {
            final int rowIndex = row;
            ButtonWidget rowBtn = addDrawableChild(
                    ButtonWidget.builder(Text.empty(), btn -> onTradeRowClicked(rowIndex))
                            .dimensions(
                                    this.x + LIST_X,
                                    this.y + LIST_Y + row * LIST_ROW_HEIGHT,
                                    LIST_ROW_WIDTH,
                                    LIST_ROW_HEIGHT)
                            .build());
            rowBtn.setAlpha(0.0F);
            this.tradeRowButtons.add(rowBtn);
        }

        this.selectedIndex = 0;
        this.indexStartOffset = 0;
        this.hasManualTradeSelection = false;
        clampOffsetToOfferCount();
        this.lastOfferCount = this.handler.getRecipes().size();
        this.initialOfferSyncDone = false;
        if (this.lastOfferCount > 0) {
            // Do not auto-fill on first open. Only user actions (row click/page switch)
            // should trigger server-side recipe selection + autofill.
            syncSelectedOffer(false, false);
            this.initialOfferSyncDone = true;
        }
        updateControlState();

        LOGGER.info("[MoonTrade] BOOK_UI_INIT syncId={} offers={}",
                this.handler.syncId, this.handler.getRecipes().size());
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int left = this.x;
        int top = this.y;
        int right = this.x + this.backgroundWidth;
        int bottom = this.y + this.backgroundHeight;

        // Solid parchment panel (1:1, no matrix scaling).
        context.fill(left, top, right, bottom, 0xFFD7CFAB);

        // Optional subtle outer shadow.
        context.fill(left - 2, top - 2, right + 2, top, 0x22000000);
        context.fill(left - 2, bottom, right + 2, bottom + 2, 0x22000000);
        context.fill(left - 2, top, left, bottom, 0x22000000);
        context.fill(right, top, right + 2, bottom, 0x22000000);

        // 1px border.
        context.fill(left, top, right, top + 1, 0xFFB7AE90);
        context.fill(left, bottom - 1, right, bottom, 0xFFB7AE90);
        context.fill(left, top, left + 1, bottom, 0xFFB7AE90);
        context.fill(right - 1, top, right, bottom, 0xFFB7AE90);

        drawTradeOfferRows(context);
        drawArrow(context);
        drawTradeStatus(context);
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        int titleWidth = this.textRenderer.getWidth(this.title);
        int maxTitleX = BTN_PREV_X - titleWidth - 6;
        int titleX = Math.max(8, Math.min(this.titleX, maxTitleX));
        context.drawText(this.textRenderer, this.title, titleX, this.titleY, 0xE39B26, false);

        int totalPages = getTotalPages(this.handler.getRecipes().size());
        int currentPage = getCurrentPage();
        Text pageText = Text.translatable("gui.xqanzd_moonlit_broker.trade.page", currentPage + 1, totalPages);
        context.drawText(this.textRenderer, pageText, PAGE_LABEL_X, PAGE_LABEL_Y, 0x5A4A35, false);

        // Intentionally do not render "Inventory" text.
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int offersTotal = this.handler.getRecipes().size();
        if (offersTotal != this.lastOfferCount) {
            this.lastOfferCount = offersTotal;
            this.initialOfferSyncDone = false;
            this.hasManualTradeSelection = false;
        }
        if (!this.initialOfferSyncDone && offersTotal > 0) {
            this.selectedIndex = MathHelper.clamp(this.selectedIndex, 0, offersTotal - 1);
            this.indexStartOffset = (this.selectedIndex / LOGICAL_PAGE_SIZE) * LOGICAL_PAGE_SIZE;
            clampOffsetToOfferCount();
            // Keep UI selection stable after first offer sync, but avoid auto-fill.
            syncSelectedOffer(false, false);
            this.initialOfferSyncDone = true;
        }

        clampOffsetToOfferCount();
        syncControlPositions();
        updateControlState();

        super.render(context, mouseX, mouseY, delta);

        drawButtonIcon(context, this.prevButton, PREV_TEXTURE, mouseX, mouseY);
        drawButtonIcon(context, this.nextButton, NEXT_TEXTURE, mouseX, mouseY);
        drawButtonIcon(context, this.refreshButton, REFRESH_TEXTURE, mouseX, mouseY);

        if (!drawTradePreviewTooltip(context, mouseX, mouseY)) {
            this.drawMouseoverTooltip(context, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int offersTotal = this.handler.getRecipes().size();
        if (offersTotal <= 0) {
            return true;
        }
        int pageStart = getCurrentPage() * LOGICAL_PAGE_SIZE;
        int pageItemCount = Math.max(0, Math.min(LOGICAL_PAGE_SIZE, offersTotal - pageStart));
        int maxOffset = Math.max(0, pageItemCount - VISIBLE_ROWS);
        if (maxOffset <= 0) {
            return true;
        }
        int currentOffset = this.indexStartOffset - pageStart;
        int step = verticalAmount < 0 ? 1 : (verticalAmount > 0 ? -1 : 0);
        if (step == 0) {
            return true;
        }
        int targetOffset = MathHelper.clamp(currentOffset + step, 0, maxOffset);
        if (targetOffset != currentOffset) {
            this.indexStartOffset = pageStart + targetOffset;
            updateControlState();
        }
        return true;
    }

    @Override
    protected void drawSlot(DrawContext context, Slot slot) {
        // Render slot background in the exact same coordinate space used by vanilla item rendering.
        int sx = slot.x - 1;
        int sy = slot.y - 1;
        context.drawTexture(SLOT_TEXTURE, sx, sy, 0.0F, 0.0F, 18, 18, 18, 18);
        super.drawSlot(context, slot);
    }

    private void onTradeRowClicked(int localRow) {
        int offersTotal = this.handler.getRecipes().size();
        int globalIndex = this.indexStartOffset + localRow;
        if (globalIndex >= offersTotal) {
            return;
        }
        this.selectedIndex = globalIndex;
        this.hasManualTradeSelection = true;
        // User-triggered selection should immediately auto-fill trade slots.
        syncSelectedOffer(true, true);
    }

    private void changePage(int delta) {
        int offersTotal = this.handler.getRecipes().size();
        int pages = getTotalPages(offersTotal);
        if (pages <= 0) {
            this.indexStartOffset = 0;
            this.selectedIndex = 0;
            this.hasManualTradeSelection = false;
            return;
        }
        int currentPage = getCurrentPage();
        int targetPage = MathHelper.clamp(currentPage + delta, 0, pages - 1);
        this.indexStartOffset = targetPage * LOGICAL_PAGE_SIZE;
        clampSelectedIndexToPage(offersTotal);
        // Page switch is visual-only. Do not change active trade server-side.
        this.hasManualTradeSelection = false;
        updateControlState();
    }

    private void sendRefreshRequest() {
        if (this.client == null || this.client.player == null) {
            return;
        }
        ClientPlayNetworking.send(new TradeActionC2SPacket(TradeAction.REFRESH.ordinal(), -1, getCurrentPage()));
        LOGGER.info("[MoonTrade] action=REFRESH_REQUEST side=C player={} page={}",
                this.client.player.getName().getString(), getCurrentPage());
    }

    private void updateControlState() {
        int offersTotal = this.handler.getRecipes().size();
        int totalPages = getTotalPages(offersTotal);
        int currentPage = getCurrentPage();
        boolean showPager = offersTotal > LOGICAL_PAGE_SIZE;

        if (this.prevButton != null) {
            this.prevButton.visible = showPager;
            this.prevButton.active = showPager && currentPage > 0;
        }
        if (this.nextButton != null) {
            this.nextButton.visible = showPager;
            this.nextButton.active = showPager && currentPage < totalPages - 1;
        }
        if (this.refreshButton != null) {
            this.refreshButton.visible = true;
            int need = TradeConfig.COST_REFRESH;
            int have = countRefreshScrollsClient();
            this.refreshButton.setTooltip(Tooltip.of(Text.translatable(
                    "gui.xqanzd_moonlit_broker.trade.refresh.tooltip",
                    new ItemStack(ModItems.TRADE_SCROLL).getName(),
                    need,
                    have)));
        }
        for (int row = 0; row < this.tradeRowButtons.size(); row++) {
            ButtonWidget rowButton = this.tradeRowButtons.get(row);
            int globalIndex = this.indexStartOffset + row;
            boolean hasOffer = globalIndex < offersTotal;
            rowButton.visible = hasOffer;
            rowButton.active = hasOffer;
        }
    }

    private void drawTradeOfferRows(DrawContext context) {
        TradeOfferList offers = this.handler.getRecipes();
        int offersTotal = offers.size();

        for (int row = 0; row < VISIBLE_ROWS; row++) {
            int globalIndex = this.indexStartOffset + row;
            if (globalIndex >= offersTotal) {
                // Do not render placeholder/empty rows; avoid "shadow list" at page tail.
                continue;
            }
            int rowX = this.x + LIST_X;
            int rowY = this.y + LIST_Y + row * LIST_ROW_HEIGHT;

            context.drawTexture(
                    TRADE_STRIP_TEXTURE,
                    rowX,
                    rowY,
                    0,
                    0,
                    LIST_ROW_WIDTH,
                    LIST_ROW_HEIGHT,
                    LIST_ROW_WIDTH,
                    LIST_ROW_HEIGHT);

            if (this.hasManualTradeSelection && globalIndex == this.selectedIndex) {
                context.fill(rowX, rowY, rowX + LIST_ROW_WIDTH, rowY + LIST_ROW_HEIGHT, 0x66D6A05A);
            }

            TradeOffer offer = offers.get(globalIndex);
            ItemStack buy1 = offer.getDisplayedFirstBuyItem();
            ItemStack buy2 = offer.getDisplayedSecondBuyItem();
            ItemStack sell = offer.getSellItem();

            context.drawItem(buy1, rowX + 2, rowY + 2);
            context.drawItemInSlot(this.textRenderer, buy1, rowX + 2, rowY + 2);

            if (!buy2.isEmpty()) {
                context.drawItem(buy2, rowX + 22, rowY + 2);
                context.drawItemInSlot(this.textRenderer, buy2, rowX + 22, rowY + 2);
            }

            context.drawText(this.textRenderer, Text.literal("\u2192"), rowX + 42, rowY + 5, 0x4C3B28, false);

            context.drawItem(sell, rowX + 56, rowY + 2);
            context.drawItemInSlot(this.textRenderer, sell, rowX + 56, rowY + 2);

            if (offer.isDisabled()) {
                context.fill(rowX, rowY, rowX + LIST_ROW_WIDTH, rowY + LIST_ROW_HEIGHT, 0x66AA3B2E);
            }
        }
    }

    private void drawArrow(DrawContext context) {
        if (this.handler.slots.size() <= 2) {
            return;
        }
        Slot buy2 = this.handler.slots.get(1);
        Slot sell = this.handler.slots.get(2);
        int arrowX = this.x + (buy2.x + sell.x) / 2 - 2;
        int arrowY = this.y + buy2.y + 1;
        context.drawText(this.textRenderer, Text.literal("\u2192"), arrowX, arrowY, 0x4C3B28, false);
    }

    private void drawTradeStatus(DrawContext context) {
        TradeOfferList offers = this.handler.getRecipes();
        if (offers.isEmpty() || this.selectedIndex >= offers.size()) {
            return;
        }

        TradeOffer offer = offers.get(this.selectedIndex);
        if (!offer.isDisabled() || this.handler.slots.size() <= 2) {
            return;
        }

        Slot sell = this.handler.slots.get(2);
        int sx = this.x + sell.x;
        int sy = this.y + sell.y;
        context.fill(sx, sy, sx + 16, sy + 16, 0x44FF0000);
    }

    private void drawButtonIcon(DrawContext context, ButtonWidget button, Identifier texture, int mouseX, int mouseY) {
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

    private void clampOffsetToOfferCount() {
        int offersTotal = this.handler.getRecipes().size();
        if (offersTotal <= 0) {
            this.indexStartOffset = 0;
            this.selectedIndex = 0;
            return;
        }
        int pages = getTotalPages(offersTotal);
        int currentPage = MathHelper.clamp(this.indexStartOffset / LOGICAL_PAGE_SIZE, 0, Math.max(0, pages - 1));
        int pageStart = currentPage * LOGICAL_PAGE_SIZE;
        int pageItemCount = Math.max(0, Math.min(LOGICAL_PAGE_SIZE, offersTotal - pageStart));
        int maxOffset = Math.max(0, pageItemCount - VISIBLE_ROWS);
        int pageOffset = MathHelper.clamp(this.indexStartOffset - pageStart, 0, maxOffset);
        this.indexStartOffset = pageStart + pageOffset;
        clampSelectedIndexToPage(offersTotal);
    }

    private void clampSelectedIndexToPage(int offersTotal) {
        if (offersTotal == 0) {
            this.selectedIndex = 0;
            return;
        }
        int pageStart = getCurrentPage() * LOGICAL_PAGE_SIZE;
        int pageEnd = Math.min(pageStart + LOGICAL_PAGE_SIZE, offersTotal) - 1;
        if (this.selectedIndex < pageStart || this.selectedIndex > pageEnd) {
            this.selectedIndex = pageStart;
        }
        this.selectedIndex = MathHelper.clamp(this.selectedIndex, 0, offersTotal - 1);
    }

    private int getCurrentPage() {
        return Math.max(0, this.indexStartOffset / LOGICAL_PAGE_SIZE);
    }

    private int getTotalPages(int offersTotal) {
        if (offersTotal <= 0) {
            return 1;
        }
        int pages = (offersTotal + LOGICAL_PAGE_SIZE - 1) / LOGICAL_PAGE_SIZE;
        return Math.max(1, Math.min(MAX_LOGICAL_PAGES, pages));
    }

    private int countRefreshScrollsClient() {
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

    private void syncSelectedOffer(boolean sendPacket, boolean applyLocalSwitch) {
        TradeOfferList offers = this.handler.getRecipes();
        if (offers.isEmpty()) {
            return;
        }
        this.selectedIndex = MathHelper.clamp(this.selectedIndex, 0, offers.size() - 1);
        this.handler.setRecipeIndex(this.selectedIndex);
        if (applyLocalSwitch) {
            this.handler.switchTo(this.selectedIndex);
        }
        if (sendPacket && this.client != null && this.client.getNetworkHandler() != null) {
            // Match vanilla MerchantScreen behavior: use SelectMerchantTradeC2SPacket,
            // not generic clickButton.
            this.client.getNetworkHandler().sendPacket(new SelectMerchantTradeC2SPacket(this.selectedIndex));
        }
    }

    private void syncControlPositions() {
        if (this.prevButton != null) {
            this.prevButton.setX(this.x + BTN_PREV_X);
            this.prevButton.setY(this.y + BTN_Y);
        }
        if (this.nextButton != null) {
            this.nextButton.setX(this.x + BTN_NEXT_X);
            this.nextButton.setY(this.y + BTN_Y);
        }
        if (this.refreshButton != null) {
            this.refreshButton.setX(this.x + BTN_REFRESH_X);
            this.refreshButton.setY(this.y + BTN_Y);
        }
        for (int i = 0; i < this.tradeRowButtons.size(); i++) {
            ButtonWidget rowBtn = this.tradeRowButtons.get(i);
            rowBtn.setX(this.x + LIST_X);
            rowBtn.setY(this.y + LIST_Y + i * LIST_ROW_HEIGHT);
        }
    }

    private boolean drawTradePreviewTooltip(DrawContext context, int mouseX, int mouseY) {
        TradeOfferList offers = this.handler.getRecipes();
        int offersTotal = offers.size();
        for (int row = 0; row < VISIBLE_ROWS; row++) {
            int globalIndex = this.indexStartOffset + row;
            if (globalIndex >= offersTotal) {
                continue;
            }

            TradeOffer offer = offers.get(globalIndex);
            int rowX = this.x + LIST_X;
            int rowY = this.y + LIST_Y + row * LIST_ROW_HEIGHT;

            if (isInside(mouseX, mouseY, rowX + 2, rowY + 2, 16, 16)) {
                context.drawItemTooltip(this.textRenderer, offer.getDisplayedFirstBuyItem(), mouseX, mouseY);
                return true;
            }
            ItemStack buy2 = offer.getDisplayedSecondBuyItem();
            if (!buy2.isEmpty() && isInside(mouseX, mouseY, rowX + 22, rowY + 2, 16, 16)) {
                context.drawItemTooltip(this.textRenderer, buy2, mouseX, mouseY);
                return true;
            }
            if (isInside(mouseX, mouseY, rowX + 56, rowY + 2, 16, 16)) {
                context.drawItemTooltip(this.textRenderer, offer.getSellItem(), mouseX, mouseY);
                return true;
            }
        }
        return false;
    }

    private static boolean isInside(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }
}
