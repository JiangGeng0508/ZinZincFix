package com.zinzinc.performancesmoke;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;

@Mod("performance_smoke")
public final class PerformanceSmoke {
    public PerformanceSmoke(IEventBus bus) {
        if (Boolean.getBoolean("performance.absent")) {
            try {
                for (String target : new String[]{"com.xm666.alivecombat.handler.PassHandler",
                        "einstein.subtle_effects.ticking.FireflyManager", "einstein.subtle_effects.ticking.BiomeEffectsManager"}) {
                    try {
                        Class.forName(target);
                        throw new AssertionError("Unexpected optional target: " + target);
                    } catch (ClassNotFoundException expected) {
                        // Client-only checks remain unloaded on a dedicated server.
                    }
                }
                System.out.println("PERFORMANCE ABSENT PASS: packaged patch loads without optional mods");
                System.exit(0);
            } catch (Throwable failure) {
                failure.printStackTrace();
                System.exit(1);
            }
        }
        bus.addListener((FMLLoadCompleteEvent event) -> event.enqueueWork(() -> {
            try {
                ClientChecks.run();
                System.exit(0);
            } catch (Throwable failure) {
                failure.printStackTrace();
                System.exit(1);
            }
        }));
    }
}
