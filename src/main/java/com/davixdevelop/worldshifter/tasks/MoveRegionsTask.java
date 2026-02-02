package com.davixdevelop.worldshifter.tasks;

import com.davixdevelop.worldshifter.model.MultiTerrainRegion;
import com.davixdevelop.worldshifter.utils.LogUtils;

import java.io.File;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class MoveRegionsTask implements Callable<Integer> {

    private final Integer targetWorldMin;
    private final Integer targetWorldMax;
    private final int offsetY;
    private final int sectionOffsetY;
    private final boolean isMultiWorld;
    private final File outputFolder;
    private final int regionFilesCount;
    private final AtomicInteger counter;
    private final AtomicInteger removedEntities;

    private final ConcurrentLinkedQueue<File> regionQueue;

    public MoveRegionsTask(Integer targetWorldMin, Integer targetWorldMax, int offsetY, int sectionOffsetY, boolean isMultiWorld, File outputFolder, int regionFilesCount, AtomicInteger counter, AtomicInteger removedEntities, ConcurrentLinkedQueue<File> regionQueue) {
        this.targetWorldMin = targetWorldMin;
        this.targetWorldMax = targetWorldMax;
        this.offsetY = offsetY;
        this.sectionOffsetY = sectionOffsetY;
        this.isMultiWorld = isMultiWorld;
        this.outputFolder = outputFolder;
        this.regionFilesCount = regionFilesCount;
        this.counter = counter;
        this.removedEntities = removedEntities;
        this.regionQueue = regionQueue;
    }

    @Override
    public Integer call() {
        AtomicInteger ignoredSections = new AtomicInteger(0);
        while (!regionQueue.isEmpty()) {
            File regionFile = regionQueue.poll();

            if (regionFile == null)
                break;

            int c = counter.incrementAndGet();
            System.out.println("\rProcessing: " + regionFile.getName() + "(" + ((c * 100) / regionFilesCount) + "%)");

            MultiTerrainRegion terrainRegion = new MultiTerrainRegion(regionFile, targetWorldMin, targetWorldMax, offsetY, sectionOffsetY, outputFolder, isMultiWorld, ignoredSections, removedEntities);
            try {
                terrainRegion.shiftRegion();
            }catch (Exception ex) {
                LogUtils.logError("An exception happened while shifting region: " + regionFile.getName(), ex);
            }finally {
                terrainRegion.deleteTempRegionFile();
            }
        }

        return ignoredSections.get();
    }
}
