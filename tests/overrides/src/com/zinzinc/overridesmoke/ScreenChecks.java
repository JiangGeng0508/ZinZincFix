package com.zinzinc.overridesmoke;
import net.mehvahdjukaar.moonlight.api.util.CartographyTableHelper;
import net.mehvahdjukaar.supplementaries.reg.ModTextures;
import net.minecraft.client.gui.screens.inventory.CartographyTableScreen;
import net.minecraft.client.gui.screens.inventory.CyclingSlotBackground;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.CartographyTableMenu;
import java.lang.reflect.Field;
import java.util.*;

public class ScreenChecks {
    public static int baseTicks;
    private static int assertions;
    static void check(boolean test, String message) {
        assertions++;
        if (!test) throw new AssertionError(message);
    }
    static class Counter extends CyclingSlotBackground {
        int calls;
        List<ResourceLocation> last;
        Counter(int slot) { super(slot); }
        @Override public void tick(List<ResourceLocation> icons) { calls++; last = List.copyOf(icons); super.tick(icons); }
    }
    static Counter replace(CartographyTableScreen screen, String name, int slot) throws Exception {
        Field field = CartographyTableScreen.class.getDeclaredField(name); field.setAccessible(true);
        Counter counter = new Counter(slot); field.set(screen, counter); return counter;
    }
    public static void run() throws Exception {
        String mode = System.getProperty("overrideSmoke.mode");
        boolean left = Set.of("both", "left", "baseline").contains(mode);
        boolean right = Set.of("both", "right", "baseline").contains(mode);
        boolean baseline = mode.equals("baseline");
        Inventory inventory = new Inventory(null);
        CartographyTableScreen screen = new CartographyTableScreen(new CartographyTableMenu(1, inventory), inventory, Component.empty());
        Counter ml0 = left ? replace(screen, "moonlight$mapSlotIcons", 0) : null;
        Counter ml1 = left ? replace(screen, "moonlight$additionalSlotIcons", 1) : null;
        Counter su0 = right ? replace(screen, "supplementaries$mapSlotIcon", 0) : null;
        Counter su1 = right ? replace(screen, "supplementaries$ingredientsSlotIcon", 1) : null;
        var tick = CartographyTableScreen.class.getDeclaredMethod("containerTick"); tick.setAccessible(true);
        baseTicks = 0;
        tick.invoke(screen);
        check(baseTicks == 1, "Base container tick was duplicated before registering icons");
        if (left) check(ml0.calls == 0 && ml1.calls == 0, "Moonlight ignored hasCustomIcons guard");
        if (right) check(su0.calls == 1 && su1.calls == 1, "Supplementaries icon tick missing");
        CartographyTableHelper.addSlotIcon(0, ResourceLocation.parse("test:custom_map"));
        CartographyTableHelper.addSlotIcon(1, ResourceLocation.parse("test:custom_ingredient"));
        for (int i = 0; i < 80; i++) tick.invoke(screen);
        check(baseTicks == 81, "Base container tick must run exactly once per call");
        if (left) {
            check(ml0.calls == (baseline ? 0 : 80) && ml1.calls == (baseline ? 0 : 80), "Moonlight icon tick count incorrect");
            if (!baseline) {
                check(ml0.last.equals(CartographyTableHelper.getSlotIcons(0)), "Wrong map icon list");
                check(ml1.last.equals(CartographyTableHelper.getSlotIcons(1)), "Wrong ingredient icon list");
                CartographyTableHelper.addSlotIcon(0, ResourceLocation.parse("test:late_icon"));
                tick.invoke(screen);
                check(ml0.last.contains(ResourceLocation.parse("test:late_icon")), "Late icon registration ignored");
            }
        }
        if (right) {
            check(su0.calls == baseTicks && su1.calls == baseTicks, "Supplementaries tick lost or duplicated");
            check(su0.last.equals(ModTextures.MAP_ICONS), "Supplementaries map icons changed");
            check(su1.last.equals(ModTextures.CARTOGRAPHY_INGREDIENTS_ICONS), "Supplementaries ingredient icons changed");
        }
        if (baseline) System.out.println("BASELINE REPRODUCED: Moonlight custom icons receive zero ticks");
        System.out.println("SCREEN CHECKS " + mode + ": " + assertions);
    }
}
