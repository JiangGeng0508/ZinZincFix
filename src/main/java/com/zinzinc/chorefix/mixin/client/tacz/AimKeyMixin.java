package com.zinzinc.chorefix.mixin.client.tacz;

import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.tacz.guns.client.input.AimKey", remap = false)
public abstract class AimKeyMixin {
    @Shadow
    private static boolean controllerAimDown;

    @Inject(method = "onAimHoldingPreInput", at = @At("HEAD"), cancellable = true)
    private static void zinzincfix$waitForClientConfig(ClientTickEvent.Post event, CallbackInfo ci) {
        var spec = ClientConfigAccessor.zinzincfix$getClientConfigSpec();
        if (spec == null || !spec.isLoaded()) {
            controllerAimDown = false;
            ci.cancel();
        }
    }
}
