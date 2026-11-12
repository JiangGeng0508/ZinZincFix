package com.zinzinc.overridesmoke;
import com.sun.jna.Platform;
import com.sun.jna.platform.win32.Kernel32;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import com.simibubi.create.AllBlocks;
@Mod("override_smoke")
public class OverrideSmoke {
    public OverrideSmoke(IEventBus bus) {
        bus.addListener((RegisterEvent event) -> {
            if (event.getRegistryKey().equals(Registries.BLOCK))
                event.register(Registries.BLOCK, ResourceLocation.parse("override_smoke:panel"), () -> AllBlocks.TEST_BLOCK);
        });
        bus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(() -> {
            try {
                PanelChecks.run();
                if (FMLEnvironment.dist == Dist.CLIENT) ScreenChecks.run();
                System.out.println("OVERRIDE PASS " + System.getProperty("overrideSmoke.mode") + " " + FMLEnvironment.dist);
                finish(0);
            } catch (Throwable failure) { failure.printStackTrace(); finish(1); }
        }));
    }
    static void finish(int code) {
        System.out.flush(); System.err.flush();
        if (Platform.isWindows()) Kernel32.INSTANCE.TerminateProcess(Kernel32.INSTANCE.GetCurrentProcess(), code);
        Runtime.getRuntime().halt(code);
    }
}
