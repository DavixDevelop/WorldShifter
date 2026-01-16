package com.davixdevelop.worldshifter;

import io.github.ensgijs.nbt.mca.EntitiesChunk;
import io.github.ensgijs.nbt.mca.io.LoadFlags;
import io.github.ensgijs.nbt.mca.io.RandomAccessMcaFile;
import io.github.ensgijs.nbt.tag.CompoundTag;
import io.github.ensgijs.nbt.tag.ListTag;

import java.io.File;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class MoveEntitiesRegion implements Callable<Integer> {
    private final Integer worldMin;
    private final Integer worldMax;
    private final int offsetY;
    private final File entitiesOutputFolder;
    private final int totalRegionsCount;
    private final AtomicInteger counter;

    private final ConcurrentLinkedQueue<File> entitiesRegionQueue;

    public MoveEntitiesRegion(Integer worldMin, Integer worldMax, int offsetY, File entitiesOutputFolder, int totalRegionsCount, AtomicInteger counter, ConcurrentLinkedQueue<File> entitiesRegionQueue) {
        this.worldMin = worldMin;
        this.worldMax = worldMax;
        this.offsetY = offsetY;
        this.entitiesOutputFolder = entitiesOutputFolder;
        this.totalRegionsCount = totalRegionsCount;
        this.counter = counter;
        this.entitiesRegionQueue = entitiesRegionQueue;
    }


    @Override
    public Integer call() throws Exception {
        AtomicInteger removedEntities = new AtomicInteger(0);
        int dataVersion = 0;
        WorldHeight dataWorldHeight = null;

        while (!entitiesRegionQueue.isEmpty()) {
            File entitiesFile = entitiesRegionQueue.poll();

            if(entitiesFile == null)
                break;

            int c = counter.incrementAndGet();
            System.out.println("\rProcessing entities file: " + entitiesFile.getName() + "(" + ((c * 100) / totalRegionsCount) + "%)");

            File newEntities = Paths.get(entitiesOutputFolder.getPath(), entitiesFile.getName()).toFile();
            if (!Utils.copyFile(entitiesFile, newEntities)) {
                System.out.println("Error while copying source entities region: " + entitiesFile.getName());
                System.out.println();
                continue;
            }

            try(RandomAccessMcaFile<EntitiesChunk> entitiesMCA = new RandomAccessMcaFile<>(EntitiesChunk.class, newEntities, "rw")) {
                entitiesMCA.touch();

                for (int index = 0; index < 1024; index++) {
                    EntitiesChunk chunk;

                    try {
                        if(!entitiesMCA.hasChunk(index))
                            continue;

                        chunk = entitiesMCA.read(index);
                    }catch (Exception ex) {
                        System.out.println("Error while reading next chunk (index:" + index + "): " + ex.getMessage() + "\n" + Arrays.toString(ex.getStackTrace()));
                        //ex.printStackTrace();
                        continue;
                    }

                    if(chunk == null)
                        continue;

                    if (dataVersion != chunk.getDataVersion() || dataWorldHeight == null) {
                        dataVersion =  chunk.getDataVersion();
                        dataWorldHeight = Main.WORLDS_HEIGHTS.get().get(dataVersion);

                        if (dataVersion >= 2860 && worldMin != null && worldMax != null && worldMin >= -2032 && worldMax <= 2032) {
                            dataWorldHeight = new WorldHeight(worldMin, worldMax);
                        }
                    }

                    ListTag<CompoundTag> entities = chunk.getEntitiesTag();
                    if(entities != null && !entities.isEmpty() && offsetY != 0) {
                        Iterator<CompoundTag> entityTagsIterator = entities.iterator();

                        while (entityTagsIterator.hasNext()) {
                            CompoundTag entityTags = entityTagsIterator.next();
                            Boolean res = Utils.offsetTags(entityTags, offsetY, dataVersion, dataWorldHeight, removedEntities);

                            if(res == null)
                                entityTagsIterator.remove();
                        }

                        chunk.setEntitiesTag(entities);
                    }

                    entitiesMCA.write(chunk);

                }

                entitiesMCA.flush();

            }catch (Exception ex) {
                System.out.println(ex.getMessage());
                ex.printStackTrace();
            }

        }

        return removedEntities.get();
    }
}
