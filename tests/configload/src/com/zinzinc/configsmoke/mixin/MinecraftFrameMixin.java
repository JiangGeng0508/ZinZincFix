package com.zinzinc.configsmoke.mixin;

import com.zinzinc.configsmoke.ClientChecks;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftFrameMixin {
    @Inject(method = "runTick", at = @At("TAIL"))
    private void configSmoke$checkErrorScreen(boolean renderLevel, CallbackInfo ci) {
        ClientChecks.afterFrame();
    }
}
