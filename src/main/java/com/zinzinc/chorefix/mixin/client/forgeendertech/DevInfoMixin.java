package com.zinzinc.chorefix.mixin.client.forgeendertech;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.InputEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.endertech.minecraft.forge.client.DevInfo", remap = false)
public abstract class DevInfoMixin {
    @Inject(method = "onKeyPressed", at = @At("HEAD"), cancellable = true)
    private static void zinzincfix$checkWorldBeforeConfig(InputEvent.Key event, CallbackInfo ci) {
        // Move the original world requirement ahead of ForgeEndertech.isDebugMode().
        if (Minecraft.getInstance().level == null) {
            ci.cancel();
        }
    }
}
