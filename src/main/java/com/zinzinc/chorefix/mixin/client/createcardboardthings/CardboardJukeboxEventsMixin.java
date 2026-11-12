package com.zinzinc.chorefix.mixin.client.createcardboardthings;

import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.chaolux.createcardboardthings.client.sound.CardboardJukeboxEvents", remap = false)
public abstract class CardboardJukeboxEventsMixin {
    @Inject(method = "onPlaySound", at = @At("HEAD"), cancellable = true)
    private static void zinzincfix$waitForCommonConfig(PlaySoundEvent event, CallbackInfo ci) {
        // Menu music also reaches this callback before COMMON configs are loaded.
        // Leave the event's sound untouched until the jukebox setting is available.
        if (!ConfigAccessor.zinzincfix$getConfigSpec().isLoaded()) {
            ci.cancel();
        }
    }
}
