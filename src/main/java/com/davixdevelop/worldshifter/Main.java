package com.davixdevelop.worldshifter;

import io.github.ensgijs.nbt.mca.*;
import io.github.ensgijs.nbt.mca.io.*;
import io.github.ensgijs.nbt.mca.util.ChunkIterator;
import io.github.ensgijs.nbt.mca.util.PalettizedCuboid;
import io.github.ensgijs.nbt.tag.CompoundTag;
import io.github.ensgijs.nbt.tag.IntArrayTag;
import io.github.ensgijs.nbt.tag.ListTag;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class Main {
    public static void main(String[] args) {
        if(args == null || args.length == 0 || args.length < 2) {
            System.out.println("No input world path/offset");
            return;
        }

        String inputWorld = args[0];
        int offsetY = Integer.parseInt(args[1]);

        if(offsetY % 16 != 0) {
            System.out.println("Offset must be in the values of 16");
            return;
        }

        int sectionOffsetY = offsetY / 16;

        String regionFolderPath = Paths.get(inputWorld, "region").toString();
        File regionFolder = new File(regionFolderPath);
        if(!regionFolder.isDirectory()) {
            System.out.println("No region folder in input world");
            return;
        }

        File[] regionFiles = regionFolder.listFiles(path -> path.getName().endsWith("mca"));

        if(regionFiles == null || regionFiles.length == 0) {
            System.out.println("No region files detected");
            return;
        }

        File regionOutputFolder = Paths.get(inputWorld, "regionShifted").toFile();
        regionOutputFolder.mkdirs();
        System.out.println();

        int removedChunks = 0;

        int c = 0;
        for(File regionFile : regionFiles) {
            c++;
            System.out.print("\rProcessing: " + regionFile.getName() + "(" + ((c * 100) / regionFiles.length) + "%)");

            File newRegion = Paths.get(regionOutputFolder.getPath(), regionFile.getName()).toFile();
            if(!copyFile(regionFile, newRegion)) {
                System.out.println("Error while copying source region: " + regionFile.getName());
                System.out.println();
                continue;
            }

            int writtenChunks = 0;
            try(RandomAccessMcaFile<TerrainChunk> regionMCA = new RandomAccessMcaFile<>(TerrainChunk.class, newRegion, "rw")){
                regionMCA.touch();
                ChunkIterator<TerrainChunk> chunkIterator = regionMCA.chunkIterator(LoadFlags.LOAD_ALL_DATA);

                while (chunkIterator.hasNext()) {
                    HashMap<Integer, TerrainSection> offsetSections = new HashMap<>();

                    TerrainChunk chunk = chunkIterator.next();

                    if(chunk == null)
                        continue;

                    Integer newMinY = null;
                    Integer newMaxY = null;

                    if(chunk.getChunkX() == 298174 && chunk.getChunkZ() == -231232) {
                        String w = "Carigrad North";
                    }

                    if(chunk.getChunkX() == 298142 && chunk.getChunkZ() == -231233) {
                        String w = "Carigrad North - Empty Chunk";
                    }

                    if(chunk.getChunkX() == 286632 && chunk.getChunkZ() == -213977) {
                        String w = "Turkey South - Port";
                    }

                    //Offset sections
                    int minY = chunk.getMinSectionY();
                    int maxY = chunk.getMaxSectionY();

                    if(minY != SectionBase.NO_SECTION_Y_SENTINEL && maxY != SectionBase.NO_SECTION_Y_SENTINEL) {
                        Boolean isEmpty = null;

                        for (int sectionY = minY; sectionY <= maxY; sectionY++) {
                            TerrainSection terrainSection = chunk.getSection(sectionY);
                            int newSectionY = sectionY + sectionOffsetY;
                            if (newSectionY >= Byte.MIN_VALUE && newSectionY <= Byte.MAX_VALUE) {
                                offsetSections.put(newSectionY, terrainSection);
                                if (newMinY == null) {
                                    newMinY = newSectionY;
                                    newMaxY = newSectionY;
                                } else if (newSectionY > newMaxY)
                                    newMaxY = newSectionY;

                                PalettizedCuboid<CompoundTag> blockStates = terrainSection.getBlockStates();
                                if(blockStates.paletteSize() > 0) {

                                    if(blockStates.paletteSize() > 1)
                                        isEmpty = false;
                                    else if(isEmpty == null) {
                                        CompoundTag blockState = blockStates.getByRef(0);
                                        if(blockState == null || blockState.isEmpty())
                                            isEmpty = false;
                                        else
                                            isEmpty = blockState.getString("Name").equals("minecraft:air");
                                    }
                                }
                            }

                            chunk.setSection(sectionY, null);
                        }

                        if(isEmpty != null && isEmpty) {
                            //Remove empty chunk
                            regionMCA.removeChunk(chunk.getIndex());
                            removedChunks++;
                            continue;
                        }

                    }else {
                        //Remove empty chunk
                        regionMCA.removeChunk(chunk.getIndex());
                        removedChunks++;
                        continue;
                    }

                    CompoundTag blendingData = chunk.getBlendingData();
                    if(blendingData != null && newMinY != null && newMaxY != null) {
                        if(blendingData.containsKey("max_section")) {
                            blendingData.putInt("max_section", newMaxY);
                        }
                        if(blendingData.containsKey("min_section"))
                            blendingData.putInt("min_section", newMinY);
                    }

                    for(Map.Entry<Integer, TerrainSection> sectionEntry : offsetSections.entrySet()) {
                        chunk.setSection(sectionEntry.getKey(), sectionEntry.getValue());
                    }

                    //Offset entities
                    ListTag<CompoundTag> entities = chunk.getEntities();
                    if(entities != null && !entities.isEmpty()) {
                        Iterator<CompoundTag> tagIterator = entities.iterator();

                        while (tagIterator.hasNext()) {
                            CompoundTag entity = tagIterator.next();
                            Boolean res = offsetTags(entity, offsetY);

                            if(res == null)
                                tagIterator.remove();
                        }

                        chunk.setEntities(entities);
                    }

                    ListTag<CompoundTag> tileEntities = chunk.getTileEntities();
                    if(tileEntities != null && !tileEntities.isEmpty()) {
                        Iterator<CompoundTag> tagIterator = tileEntities.iterator();
                        while (tagIterator.hasNext()) {
                            CompoundTag tileEntity = tagIterator.next();
                            Boolean res = offsetTags(tileEntity, offsetY);

                            if(res == null)
                                tagIterator.remove();
                        }

                        chunk.setTileEntities(tileEntities);
                    }


                    CompoundTag chunkHandle = chunk.getHandle();

                    //Offset HeightMap
                    IntArrayTag legacyHeightMap = chunk.getLegacyHeightMap();
                    if(legacyHeightMap != null) {
                        int[] h = legacyHeightMap.getValue();
                        for(int hi = 0; hi < h.length; hi++)
                            h[hi] += Math.min(Math.max(h[hi] + offsetY, -2032), 2031);

                        legacyHeightMap.setValue(h);
                        chunk.setLegacyHeightMap(legacyHeightMap);
                    }

                    if(chunkHandle.containsKey("HeightMap")) {
                        int[] h = chunkHandle.getIntArray("HeightMap");
                        for(int hi = 0; hi < h.length; hi++) {
                            h[hi] += Math.min(Math.max(h[hi] + offsetY, -2032), 2031);
                        }
                        chunkHandle.putIntArray("HeightMap", h);
                    }


                    if(newMinY != null) {
                        chunkHandle.putInt("yPos", newMinY);
                        Field yPosField = chunk.getClass().getSuperclass().getDeclaredField("yPos");
                        yPosField.setAccessible(true);
                        yPosField.setInt(chunk, newMinY);
                    }

                    regionMCA.write(chunk);
                    writtenChunks++;
                }

                regionMCA.flush();

            }catch (Exception ex) {
                System.out.println(ex.getMessage());
            }

            if(writtenChunks == 0 && newRegion.delete())
            {
                System.out.println();
                System.out.println("Deleted empty region file: " + newRegion.getName());
                System.out.println();
            }


            /*RegionMCAFile mcaFile = new RegionMCAFile(regionFile);
            RegionChunk chunk = mcaFile.getChunk(0);
            var c = chunk.getData();*/
            ///CompoundTag compoundTag = chunk.getData();
        }

        System.out.println();

        if(removedChunks > 0)
            System.out.println("Removed " + removedChunks + " empty chunks");

        if(removedEntities > 0)
            System.out.println("Removed " + removedEntities + " out of bounds entities/tile entities");

        System.out.println("Done");
    }

    private static boolean copyFile(File source, File target) {
        InputStream is = null;
        OutputStream os = null;
        try {
            is = new FileInputStream(source);
            os = new FileOutputStream(target);
            byte[] buffer = new byte[1024];
            int length;
            while ((length = is.read(buffer)) > 0) {
                os.write(buffer, 0, length);
            }

            is.close();
            os.close();

        }catch (Exception ex ) {
            return false;
        }

        return true;
    }

    private static int removedEntities = 0;

    private static Boolean offsetTags(CompoundTag compoundTag, int offsetY) {
        if (compoundTag.containsKey("Pos")) {
            double[] pos = compoundTag.getDoubleTagListAsArray("Pos");
            pos[1] += offsetY;

            if(pos[1] < -2032 || pos[1] > 2031) {
                removedEntities++;
                String id = compoundTag.getString("id");
                return null;
            }

            compoundTag.putDoubleArrayAsTagList("Pos", pos);
        }

        Integer location;
        location = offsetIntTag(compoundTag, "y", offsetY, null);
        location = offsetIntTag(compoundTag, "TileY", offsetY, location);
        location = offsetIntTag(compoundTag, "posY", offsetY, location);

        location = offsetCompoundTag(compoundTag, "FlowerPos", "Y", offsetY, location);
        location = offsetCompoundTag(compoundTag, "HivePos", "Y", offsetY, location);
        location = offsetCompoundTag(compoundTag, "beam_target", "Y", offsetY, location);
        location = offsetIntTag(compoundTag, "TreasurePosY", offsetY, location);
        location = offsetIntTag(compoundTag, "AY", offsetY, location);
        location = offsetIntTag(compoundTag, "APY", offsetY, location);
        location = offsetIntTag(compoundTag, "HomePosY", offsetY, location);
        location = offsetIntTag(compoundTag, "TravelPosY", offsetY, location);
        location = offsetIntTag(compoundTag, "BoundY", offsetY, location);
        location = offsetIntTag(compoundTag, "AY", offsetY, location);

        if(location != null) {
            if(location < -2032 || location > 2031) {
                removedEntities++;
                return null;
            }

            return true;
        }

        return false;

    }

    private static Integer offsetIntTag(CompoundTag compoundTag, String tagName, int offset, Integer newLocation) {
        if(compoundTag.containsKey(tagName)) {
            int y = compoundTag.getInt(tagName);
            y += offset;
            compoundTag.putInt(tagName, y);
            return y;
        }

        return newLocation;
    }

    private static Integer offsetCompoundTag(CompoundTag compoundTag, String compoundName, String tagName, int offset, Integer newLocation) {
        Integer res = null;

        if(compoundTag.containsKey(compoundName)) {
            CompoundTag comp = compoundTag.getCompoundTag(compoundName);
            res = offsetIntTag(comp, tagName, offset, newLocation);
            compoundTag.put(compoundName, comp);

            return res;
        }

        return newLocation;

    }
}