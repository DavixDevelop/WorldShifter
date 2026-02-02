package com.davixdevelop.worldshifter.tasks;

import com.davixdevelop.worldshifter.model.MultiEntitiesRegion;
import com.davixdevelop.worldshifter.utils.LogUtils;

import java.io.File;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class MoveEntitiesTask implements Callable<Integer> {
    private final Integer targetWorldMin;
    private final Integer targetWorldMax;
    private final int offsetY;
    private final boolean isMultiWorld;
    private final File outputFolder;
    private final int totalRegionsCount;
    private final AtomicInteger counter;

    private final ConcurrentLinkedQueue<File> entitiesRegionQueue;

    public MoveEntitiesTask(Integer targetWorldMin, Integer targetWorldMax, int offsetY, boolean isMultiWorld, File outputFolder, int totalRegionsCount, AtomicInteger counter, ConcurrentLinkedQueue<File> entitiesRegionQueue) {
        this.targetWorldMin = targetWorldMin;
        this.targetWorldMax = targetWorldMax;
        this.offsetY = offsetY;
        this.isMultiWorld = isMultiWorld;
        this.outputFolder = outputFolder;
        this.totalRegionsCount = totalRegionsCount;
        this.counter = counter;
        this.entitiesRegionQueue = entitiesRegionQueue;
    }


    @Override
    public Integer call() throws Exception {
        AtomicInteger removedEntities = new AtomicInteger(0);
        while (!entitiesRegionQueue.isEmpty()) {
            File entitiesFile = entitiesRegionQueue.poll();

            if(entitiesFile == null)
                break;

            int c = counter.incrementAndGet();
            System.out.println("\rProcessing entities file: " + entitiesFile.getName() + "(" + ((c * 100) / totalRegionsCount) + "%)");

            MultiEntitiesRegion entitiesRegion = new MultiEntitiesRegion(entitiesFile, targetWorldMin, targetWorldMax, offsetY, outputFolder, isMultiWorld, removedEntities);
            try{
                entitiesRegion.shiftRegion();
            }catch (Exception ex) {
                LogUtils.logError("An exception happened while shifting entities region: " + entitiesFile.getName(), ex);
            }finally {
                entitiesRegion.deleteTempRegionFile();
            }
        }

        return removedEntities.get();
    }
}
