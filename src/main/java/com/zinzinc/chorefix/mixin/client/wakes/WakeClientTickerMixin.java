package com.zinzinc.chorefix.mixin.client.wakes;

import com.leclowndu93150.wakes.config.WakesConfig;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.leclowndu93150.wakes.event.WakeClientTicker", remap = false)
public abstract class WakeClientTickerMixin {
    @Inject(method = "onClientTick", at = @At("HEAD"), cancellable = true)
    private static void zinzincfix$waitForClientConfig(ClientTickEvent.Pre event, CallbackInfo ci) {
        // Even kill() triggers WakeHandler's static config read. The first menu tick
        // can arrive before client configs are loaded, permanently failing that class.
        if (!WakesConfig.CLIENT_SPEC.isLoaded()) {
            ci.cancel();
        }
    }
}
