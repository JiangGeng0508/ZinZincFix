package com.simibubi.create.content.logistics.factoryBoard;
import java.util.EnumMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
public class FactoryPanelBlockEntity extends BlockEntity {
    public final EnumMap<FactoryPanelBlock.PanelSlot, FactoryPanelBehaviour> panels = new EnumMap<>(FactoryPanelBlock.PanelSlot.class);
    public FactoryPanelBlockEntity() { super(BlockEntityType.CHEST, BlockPos.ZERO, Blocks.CHEST.defaultBlockState()); }
}
