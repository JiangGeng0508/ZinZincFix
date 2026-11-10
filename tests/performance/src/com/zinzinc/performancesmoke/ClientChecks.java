package com.zinzinc.performancesmoke;

import com.mojang.serialization.Lifecycle;
import com.zinzinc.chorefix.optimization.MethodDeclarationCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.*;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import sun.misc.Unsafe;

import java.lang.management.ManagementFactory;
import java.lang.reflect.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.ZipFile;

public final class ClientChecks {
    private static final String ALIVE = "com.xm666.alivecombat.handler.PassHandler";
    private static final String FIREFLY = "einstein.subtle_effects.ticking.FireflyManager";
    private static final String BIOMES = "einstein.subtle_effects.ticking.BiomeEffectsManager";
    private static int checks;
    private static Holder<Biome> plains, swamp, flower, cold;
    private static Holder<DimensionType> dimension;

    public static void run() throws Exception {
        alive();
        fixtures();
        var player = Minecraft.getInstance().player;
        try {
            var temporary = allocate(LocalPlayer.class);
            temporary.tickCount = 400;
            Minecraft.getInstance().player = temporary;
            biomes();
            fireflies();
        } finally {
            Minecraft.getInstance().player = player;
        }
        System.out.println("PERFORMANCE PRESENT PASS: " + checks + " checks against original mod bytecode");
    }

    private static void alive() throws Exception {
        Class<?> patchedClass = Class.forName(ALIVE);
        Class<?> originalClass = new OriginalLoader().loadClass(ALIVE);
        check(Arrays.stream(patchedClass.getDeclaredMethods()).anyMatch(m -> m.getName().contains("cacheDeclaration")), "AliveCombat mixin missing");
        Method patched = lookup(patchedClass, "declaresMethod", Class.class, Class.class, String.class, Class[].class);
        Method original = lookup(originalClass, "declaresMethod", Class.class, Class.class, String.class, Class[].class);
        Class<?>[] types = {Base.class, Child.class, Grandchild.class, ImplementsDefault.class, Object.class, null};
        Class<?>[] bases = {Base.class, Child.class, Object.class, null};
        for (Class<?> type : types) for (Class<?> base : bases) {
            for (String name : new String[]{"action", "privateAction", "overload", "missing", "inInterface", null}) {
                for (Class<?>[] signature : new Class<?>[][]{null, {}, {int.class}, {String.class}, {null}}) {
                    Object[] args = {type, base, name, signature};
                    check(outcome(original, args).equals(outcome(patched, args)), "Declaration behavior mismatch: " + Arrays.toString(args));
                    check(outcome(original, args).equals(outcome(patched, args)), "Cached declaration mismatch");
                }
            }
        }
        Class<?>[] mutable = {int.class};
        Object[] args = {Child.class, Base.class, "overload", mutable};
        check(outcome(original, args).equals(outcome(patched, args)), "First mutable signature");
        mutable[0] = String.class;
        check(outcome(original, args).equals(outcome(patched, args)), "Mutated signature must not poison cache");
        check(Boolean.FALSE.equals(MethodDeclarationCache.forClass(Child.class).find(Base.class, "missing", new Class<?>[0])), "Negative results not cached");
        try (var executor = Executors.newFixedThreadPool(6)) {
            List<Callable<Boolean>> jobs = new ArrayList<>();
            for (int i = 0; i < 100; i++) jobs.add(() -> outcome(original, args).equals(outcome(patched, args)));
            for (var result : executor.invokeAll(jobs)) check(result.get(), "Concurrent lookup");
        }
        Object[] negative = {Grandchild.class, Base.class, "neverDeclared", new Class<?>[0]};
        for (int i = 0; i < 2000; i++) { original.invoke(null, negative); patched.invoke(null, negative); }
        var bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        bean.setThreadAllocatedMemoryEnabled(true);
        long before = allocated(bean, original, negative), after = allocated(bean, patched, negative);
        check(after < before / 3, "Repeated lookup allocation did not fall: " + before + " -> " + after);
        System.out.println("ALIVECOMBAT WORK: 10000 warmed missing-method checks allocated " + before + " -> " + after + " bytes");
    }

    private static long allocated(com.sun.management.ThreadMXBean bean, Method method, Object[] args) throws Exception {
        long thread = Thread.currentThread().threadId(), start = bean.getThreadAllocatedBytes(thread);
        for (int i = 0; i < 10000; i++) method.invoke(null, args);
        return bean.getThreadAllocatedBytes(thread) - start;
    }

    private static Object outcome(Method method, Object[] args) throws Exception {
        try { return method.invoke(null, args); }
        catch (InvocationTargetException e) { return e.getCause().getClass(); }
    }

    private static void biomes() throws Exception {
        Class<?> patched = Class.forName(BIOMES), original = new OriginalLoader().loadClass(BIOMES);
        check(Arrays.stream(patched.getDeclaredMethods()).anyMatch(m -> m.getName().contains("reuseCandidateBiome")), "Biome mixin missing");
        patched.getMethod("init").invoke(null); original.getMethod("init").invoke(null);
        Method before = tick(original), after = tick(patched);
        TestLevel world = world();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(2, 65, 3);
        world.candidate = plains;
        world.reset(); before.invoke(null, world, pos, Blocks.AIR.defaultBlockState(), RandomSource.create(1)); int reads = world.biomeReads;
        world.reset(); after.invoke(null, world, pos, Blocks.AIR.defaultBlockState(), RandomSource.create(1));
        check(reads == 3 && world.biomeReads == 1, "Expected three candidate queries to become one");
        System.out.println("SUBTLE BIOME WORK: " + reads + " -> " + world.biomeReads + " candidate biome queries per invocation");
        for (int seed = 0; seed < 300; seed++) {
            world.candidate = seed % 3 == 0 ? flower : swamp;
            world.surface = seed % 2 == 0 ? world.candidate : plains;
            world.raining = seed % 5 == 0;
            world.sky = seed % 4 == 0 ? 0 : 15;
            pos.set(2, seed % 2 == 0 ? 60 : 65, 3);
            compare(before, after, world, pos, Blocks.AIR.defaultBlockState(), seed, "biome particles");
        }
        world.candidate = flower; world.surface = plains; world.raining = false; world.sky = 15;
        pos.set(2, 65, 3);
        compare(before, after, world, pos, Blocks.AIR.defaultBlockState(), -1, "guaranteed pollen");
        check(!world.output.isEmpty(), "Biome comparison must exercise particle output");
        pos.set(2, 60, 3); world.sky = 0;
        compare(before, after, world, pos, Blocks.AIR.defaultBlockState(), -1, "distinct surface biome");
        check(!world.output.isEmpty(), "Surface biome lookup must not reuse the candidate biome");
        world.surface = flower;
        compare(before, after, world, pos, Blocks.AIR.defaultBlockState(), -1, "same surface biome");
        check(world.output.isEmpty(), "Same-biome underground early return must remain");
        // Changes between invocations must be immediately visible: no tick/world cache.
        Object configs = configSection("biomes");
        Object density = field(configs, "pollenDensity");
        setConfig(density, 0.0F);
        compare(before, after, world, pos, Blocks.AIR.defaultBlockState(), 4, "density off");
        setConfig(density, 1.0F);
        compare(before, after, world(), pos, Blocks.AIR.defaultBlockState(), 4, "density restored and new world");
        compare(before, after, world, pos, Blocks.STONE.defaultBlockState(), 4, "solid block");
        world.fluid = Fluids.WATER.defaultFluidState();
        compare(before, after, world, pos, Blocks.AIR.defaultBlockState(), 4, "fluid block");
    }

    private static void fireflies() throws Exception {
        Class<?> patched = Class.forName(FIREFLY), original = new OriginalLoader().loadClass(FIREFLY);
        check(Arrays.stream(patched.getDeclaredMethods()).anyMatch(m -> m.getName().contains("rejectBrightCandidates")), "Firefly mixin missing");
        Method before = tick(original), after = tick(patched);
        TestLevel world = world(); BlockPos pos = new BlockPos(2, 65, 3);
        world.candidate = swamp; world.time = 6000; world.sky = 15;
        world.reset(); before.invoke(null, world, pos, Blocks.AIR.defaultBlockState(), RandomSource.create(0)); int reads = world.biomeReads;
        world.reset(); after.invoke(null, world, pos, Blocks.AIR.defaultBlockState(), RandomSource.create(0));
        check(reads == 1 && world.biomeReads == 0, "Daylight should reject before querying biome");
        System.out.println("SUBTLE FIREFLY WORK: " + reads + " -> " + world.biomeReads + " biome queries for a daylight candidate");
        long[] times = {0, 13000, 13001, 18000, 22999, 23000, 24000, -1, Long.MAX_VALUE};
        int outputs = 0;
        for (long time : times) for (int sky : new int[]{0, 15}) for (int block : new int[]{0, 5, 6, 15}) {
            world.time = time; world.sky = sky; world.blockLight = block;
            for (int seed = 0; seed < 100; seed++) {
                world.candidate = seed % 3 == 0 ? cold : swamp;
                world.raining = seed % 7 == 0;
                world.seeSky = seed % 2 == 0;
                compare(before, after, world, pos, Blocks.AIR.defaultBlockState(), seed, "fireflies time/light/weather");
                outputs += world.output.size();
            }
        }
        world.candidate = swamp; world.time = 18000; world.sky = 15; world.blockLight = 0;
        world.raining = false; world.seeSky = true;
        compare(before, after, world, pos, Blocks.AIR.defaultBlockState(), -1, "guaranteed firefly particle and sound");
        check(world.output.size() == 2, "Firefly comparison must exercise both particle and sound output");
        Object configs = configSection("fireflies");
        for (boolean enabled : new boolean[]{false, true}) {
            setConfig(field(configs, "firefliesEnabled"), enabled);
            compare(before, after, world, pos, Blocks.AIR.defaultBlockState(), 7, "live firefly toggle");
        }
        Minecraft.getInstance().player.tickCount = 199;
        compare(before, after, world, pos, Blocks.AIR.defaultBlockState(), 7, "player warmup");
        Minecraft.getInstance().player.tickCount = 200;
        compare(before, after, world(), pos, Blocks.STONE.defaultBlockState(), 7, "new world and solid block");
    }

    private static void compare(Method before, Method after, TestLevel world, BlockPos pos, BlockState state, int seed, String label) throws Exception {
        world.reset(); RandomSource a = random(seed);
        before.invoke(null, world, pos, state, a);
        List<String> expected = List.copyOf(world.output); long next = a.nextLong();
        world.reset(); RandomSource b = random(seed);
        after.invoke(null, world, pos, state, b);
        check(expected.equals(world.output), label + ": particle/sound output changed");
        check(next == b.nextLong(), label + ": random sequence changed");
    }

    private static RandomSource random(int seed) {
        // Small consecutive Java LCG seeds have highly correlated first draws.
        return seed < 0 ? new ZeroDoubleRandom() : RandomSource.create(seed * 0x9E3779B97F4A7C15L);
    }

    private static class ZeroDoubleRandom extends LegacyRandomSource {
        ZeroDoubleRandom() { super(123); }
        @Override public double nextDouble() { super.nextDouble(); return 0.0; }
    }

    private static void fixtures() {
        var registry = new MappedRegistry<Biome>(Registries.BIOME, Lifecycle.stable());
        plains = biome(registry, "plains", 0.8F);
        swamp = biome(registry, "swamp", 0.8F);
        flower = biome(registry, "flower_forest", 0.8F);
        cold = biome(registry, "snowy_plains", -0.5F);
        // Only unwrapKey is used on the dimension holder in these manager tests.
        dimension = Holder.Reference.createStandAlone(new HolderOwner<>() {}, ResourceKey.create(Registries.DIMENSION_TYPE, ResourceLocation.withDefaultNamespace("overworld")));
    }

    private static Holder<Biome> biome(MappedRegistry<Biome> registry, String id, float temperature) {
        Biome value = new Biome.BiomeBuilder().hasPrecipitation(true).temperature(temperature).downfall(0.5F)
                .specialEffects(new BiomeSpecialEffects.Builder().fogColor(0).waterColor(0).waterFogColor(0).skyColor(0).build())
                .mobSpawnSettings(MobSpawnSettings.EMPTY).generationSettings(BiomeGenerationSettings.EMPTY).build();
        return registry.register(ResourceKey.create(Registries.BIOME, ResourceLocation.withDefaultNamespace(id)), value, RegistrationInfo.BUILT_IN);
    }

    private static Object configSection(String section) throws Exception {
        return field(Class.forName("einstein.subtle_effects.init.ModConfigs").getField("ENVIRONMENT").get(null), section);
    }
    private static Object field(Object object, String field) throws Exception { return object.getClass().getField(field).get(object); }
    private static void setConfig(Object field, Object value) throws Exception { field.getClass().getMethod("accept", Object.class).invoke(field, value); }
    private static Method tick(Class<?> type) throws Exception { return lookup(type, "tick", Level.class, BlockPos.class, BlockState.class, RandomSource.class); }
    private static Method lookup(Class<?> type, String name, Class<?>... args) throws Exception { var m = type.getDeclaredMethod(name, args); m.setAccessible(true); return m; }
    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
    private static <T> T allocate(Class<T> type) throws Exception {
        var field = Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true);
        return type.cast(((Unsafe) field.get(null)).allocateInstance(type));
    }
    private static TestLevel world() throws Exception {
        TestLevel world = allocate(TestLevel.class);
        world.candidate = plains; world.surface = plains; world.surfaceY = 64; world.sky = 15;
        world.time = 18000; world.seeSky = true; world.fluid = Fluids.EMPTY.defaultFluidState(); world.output = new ArrayList<>();
        return world;
    }

    public static class TestLevel extends ClientLevel {
        Holder<Biome> candidate, surface; int surfaceY, sky, blockLight, biomeReads;
        long time; boolean raining, seeSky; FluidState fluid; List<String> output;
        private TestLevel() { super(null, null, null, null, 0, 0, null, null, false, 0); }
        void reset() { output.clear(); biomeReads = 0; }
        @Override public Holder<Biome> getBiome(BlockPos pos) { biomeReads++; return pos.getY() == surfaceY ? surface : candidate; }
        @Override public Holder<DimensionType> dimensionTypeRegistration() { return dimension; }
        @Override public int getBrightness(LightLayer layer, BlockPos pos) { return layer == LightLayer.SKY ? sky : blockLight; }
        @Override public BlockPos getHeightmapPos(Heightmap.Types type, BlockPos pos) { return new BlockPos(pos.getX(), surfaceY, pos.getZ()); }
        @Override public long getDayTime() { return time; }
        @Override public boolean isRaining() { return raining; }
        @Override public boolean canSeeSky(BlockPos pos) { return seeSky; }
        @Override public FluidState getFluidState(BlockPos pos) { return fluid; }
        @Override public BlockState getBlockState(BlockPos pos) { return Blocks.AIR.defaultBlockState(); }
        @Override public void addParticle(ParticleOptions type, double x, double y, double z, double vx, double vy, double vz) {
            output.add("particle:" + type + ":" + x + ":" + y + ":" + z + ":" + vx + ":" + vy + ":" + vz);
        }
        @Override public void playLocalSound(double x, double y, double z, SoundEvent event, SoundSource source, float volume, float pitch, boolean delay) {
            output.add("sound:" + event.getLocation() + ":" + x + ":" + y + ":" + z + ":" + volume + ":" + pitch);
        }
    }
    private static class OriginalLoader extends ClassLoader {
        OriginalLoader() { super(ClientChecks.class.getClassLoader()); }
        @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.equals(ALIVE) && !name.equals(FIREFLY) && !name.equals(BIOMES) && !name.equals(BIOMES + "$Options")) return super.loadClass(name, resolve);
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    String jar = System.getProperty(name.equals(ALIVE) ? "performance.aliveJar" : "performance.subtleJar");
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
    public static class Base { public void action() {} private void privateAction() {} public void overload(int value) {} }
    public static class Child extends Base { @Override public void action() {} public void overload(String value) {} }
    public static class Grandchild extends Child {}
    public interface HasDefault { default void inInterface() {} }
    public static class ImplementsDefault implements HasDefault {}
}
