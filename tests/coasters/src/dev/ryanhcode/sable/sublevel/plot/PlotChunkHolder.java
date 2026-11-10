package dev.ryanhcode.sable.sublevel.plot;

import net.minecraft.world.level.chunk.LevelChunk;

// Test-only adapter; no chunk scheduling or physics is needed for a scan.
public final class PlotChunkHolder {
    private final LevelChunk chunk;

    public PlotChunkHolder(LevelChunk chunk) {
        this.chunk = chunk;
    }

    public LevelChunk getChunk() {
        return chunk;
    }
}
