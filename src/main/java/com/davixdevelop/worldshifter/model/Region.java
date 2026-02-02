package com.davixdevelop.worldshifter.model;

import com.davixdevelop.worldshifter.utils.LogUtils;
import io.github.ensgijs.nbt.mca.ChunkBase;
import io.github.ensgijs.nbt.mca.io.RandomAccessMcaFile;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public class Region<C extends ChunkBase> {
    private final RandomAccessMcaFile<C> mcaFile;
    private final Map<ChunkPos, C> chunks;
    private final Set<ChunkPos> emptyChunks;

    private Boolean isEmpty = false;

    public Region(RandomAccessMcaFile<C> mcaFile) {
        this.mcaFile = mcaFile;
        chunks = new TreeMap<>();
        emptyChunks = new TreeSet<>();
    }

    public RandomAccessMcaFile<C> getMcaFile() {
        return mcaFile;
    }

    public Map<ChunkPos, C> getChunks() {
        return chunks;
    }

    public C getChunk(ChunkPos pos) {
        return chunks.computeIfAbsent(pos, p -> {
            try {
                return mcaFile.readAbsolute(p.getX(), p.getZ());
            }catch (Exception ex) {
                LogUtils.logError("Error while reading chunk at [" + p.getX() + "," + p.getZ() + "]", ex);
                return null;
            }
        });
    }

    public void markChunkEmpty(ChunkPos pos) {
        emptyChunks.add(pos);
    }

    public Boolean isEmpty() {
        return isEmpty;
    }

    public void setEmpty(Boolean empty) {
        isEmpty = empty;
    }
}
