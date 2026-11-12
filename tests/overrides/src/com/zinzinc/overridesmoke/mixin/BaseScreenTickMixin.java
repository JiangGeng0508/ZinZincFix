package com.zinzinc.overridesmoke.mixin;
import com.zinzinc.overridesmoke.ScreenChecks;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(AbstractContainerScreen.class)
public class BaseScreenTickMixin {
    @Inject(method = "containerTick", at = @At("HEAD"))
    private void count(CallbackInfo ci) { ScreenChecks.baseTicks++; }
}
