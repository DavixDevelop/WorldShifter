package com.davixdevelop.worldshifter.model;

import com.davixdevelop.worldshifter.utils.LogUtils;
import com.davixdevelop.worldshifter.utils.Utils;
import io.github.ensgijs.nbt.mca.ChunkBase;
import io.github.ensgijs.nbt.mca.io.RandomAccessMcaFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public abstract class MultiRegion<C extends ChunkBase> {
    public final File sourceRegionFile;
    public final File outputFolder;
    public final String subFolder;
    protected final boolean isMultiWorld;
    protected final Integer targetWorldMin;
    protected final Integer targetWorldMax;
    protected final int offsetY;

    private final Map<Integer, Region<C>> regions;
    private File tempClearRegionFile;

    public final Set<Integer> emptyChunks = new TreeSet<>();

    /**
     * Create multi region files from a single region file
     * @param sourceRegionFile The source region file
     * @param outputFolder The path to the output folder
     * @param subFolder The type of the region, ex "region"
     * @param isMultiWorld If multi world should be enabled
     * @param targetWorldMin The min Y (inclusive) of the target world
     * @param targetWorldMax The max Y (exclusive) of the target world
     * @param offsetY The offset of the target world
     */
    public MultiRegion(File sourceRegionFile, File outputFolder, String subFolder, boolean isMultiWorld, Integer targetWorldMin, Integer targetWorldMax, int offsetY) {
        this.sourceRegionFile = sourceRegionFile;
        this.outputFolder = outputFolder;
        this.subFolder = subFolder;
        regions = new TreeMap<>();
        this.isMultiWorld = isMultiWorld;
        this.targetWorldMin = targetWorldMin;
        this.targetWorldMax = targetWorldMax;
        this.offsetY = offsetY;
    }

    /**
     * Get the *cleared chunk of the world index
     * @param pos The position of the chunk
     * @param worldIndex The index of the world slice
     * @return The *cleared chunk to write to, else null
     */
    public C getChunk(ChunkPos pos, int worldIndex) {
        if(!regions.containsKey(worldIndex)) {
            File newRegionFile = getRegionFileForIndex(worldIndex);
            newRegionFile.getParentFile().mkdirs();

            // If temp clear region file is not yet created, create it in the temp folder in the output folder
            if(tempClearRegionFile == null && isMultiWorld) {
                tempClearRegionFile = Paths.get(outputFolder.getPath(),"temp", subFolder, sourceRegionFile.getName()).toFile();
                tempClearRegionFile.getParentFile().mkdirs();

                try {
                    Utils.copyFile(sourceRegionFile, tempClearRegionFile);
                    try(RandomAccessMcaFile<C> tempClearRegion = readMca(tempClearRegionFile)) {
                        clearMca(tempClearRegion);
                        tempClearRegion.flush();
                    }
                } catch (Exception ex) {
                    LogUtils.logError("Creating the temp " + subFolder + " file " + sourceRegionFile.getName() + " failed", ex);
                    return null;
                }
            }

            try {
                Utils.copyFile(isMultiWorld ? tempClearRegionFile : sourceRegionFile, newRegionFile);

                if(!isMultiWorld) {
                    try(RandomAccessMcaFile<C> clearRegion = readMca(newRegionFile)) {
                        clearMca(clearRegion);
                        clearRegion.flush();
                    }
                }

            }catch (Exception ex) {
                LogUtils.logError("Copying the source " + subFolder + " file " + sourceRegionFile.getName() + " failed", ex);
                return null;
            }

            try {
                Region<C> newRegion = new Region<>(readMca(newRegionFile));
                regions.put(worldIndex, newRegion);
            }catch (Exception ex) {
                LogUtils.logError("Error while reading mca file `" + newRegionFile.getPath() + "`", ex);
                return null;
            }
        }

        return regions.get(worldIndex).getChunk(pos);
    }

    public File getRegionFileForIndex(int worldIndex) {
        return isMultiWorld ?
                Paths.get(outputFolder.getPath(), "world" + worldIndex, subFolder, sourceRegionFile.getName()).toFile() :
                Paths.get(outputFolder.getPath(), subFolder, sourceRegionFile.getName()).toFile();
    }

    /**
     * Shift and slice region
     * @throws Exception If an exception happened while shifting the region
     */
    public abstract void shiftRegion() throws Exception;

    /**
     * Read the mca file and load it
     * @param regionFile The path to the region file
     * @return The random access mca file of the region file
     * @throws IOException If there was an error reading the region file
     */
    public abstract RandomAccessMcaFile<C> readMca(File regionFile) throws IOException;

    /**
     * Read a mca file, clear the chunk content and return it
     *
     * @param regionMCA The path to the region file
     * @return The random access mca file of the region file
     * @throws IOException If there was an error while reading the MCA file
     */
    public abstract void clearMca(RandomAccessMcaFile<C> regionMCA) throws IOException;

    /**
     * Write the regions chunks and flush their MCA files
     */
    public void flushRegions() {
        for(int worldIndex : regions.keySet()) {
            Region<C> region = regions.get(worldIndex);

            try(RandomAccessMcaFile<C> mcaFile = region.getMcaFile()) {
                Map<ChunkPos, C> chunks = region.getChunks();
                for(ChunkPos pos : chunks.keySet()) {
                    mcaFile.write(chunks.get(pos));
                }

                /*try{
                    mcaFile.optimizeFile();
                    //if(bytesSaved != 0)
                    //    LogUtils.log("Optimized sub-region file, saved: " + (bytesSaved/1000) + "KB \nfor file: world-shifted/" + "world" + worldIndex + "/" + subFolder + "/" + sourceRegionFile.getName());
                }catch (Exception ignored) {}*/
                mcaFile.flush();
            }catch (Exception ex ) {
                LogUtils.logError("Error while writing file", getRegionFileForIndex(worldIndex), ex);
            }
        }
    }

    public void deleteTempRegionFile() {
        if(tempClearRegionFile != null) {
            tempClearRegionFile.delete();
        }
    }
}
