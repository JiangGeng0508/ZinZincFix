package com.zinzinc.configsmoke;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.ModLoader;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.loading.ClientModLoader;
import net.neoforged.neoforge.client.gui.LoadingErrorScreen;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;

import java.lang.reflect.*;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipFile;

public final class ClientChecks {
    static final String CRAWL = "com.tacz.guns.client.input.CrawlKey";
    static final String AIM = "com.tacz.guns.client.input.AimKey";
    static final String HUD = "squeek.appleskin.client.HUDOverlayHandler";
    static final String CORO = "com.corosus.modconfig.CoroConfigRegistry";
    static final String CURIOS = "top.theillusivec4.curios.client.gui.GuiEventHandler";
    static final String DEV_INFO = "com.endertech.minecraft.forge.client.DevInfo";
    static int checks, ticks;
    static boolean ran;

    static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    static Field field(Class<?> type, String name) throws Exception {
        var f = type.getDeclaredField(name); f.setAccessible(true); return f;
    }

    static Class<?> target(String name) throws Exception { return Class.forName(name); }

    static Object invokeTick(Class<?> type, String name) throws Exception {
        try { return type.getMethod(name, ClientTickEvent.Post.class).invoke(null, new ClientTickEvent.Post()); }
        catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception exception) throw exception;
            throw e;
        }
    }

    static ModConfigSpec taczSpec() throws Exception {
        return (ModConfigSpec) target("com.tacz.guns.config.ClientConfig").getField("CLIENT_CONFIG_SPEC").get(null);
    }

    static ModConfigSpec appleSpec() throws Exception {
        return (ModConfigSpec) target("squeek.appleskin.ModConfig").getField("SPEC").get(null);
    }

    static Object coro() throws Exception { return target(CORO).getMethod("instance").invoke(null); }

    static boolean needsCoroInit() throws Exception { return target(CORO).getField("needsInitialConfigRegistration").getBoolean(coro()); }

    static void callCoro() throws Exception { target(CORO).getMethod("allModsConfigsLoadedAndRegisteredHook").invoke(coro()); }

    public static void early() throws Exception {
        check(ClientModLoader.isLoading(), "Expected mod construction before loading completes");
        check(!taczSpec().isLoaded() && !appleSpec().isLoaded(), "Expected unloaded client configs");
        for (String name : List.of(CRAWL, AIM, HUD, CORO)) {
            check(Arrays.stream(target(name).getDeclaredMethods()).anyMatch(m -> m.getName().contains("zinzincfix$waitFor")), "Missing mixin: " + name);
        }
        unloadedInputs();
        screenAndKeyGuards(true);
        check(needsCoroInit(), "CoroUtil should still need initial synchronization");
        callCoro();
        check(needsCoroInit(), "Early callback consumed CoroUtil's initialization flag");
        for (var entry : Map.of(CRAWL, "onCrawlHoldingInput", AIM, "onAimHoldingPreInput", HUD, "onClientTick").entrySet()) {
            Class<?> original = new OriginalLoader().loadClass(entry.getKey());
            try {
                invokeTick(original, entry.getValue());
                throw new AssertionError("Original did not reproduce unloaded config failure: " + entry.getKey());
            } catch (IllegalStateException expected) {
                check(expected.getMessage().contains("before config is loaded"), "Unexpected baseline failure");
            }
        }
        System.out.println("CONFIG EARLY PASS: original TaCZ crawl/aim and AppleSkin fail; patched callbacks defer; CoroUtil flag preserved");
    }

    static void unloadedInputs() throws Exception {
        for (var entry : Map.of(CRAWL, "controllerCrawlDown", AIM, "controllerAimDown").entrySet()) {
            var type = target(entry.getKey());
            field(type, entry.getValue()).setBoolean(null, true);
            invokeTick(type, type.getName().equals(CRAWL) ? "onCrawlHoldingInput" : "onAimHoldingPreInput");
            check(!field(type, entry.getValue()).getBoolean(null), "Stale controller input survived unloaded config");
        }
        var hud = target(HUD);
        float before = field(hud, "unclampedFlashAlpha").getFloat(null);
        invokeTick(hud, "onClientTick");
        check(before == field(hud, "unclampedFlashAlpha").getFloat(null), "Unloaded AppleSkin advanced animation");
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (ClientTickEvent.Post event) -> {
            if (ran) return;
            try {
                if (Boolean.getBoolean("configSmoke.failure")) {
                    check(ModLoader.hasErrors(), "Expected registry failure");
                    check(needsCoroInit(), "Failure tick consumed CoroUtil initialization");
                    if (++ticks >= 5) {
                        check(!taczSpec().isLoaded() && !appleSpec().isLoaded(), "Registry failure should precede config loading");
                        System.out.println("CONFIG FAILURE PASS: loading error screen reached; five event-bus tick posts after registry rollback without secondary config errors");
                        finish(0);
                    }
                }
            } catch (Throwable failure) {
                failure.printStackTrace(); finish(1);
            }
        });
    }

    public static void afterFrame() {
        if (Boolean.getBoolean("configSmoke.failure") && Minecraft.getInstance().screen instanceof LoadingErrorScreen) {
            // NeoForge normally leaves the game bus stopped after registry failure.
            // Reproduce the full modpack's prematurely active bus on the render thread.
            NeoForge.EVENT_BUS.start();
            for (int i = 0; i < 5; i++) NeoForge.EVENT_BUS.post(new ClientTickEvent.Post());
        }
    }

    public static void loadedPhase() {
        try {
            loaded();
            System.out.println("CONFIG PRESENT PASS: " + checks + " checks against original mod bytecode");
            finish(0);
        } catch (Throwable failure) { failure.printStackTrace(); finish(1); }
    }

    static void finish(int code) {
        TestExit.finish(code);
    }

    static void screenAndKeyGuards(boolean unloaded) throws Exception {
        var patched = target(CURIOS);
        var original = new OriginalLoader().loadClass(CURIOS);
        for (Screen screen : List.of(new TitleScreen(), new Screen(Component.literal("loading error test")) {})) {
            var event = new ScreenEvent.Init.Post(screen, new ArrayList<>(), listener -> {
                throw new AssertionError("Added an inventory button to a non-inventory screen");
            }, listener -> {});
            patched.getMethod("onInventoryGuiInit", ScreenEvent.Init.Post.class).invoke(patched.getConstructor().newInstance(), event);
            check(true, "Non-inventory screen skipped");
            if (unloaded) {
                try {
                    original.getMethod("onInventoryGuiInit", ScreenEvent.Init.Post.class).invoke(original.getConstructor().newInstance(), event);
                    throw new AssertionError("Original Curios did not reproduce early config read");
                } catch (InvocationTargetException expected) {
                    check(expected.getCause() instanceof IllegalStateException, "Unexpected Curios baseline failure");
                }
            } else original.getMethod("onInventoryGuiInit", ScreenEvent.Init.Post.class).invoke(original.getConstructor().newInstance(), event);
        }
        var key = new InputEvent.Key(281, 0, 1, 0);
        target(DEV_INFO).getMethod("onKeyPressed", InputEvent.Key.class).invoke(null, key);
        check(true, "Out-of-world key skipped");
        var originalDev = new OriginalLoader().loadClass(DEV_INFO);
        if (unloaded) {
            try {
                originalDev.getMethod("onKeyPressed", InputEvent.Key.class).invoke(null, key);
                throw new AssertionError("Original ForgeEndertech did not reproduce early config read");
            } catch (InvocationTargetException expected) {
                check(expected.getCause() instanceof IllegalStateException, "Unexpected ForgeEndertech baseline failure");
            }
        } else originalDev.getMethod("onKeyPressed", InputEvent.Key.class).invoke(null, key);
    }

    static void loaded() throws Exception {
        check(taczSpec().isLoaded() && appleSpec().isLoaded(), "Configs did not load");
        callCoro();
        check(needsCoroInit(), "CoroUtil must still wait during common setup");
        // Exercise completion deterministically before native rendering initializes.
        // All real configs are loaded; temporarily model ClientModLoader's final transition.
        var loading = field(ClientModLoader.class, "loading");
        boolean savedLoading = loading.getBoolean(null);
        try {
            loading.setBoolean(null, false);
            callCoro();
            check(!needsCoroInit(), "CoroUtil never completed deferred initialization");
            callCoro();
            check(!needsCoroInit(), "CoroUtil one-time initialization changed");
        } finally { loading.setBoolean(null, savedLoading); }
        screenAndKeyGuards(false);
        check(Minecraft.getInstance().player == null, "Test must not enter a world");
        loadedInputs();
        var originalHud = new OriginalLoader().loadClass(HUD);
        var patchedHud = target(HUD);
        var alpha = (ModConfigSpec.DoubleValue) target("squeek.appleskin.ModConfig").getField("MAX_HUD_OVERLAY_FLASH_ALPHA").get(null);
        double savedAlpha = alpha.get();
        try {
            for (double value : new double[]{0.0, 0.35, 1.0}) {
                alpha.set(value);
                originalHud.getMethod("resetFlash").invoke(null);
                patchedHud.getMethod("resetFlash").invoke(null);
                for (int i = 0; i < 40; i++) {
                    invokeTick(originalHud, "onClientTick"); invokeTick(patchedHud, "onClientTick");
                    for (String name : List.of("flashAlpha", "unclampedFlashAlpha", "alphaDir"))
                        check(Objects.equals(field(originalHud, name).get(null), field(patchedHud, name).get(null)), "Loaded AppleSkin animation changed: " + name);
                }
            }
        } finally { alpha.set(savedAlpha); }
        for (ModConfigSpec spec : List.of(taczSpec(), appleSpec())) {
            // Detach/reattach the real loaded configuration without changing files.
            var saved = field(ModConfigSpec.class, "loadedConfig").get(spec);
            try {
                spec.acceptConfig(null);
                if (spec == taczSpec()) {
                    invokeTick(target(CRAWL), "onCrawlHoldingInput"); invokeTick(target(AIM), "onAimHoldingPreInput");
                } else invokeTick(target(HUD), "onClientTick");
                check(!spec.isLoaded(), "Patch must not load configuration itself");
            } finally { spec.acceptConfig((IConfigSpec.ILoadedConfig) saved); }
            check(spec.isLoaded(), "Config did not resume after reattachment");
        }
        loadedInputs();
    }

    static void loadedInputs() throws Exception {
        var keyConfig = target("com.tacz.guns.config.client.KeyConfig");
        for (var entry : Map.of(CRAWL, "HOLD_TO_CRAWL", AIM, "HOLD_TO_AIM").entrySet()) {
            var setting = (ModConfigSpec.BooleanValue) keyConfig.getField(entry.getValue()).get(null);
            boolean saved = setting.get();
            String callback = entry.getKey().equals(CRAWL) ? "onCrawlHoldingInput" : "onAimHoldingPreInput";
            String controller = entry.getKey().equals(CRAWL) ? "controllerCrawlDown" : "controllerAimDown";
            var patched = target(entry.getKey());
            var original = new OriginalLoader().loadClass(entry.getKey());
            try {
                for (boolean hold : new boolean[]{false, true}) for (boolean down : new boolean[]{false, true}) {
                    setting.set(hold);
                    field(patched, controller).setBoolean(null, down); field(original, controller).setBoolean(null, down);
                    invokeTick(patched, callback); invokeTick(original, callback);
                    check(field(patched, controller).getBoolean(null) == field(original, controller).getBoolean(null), "Loaded input reset differs from original");
                }
            } finally { setting.set(saved); field(patched, controller).setBoolean(null, false); }
        }
    }

    static final class OriginalLoader extends ClassLoader {
        OriginalLoader() { super(ClientChecks.class.getClassLoader()); }
        @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!List.of(CRAWL, AIM, HUD, CURIOS, DEV_INFO).contains(name)
                    && !name.startsWith(HUD + "$") && !name.startsWith(DEV_INFO + "$")) return super.loadClass(name, resolve);
            var loaded = findLoadedClass(name);
            if (loaded == null) {
                String mod = name.startsWith(HUD) ? "appleskin" : name.startsWith(CURIOS) ? "curios"
                        : name.startsWith(DEV_INFO) ? "forgeendertech" : "tacz";
                String jar = System.getProperty("configSmoke." + mod + "Jar");
                try (var zip = new ZipFile(Path.of(jar).toFile())) {
                    byte[] bytes = zip.getInputStream(zip.getEntry(name.replace('.', '/') + ".class")).readAllBytes();
                    loaded = defineClass(name, bytes, 0, bytes.length);
                } catch (Exception e) { throw new ClassNotFoundException(name, e); }
            }
            if (resolve) resolveClass(loaded);
            return loaded;
        }
    }
}
