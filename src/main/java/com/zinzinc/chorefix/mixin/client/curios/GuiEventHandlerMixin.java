package com.zinzinc.chorefix.mixin.client.curios;

import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "top.theillusivec4.curios.client.gui.GuiEventHandler", remap = false)
public abstract class GuiEventHandlerMixin {
    @Inject(method = "onInventoryGuiInit", at = @At("HEAD"), cancellable = true)
    private void zinzincfix$checkScreenBeforeConfig(ScreenEvent.Init.Post event, CallbackInfo ci) {
        // The original handler only adds buttons to these screens, but reads its
        // config first even for the loading error screen, where configs may be absent.
        if (!(event.getScreen() instanceof InventoryScreen)
                && !(event.getScreen() instanceof CreativeModeInventoryScreen)) {
            ci.cancel();
        }
    }
}
