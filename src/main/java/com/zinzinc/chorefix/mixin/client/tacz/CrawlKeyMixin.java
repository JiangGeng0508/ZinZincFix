package com.zinzinc.chorefix.mixin.client.tacz;

import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.tacz.guns.client.input.CrawlKey", remap = false)
public abstract class CrawlKeyMixin {
    @Shadow
    private static boolean controllerCrawlDown;

    @Inject(method = "onCrawlHoldingInput", at = @At("HEAD"), cancellable = true)
    private static void zinzincfix$waitForClientConfig(ClientTickEvent.Post event, CallbackInfo ci) {
        var spec = ClientConfigAccessor.zinzincfix$getClientConfigSpec();
        // Client ticks also run after mod loading fails, before CLIENT configs load.
        if (spec == null || !spec.isLoaded()) {
            controllerCrawlDown = false;
            ci.cancel();
        }
    }
}
