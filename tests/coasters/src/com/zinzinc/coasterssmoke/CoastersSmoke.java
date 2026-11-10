package com.zinzinc.coasterssmoke;

import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.neoforged.fml.common.Mod;
import sun.misc.Unsafe;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

@Mod("coasters_smoke")
public final class CoastersSmoke {
    private static final String SCANNER = "dev.silvergold.simulatedcoasters.track.cart.CoasterCartPlotScan";
    private static final Block TARGET = Blocks.GOLD_BLOCK;
    private static int checks;

    public CoastersSmoke() throws Exception {
        if (Boolean.getBoolean("coasters.absent")) {
            try {
                Class.forName(SCANNER);
                throw new AssertionError("Optional target unexpectedly present");
            } catch (ClassNotFoundException expected) {
                System.out.println("COASTERS ABSENT PASS: packaged patch loads without Simulated Coasters or Sable");
                System.exit(0);
            }
        }

        var patched = new Scanner(Class.forName(SCANNER));
        // Define the same original JAR bytes without ModLauncher's transformers.
        // This gives an independent baseline without duplicating the algorithm.
        var baseline = new Scanner(Class.forName(SCANNER, true, new OriginalLoader(getClass().getClassLoader())));
        check(Arrays.stream(patched.type.getDeclaredMethods()).anyMatch(m -> m.getName().contains("skipSectionsWithoutBearing")),
                "Mixin not applied to the scanner");
        check(Arrays.stream(baseline.type.getDeclaredMethods()).noneMatch(m -> m.getName().contains("skipSectionsWithoutBearing")),
                "Baseline must not contain the patch");

        compare(baseline, patched, plot(), TARGET, "empty plot");
        compare(baseline, patched, new LevelPlot(List.of(new PlotChunkHolder(null))), TARGET, "unloaded chunk");
        compare(baseline, patched, plot(chunk(-3, 5, -4, null, section(Blocks.AIR), section(Blocks.STONE))), TARGET, "null/air/no target");

        var mutable = section(Blocks.STONE);
        var edits = plot(chunk(-7, -2, -4, mutable));
        compare(baseline, patched, edits, TARGET, "before placement");
        mutable.setBlockState(15, 0, 1, TARGET.defaultBlockState());
        mutable.setBlockState(0, 15, 15, TARGET.defaultBlockState());
        compare(baseline, patched, edits, TARGET, "placement visible immediately");
        mutable.setBlockState(15, 0, 1, Blocks.STONE.defaultBlockState());
        mutable.setBlockState(0, 15, 15, Blocks.STONE.defaultBlockState());
        check(mutable.maybeHas(state -> state.is(TARGET)), "Expected a stale palette entry for the removed target");
        compare(baseline, patched, edits, TARGET, "removed target retained in palette");

        var replaced = chunk(0, 0, -4, section(Blocks.STONE));
        var replacementPlot = plot(replaced);
        compare(baseline, patched, replacementPlot, TARGET, "before section replacement");
        replaced.fixtureSections[0] = section(TARGET);
        compare(baseline, patched, replacementPlot, TARGET, "replaced section / uniform target");

        var fence = section(Blocks.STONE);
        var fenceStates = Blocks.OAK_FENCE.getStateDefinition().getPossibleStates();
        for (int i = 0; i < fenceStates.size(); i++) fence.setBlockState(i % 16, i / 16, 0, fenceStates.get(i));
        compare(baseline, patched, plot(chunk(9, -8, -4, fence)), Blocks.OAK_FENCE, "multiple states of the same target block");

        var diverse = section(Blocks.STONE);
        int count = 0;
        for (var state : Block.BLOCK_STATE_REGISTRY) {
            if (state.is(TARGET) || state.isAir()) continue;
            diverse.setBlockState(count & 15, (count >> 8) & 15, (count >> 4) & 15, state);
            if (++count == 300) break;
        }
        compare(baseline, patched, plot(chunk(-1, 1, -8, diverse)), TARGET, "global palette without target");
        diverse.setBlockState(15, 15, 15, TARGET.defaultBlockState());
        compare(baseline, patched, plot(chunk(-1, 1, -8, diverse)), TARGET, "global palette with target");

        for (int seed = 0; seed < 32; seed++) {
            var random = new Random(seed);
            var chunks = new TestChunk[3];
            for (int c = 0; c < chunks.length; c++) {
                var sections = new CountingSection[4];
                for (int i = 0; i < sections.length; i++) {
                    sections[i] = section(i % 2 == 0 ? Blocks.AIR : Blocks.STONE);
                    for (int edit = 0; edit < 30; edit++) {
                        var block = random.nextInt(3) == 0 ? TARGET : Blocks.OAK_PLANKS;
                        sections[i].setBlockState(random.nextInt(16), random.nextInt(16), random.nextInt(16), block.defaultBlockState());
                    }
                }
                chunks[c] = chunk(c - 2, 2 - c, -4, sections);
            }
            compare(baseline, patched, plot(chunks), TARGET, "random layout " + seed);
        }

        // Preserve callback order and mutations affecting later cells in the scan.
        var baselineMutations = callbackMutations(baseline);
        var patchedMutations = callbackMutations(patched);
        check(baselineMutations.equals(patchedMutations), "Callback mutation behavior changed");
        check(patchedMutations.size() == 2, "Mutation scenario must visit the newly placed target");

        var shipChunks = new TestChunk[8];
        for (int c = 0; c < shipChunks.length; c++) {
            var sections = new CountingSection[16];
            for (int i = 0; i < sections.length; i++) sections[i] = section(Blocks.STONE);
            shipChunks[c] = chunk(c, 0, -4, sections);
        }
        shipChunks[3].fixtureSections[7].setBlockState(1, 2, 3, TARGET.defaultBlockState());
        var ship = plot(shipChunks);
        compare(baseline, patched, ship, TARGET, "128 occupied sections, one containing a bearing");
        for (boolean callback : new boolean[]{false, true}) {
            resetReads(ship);
            if (callback) baseline.cells(ship, TARGET); else baseline.scan(ship, TARGET);
            long before = reads(ship);
            resetReads(ship);
            if (callback) patched.cells(ship, TARGET); else patched.scan(ship, TARGET);
            long after = reads(ship);
            check(before == 128L * 4096 && after == 4096, "Unexpected scan work: " + before + " -> " + after);
            System.out.println("COASTERS WORK " + (callback ? "forEachBearingWorldCell" : "scanBearingCells")
                    + ": " + before + " -> " + after + " block reads (99.21875% fewer)");
        }
        System.out.println("COASTERS PRESENT PASS: " + checks + " checks; original bytecode agrees on positions, count, representative, order and mutations");
        System.exit(0);
    }

    private static void compare(Scanner baseline, Scanner patched, LevelPlot plot, Block block, String label) throws Exception {
        check(baseline.scan(plot, block).equals(patched.scan(plot, block)), label + ": scan result changed");
        check(baseline.cells(plot, block).equals(patched.cells(plot, block)), label + ": callback cells/order changed");
    }

    private static List<BlockPos> callbackMutations(Scanner scanner) throws Exception {
        var section = section(Blocks.STONE);
        section.setBlockState(0, 0, 0, TARGET.defaultBlockState());
        var cells = new ArrayList<BlockPos>();
        scanner.each.invoke(null, plot(chunk(0, 0, 0, section)), TARGET, (Consumer<BlockPos.MutableBlockPos>) pos -> {
            cells.add(pos.immutable());
            if (cells.size() == 1) section.setBlockState(0, 0, 1, TARGET.defaultBlockState());
        });
        return cells;
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static CountingSection section(Block fill) {
        return new CountingSection(fill.defaultBlockState());
    }

    private static LevelPlot plot(TestChunk... chunks) {
        return new LevelPlot(Arrays.stream(chunks).map(PlotChunkHolder::new).toList());
    }

    private static TestChunk chunk(int x, int z, int minSection, CountingSection... sections) throws Exception {
        // These scan-only chunks override every accessed method. Allocate without
        // a Level constructor to avoid opening/generating a world in a load test.
        var field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        var chunk = (TestChunk) ((Unsafe) field.get(null)).allocateInstance(TestChunk.class);
        chunk.fixturePos = new ChunkPos(x, z);
        chunk.fixtureMinSection = minSection;
        chunk.fixtureSections = sections;
        return chunk;
    }

    private static long reads(LevelPlot plot) {
        long reads = 0;
        for (var holder : plot.getLoadedChunks()) {
            if (holder.getChunk() instanceof TestChunk chunk) {
                for (var section : chunk.fixtureSections) if (section != null) reads += section.reads;
            }
        }
        return reads;
    }

    private static void resetReads(LevelPlot plot) {
        for (var holder : plot.getLoadedChunks()) {
            if (holder.getChunk() instanceof TestChunk chunk) {
                for (var section : chunk.fixtureSections) if (section != null) section.reads = 0;
            }
        }
    }

    private static final class CountingSection extends LevelChunkSection {
        private long reads;

        private CountingSection(BlockState fill) {
            super(new PalettedContainer<>(Block.BLOCK_STATE_REGISTRY, fill, PalettedContainer.Strategy.SECTION_STATES), null);
        }

        @Override
        public BlockState getBlockState(int x, int y, int z) {
            reads++;
            return super.getBlockState(x, y, z);
        }
    }

    private static final class TestChunk extends LevelChunk {
        private CountingSection[] fixtureSections;
        private ChunkPos fixturePos;
        private int fixtureMinSection;

        private TestChunk() { super(null, null); }
        @Override public ChunkPos getPos() { return fixturePos; }
        @Override public int getSectionsCount() { return fixtureSections.length; }
        @Override public LevelChunkSection getSection(int index) { return fixtureSections[index]; }
        @Override public int getSectionYFromSectionIndex(int index) { return fixtureMinSection + index; }
    }

    private record Result(BlockPos representative, int count, List<?> cells) {}

    private static final class Scanner {
        private final Class<?> type;
        private final Method scan;
        private final Method each;

        private Scanner(Class<?> type) throws Exception {
            this.type = type;
            scan = type.getMethod("scanBearingCells", LevelPlot.class, Block.class);
            each = type.getMethod("forEachBearingWorldCell", LevelPlot.class, Block.class, Consumer.class);
        }

        private Result scan(LevelPlot plot, Block block) throws Exception {
            var result = scan.invoke(null, plot, block);
            var type = result.getClass();
            return new Result((BlockPos) type.getMethod("representative").invoke(result),
                    (Integer) type.getMethod("bearingCount").invoke(result),
                    (List<?>) type.getMethod("cells").invoke(result));
        }

        private List<BlockPos> cells(LevelPlot plot, Block block) throws Exception {
            var cells = new ArrayList<BlockPos>();
            each.invoke(null, plot, block, (Consumer<BlockPos.MutableBlockPos>) pos -> cells.add(pos.immutable()));
            return cells;
        }
    }

    private static final class OriginalLoader extends ClassLoader {
        private OriginalLoader(ClassLoader parent) { super(parent); }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.equals(SCANNER) && !name.startsWith(SCANNER + "$")) return super.loadClass(name, resolve);
            synchronized (getClassLoadingLock(name)) {
                var type = findLoadedClass(name);
                if (type == null) {
                    try (var stream = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                        if (stream == null) throw new ClassNotFoundException(name);
                        var bytes = stream.readAllBytes();
                        type = defineClass(name, bytes, 0, bytes.length);
                    } catch (IOException e) {
                        throw new ClassNotFoundException(name, e);
                    }
                }
                if (resolve) resolveClass(type);
                return type;
            }
        }
    }
}
