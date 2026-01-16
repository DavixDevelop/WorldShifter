package com.davixdevelop.worldshifter;

import io.github.ensgijs.nbt.mca.*;
import io.github.ensgijs.nbt.mca.entities.Entity;
import io.github.ensgijs.nbt.mca.io.*;
import io.github.ensgijs.nbt.mca.util.IntPointXZ;
import io.github.ensgijs.nbt.mca.util.PalettizedCuboid;
import io.github.ensgijs.nbt.mca.util.VersionAware;
import io.github.ensgijs.nbt.query.NbtPath;
import io.github.ensgijs.nbt.tag.CompoundTag;
import io.github.ensgijs.nbt.tag.IntArrayTag;
import io.github.ensgijs.nbt.tag.ListTag;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class Main {

    static final AtomicReference<VersionAware<WorldHeight>> WORLDS_HEIGHTS = new AtomicReference<>(new VersionAware<WorldHeight>()
            .register(100, new WorldHeight(0, 256))
            .register(2825, new WorldHeight(-64, 320)));

    public static void main(String[] args) throws InterruptedException {
        if (args == null || args.length == 0 || args.length < 2) {
            System.out.println("No input world path/offset");
            return;
        }

        String inputWorld = args[0];
        int offsetY = Integer.parseInt(args[1]);

        if (offsetY % 16 != 0) {
            System.out.println("Offset must be in the values of 16");
            return;
        }

        int sectionOffsetY = offsetY / 16;

        Integer worldMin = null;
        Integer worldMax = null;

        int defaultThreadCount = 2;
        String threadCountParam = "--threadCount";

        if (args.length >= 4) {
            Integer threadCountIndex = null;
            if(args[2].equals(threadCountParam)) {
                threadCountIndex = 3;
            } else {
                if(args.length >= 6 && args[4].equals(threadCountParam)) {
                    threadCountIndex = 5;
                }

                try {
                    worldMin = Integer.parseInt(args[2]);
                    worldMax = Integer.parseInt(args[3]);
                    if (worldMin % 16 != 0 || worldMax % 16 != 0) {
                        System.out.println("Incorrect world min/max height. Must be in values of 16");
                        return;
                    }

                    if (worldMin == worldMax) {
                        System.out.println("Incorrect world min/max height. Max world height must be bigger then min world height");
                        return;
                    }
                }catch (NumberFormatException ex) {
                    System.out.println("Incorrect world min/max height format");
                    return;
                }

            }

            if(threadCountIndex != null) {
                try {
                    defaultThreadCount = Integer.parseInt(args[threadCountIndex]);
                }catch (NumberFormatException ex) {
                    System.out.println("Incorrect thread count format, ex. usage: --threadCount 2");
                    return;
                }
            }

        }

        int threadCount = Math.min(Runtime.getRuntime().availableProcessors(), defaultThreadCount);

        String regionFolderPath = Paths.get(inputWorld, "region").toString();
        File regionFolder = new File(regionFolderPath);
        if (!regionFolder.isDirectory()) {
            System.out.println("No region folder in input world");
            return;
        }

        File[] regionFiles = regionFolder.listFiles(path -> path.getName().endsWith("mca"));

        if (regionFiles == null || regionFiles.length == 0) {
            System.out.println("No region files detected");
            return;
        }

        File regionOutputFolder = Paths.get(inputWorld, "regionShifted").toFile();
        regionOutputFolder.mkdirs();
        System.out.println();

        //Register paths for version 1631
        //HeightMap
        TerrainChunkBase.LEGACY_HEIGHT_MAP_PATH.register(1631, NbtPath.of("Level.HeightMap"));
        //TerrainPopulated
        TerrainChunkBase.TERRAIN_POPULATED_PATH.register(1631, NbtPath.of("Level.TerrainPopulated"));
        //yPos
        TerrainChunkBase.Y_POS_PATH.register(1631, NbtPath.of("Level.yPos"));

        AtomicInteger counter = new AtomicInteger(0);
        AtomicInteger removedEntities = new AtomicInteger(0);
        int totalRegionsCount = regionFiles.length;

        String entitiesFolderPath = Paths.get(inputWorld, "entities").toString();
        File entitiesFolder = new File(entitiesFolderPath);
        File[] entitiesFiles = null;

        //First move the entities in the entities folder, if there are any
        if(entitiesFolder.isDirectory()) {
            entitiesFiles = entitiesFolder.listFiles(path -> path.getName().endsWith("mca"));
            if(entitiesFiles != null) {
                totalRegionsCount += entitiesFiles.length;

                File entitiesOutputFolder = Paths.get(inputWorld, "entitiesShifted").toFile();
                entitiesOutputFolder.mkdirs();

                final ConcurrentLinkedQueue<File> entitiesRegionQueue = new ConcurrentLinkedQueue<>(Arrays.stream(entitiesFiles).toList());

                ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
                ArrayList<MoveEntitiesRegion> moveTasks = new ArrayList<>();

                for(int t = 0; t < threadCount; t++) {
                    moveTasks.add(new MoveEntitiesRegion(worldMin, worldMax, offsetY, entitiesOutputFolder, totalRegionsCount, counter, entitiesRegionQueue));
                }

                List<Future<Integer>> completedTasks = executorService.invokeAll(moveTasks);
                executorService.shutdown();

                for(Future<Integer> future : completedTasks) {
                    if(future.state() == Future.State.SUCCESS)
                        removedEntities.addAndGet(future.resultNow());
                }

            }
        }

        //Then move the terrain regions
        final ConcurrentLinkedQueue<File> regionQueue = new ConcurrentLinkedQueue<>(Arrays.stream(regionFiles).toList());

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        ArrayList<MoveRegions> tasks = new ArrayList<>();

        for (int t = 0; t < threadCount; t++) {
            tasks.add(new MoveRegions(worldMin, worldMax, offsetY, sectionOffsetY, regionOutputFolder, totalRegionsCount, counter, removedEntities, regionQueue));
        }

        List<Future<Integer>> completed = executorService.invokeAll(tasks);
        executorService.shutdown();

        int removedChunks = 0;
        for(Future<Integer> future : completed) {
            if(future.state() == Future.State.SUCCESS)
                removedChunks += future.resultNow();
        }

        System.out.println();

        /*RegionMCAFile mcaFile = new RegionMCAFile(regionFile);
            RegionChunk chunk = mcaFile.getChunk(0);
            var c = chunk.getData();*/
        ///CompoundTag compoundTag = chunk.getData();

        /*Set<Integer> chunkIndexes = new TreeSet<>();
        try(McaFileChunkIterator<TerrainChunk> sourceChunkIterator = McaFileChunkIterator.iterate(regionFile, LoadFlags.RAW)) {
            sourceChunkIterator.forEachRemaining(chunk -> {
                chunkIndexes.add(chunk.getIndex());
            });
        }catch (Exception ex) {
            System.out.println("Error while reading chunk index: " + ex.getMessage());
            ex.printStackTrace();
        }*/

        if (removedChunks > 0)
            System.out.println("Removed " + removedChunks + " empty chunks");

        if (removedEntities.get() > 0)
            System.out.println("Removed " + removedEntities.get() + " out of bounds entities/tile entities");

        System.out.println("Done");
    }

}