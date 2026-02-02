package com.davixdevelop.worldshifter.model;

import com.davixdevelop.worldshifter.Main;
import com.davixdevelop.worldshifter.utils.EntityUtils;
import com.davixdevelop.worldshifter.utils.LogUtils;
import io.github.ensgijs.nbt.mca.EntitiesChunk;
import io.github.ensgijs.nbt.mca.io.RandomAccessMcaFile;
import io.github.ensgijs.nbt.tag.CompoundTag;
import io.github.ensgijs.nbt.tag.ListTag;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class MultiEntitiesRegion extends MultiRegion<EntitiesChunk> {
    private final AtomicInteger removedEntities;

    public MultiEntitiesRegion(File sourceRegionFile, Integer targetWorldMin, Integer targetWorldMax, int offsetY, File outputFolder, boolean isMultiWorld, AtomicInteger removedEntities) {
        super(sourceRegionFile, outputFolder, "entities", isMultiWorld, targetWorldMin, targetWorldMax, offsetY);
        this.removedEntities = removedEntities;
    }

    @Override
    public void shiftRegion() throws Exception {
        int dataVersion = 0;
        WorldHeight targetWorldHeight = null;
        ChunkPos chunkPos = null;

        try(RandomAccessMcaFile<EntitiesChunk> entitiesMCA = new RandomAccessMcaFile<>(EntitiesChunk.class, sourceRegionFile, "r")) {
            entitiesMCA.touch();

            for (int index = 0; index < 1024; index++) {
                EntitiesChunk chunk;

                try {
                    if(!entitiesMCA.hasChunk(index)) {
                        emptyChunks.add(index);
                        continue;
                    }

                    chunk = entitiesMCA.read(index);
                }catch (Exception ex) {
                    LogUtils.logError("Error while reading next chunk at index:" + index + " " + ((chunkPos != null) ? "(Previous was " + chunkPos.toString() +  ")" : ""), sourceRegionFile, ex);
                    continue;
                }

                if(chunk == null) {
                    emptyChunks.add(index);
                    continue;
                }

                chunkPos = new ChunkPos(chunk.getChunkX(), chunk.getChunkZ());

                if (dataVersion != chunk.getDataVersion() || targetWorldHeight == null) {
                    dataVersion =  chunk.getDataVersion();
                    targetWorldHeight = Main.WORLDS_HEIGHTS.get().get(dataVersion);

                    if (dataVersion >= 2860 && targetWorldMin != null && targetWorldMax != null && targetWorldMin >= -2032 && targetWorldMax <= 2032) {
                        targetWorldHeight = new WorldHeight(targetWorldMin, targetWorldMax);
                    }
                }

                ListTag<CompoundTag> entities = chunk.getEntitiesTag();
                if(entities != null && !entities.isEmpty()) {
                    Map<Integer, ListTag<CompoundTag>> entitiesPerWorldIndex = EntityUtils.offsetEntities(entities, offsetY, dataVersion, targetWorldHeight, removedEntities, isMultiWorld);

                    for(Integer worldIndex : entitiesPerWorldIndex.keySet()) {
                        ListTag<CompoundTag> ent = entitiesPerWorldIndex.get(worldIndex);

                        EntitiesChunk entitiesChunk = getChunk(new ChunkPos(chunk.getChunkX(), chunk.getChunkZ()), worldIndex);
                        if(entitiesChunk != null)
                            entitiesChunk.setEntitiesTag(ent);
                    }
                }
            }

            //FLush the region to the output
            flushRegions();
        }
    }

    @Override
    public RandomAccessMcaFile<EntitiesChunk> readMca(File regionFile) throws IOException {
        RandomAccessMcaFile<EntitiesChunk> regionMCA = new RandomAccessMcaFile<>(EntitiesChunk.class, regionFile, "rw");
        regionMCA.touch();
        return regionMCA;
    }

    @Override
    public void clearMca(RandomAccessMcaFile<EntitiesChunk> regionMCA) throws IOException {
        ChunkPos chunkPos = null;

        for (int index = 0; index < 1024; index++) {
            EntitiesChunk chunk = null;

            try {
                if(emptyChunks.contains(index) || !regionMCA.hasChunk(index)) {
                    emptyChunks.add(index);
                    continue;
                }

                chunk = regionMCA.read(index);
            }catch (Exception ex) {
                LogUtils.logError("Error while reading next chunk at index:" + index + " " + ((chunkPos != null) ? "(Previous was " + chunkPos.toString() +  ")" : "") + "\n File: world-shifted/temp/entities/" + sourceRegionFile.getName() , ex);
                continue;
            }

            if(chunk == null) {
                emptyChunks.add(index);
                continue;
            }

            chunkPos = new ChunkPos(chunk.getChunkX(), chunk.getChunkZ());

            chunk.setEntitiesTag(ListTag.createUnchecked(CompoundTag.class).asCompoundTagList());
            regionMCA.write(chunk);
        }
    }
}
