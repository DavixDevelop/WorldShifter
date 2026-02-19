package com.davixdevelop.worldshifter.model;

import com.davixdevelop.worldshifter.Main;
import com.davixdevelop.worldshifter.utils.EntityUtils;
import com.davixdevelop.worldshifter.utils.LogUtils;
import com.davixdevelop.worldshifter.utils.Utils;
import io.github.ensgijs.nbt.mca.SectionBase;
import io.github.ensgijs.nbt.mca.TerrainChunk;
import io.github.ensgijs.nbt.mca.TerrainSection;
import io.github.ensgijs.nbt.mca.io.RandomAccessMcaFile;
import io.github.ensgijs.nbt.tag.*;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static io.github.ensgijs.nbt.mca.DataVersion.JAVA_1_18_0;
import static io.github.ensgijs.nbt.mca.DataVersion.JAVA_1_18_21W43A;

public class MultiTerrainRegion extends MultiRegion<TerrainChunk> {
    private final int sectionOffsetY;
    private final AtomicInteger excludedSections;
    private final AtomicInteger removedEntities;

    public MultiTerrainRegion(File sourceRegionFile, Integer targetWorldMin, Integer targetWorldMax, int offsetY, int sectionOffsetY, File outputFolder, boolean isMultiWorld, AtomicInteger emptySections, AtomicInteger removedEntities) {
        super(sourceRegionFile, outputFolder, "region", isMultiWorld, targetWorldMin, targetWorldMax, offsetY);
        this.sectionOffsetY = sectionOffsetY;
        this.excludedSections = emptySections;
        this.removedEntities = removedEntities;
    }

    @Override
    public void shiftRegion() throws Exception {
        int dataVersion = 0;
        WorldHeight targetWorldHeight = null;

        try (RandomAccessMcaFile<TerrainChunk> regionMCA = new RandomAccessMcaFile<>(TerrainChunk.class, sourceRegionFile, "r")) {
            regionMCA.touch();
            ChunkPos chunkPos = null;

            for (int index = 0; index < 1024; index++) {
                TerrainChunk chunk = null;

                try {
                    if (!regionMCA.hasChunk(index)) {
                        emptyChunks.add(index);
                        continue;
                    }

                    chunk = regionMCA.read(index);
                } catch (Exception ex) {
                    LogUtils.logError("Error while reading next chunk at index:" + index + " " + ((chunkPos != null) ? "(Previous was " + chunkPos.toString() +  ")" : ""), sourceRegionFile, ex);
                    continue;
                }

                if (chunk == null) {
                    emptyChunks.add(index);
                    continue;
                }

                chunkPos = new ChunkPos(chunk.getChunkX(), chunk.getChunkZ());

                if (dataVersion != chunk.getDataVersion() || targetWorldHeight == null) {
                    dataVersion = chunk.getDataVersion();
                    targetWorldHeight = Main.WORLDS_HEIGHTS.get().get(dataVersion);

                    if (dataVersion >= JAVA_1_18_0.id() && targetWorldMin != null && targetWorldMax != null && targetWorldMin >= -2032 && targetWorldMax <= 2032) {
                        targetWorldHeight = new WorldHeight(targetWorldMin, targetWorldMax);
                    }
                }



                Integer minChunkWorldIndex = null;
                Integer maxChunkWorldIndex = null;

                ListTag<CompoundTag> entities = chunk.getEntities();
                TreeMap<Integer, ListTag<CompoundTag>> entitiesPerWorldIndex = new TreeMap<>();

                if (entities != null && !entities.isEmpty()) {
                    entitiesPerWorldIndex = EntityUtils.offsetEntities(entities, offsetY, dataVersion, targetWorldHeight, removedEntities, isMultiWorld);

                    if(entitiesPerWorldIndex.isEmpty()) {
                        minChunkWorldIndex = entitiesPerWorldIndex.firstKey();
                        maxChunkWorldIndex = entitiesPerWorldIndex.lastKey();
                    }
                }

                ListTag<CompoundTag> tileEntities = chunk.getTileEntities();
                TreeMap<Integer, ListTag<CompoundTag>> tileEntitiesPerWorldIndex = new TreeMap<>();

                if (tileEntities != null && !tileEntities.isEmpty()) {
                     tileEntitiesPerWorldIndex = EntityUtils.offsetEntities(tileEntities, offsetY, dataVersion, targetWorldHeight, removedEntities, isMultiWorld);

                     if(!tileEntitiesPerWorldIndex.isEmpty()) {
                         if (minChunkWorldIndex == null || minChunkWorldIndex > tileEntitiesPerWorldIndex.firstKey())
                             minChunkWorldIndex = tileEntitiesPerWorldIndex.firstKey();
                         if (maxChunkWorldIndex == null || maxChunkWorldIndex < tileEntitiesPerWorldIndex.lastKey())
                             maxChunkWorldIndex = tileEntitiesPerWorldIndex.lastKey();
                     }
                }

                TreeMap<Integer, TreeMap<Integer, TerrainSection>> terrainSectionsPerWorld = new TreeMap<>();

                int minSectionY = chunk.getMinSectionY();
                int maxSectionY = chunk.getMaxSectionY(); //inclusive

                //Offset sections
                if (chunk.hasSections()) {
                    for (int sectionY = minSectionY; sectionY <= maxSectionY; sectionY++) {
                        TerrainSection terrainSection = chunk.getSection(sectionY);
                        int newSectionY = sectionY + sectionOffsetY;
                        int worldIndex = targetWorldHeight.getWorldIndexFromSection(newSectionY);

                        //Check if it lies into the world or if multi world is enabled
                        if (isMultiWorld || worldIndex == 0) {
                            //Make the section pos fit into the world height if world index differs from 0
                            if (worldIndex != 0)
                                newSectionY = targetWorldHeight.calcSectionOffset(worldIndex, newSectionY);

                            terrainSectionsPerWorld.computeIfAbsent(worldIndex, ind -> new TreeMap<>())
                                    .put(newSectionY, terrainSection);
                        } else
                            excludedSections.incrementAndGet();
                    }

                    if(!terrainSectionsPerWorld.isEmpty()) {
                        if (minChunkWorldIndex == null || minChunkWorldIndex > terrainSectionsPerWorld.firstKey())
                            minChunkWorldIndex = terrainSectionsPerWorld.firstKey();
                        if (maxChunkWorldIndex == null || maxChunkWorldIndex < terrainSectionsPerWorld.lastKey())
                            maxChunkWorldIndex = terrainSectionsPerWorld.lastKey();
                    }

                }

                CompoundTag blendingData = chunk.getBlendingData();
                CompoundTag upgradeData = chunk.getUpgradeData();

                Integer blendingMinSection = null;
                Integer blendingMaxSection = null;
                if(blendingData != null) {
                    blendingMinSection = blendingData.getInt("min_section");
                    blendingMaxSection = blendingData.getInt("max_section");
                }

                if(blendingMinSection == null)
                    blendingMinSection = minSectionY;

                boolean isLegacyClassic = blendingData == null && dataVersion < JAVA_1_18_21W43A.id();

                //Offset legacy heightmap
                IntArrayTag legacyHeightMap = chunk.getLegacyHeightMap();
                //Array to store the offset heights map per world index
                //When multi world is disabled, this array is clipped to the target world height
                int[] offsetLegacyHeightMap = new int[256];

                if(legacyHeightMap != null) {
                    int[] heights = legacyHeightMap.getValue();
                    if(Arrays.stream(heights).min().isPresent()) {

                        if (isMultiWorld) {
                            //Offset the legacy heightmap
                            offsetLegacyHeightMap = Arrays.stream(heights).flatMap(h -> IntStream.of(h + offsetY)).toArray();

                        } else {
                            WorldHeight finalTargetWorldHeight = targetWorldHeight;
                            //Offset and clip the legacy height map to the target world height
                            offsetLegacyHeightMap = Arrays.stream(heights).flatMap(h -> IntStream.of(Math.min(Math.max(h + offsetY, finalTargetWorldHeight.getMinHeight()), finalTargetWorldHeight.getMaxHeight() - 1))).toArray();
                        }

                        OptionalInt min = Arrays.stream(offsetLegacyHeightMap).min();
                        if(min.isPresent()) {
                            int minOffsetHeightMapWorldIndex = targetWorldHeight.getWorldIndexFromY(min.getAsInt());
                            int maxOffsetHeightMapWorldIndex = targetWorldHeight.getWorldIndexFromY(Arrays.stream(offsetLegacyHeightMap).max().getAsInt());

                            if(minChunkWorldIndex == null || minChunkWorldIndex > minOffsetHeightMapWorldIndex)
                                minChunkWorldIndex = minOffsetHeightMapWorldIndex;
                            if(maxChunkWorldIndex == null || maxChunkWorldIndex < maxOffsetHeightMapWorldIndex)
                                maxChunkWorldIndex = maxOffsetHeightMapWorldIndex;
                        }
                    }
                }

                CompoundTag heightMapsTag = chunk.getHeightMaps();
                TreeMap<String, int[]> heightMaps = new TreeMap<>();
                if(heightMapsTag != null) {
                    int chunkYPosBlock = chunk.getChunkY() * 16;

                    for(String key : heightMapsTag.keySet()) {
                        LongArrayTag tag = heightMapsTag.getLongArrayTag(key);
                        int[] decoded = Utils.decodeDataLongArray(tag.getValue(), 9);

                        int[] heights = new int[256];
                        Integer min = null;
                        Integer max = null;
                        boolean isEmpty = false;
                        for(int i = 0; i < 256; i++) {
                            final int h = chunkYPosBlock - 1 + decoded[i];

                            //If a height is bellow the min source world height, exclude the height map
                            if(h < chunkYPosBlock) {
                                isEmpty = true;
                                break;
                            }

                            if(min == null) {
                                min = h;
                                max = h;
                            } else {
                                if(h < min)
                                    min = h;
                                else if(h > max)
                                    max = h;
                            }
                            heights[i] = h;
                        }

                        if(isEmpty)
                            continue;

                        min = targetWorldHeight.getWorldIndexFromY(min + offsetY);
                        max = targetWorldHeight.getWorldIndexFromY(max + offsetY);

                        if(minChunkWorldIndex == null || minChunkWorldIndex > min)
                            minChunkWorldIndex = min;

                        else if(maxChunkWorldIndex == null || maxChunkWorldIndex < max)
                            maxChunkWorldIndex = max;

                        heightMaps.put(key, heights);
                    }
                }

                TreeMap<Integer, TreeMap<Integer, ListTag<?>>> postProcessingPerWorldIndex = new TreeMap<>();
                ListTag<ListTag<?>> postProcessing = chunk.getPostProcessing();
                if(postProcessing != null && !postProcessing.isEmpty()) {
                    postProcessingPerWorldIndex = offsetToBeTickedFormat(postProcessing, chunk.getChunkY(), targetWorldHeight);

                    if(!postProcessingPerWorldIndex.isEmpty()) {
                        if (minChunkWorldIndex == null || minChunkWorldIndex > postProcessingPerWorldIndex.firstKey())
                            minChunkWorldIndex = postProcessingPerWorldIndex.firstKey();

                        if (maxChunkWorldIndex == null || maxChunkWorldIndex < postProcessingPerWorldIndex.lastKey())
                            maxChunkWorldIndex = postProcessingPerWorldIndex.lastKey();
                    }
                }

                TreeMap<Integer, TreeMap<Integer, ListTag<?>>> toBeTickedPerWorldIndex = new TreeMap<>();
                ListTag<ListTag<?>> toBeTicked = chunk.getToBeTicked();
                if(toBeTicked != null && !toBeTicked.isEmpty()) {
                    toBeTickedPerWorldIndex = offsetToBeTickedFormat(toBeTicked, chunk.getChunkY(), targetWorldHeight);

                    if(!toBeTickedPerWorldIndex.isEmpty()) {
                        if (minChunkWorldIndex == null || minChunkWorldIndex > toBeTickedPerWorldIndex.firstKey())
                            minChunkWorldIndex = toBeTickedPerWorldIndex.firstKey();

                        if (maxChunkWorldIndex == null || maxChunkWorldIndex < toBeTickedPerWorldIndex.lastKey())
                            maxChunkWorldIndex = toBeTickedPerWorldIndex.lastKey();
                    }
                }

                TreeMap<Integer, TreeMap<Integer, ListTag<?>>> liquidsToBeTickedPerWorldIndex = new TreeMap<>();
                ListTag<ListTag<?>> liquidsToBeTicked = chunk.getLiquidsToBeTicked();
                if(liquidsToBeTicked != null && !liquidsToBeTicked.isEmpty()) {
                    liquidsToBeTickedPerWorldIndex = offsetToBeTickedFormat(liquidsToBeTicked, chunk.getChunkY(), targetWorldHeight);

                    if(!liquidsToBeTickedPerWorldIndex.isEmpty()) {
                        if (minChunkWorldIndex == null || minChunkWorldIndex > liquidsToBeTickedPerWorldIndex.firstKey())
                            minChunkWorldIndex = liquidsToBeTickedPerWorldIndex.firstKey();

                        if (maxChunkWorldIndex == null || maxChunkWorldIndex < liquidsToBeTickedPerWorldIndex.lastKey())
                            maxChunkWorldIndex = liquidsToBeTickedPerWorldIndex.lastKey();
                    }
                }

                ListTag<CompoundTag> tileTicks = chunk.getTileTicks();
                TreeMap<Integer, ListTag<CompoundTag>> tileTicksPerWorldIndex = new TreeMap<>();
                if(tileTicks != null && !tileTicks.isEmpty()) {
                    tileTicksPerWorldIndex = offsetTileTickFormat(tileTicks, targetWorldHeight);

                    if(!tileTicksPerWorldIndex.isEmpty()) {
                        if (minChunkWorldIndex == null || minChunkWorldIndex > tileTicksPerWorldIndex.firstKey())
                            minChunkWorldIndex = tileTicksPerWorldIndex.firstKey();

                        if (maxChunkWorldIndex == null || maxChunkWorldIndex < tileTicksPerWorldIndex.lastKey())
                            maxChunkWorldIndex = tileTicksPerWorldIndex.lastKey();
                    }
                }

                ListTag<CompoundTag> liquidTicks = chunk.getLiquidTicks();
                TreeMap<Integer, ListTag<CompoundTag>> liquidTicksPerWorldIndex = new TreeMap<>();
                if(liquidTicks != null && !liquidTicks.isEmpty()) {
                    liquidTicksPerWorldIndex = offsetTileTickFormat(liquidTicks, targetWorldHeight);

                    if(!liquidTicksPerWorldIndex.isEmpty()) {
                        if (minChunkWorldIndex == null || minChunkWorldIndex > liquidTicksPerWorldIndex.firstKey())
                            minChunkWorldIndex = liquidTicksPerWorldIndex.firstKey();

                        if (maxChunkWorldIndex == null || maxChunkWorldIndex < liquidTicksPerWorldIndex.lastKey())
                            maxChunkWorldIndex = liquidTicksPerWorldIndex.lastKey();
                    }
                }


                if(minChunkWorldIndex == null || maxChunkWorldIndex == null)
                    continue;

                if(!isMultiWorld) {
                    //If multi world is disabled, always choose the world index 0
                    //This is so that each chunk in each region in the target corresponds to the same world index
                    minChunkWorldIndex = 0;
                    maxChunkWorldIndex = 0;
                }

                for(int worldIndex = minChunkWorldIndex; worldIndex <= maxChunkWorldIndex; worldIndex++) {
                    TerrainChunk indexChunk = getChunk(chunkPos, worldIndex);

                    if(indexChunk == null)
                        continue;

                    if(entitiesPerWorldIndex.containsKey(worldIndex)) {
                        ListTag<CompoundTag> ent = entitiesPerWorldIndex.get(worldIndex);
                        indexChunk.setEntities(ent);
                    }

                    if(tileEntitiesPerWorldIndex.containsKey(worldIndex)) {
                        ListTag<CompoundTag> tent = tileEntitiesPerWorldIndex.get(worldIndex);
                        indexChunk.setTileEntities(tent);
                    }

                    if(terrainSectionsPerWorld.containsKey(worldIndex)) {
                        TreeMap<Integer, TerrainSection> sections = terrainSectionsPerWorld.get(worldIndex);

                        //int indexTerrainMinSectionY = sections.firstKey();

                        for (Integer y : sections.keySet()) {
                            indexChunk.setSection(y, sections.get(y));
                        }

                        if (upgradeData != null && upgradeData.containsKey("Indices")) {
                            CompoundTag indexUpgradeData = indexChunk.getUpgradeData();

                            CompoundTag indices = indexUpgradeData.getCompoundTag("Indices");
                            CompoundTag newIndices = new CompoundTag();

                            for (String key : indices.keySet()) {
                                int indY = Integer.parseInt(key);

                                int newSectionY = ((blendingMinSection != null ? blendingMinSection : minSectionY) + indY) + sectionOffsetY;
                                //Only treat key of indice as absolute section y if blending data is not present
                                // and the version is less than 1.18
                                if (isLegacyClassic) {
                                    newSectionY = indY + sectionOffsetY;
                                }

                                int newWorldIndex = targetWorldHeight.getWorldIndexFromSection(newSectionY);

                                //Only include indices that fit into the current world index
                                // and multi world is enabled or current world index is 0
                                if(newWorldIndex == worldIndex && (isMultiWorld || worldIndex == 0)) {

                                    //Make the section pos fit into the world height if world index differs from 0
                                    if (worldIndex != 0)
                                        newSectionY = targetWorldHeight.calcSectionOffset(worldIndex, newSectionY);


                                    //int newIndY = (isLegacyClassic) ? newSectionY : newSectionY - indexTerrainMinSectionY;
                                    int newIndY = (isLegacyClassic) ? newSectionY : newSectionY - targetWorldHeight.getFirstSection();

                                    //Add the new indices at the new key using the old indice value
                                    newIndices.putIntArray(String.valueOf(newIndY), indices.getIntArray(key));
                                }
                            }

                            //Re-add the new indices
                            indexUpgradeData.put("Indices", newIndices);
                            indexChunk.setUpgradeData(indexUpgradeData);
                        }
                    } else {
                        //If the current world index has no sections, but the source chunk has
                        // upgrade data, make sure to reset them

                        if(upgradeData != null && upgradeData.containsKey("Indices")) {
                            CompoundTag indexUpgradeData = indexChunk.getUpgradeData();
                            indexUpgradeData.clear();
                            indexChunk.setUpgradeData(indexUpgradeData);
                        }
                    }

                    if (blendingData != null) {
                        CompoundTag indexBlendingData = indexChunk.getBlendingData();
                        int indexBlendingMaxSection = blendingMaxSection + sectionOffsetY;
                        int indexBlendingMinSection = blendingMinSection + sectionOffsetY;


                        int indexBlendMaxWI = targetWorldHeight.getWorldIndexFromSection(indexBlendingMaxSection);
                        int indexBlendMinWI = targetWorldHeight.getWorldIndexFromSection(indexBlendingMinSection);

                        //Only set the blending data, if the current world index fits into
                        //the blending  data min anx max world index
                        if(worldIndex >= indexBlendMinWI && worldIndex <= indexBlendMaxWI) {
                            if (indexBlendMaxWI == worldIndex) {
                                if (worldIndex != 0)
                                    indexBlendingMaxSection = targetWorldHeight.calcSectionOffset(indexBlendMaxWI, indexBlendingMaxSection);
                            } else if (indexBlendMaxWI > worldIndex) {
                                indexBlendingMaxSection = targetWorldHeight.getLastSection() + 1;
                            }

                            if (indexBlendMinWI == worldIndex) {
                                if (worldIndex != 0)
                                    indexBlendingMinSection = targetWorldHeight.calcSectionOffset(indexBlendMinWI, indexBlendingMinSection);
                            } else if (indexBlendMinWI < worldIndex) {
                                indexBlendingMinSection = targetWorldHeight.getFirstSection();
                            }

                            if (indexBlendingData.containsKey("max_section"))
                                indexBlendingData.putInt("max_section", indexBlendingMaxSection);

                            if (indexBlendingData.containsKey("min_section"))
                                indexBlendingData.putInt("min_section", indexBlendingMinSection);

                        }else //else clear it
                            indexBlendingData.clear();
                    }

                    if(legacyHeightMap != null) {
                        IntArrayTag indexLegacyHeightMap = indexChunk.getLegacyHeightMap();
                        int[] indexLegacyHeight = indexLegacyHeightMap.getValue();

                        for(int i = 0; i < offsetLegacyHeightMap.length; i++) {
                            int h = offsetLegacyHeightMap[i];
                            int hWorldIndex = targetWorldHeight.getWorldIndexFromY(h);
                            indexLegacyHeight[i] = isMultiWorld ? ( hWorldIndex == worldIndex ? targetWorldHeight.calcBlockOffset(worldIndex, h) :
                                    (hWorldIndex < worldIndex ? targetWorldHeight.getMinHeight() : targetWorldHeight.getMaxHeight() - 1)) : h;
                        }

                        indexLegacyHeightMap.setValue(indexLegacyHeight);
                        indexChunk.setLegacyHeightMap(indexLegacyHeightMap);
                    }

                    if(heightMapsTag != null) {
                        int bits_per_value = 32 - Integer.numberOfLeadingZeros(targetWorldHeight.getWorldHeight() - 1);
                        int chunkYPosBlock = targetWorldHeight.getMinHeight();

                        CompoundTag indexHeightMaps = indexChunk.getHeightMaps();
                        int[] decoded = new int[256];
                        for (String key : heightMaps.keySet()) {
                            int[] heights = heightMaps.get(key);
                            for (int i = 0; i < heights.length; i++) {
                                int h = heights[i] + offsetY;
                                int hWorldIndex = targetWorldHeight.getWorldIndexFromY(h);
                                h =  isMultiWorld ? ( hWorldIndex == worldIndex ? targetWorldHeight.calcBlockOffset(worldIndex, h) :
                                        (hWorldIndex < worldIndex ? chunkYPosBlock : targetWorldHeight.getMaxHeight() - 1)) :
                                        Math.min(Math.max(h, chunkYPosBlock), targetWorldHeight.getMaxHeight() - 1);
                                decoded[i] = h  - chunkYPosBlock + 1;
                            }


                            long[] encoded = Utils.encodeDataLongArray(decoded, bits_per_value);
                            indexHeightMaps.putLongArray(key, encoded);
                        }

                        indexChunk.setHeightMaps(indexHeightMaps);

                    }

                    if(postProcessing != null || !postProcessing.isEmpty()) {
                        ListTag<ListTag<?>> indexPostProcessing = indexChunk.getPostProcessing();

                        if(postProcessingPerWorldIndex.containsKey(worldIndex)) {
                            TreeMap<Integer, ListTag<?>> newPostProc = postProcessingPerWorldIndex.get(worldIndex);

                            //Create empty section, to use fill it up to the actual min new section
                            ListTag<?> empty = postProcessing.getFirst().clone();
                            empty.clear();
                            //Fill the index post-processing up the new post-processing last segment
                            updateToBeTickedFormat(indexPostProcessing, newPostProc, empty);
                            indexChunk.setPostProcessing(indexPostProcessing);
                        }
                    }

                    if(toBeTicked != null && !toBeTicked.isEmpty() && toBeTickedPerWorldIndex.containsKey(worldIndex)) {
                        ListTag<ListTag<?>> indexToBeTicked = indexChunk.getToBeTicked();
                        TreeMap<Integer, ListTag<?>> newToBeTicked = toBeTickedPerWorldIndex.get(worldIndex);

                        //Create empty section, to use fill it up to the actual min new section
                        ListTag<?> empty = toBeTicked.getFirst().clone();
                        empty.clear();
                        //Fill the index to be ticked up to the new to be ticked last segment
                        updateToBeTickedFormat(indexToBeTicked, newToBeTicked, empty);
                        indexChunk.setToBeTicked(indexToBeTicked);
                    }

                    if(liquidsToBeTicked != null && !liquidsToBeTicked.isEmpty() && liquidsToBeTickedPerWorldIndex.containsKey(worldIndex)) {
                        ListTag<ListTag<?>> indexLiquidsToBeTicked = indexChunk.getLiquidsToBeTicked();
                        TreeMap<Integer, ListTag<?>> newLiquidsToBeTicked = liquidsToBeTickedPerWorldIndex.get(worldIndex);

                        //Create empty section, to use fill it up to the actual min new section
                        ListTag<?> empty = liquidsToBeTicked.getFirst().clone();
                        empty.clear();
                        //Fill the index liquids to be ticked up to the new liquids to be ticked last segment
                        updateToBeTickedFormat(indexLiquidsToBeTicked, newLiquidsToBeTicked, empty);
                        indexChunk.setLiquidsToBeTicked(indexLiquidsToBeTicked);
                    }

                    if(tileTicks != null && !tileTicks.isEmpty() && tileTicksPerWorldIndex.containsKey(worldIndex))
                        indexChunk.setTileTicks(tileTicksPerWorldIndex.get(worldIndex));

                    if(liquidTicks != null && !liquidTicks.isEmpty() && liquidTicksPerWorldIndex.containsKey(worldIndex)) {
                        indexChunk.setLiquidTicks(liquidTicksPerWorldIndex.get(worldIndex));
                    }

                    CompoundTag indexChunkHandle = indexChunk.getHandle();
                    indexChunkHandle.putInt("yPos", targetWorldHeight.getFirstSection());
                    Field yPosField = indexChunk.getClass().getSuperclass().getDeclaredField("yPos");
                    yPosField.setAccessible(true);
                    yPosField.setInt(indexChunk, targetWorldHeight.getFirstSection());
                    yPosField.setAccessible(false);
                }
            }

            //Flush the world slices region files
            flushRegions();
        }
    }

    @Override
    public RandomAccessMcaFile<TerrainChunk> readMca(File regionFile) throws IOException {
        RandomAccessMcaFile<TerrainChunk> regionMCA = new RandomAccessMcaFile<>(TerrainChunk.class, regionFile, "rw");
        regionMCA.touch();
        return regionMCA;
    }

    @Override
    public void clearMca(RandomAccessMcaFile<TerrainChunk> regionMCA) throws IOException {
        ChunkPos chunkPos = null;
        int dataVersion = 0;
        WorldHeight targetWorldHeight = null;

        for (int index = 0; index < 1024; index++) {
            TerrainChunk chunk = null;

            try {
                if (emptyChunks.contains(index) || !regionMCA.hasChunk(index)) {
                    emptyChunks.add(index);
                    continue;
                }

                chunk = regionMCA.read(index);
            } catch (Exception ex) {
                LogUtils.logError("Error while reading next chunk at index:" + index + " " + ((chunkPos != null) ? "(Previous was " + chunkPos.toString() +  ")" : "") + "\n File: world-shifted/temp/region/" + sourceRegionFile.getName() , ex);
                continue;
            }

            if (chunk == null) {
                emptyChunks.add(index);
                continue;
            }

            chunkPos = new ChunkPos(chunk.getChunkX(), chunk.getChunkZ());


            if (dataVersion != chunk.getDataVersion() || targetWorldHeight == null) {
                dataVersion = chunk.getDataVersion();
                targetWorldHeight = Main.WORLDS_HEIGHTS.get().get(dataVersion);

                if (dataVersion >= JAVA_1_18_0.id() && targetWorldMin != null && targetWorldMax != null && targetWorldMin >= -2032 && targetWorldMax <= 2032) {
                    targetWorldHeight = new WorldHeight(targetWorldMin, targetWorldMax);
                }
            }

            chunk.setTileEntities(ListTag.createUnchecked(CompoundTag.class).asCompoundTagList());
            chunk.setEntities(ListTag.createUnchecked(CompoundTag.class).asCompoundTagList());
            ListTag<ListTag<?>> postProcessing = chunk.getPostProcessing();
            if(postProcessing != null && !postProcessing.isEmpty()) {
                //Clear the post-processing
                postProcessing.clear();
                chunk.setPostProcessing(postProcessing);
            }

            ListTag<ListTag<?>> toBeTicked = chunk.getToBeTicked();
            if(toBeTicked != null && !toBeTicked.isEmpty()) {
                //Clear the to be ticked
                toBeTicked.clear();
                chunk.setToBeTicked(toBeTicked);
            }

            ListTag<ListTag<?>> liquidsToBeTicked = chunk.getLiquidsToBeTicked();
            if(liquidsToBeTicked != null && !liquidsToBeTicked.isEmpty()) {
                //Clear the liquids to be ticked
                liquidsToBeTicked.clear();
                chunk.setLiquidsToBeTicked(liquidsToBeTicked);
            }

            ListTag<CompoundTag> tileTicks = chunk.getTileTicks();
            if(tileTicks != null && !tileTicks.isEmpty()) {
                //Clear the tile ticks
                tileTicks.clear();
                chunk.setTileTicks(tileTicks);
            }

            ListTag<CompoundTag> liquidTicks = chunk.getLiquidTicks();
            if(liquidTicks != null && !liquidTicks.isEmpty()) {
                //Clear the liquid ticks
                liquidTicks.clear();
                chunk.setLiquidTicks(liquidTicks);
            }

            int minY = chunk.getMinSectionY();
            int maxY = chunk.getMaxSectionY(); //inclusive


            if (minY != SectionBase.NO_SECTION_Y_SENTINEL && maxY != SectionBase.NO_SECTION_Y_SENTINEL) {

                for (int sectionY = minY; sectionY <= maxY; sectionY++) {
                    chunk.setSection(sectionY, null);
                }
            }

            regionMCA.write(chunk);
        }
    }

    /**
     * Sort the to be ticked format list tags to the target world indexes in the form of a map after shifting
     * @param toBeTicked The to be tocked format list tag to be sorted
     * @param chunkYPos The bottom y section of the chunk
     * @param targetWorldHeight The target world height
     * @return A map where the key is the world index and the value are a map of to be ticked format list tags per index from the bottom y section of the chunk
     */
    private TreeMap<Integer, TreeMap<Integer, ListTag<?>>> offsetToBeTickedFormat(ListTag<ListTag<?>> toBeTicked, int chunkYPos, WorldHeight targetWorldHeight) {
        TreeMap<Integer, TreeMap<Integer, ListTag<?>>> toBeTickedPerWorldIndex = new TreeMap<>();

        for(int i = 0; i < toBeTicked.size(); i++) {
            int newSectionPos = chunkYPos + i + sectionOffsetY;
            int newSectionPosWorldIndex = targetWorldHeight.getWorldIndexFromSection(newSectionPos);
            //Only include to be ticked section if multi world is enabled or the world index is 0
            if(isMultiWorld || newSectionPosWorldIndex == 0) {
                TreeMap<Integer, ListTag<?>> indexToBeTicked = toBeTickedPerWorldIndex.computeIfAbsent(newSectionPosWorldIndex, ind -> new TreeMap<>());

                //Make the section pos fit into the world height if world index differs from 0
                if(newSectionPosWorldIndex != 0)
                    newSectionPos = targetWorldHeight.calcSectionOffset(newSectionPosWorldIndex, newSectionPos);

                //Calculate the index (from bottom of chunk to top) position within the world slice for the new section pos
                indexToBeTicked.put( newSectionPos - targetWorldHeight.getFirstSection(), toBeTicked.get(i));
            }
        }

        return toBeTickedPerWorldIndex;
    }

    /**
     * Update the to be ticked format with the new to be ticked format segments, so that if the new to be ticked format sections are
     * at the offset from the chunky y pos, these pre offset section contain empty sections
     * @param toBeTickedFormat The to be ticked format to be updated. **Must be empty**
     * @param newToBeTicked A tree map that contains the new segments at the proper index from the bottom of the chunk. The key is the index, while the value is the to be ticked format
     * @param emptySection An empty to be ticked section to use as filler
     */
    private void updateToBeTickedFormat(ListTag<ListTag<?>> toBeTickedFormat, TreeMap<Integer, ListTag<?>> newToBeTicked, ListTag<?> emptySection) {
        //Fill the index to be ticked format up the new to be ticked format last segment
        for(int i = 0; i <= newToBeTicked.lastKey(); i++) {
            //If the indice is less then the new to be ticked format first segment
            //add an empty segment. This makes sure the actual new to be ticked format segments are at the
            //correct offset from the bottom of the chunk
            if(i < newToBeTicked.firstKey())
                toBeTickedFormat.add(emptySection.clone());
            else
                toBeTickedFormat.add(newToBeTicked.get(i));
        }
    }

    /**
     * Sort the tile tick format compound tag list to the target world indexes in the form of a map after shifting
     * @param tileTickFormatList The compound tag list of tile tick format to be sorted
     * @param targetWorldHeight The target world height
     * @return A mep where the key is the world index and the values ore a compound tag list of tile tick formats
     */
    private TreeMap<Integer, ListTag<CompoundTag>> offsetTileTickFormat(ListTag<CompoundTag> tileTickFormatList, WorldHeight targetWorldHeight){
        TreeMap<Integer, ListTag<CompoundTag>> tileTickFormatPerWorldIndex = new TreeMap<>();

        for(CompoundTag tileTickFormat : tileTickFormatList) {
            if(tileTickFormat.containsKey("y")) {
                int yPos = tileTickFormat.getInt("y") + offsetY;
                int yPosWorldIndex = targetWorldHeight.getWorldIndexFromY(yPos);

                //Only include the tile tick if multi world is enabled or the world index is 0
                if(isMultiWorld || yPosWorldIndex == 0) {
                    ListTag<CompoundTag> indexTileTickFormatList = tileTickFormatPerWorldIndex.computeIfAbsent(yPosWorldIndex, y-> ListTag.createUnchecked(CompoundTag.class).asCompoundTagList());

                    //Make the tile tick fit into the world height if world index differs from 0
                    if(yPosWorldIndex != 0)
                        yPos = targetWorldHeight.calcBlockOffset(yPosWorldIndex, yPos);

                    tileTickFormat.putInt("y", yPos);
                    indexTileTickFormatList.add(tileTickFormat);
                }
            }
        }

        return tileTickFormatPerWorldIndex;
    }
}
