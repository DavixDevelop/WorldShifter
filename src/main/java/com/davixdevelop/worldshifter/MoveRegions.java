package com.davixdevelop.worldshifter;

import io.github.ensgijs.nbt.mca.SectionBase;
import io.github.ensgijs.nbt.mca.TerrainChunk;
import io.github.ensgijs.nbt.mca.TerrainSection;
import io.github.ensgijs.nbt.mca.io.LoadFlags;
import io.github.ensgijs.nbt.mca.io.RandomAccessMcaFile;
import io.github.ensgijs.nbt.mca.util.IntPointXZ;
import io.github.ensgijs.nbt.mca.util.PalettizedCuboid;
import io.github.ensgijs.nbt.tag.CompoundTag;
import io.github.ensgijs.nbt.tag.IntArrayTag;
import io.github.ensgijs.nbt.tag.ListTag;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class MoveRegions implements Callable<Integer> {

    private final Integer worldMin;
    private final Integer worldMax;
    private final int offsetY;
    private final int sectionOffsetY;
    private final File regionOutputFolder;
    private final int regionFilesCount;
    private final AtomicInteger counter;
    private final AtomicInteger removedEntities;

    private final ConcurrentLinkedQueue<File> regionQueue;

    public MoveRegions(Integer worldMin, Integer worldMax, int offsetY, int sectionOffsetY, File regionOutputFolder, int regionFilesCount, AtomicInteger counter, AtomicInteger removedEntities, ConcurrentLinkedQueue<File> regionQueue) {
        this.worldMin = worldMin;
        this.worldMax = worldMax;
        this.offsetY = offsetY;
        this.sectionOffsetY = sectionOffsetY;
        this.regionOutputFolder = regionOutputFolder;
        this.regionFilesCount = regionFilesCount;
        this.counter = counter;
        this.removedEntities = removedEntities;
        this.regionQueue = regionQueue;
    }

    @Override
    public Integer call() {
        int removedChunks = 0;
        int dataVersion = 0;
        WorldHeight dataWorldHeight = null;


        while (!regionQueue.isEmpty()) {
            File regionFile = regionQueue.poll();

            if (regionFile == null)
                break;

            int c = counter.incrementAndGet();
            System.out.println("\rProcessing: " + regionFile.getName() + "(" + ((c * 100) / regionFilesCount) + "%)");

            File newRegion = Paths.get(regionOutputFolder.getPath(), regionFile.getName()).toFile();
            if (!Utils.copyFile(regionFile, newRegion)) {
                System.out.println("Error while copying source region: " + regionFile.getName());
                System.out.println();
                continue;
            }


            //Map<Integer, TerrainChunk> newChunks = new TreeMap<>();

            int writtenChunks = 0;

            try (RandomAccessMcaFile<TerrainChunk> regionMCA = new RandomAccessMcaFile<>(TerrainChunk.class, newRegion, "rw")) {
                //regionMCA.setLoadFlags(LoadFlags.HEIGHTMAPS | LoadFlags.ENTITIES | LoadFlags.TILE_ENTITIES | LoadFlags.TILE_TICKS | LoadFlags.LIQUID_TICKS | LoadFlags.TO_BE_TICKED | LoadFlags.POST_PROCESSING | LoadFlags.BLOCK_STATES | LoadFlags.SKY_LIGHT | LoadFlags.LIGHTS | LoadFlags.LIQUIDS_TO_BE_TICKED | LoadFlags.POI_RECORDS | LoadFlags.WORLD_UPGRADE_HINTS);
                regionMCA.touch();
                try {
                    regionMCA.optimizeFile();
                }catch (Exception ignored){}

                regionMCA.flush();

                //ChunkIterator<TerrainChunk> chunkIterator = regionMCA.chunkIterator(LoadFlags.HEIGHTMAPS | LoadFlags.ENTITIES | LoadFlags.TILE_ENTITIES | LoadFlags.TILE_TICKS | LoadFlags.LIQUID_TICKS | LoadFlags.TO_BE_TICKED | LoadFlags.POST_PROCESSING | LoadFlags.BLOCK_STATES | LoadFlags.SKY_LIGHT | LoadFlags.LIGHTS | LoadFlags.LIQUIDS_TO_BE_TICKED | LoadFlags.POI_RECORDS | LoadFlags.WORLD_UPGRADE_HINTS);

                Set<ChunkPos> chunksToRemove = new HashSet<>();


                for (int index = 0; index < 1024; index++) {
                    TerrainChunk chunk = null;

                    try {
                        if(!regionMCA.hasChunk(index))
                            continue;

                        chunk = regionMCA.read(index);
                    } catch (Exception ex) {
                        System.out.println("Error while reading next chunk (index:" + index + "): " + ex.getMessage() + "\n" + Arrays.toString(ex.getStackTrace()));
                        //ex.printStackTrace();
                        continue;
                    }


                    if (chunk == null)
                        continue;


                    HashMap<Integer, TerrainSection> offsetSections = new HashMap<>();

                    if (dataVersion != chunk.getDataVersion() || dataWorldHeight == null) {
                        dataVersion =  chunk.getDataVersion();
                        dataWorldHeight = Main.WORLDS_HEIGHTS.get().get(dataVersion);

                        if (dataVersion >= 2860 && worldMin != null && worldMax != null && worldMin >= -2032 && worldMax <= 2032) {
                            dataWorldHeight = new WorldHeight(worldMin, worldMax);
                        }
                    }

                    Integer newMinY = null;
                    Integer newMaxY = null;

                    //Offset sections
                    int minY = chunk.getMinSectionY();
                    int maxY = chunk.getMaxSectionY();

                    if (minY != SectionBase.NO_SECTION_Y_SENTINEL && maxY != SectionBase.NO_SECTION_Y_SENTINEL) {
                        Boolean isEmpty = null;

                        for (int sectionY = minY; sectionY <= maxY; sectionY++) {
                            TerrainSection terrainSection = chunk.getSection(sectionY);
                            int newSectionY = sectionY + sectionOffsetY;
                            if (newSectionY >= Byte.MIN_VALUE && newSectionY <= Byte.MAX_VALUE
                                    && newSectionY >= dataWorldHeight.getMinSection() && newSectionY <= dataWorldHeight.getMaxSection()) {
                                offsetSections.put(newSectionY, terrainSection);
                                if (newMinY == null) {
                                    newMinY = newSectionY;
                                    newMaxY = newSectionY;
                                } else if (newSectionY > newMaxY)
                                    newMaxY = newSectionY;

                                PalettizedCuboid<CompoundTag> blockStates = terrainSection.getBlockStates();
                                if (blockStates != null && blockStates.paletteSize() > 0) {

                                    if (blockStates.paletteSize() > 1)
                                        isEmpty = false;
                                    else if (isEmpty == null) {
                                        CompoundTag blockState = blockStates.getByRef(0);
                                        if (blockState == null || blockState.isEmpty())
                                            isEmpty = false;
                                        else
                                            isEmpty = blockState.getString("Name").equals("minecraft:air");
                                    }
                                }
                            }

                            chunk.setSection(sectionY, null);
                        }

                        if (isEmpty != null && isEmpty) {
                            //Remove empty chunk
                            IntPointXZ chunkXZ = chunk.getChunkXZ();
                            chunksToRemove.add(new ChunkPos(chunkXZ.getX(), chunkXZ.getZ()));
                            //chunkIterator.remove();
                            removedChunks++;
                            continue;
                        }

                    } else {
                        //Remove empty chunk
                        IntPointXZ chunkXZ = chunk.getChunkXZ();
                        chunksToRemove.add(new ChunkPos(chunkXZ.getX(), chunkXZ.getZ()));
                        //chunkIterator.remove();
                        removedChunks++;
                        continue;
                    }

                    CompoundTag blendingData = chunk.getBlendingData();
                    if (blendingData != null && newMinY != null && newMaxY != null) {
                        if (blendingData.containsKey("max_section")) {
                            blendingData.putInt("max_section", newMaxY);
                        }
                        if (blendingData.containsKey("min_section"))
                            blendingData.putInt("min_section", newMinY);
                    }

                    for (Map.Entry<Integer, TerrainSection> sectionEntry : offsetSections.entrySet()) {
                        chunk.setSection(sectionEntry.getKey(), sectionEntry.getValue());
                    }

                    //Offset UpgradeData
                    CompoundTag upgradeData = chunk.getUpgradeData();
                    if (upgradeData != null && upgradeData.containsKey("Indices") && newMinY != null) {
                        CompoundTag indices = upgradeData.getCompoundTag("Indices");
                        Set<String> indicesKeys = indices.keySet();

                        CompoundTag newIndices = new CompoundTag();

                        for (String key : indicesKeys) {
                            int indY = Integer.parseInt(key);

                            int newIndY = indY + sectionOffsetY;
                            int newSectionY = newIndY;
                            //If chunks contain blending_data treat the name of the indice as an index (min_section + sectionY)
                            if (blendingData != null) {
                                newSectionY = (minY + indY) + sectionOffsetY;
                                newIndY = -newMinY + newSectionY;
                            }

                            //Check if newSectionY falls within the Byte range
                            if (newSectionY >= Byte.MIN_VALUE && newSectionY <= Byte.MAX_VALUE
                                    && newSectionY >= dataWorldHeight.getMinSection() && newSectionY <= dataWorldHeight.getMaxSection())
                                newIndices.putIntArray(String.valueOf(newIndY), indices.getIntArrayTag(key).getValue());
                        }

                        //Re-add the new indices
                        upgradeData.put("Indices", newIndices);
                        chunk.setUpgradeData(upgradeData);
                    }


                    //Offset entities
                    ListTag<CompoundTag> entities = chunk.getEntities();
                    if (entities != null && !entities.isEmpty() && offsetY != 0) {
                        Iterator<CompoundTag> tagIterator = entities.iterator();

                        while (tagIterator.hasNext()) {
                            CompoundTag entity = tagIterator.next();
                            Boolean res = Utils.offsetTags(entity, offsetY, dataVersion, dataWorldHeight, removedEntities);

                            if (res == null)
                                tagIterator.remove();
                        }

                        chunk.setEntities(entities);
                    }

                    ListTag<CompoundTag> tileEntities = chunk.getTileEntities();
                    if (tileEntities != null && !tileEntities.isEmpty() && offsetY != 0) {
                        Iterator<CompoundTag> tagIterator = tileEntities.iterator();
                        while (tagIterator.hasNext()) {
                            CompoundTag tileEntity = tagIterator.next();
                            Boolean res = Utils.offsetTags(tileEntity, offsetY, dataVersion, dataWorldHeight, removedEntities);

                            if (res == null)
                                tagIterator.remove();
                        }

                        chunk.setTileEntities(tileEntities);
                    }


                    CompoundTag chunkHandle = chunk.getHandle();

                    //Offset HeightMap
                    IntArrayTag legacyHeightMap = chunk.getLegacyHeightMap();
                    if (legacyHeightMap != null && offsetY != 0) {
                        int[] h = legacyHeightMap.getValue();
                        for (int hi = 0; hi < h.length; hi++)
                            h[hi] = Math.min(Math.max(h[hi] + offsetY, dataWorldHeight.getMinHeight()), dataWorldHeight.getMaxHeight() - 1);

                        legacyHeightMap.setValue(h);
                        chunk.setLegacyHeightMap(legacyHeightMap);
                    }

                    if (chunkHandle.containsKey("HeightMap") && offsetY != 0) {
                        int[] h = chunkHandle.getIntArray("HeightMap");
                        for (int hi = 0; hi < h.length; hi++) {
                            h[hi] = Math.min(Math.max(h[hi] + offsetY, dataWorldHeight.getMinHeight()), dataWorldHeight.getMaxHeight() - 1);
                        }
                        chunkHandle.putIntArray("HeightMap", h);
                    }


                    if (newMinY != null) {
                        chunkHandle.putInt("yPos", newMinY);
                        Field yPosField = chunk.getClass().getSuperclass().getDeclaredField("yPos");
                        yPosField.setAccessible(true);
                        yPosField.setInt(chunk, newMinY);
                    }

                    regionMCA.write(chunk);
                    //chunkIterator.set(chunk);
                    writtenChunks++;
                }

                for (ChunkPos chunkXZ : chunksToRemove) {
                    regionMCA.removeChunkAbsolute(chunkXZ.getX(), chunkXZ.getZ());
                }


                regionMCA.flush();

            } catch (Exception ex) {
                System.out.println(ex.getMessage());
                ex.printStackTrace();
            }
            //}

            if (writtenChunks == 0 && newRegion.delete()) {
                System.out.println();
                System.out.println("Deleted empty region file: " + newRegion.getName());
                System.out.println();
            }
        }

        return removedChunks;
    }
}
