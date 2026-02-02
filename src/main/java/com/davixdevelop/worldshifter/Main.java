package com.davixdevelop.worldshifter;

import com.davixdevelop.worldshifter.model.WorldHeight;
import com.davixdevelop.worldshifter.tasks.MoveEntitiesTask;
import com.davixdevelop.worldshifter.tasks.MoveRegionsTask;
import com.davixdevelop.worldshifter.utils.LogUtils;
import io.github.ensgijs.nbt.mca.*;
import io.github.ensgijs.nbt.mca.util.VersionAware;
import io.github.ensgijs.nbt.query.NbtPath;

import java.io.*;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.ensgijs.nbt.mca.DataVersion.JAVA_1_13_2;
import static io.github.ensgijs.nbt.mca.DataVersion.JAVA_1_18_21W43A;

public class Main {

    public static final AtomicReference<VersionAware<WorldHeight>> WORLDS_HEIGHTS = new AtomicReference<>(new VersionAware<WorldHeight>()
            .register(100, new WorldHeight(0, 256))
            .register(2825, new WorldHeight(-64, 320)));

    public static void main(String[] args) throws InterruptedException {
        if (args == null || args.length == 0) {
            LogUtils.log("No input arguments");
            printHelp();
            return;
        }

        ArrayList<String> argsList = new ArrayList<>(Arrays.stream(args).toList());

        if(argsList.contains("-help")) {
            printHelp();
            return;
        }



        int defaultThreadCount = 2;
        boolean isMultiWorld = false;

        String threadCountParam = "--threadCount";
        String multiWorldParam = "--multiWorld";


        String inputWorld = argsList.getFirst();
        argsList.removeFirst();
        File regionFolder;
        //Validate input world
        try {
            File worldFolder = Paths.get(inputWorld).toFile();
            if(worldFolder.isDirectory()){
                regionFolder = Paths.get(inputWorld, "region").toFile();
                if (!regionFolder.isDirectory()) {
                    throw new FileNotFoundException("No region folder in input world");
                }
            } else
                throw new IllegalArgumentException("Input world path invalid");

        }catch (Exception ex) {
            LogUtils.log("An exception happened while parsing input world path (" + inputWorld + "):");
            LogUtils.logHelp("\t" + ex.getMessage());
            return;
        }

        //Check if custom thread count is specified
        if(argsList.contains(threadCountParam)) {
            int threadCountIndex = argsList.indexOf(threadCountParam);
            String errorMessage = null;
            if(threadCountIndex + 1 < argsList.size()) {
                try {
                    defaultThreadCount = Integer.parseInt(argsList.get(threadCountIndex + 1));
                } catch (NumberFormatException ex) {
                   errorMessage = "Incorrect thread count format, could not parse: " + argsList.get(threadCountIndex + 1);
                }
            }else
                errorMessage = "No thread count specified";

            if(errorMessage != null) {
                LogUtils.log("Incorrect usage of [--threadCount <count>]: ");
                LogUtils.logHelp("\t" + errorMessage);
                return;
            }

            //Remove the param from the args list
            argsList.remove(threadCountIndex);
            argsList.remove(threadCountIndex);
        }

        //Check if multi world option should be enabled
        if(argsList.contains(multiWorldParam)) {
            argsList.remove(multiWorldParam);
            isMultiWorld = true;
        }

        if(argsList.isEmpty()) {
            LogUtils.logHelp("No offset specified");
            return;
        }

        //Get offset param and remove it from the param
        String rawOffsetY = argsList.getFirst();
        int offsetY = 0;
        argsList.removeFirst();
        try {
            offsetY = Integer.parseInt(rawOffsetY);
            if (offsetY % 16 != 0)
                throw new NumberFormatException("Offset must be in the values of 16");

        }catch (Exception ex) {
            LogUtils.log("Incorrect offset argument (" + rawOffsetY + "):");
            LogUtils.logHelp("\t" + ex.getMessage());
            return;
        }

        int sectionOffsetY = offsetY / 16;

        Integer targetWorldMin = null;
        Integer targetWorldMax = null;

        if (argsList.size() >= 2) {
            try {
                targetWorldMin = Integer.parseInt(argsList.getFirst());
                targetWorldMax = Integer.parseInt(argsList.get(1));
                if (targetWorldMin % 16 != 0 || targetWorldMax % 16 != 0) {
                    throw new NumberFormatException("Must be in values of 16");
                }

                if (targetWorldMin >= targetWorldMax) {
                    throw new IllegalArgumentException("Max target world height must be bigger then min world height");
                }
            }catch (Exception ex) {
                LogUtils.log("Incorrect target world min/max height argument: ");
                LogUtils.logHelp("\t" + ex.getMessage());
                return;
            }
        }

        if(targetWorldMin == null && offsetY == 0) {
            LogUtils.log("No custom target height and an zero offset");
            LogUtils.log("If you want to slice the world into multi worlds, make sure to specify the desired height of the worlds \nthe offset of zero or any other offset and use `--multiWorld`");
            return;
        }

        int threadCount = Math.min(Runtime.getRuntime().availableProcessors(), defaultThreadCount);

        File[] regionFiles = regionFolder.listFiles(path -> path.getName().endsWith("mca"));

        if (regionFiles == null || regionFiles.length == 0) {
            LogUtils.log("No region files detected");
            return;
        }

        File outputFolder = Paths.get(inputWorld, "world-shifted").toFile();
        outputFolder.mkdir();
        LogUtils.log();

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

                final ConcurrentLinkedQueue<File> entitiesRegionQueue = new ConcurrentLinkedQueue<>(Arrays.stream(entitiesFiles).toList());

                ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
                ArrayList<MoveEntitiesTask> moveTasks = new ArrayList<>();

                for(int t = 0; t < threadCount; t++) {
                    moveTasks.add(new MoveEntitiesTask(targetWorldMin, targetWorldMax, offsetY, isMultiWorld, outputFolder, totalRegionsCount, counter, entitiesRegionQueue));
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
        ArrayList<MoveRegionsTask> tasks = new ArrayList<>();

        for (int t = 0; t < threadCount; t++) {
            tasks.add(new MoveRegionsTask(targetWorldMin, targetWorldMax, offsetY, sectionOffsetY, isMultiWorld, outputFolder, totalRegionsCount, counter, removedEntities, regionQueue));
        }

        List<Future<Integer>> completed = executorService.invokeAll(tasks);
        executorService.shutdown();

        int skippedSections = 0;
        for(Future<Integer> future : completed) {
            if(future.state() == Future.State.SUCCESS)
                skippedSections += future.resultNow();
        }

        File tempFolder = Paths.get(outputFolder.getPath(), "temp").toFile();
        if(tempFolder.exists()) {
            deleteFolder(tempFolder);
        }

        LogUtils.log();

        if (skippedSections > 0)
            LogUtils.log("Skipped " + skippedSections + " terrain sections");

        if (removedEntities.get() > 0)
            LogUtils.log("Skipped " + removedEntities.get() + " out of bounds entities/tile entities");

        LogUtils.log("Done");
    }

    private static void printHelp() {
        List<String> lines = new ArrayList<>();
        lines.add("usage: WorldShifter-1.3.7 <worldPath> [minY] [maxY] [--multiWorld] [--threadCount <count>]");
        lines.add("Shift/slice a vanilla Minecraft Java (v1.13+) world on the Y axis");
        lines.add("\t<worldPath>\tPath to the world (Required)");
        lines.add("\t[minY] [maxY]\tMinimum (inclusive) anx maximum (exclusive) height  of the output world. Must be in values of 16 (Optional)");
        lines.add("\t[--multiWorld]\tCreate multiple output worlds from the out of bound chunk sections (Optional)");
        lines.add("\t[--threadCount <count>]\tThe amount of threads to use. Default 2 (Optional)");
        LogUtils.log();
        for(String l : lines) {
            LogUtils.log(l);
        }
        LogUtils.log();
    }

    private static void deleteFolder(File folder) {
        String[] files = folder.list();
        for(String filePath : files) {
            File file = Paths.get(folder.getPath(), filePath).toFile();
            if(file.isDirectory())
                deleteFolder(file);

            file.delete();
        }

        folder.delete();
    }

}