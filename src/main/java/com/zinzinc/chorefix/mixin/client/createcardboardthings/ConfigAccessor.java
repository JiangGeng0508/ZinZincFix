package com.zinzinc.chorefix.mixin.client.createcardboardthings;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(targets = "net.chaolux.createcardboardthings.Config", remap = false)
public interface ConfigAccessor {
    @Accessor("SPEC")
    static ModConfigSpec zinzincfix$getConfigSpec() {
        throw new AssertionError("Mixin accessor was not applied");
    }
}
