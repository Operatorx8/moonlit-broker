package dev.xqanzd.moonlitbroker.katana.mixin;

import dev.xqanzd.moonlitbroker.registry.ModTags;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Enchantment.class)
public class EnchantmentMixin {

    @Inject(method = "isPrimaryItem", at = @At("RETURN"), cancellable = true)
    private void katana$blockSweepingEdgeAsPrimary(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (shouldForceDisableSweeping(stack, cir.getReturnValue())) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "isAcceptableItem", at = @At("RETURN"), cancellable = true)
    private void katana$blockSweepingEdgeAsAcceptable(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (shouldForceDisableSweeping(stack, cir.getReturnValue())) {
            cir.setReturnValue(false);
        }
    }

    private boolean shouldForceDisableSweeping(ItemStack stack, boolean originalResult) {
        if (!originalResult) {
            return false;
        }
        if (!stack.isIn(ModTags.Items.KATANA)) {
            return false;
        }
        return isSweepingEdge();
    }

    private boolean isSweepingEdge() {
        @SuppressWarnings("unchecked")
        Registry<Enchantment> enchantmentRegistry =
            (Registry<Enchantment>) Registries.REGISTRIES.get(RegistryKeys.ENCHANTMENT.getValue());
        if (enchantmentRegistry == null) {
            return false;
        }
        RegistryEntry<Enchantment> self = enchantmentRegistry.getEntry((Enchantment) (Object) this);
        return self.matchesKey(Enchantments.SWEEPING_EDGE);
    }
}
