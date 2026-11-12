package com.zinzinc.pondersmoke;

import com.nododiiiii.ponderer.ponder.PonderSceneViewOffsetAccess;
import com.nododiiiii.ponderer.ui.PonderFreePreview;
import com.nododiiiii.ponderer.ui.PonderUiPreviewAccess;
import com.nododiiiii.ponderer.ui.PonderUiZoomAccess;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.Minecraft;
import java.util.List;
import static com.zinzinc.pondersmoke.ClientChecks.*;

/** Kept separate so the Simulated-only test never links Ponderer types. */
public final class PondererChecks {
    static void run(PonderScene scene, PonderScene.SceneTransform transform, boolean simulated) throws Exception {
        TestUI ui = (TestUI) UNSAFE.allocateInstance(TestUI.class);
        ui.preview = new PonderFreePreview(ui);
        set(ui, "scenes", List.of(scene));
        Minecraft.getInstance().screen = ui;
        PonderSceneViewOffsetAccess access = (PonderSceneViewOffsetAccess) scene;
        for (float zoom : new float[]{.5f, 1, 2.5f}) {
            ui.zoom = zoom;
            for (float pt : new float[]{0, .25f, .5f, 1}) {
                access.ponderer$setScaleOverrideActive(false);
                check(transform, pt, (simulated ? 2 + 2 * pt : 4) * zoom, "zoom");
                access.ponderer$getScaleOverride().startWithValue(3);
                access.ponderer$setScaleOverrideActive(true);
                check(transform, pt, 3 * zoom, "override");
            }
        }
        access.ponderer$setScaleOverrideActive(false);
        set(ui.preview, "scene", scene);
        set(ui.preview, "partialTicks", .25f);
        ui.zoom = 1.5f;
        check(transform, .9f, (simulated ? 2.5f : 4) * ui.zoom, "frozen preview");
        set(ui.preview, "scene", null);
        set(ui, "scenes", List.of());
        check(transform, .5f, simulated ? 3 : 4, "unrelated scene ignores UI zoom");
    }
    // Only zoom/preview controls are an adapter; scene, matrix, interpolation and
    // preview logic are original classes. This UI is never opened or constructed.
    public static final class TestUI extends PonderUI implements PonderUiZoomAccess, PonderUiPreviewAccess {
        float zoom;
        PonderFreePreview preview;
        private TestUI() { super(List.of()); }
        public float ponderer$getUserZoom() { return zoom; }
        public void ponderer$setUserZoom(float zoom) { this.zoom = zoom; }
        public PonderFreePreview ponderer$getFreePreview() { return preview; }
    }
}
