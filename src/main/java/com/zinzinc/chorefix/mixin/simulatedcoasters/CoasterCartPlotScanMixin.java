package com.zinzinc.chorefix.mixin.simulatedcoasters;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "dev.silvergold.simulatedcoasters.track.cart.CoasterCartPlotScan", remap = false)
public abstract class CoasterCartPlotScanMixin {
    @WrapOperation(
            method = {"scanBearingCells", "forEachBearingWorldCell"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/LevelChunkSection;hasOnlyAir()Z"),
            require = 2,
            expect = 2,
            allow = 2
    )
    private static boolean zinzincfix$skipSectionsWithoutBearing(
            LevelChunkSection section, Operation<Boolean> original,
            @Local(argsOnly = true) Block bearingBlock) {
        // A palette can retain removed states, so a positive result still needs
        // the original scan. A negative result safely skips all 4096 cells.
        // Query the current palette each time: no cache to invalidate on edits,
        // assembly, disassembly, or chunk replacement.
        return original.call(section) || !section.maybeHas(state -> state.is(bearingBlock));
    }
}
