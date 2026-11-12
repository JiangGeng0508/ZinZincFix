package com.zinzinc.pondersmoke;

import com.sun.jna.Platform;
import com.sun.jna.platform.win32.Kernel32;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

@Mod("ponder_smoke")
public final class PonderSmoke {
    public PonderSmoke(IEventBus bus) {
        if (System.getProperty("ponderSmoke.mode").equals("absent")) {
            System.out.println("PONDER ABSENT PASS");
            finish(0);
        }
        bus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(() -> {
            try { ClientChecks.run(); finish(0); }
            catch (Throwable t) {
                t.printStackTrace();
                StringBuilder causes = new StringBuilder();
                for (Throwable cause = t; cause != null; cause = cause.getCause()) causes.append(cause).append('\n');
                if (System.getProperty("ponderSmoke.mode").equals("baseline") &&
                        causes.toString().contains("interpolateScaleFactor") &&
                        (causes.toString().contains("0 succeeded") || causes.toString().contains("(0/1) succeeded"))) {
                    System.out.println("PONDER BASELINE REPRODUCED: interpolateScaleFactor zero successful injections"); finish(0);
                }
                finish(1);
            }
        }));
    }
    private static void finish(int code) {
        System.out.flush(); System.err.flush();
        if (Platform.isWindows()) Kernel32.INSTANCE.TerminateProcess(Kernel32.INSTANCE.GetCurrentProcess(), code);
        Runtime.getRuntime().halt(code);
    }
}
