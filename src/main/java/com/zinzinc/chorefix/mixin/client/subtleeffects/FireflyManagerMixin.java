package com.zinzinc.chorefix.mixin.client.subtleeffects;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "einstein.subtle_effects.ticking.FireflyManager", remap = false)
public abstract class FireflyManagerMixin {
    @Inject(
            method = "tick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getBiome(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/Holder;"),
            cancellable = true,
            require = 1,
            allow = 1
    )
    private static void zinzincfix$rejectBrightCandidates(
            Level level, BlockPos pos, BlockState state, RandomSource random, CallbackInfo ci) {
        // These are the original necessary light conditions, before any random
        // draws. Keep the original float conversion, including large world times.
        // Config, player warmup, dimension and season guards have already run.
        float time = (float) level.getDayTime() % 24000.0F;
        boolean night = time > 13000.0F && time < 23000.0F;
        if ((!night && level.getBrightness(LightLayer.SKY, pos) != 0)
                || level.getBrightness(LightLayer.BLOCK, pos) > 5) {
            ci.cancel();
        }
    }
}
