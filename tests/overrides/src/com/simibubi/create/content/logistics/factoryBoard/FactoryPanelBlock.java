package com.simibubi.create.content.logistics.factoryBoard;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
/** Geometry adapter; the five-argument pick implementation comes from the original mixins. */
public class FactoryPanelBlock extends FaceAttachedHorizontalDirectionalBlock {
    private static final MapCodec<FactoryPanelBlock> CODEC = simpleCodec(FactoryPanelBlock::new);
    public enum PanelSlot { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }
    public int fallbackCalls;
    public FactoryPanelBlock() { this(Properties.of()); }
    public FactoryPanelBlock(Properties properties) { super(properties); }
    @Override protected MapCodec<? extends FaceAttachedHorizontalDirectionalBlock> codec() { return CODEC; }
    public static PanelSlot getTargetedSlot(BlockPos pos, BlockState state, Vec3 location) {
        return PanelSlot.values()[Math.floorMod((int) location.x, 4)];
    }
    @Override public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        fallbackCalls++;
        return new ItemStack(Items.IRON_NUGGET);
    }
}
