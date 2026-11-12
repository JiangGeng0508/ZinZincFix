package com.tterrag.registrate.util.entry;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
public class BlockEntry extends ItemProviderEntry {
    private final Block block;
    public BlockEntry(Block block) { this.block = block; }
    public boolean has(BlockState state) { return state.getBlock() == block; }
    public ItemStack asStack() { return new ItemStack(Items.IRON_NUGGET); }
}
