package com.zinzinc.configsmoke;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.minecraft.core.registries.Registries;

@Mod("config_smoke")
public final class ConfigSmoke {
    public ConfigSmoke(IEventBus bus, ModContainer container) {
        if (Boolean.getBoolean("configSmoke.absent")) {
            System.out.println("CONFIG ABSENT PASS: packaged patch loads without optional mods");
            TestExit.finish(0);
        }
        try {
            ClientChecks.early();
            container.registerConfig(ModConfig.Type.COMMON, ClientChecks.cardboardSpec(), "config-smoke-cardboard.toml");
            ClientChecks.register();
            if (!Boolean.getBoolean("configSmoke.failure")) {
                bus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(ClientChecks::loadedPhase));
            }
            if (Boolean.getBoolean("configSmoke.failure")) {
                bus.addListener((RegisterEvent event) -> {
                    if (event.getRegistryKey().equals(Registries.ITEM)) {
                        throw new IllegalStateException("CONFIG SMOKE: intentional registry failure");
                    }
                });
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            TestExit.finish(1);
        }
    }
}
