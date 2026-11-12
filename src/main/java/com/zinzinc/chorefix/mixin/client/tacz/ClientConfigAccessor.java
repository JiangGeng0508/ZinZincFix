package com.zinzinc.chorefix.mixin.client.tacz;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(targets = "com.tacz.guns.config.ClientConfig", remap = false)
public interface ClientConfigAccessor {
    @Accessor("CLIENT_CONFIG_SPEC")
    static ModConfigSpec zinzincfix$getClientConfigSpec() {
        throw new AssertionError("Mixin accessor was not applied");
    }
}
