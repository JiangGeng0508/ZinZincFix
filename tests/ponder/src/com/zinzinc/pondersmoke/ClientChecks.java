package com.zinzinc.pondersmoke;

import com.mojang.blaze3d.vertex.PoseStack;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.ponder.foundation.PonderScene;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector4f;
import sun.misc.Unsafe;

import java.lang.reflect.Field;

public final class ClientChecks {
    private static int assertions;
    static final Unsafe UNSAFE;
    static {
        try { Field f = Unsafe.class.getDeclaredField("theUnsafe"); f.setAccessible(true); UNSAFE = (Unsafe) f.get(null); }
        catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }
    static void set(Object object, String name, Object value) throws Exception {
        for (Class<?> c = object.getClass(); c != null; c = c.getSuperclass()) {
            try { Field f = c.getDeclaredField(name); f.setAccessible(true); f.set(object, value); return; }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
    static void close(double actual, double expected, String label) {
        assertions++;
        // VecHelper's fast trigonometry and Matrix4f's float path differ slightly (< .01 pixel).
        double tolerance = label.contains("inverse") ? .01 : .0003;
        if (!Double.isFinite(actual) || Math.abs(actual - expected) > tolerance)
            throw new AssertionError(label + ": " + actual + " != " + expected);
    }
    public static void run() throws Exception {
        String mode = System.getProperty("ponderSmoke.mode");
        boolean simulated = !mode.equals("ponderer"), ponderer = !mode.equals("simulated");
        // Exercise original scene/transform/mixin bytecode without creating a world or loading a save.
        PonderScene scene = (PonderScene) UNSAFE.allocateInstance(PonderScene.class);
        for (Field f : PonderScene.class.getDeclaredFields()) {
            if (f.getType() == LerpedFloat.class) { f.setAccessible(true); f.set(scene, LerpedFloat.linear().startWithValue(0)); }
        }
        set(scene, "scaleFactor", 4f);
        set(scene, "basePlateSize", 5);
        if (simulated) set(scene, "simulated$oldScaleFactor", 2f);
        PonderScene.SceneTransform transform = scene.new SceneTransform();
        set(scene, "transform", transform);
        Minecraft mc = Minecraft.getInstance();
        var previousScreen = mc.screen;
        try {
            mc.screen = null;
            for (float pt : new float[]{0, .25f, .5f, .75f, 1}) {
                check(transform, pt, simulated ? 2 + 2 * pt : 4, "base");
            }
            if (ponderer) PondererChecks.run(scene, transform, simulated);
        } finally { mc.screen = previousScreen; }
        System.out.println("PONDER PASS " + mode + ": " + assertions + " numeric assertions; original SceneTransform render/picking bytecode");
    }
    static void check(PonderScene.SceneTransform transform, float pt, float expectedScale, String label) {
        transform.updateScreenParams(854, 480, 0);
        var matrix = transform.apply(new PoseStack(), pt).last().pose();
        close(matrix.getColumn(0, new Vector4f()).length(), 30 * expectedScale, label + " scale");
        // Integer screen depth avoids lossy round-trip: inverse first, then render the point.
        Vec3 scenePoint = transform.screenToScene(391.25, 213.75, 205, pt);
        Vector4f screen = matrix.transform(new Vector4f((float) scenePoint.x, (float) scenePoint.y, (float) scenePoint.z, 1));
        close(screen.x, 391.25, label + " inverse x");
        close(screen.y, 213.75, label + " inverse y");
        close(screen.z, 205, label + " inverse z");
    }
}
