package com.zinzinc.chorefix.mixin.client.appleskin;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(targets = "squeek.appleskin.ModConfig", remap = false)
public interface ModConfigAccessor {
    @Accessor("SPEC")
    static ModConfigSpec zinzincfix$getConfigSpec() {
        throw new AssertionError("Mixin accessor was not applied");
    }
}
