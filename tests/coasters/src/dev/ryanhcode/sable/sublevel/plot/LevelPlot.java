package dev.ryanhcode.sable.sublevel.plot;

import java.util.Collection;

// Test-only adapter for the one LevelPlot method used by the original scanner.
public final class LevelPlot {
    private final Collection<PlotChunkHolder> chunks;

    public LevelPlot(Collection<PlotChunkHolder> chunks) {
        this.chunks = chunks;
    }

    public Collection<PlotChunkHolder> getLoadedChunks() {
        return chunks;
    }
}
