package com.zinzinc.chorefix.mixin.client.appleskin;

import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "squeek.appleskin.client.HUDOverlayHandler", remap = false)
public abstract class HUDOverlayHandlerMixin {
    @Inject(method = "onClientTick", at = @At("HEAD"), cancellable = true)
    private static void zinzincfix$waitForClientConfig(ClientTickEvent.Post event, CallbackInfo ci) {
        if (!ModConfigAccessor.zinzincfix$getConfigSpec().isLoaded()) {
            ci.cancel();
        }
    }
}
