package dev.xqanzd.moonlitbroker.screen;

import dev.xqanzd.moonlitbroker.mixin.ScreenHandlerTypeAccessor;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.village.Merchant;

/**
 * Custom merchant screen handler with repositioned slots for the book UI.
 * Extends MerchantScreenHandler to inherit all trade logic, vanilla sync
 * protocol,
 * and shift-click behavior.
 */
public class MoonlitMerchantScreenHandler extends MerchantScreenHandler {

    // --- Slot layout positions for 320x200 book UI (relative to screen origin) ---
    // Trade slots
    private static final int BUY1_X = 162, BUY1_Y = 48;
    private static final int BUY2_X = 188, BUY2_Y = 48;
    private static final int SELL_X = 252, SELL_Y = 48;
    // Player inventory 9x3
    private static final int INV_X = 143, INV_Y = 100;
    // Hotbar
    private static final int HOTBAR_X = 143, HOTBAR_Y = 158;

    /** Client-side constructor (no real merchant). */
    public MoonlitMerchantScreenHandler(int syncId, PlayerInventory playerInventory) {
        super(syncId, playerInventory);
        overrideType();
        repositionSlots();
    }

    /** Server-side constructor (real merchant entity). */
    public MoonlitMerchantScreenHandler(int syncId, PlayerInventory playerInventory, Merchant merchant) {
        super(syncId, playerInventory, merchant);
        overrideType();
        repositionSlots();
    }

    private void overrideType() {
        ((ScreenHandlerTypeAccessor) this).xqanzd_moonlit_broker$setType(ModScreenHandlers.MOONLIT_MERCHANT);
    }

    /**
     * Reposition all slots inherited from vanilla MerchantScreenHandler to match
     * the book layout. Slot indices remain unchanged (0=buy1, 1=buy2,
     * 2=sell, 3-29=inv, 30-38=hotbar) to preserve shift-click logic.
     */
    private void repositionSlots() {
        // Slot 0: first buy input
        setSlotPos(0, BUY1_X, BUY1_Y);
        // Slot 1: second buy input
        setSlotPos(1, BUY2_X, BUY2_Y);
        // Slot 2: sell output
        setSlotPos(2, SELL_X, SELL_Y);

        // Slots 3-29: player inventory 9x3
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int index = 3 + row * 9 + col;
                setSlotPos(index, INV_X + col * 18, INV_Y + row * 18);
            }
        }

        // Slots 30-38: hotbar
        for (int col = 0; col < 9; col++) {
            setSlotPos(30 + col, HOTBAR_X + col * 18, HOTBAR_Y);
        }
    }

    private void setSlotPos(int index, int x, int y) {
        Slot slot = this.slots.get(index);
        slot.x = x;
        slot.y = y;
    }
}
