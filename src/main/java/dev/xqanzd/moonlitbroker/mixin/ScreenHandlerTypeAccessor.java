package dev.xqanzd.moonlitbroker.mixin;

import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ScreenHandler.class)
public interface ScreenHandlerTypeAccessor {
    @Mutable
    @Accessor("type")
    void xqanzd_moonlit_broker$setType(ScreenHandlerType<?> type);
}
