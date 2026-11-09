package com.zinzinc.chorefix.client;

import com.zinzinc.chorefix.ZinZincFix;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;

@EventBusSubscriber(modid = ZinZincFix.MODID, value = Dist.CLIENT)
public final class GrapplingHookModelFix {
    private static final String GRAPPLING_HOOKS_MOD_ID = "addon_gancho";

    private GrapplingHookModelFix() {}

    @SubscribeEvent
    public static void registerAdditionalModels(ModelEvent.RegisterAdditional event) {
        if (!ModList.get().isLoaded(GRAPPLING_HOOKS_MOD_ID)) {
            return;
        }

        // Create Grappling Hooks 1.2.0 initializes its PartialModels in FMLClientSetupEvent,
        // which can run after Flywheel has collected models for the initial resource reload.
        // Register the standalone models explicitly so Flywheel can always resolve them
        // at BakingCompleted, regardless of when AddonPartialModels is initialized.
        register(event, "plunger_tether");
        register(event, "tether_spool");
        register(event, "spool_joint");
    }

    private static void register(ModelEvent.RegisterAdditional event, String name) {
        event.register(ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(
                GRAPPLING_HOOKS_MOD_ID, "item/plunger_launcher/" + name)));
    }
}
