package com.zinzinc.configsmoke;

import com.sun.jna.Platform;
import com.sun.jna.platform.win32.Kernel32;

/** End a disposable no-world test process without running native renderer teardown. */
public final class TestExit {
    public static void finish(int code) {
        System.out.flush();
        System.err.flush();
        if (Platform.isWindows()) {
            // ExitProcess (also used by Runtime.halt) still invokes native DLL detach
            // callbacks. Those crash in this environment after the assertions pass.
            Kernel32.INSTANCE.TerminateProcess(Kernel32.INSTANCE.GetCurrentProcess(), code);
        }
        Runtime.getRuntime().halt(code);
    }
}
