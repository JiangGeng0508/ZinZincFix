package com.zinzinc.chorefix.mixin.client.subtleeffects;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "einstein.subtle_effects.ticking.BiomeEffectsManager", remap = false)
public abstract class BiomeEffectsManagerMixin {
    @WrapOperation(
            method = "tick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getBiome(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/Holder;", ordinal = 0),
            require = 1,
            allow = 1
    )
    private static Holder<Biome> zinzincfix$reuseCandidateBiome(
            Level level, BlockPos pos, Operation<Holder<Biome>> original,
            @Share("zinzincfix$candidateBiome") LocalRef<Holder<Biome>> candidateBiome) {
        // The first call site repeats the same candidate for each registered
        // particle. The second call site checks the surface: leave it untouched.
        // @Share is local to this invocation, including recursive/parallel calls.
        Holder<Biome> biome = candidateBiome.get();
        if (biome == null) {
            biome = original.call(level, pos);
            candidateBiome.set(biome);
        }
        return biome;
    }
}
