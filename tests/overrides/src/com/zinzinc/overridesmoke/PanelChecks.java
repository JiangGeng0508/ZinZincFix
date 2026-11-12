package com.zinzinc.overridesmoke;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.logistics.factoryBoard.*;
import com.yision.fluidlogistics.api.factorygauge.FactoryGaugeType;
import com.yision.fluidlogistics.content.logistics.factoryGauge.ResourceFactoryPanelBehaviour;
import net.liukrast.deployer.lib.logistics.board.AbstractPanelBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.*;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import java.lang.reflect.Proxy;
import java.util.Set;

public class PanelChecks {
    private static int assertions;
    static void check(boolean test, String message) {
        assertions++;
        if (!test) throw new AssertionError(message);
    }
    public static void run() {
        String mode = System.getProperty("overrideSmoke.mode");
        boolean baseline = mode.equals("baseline");
        boolean left = Set.of("both", "left").contains(mode);
        boolean right = Set.of("both", "right", "baseline").contains(mode);
        FactoryPanelBlock block = AllBlocks.TEST_BLOCK;
        FactoryPanelBlockEntity entity = new FactoryPanelBlockEntity();
        Object[] loadedEntity = {entity};
        LevelReader level = (LevelReader) Proxy.newProxyInstance(PanelChecks.class.getClassLoader(), new Class<?>[]{LevelReader.class}, (proxy, method, args) -> {
            if (method.getName().equals("getBlockEntity")) return loadedEntity[0];
            if (method.getName().equals("getBlockState")) return block.defaultBlockState();
            throw new AssertionError("Unexpected world query: " + method);
        });
        AbstractPanelBehaviour custom = new AbstractPanelBehaviour(Items.DIAMOND);
        custom.active = true;
        ResourceFactoryPanelBehaviour resource = new ResourceFactoryPanelBehaviour();
        resource.active = true; resource.type = new FactoryGaugeType(() -> Items.BUCKET);
        FactoryPanelBehaviour ordinary = new FactoryPanelBehaviour(); ordinary.active = true;
        var slots = FactoryPanelBlock.PanelSlot.values();
        entity.panels.put(slots[0], custom); entity.panels.put(slots[1], resource); entity.panels.put(slots[2], ordinary);
        Item[] expected = {left ? Items.DIAMOND : Items.IRON_NUGGET, right ? Items.BUCKET : Items.IRON_NUGGET, Items.IRON_NUGGET, Items.IRON_NUGGET};
        for (int slot = 0; slot < 4; slot++) {
            var hit = new BlockHitResult(new Vec3(slot + .25, 0, 0), Direction.UP, BlockPos.ZERO, false);
            ItemStack stack = block.getCloneItemStack(block.defaultBlockState(), hit, level, BlockPos.ZERO, null);
            check(stack.is(expected[slot]) && stack.getCount() == 1, "Mixed board slot " + slot + " returned " + stack);
        }
        if (baseline) System.out.println("BASELINE REPRODUCED: Deployer custom panel returns normal gauge");
        var customHit = new BlockHitResult(new Vec3(.25, 0, 0), Direction.UP, BlockPos.ZERO, false);
        for (int cycle = 0; cycle < 16; cycle++) {
            custom.active = cycle % 2 == 0;
            block.fallbackCalls = 0;
            ItemStack stack = block.getCloneItemStack(block.defaultBlockState(), customHit, level, BlockPos.ZERO, null);
            boolean intercepted = left && custom.active;
            check(stack.is(intercepted ? Items.DIAMOND : Items.IRON_NUGGET), "Active-state change ignored");
            check(!intercepted || block.fallbackCalls == 0, "Custom pick invoked fallback too");
        }
        entity.panels.put(slots[0], resource);
        check(block.getCloneItemStack(block.defaultBlockState(), customHit, level, BlockPos.ZERO, null).is(right ? Items.BUCKET : Items.IRON_NUGGET), "Panel replacement ignored");
        resource.type = null;
        check(block.getCloneItemStack(block.defaultBlockState(), customHit, level, BlockPos.ZERO, null).is(Items.IRON_NUGGET), "Unregistered resource fallback changed");
        resource.type = new FactoryGaugeType(() -> Items.WATER_BUCKET);
        resource.active = false;
        check(block.getCloneItemStack(block.defaultBlockState(), customHit, level, BlockPos.ZERO, null).is(Items.IRON_NUGGET), "Inactive resource fallback changed");
        resource.active = true;
        check(block.getCloneItemStack(Blocks.STONE.defaultBlockState(), customHit, level, BlockPos.ZERO, null).is(Items.IRON_NUGGET), "Non-host fallback changed");
        loadedEntity[0] = null;
        block.fallbackCalls = 0;
        check(block.getCloneItemStack(block.defaultBlockState(), customHit, level, BlockPos.ZERO, null).is(Items.IRON_NUGGET), "Missing block entity fallback changed");
        check(block.fallbackCalls == 1, "Inherited fallback must run exactly once");
        System.out.println("PANEL CHECKS " + mode + ": " + assertions);
    }
}
