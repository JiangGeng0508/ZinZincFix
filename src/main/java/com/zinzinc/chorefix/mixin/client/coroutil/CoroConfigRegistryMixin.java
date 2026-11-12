package com.zinzinc.chorefix.mixin.client.coroutil;

import net.neoforged.fml.ModLoader;
import net.neoforged.neoforge.client.loading.ClientModLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.corosus.modconfig.CoroConfigRegistry", remap = false)
public abstract class CoroConfigRegistryMixin {
    @Shadow
    public boolean needsInitialConfigRegistration;

    @Inject(method = "allModsConfigsLoadedAndRegisteredHook", at = @At("HEAD"), cancellable = true)
    private void zinzincfix$waitForModLoading(CallbackInfo ci) {
        // The first client tick is not a loading-complete event. Preserve the flag
        // until CLIENT/COMMON configs and their owning mods have finished loading.
        if (needsInitialConfigRegistration && (ClientModLoader.isLoading() || ModLoader.hasErrors())) {
            ci.cancel();
        }
    }
}
