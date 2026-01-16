package com.davixdevelop.worldshifter;

import io.github.ensgijs.nbt.io.NamedTag;
import io.github.ensgijs.nbt.mca.EntitiesChunkBase;
import io.github.ensgijs.nbt.mca.util.VersionAware;
import io.github.ensgijs.nbt.query.NbtPath;
import io.github.ensgijs.nbt.tag.CompoundTag;
import io.github.ensgijs.nbt.tag.ListTag;

import java.io.*;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;

public class Utils {
    private static final VersionAware<NbtPath> ENTITIES_BRAIN_MEMORIES_PATH = new VersionAware<NbtPath>()
            .register(0, NbtPath.of("Brain.memories"));

    static boolean copyFile(File source, File target) {
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

        } catch (Exception ex) {
            return false;
        }

        return true;
    }

    static Boolean offsetTags(CompoundTag compoundTag, int offsetY, int dataVersion, WorldHeight worldHeight, AtomicInteger removedEntities) {
        Boolean posCheck = null;

        if (compoundTag.containsKey("Pos")) {
            double[] pos = compoundTag.getDoubleTagListAsArray("Pos");
            pos[1] += offsetY;

            if (!worldHeight.isWithin(pos[1])) {
                removedEntities.incrementAndGet();
                String id = compoundTag.getString("id");
                return null;
            }

            compoundTag.putDoubleArrayAsTagList("Pos", pos);
            posCheck = true;
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



        if(compoundTag.containsKey("Paper.Origin")) {
            double[] pos = compoundTag.getDoubleTagListAsArray("Paper.Origin");

            if(pos != null) {
                double newY = pos[1] + offsetY;
                if (worldHeight.isWithin(newY)) {
                    pos[1] = newY;
                    compoundTag.putDoubleArrayAsTagList("Paper.Origin", pos);
                } else {
                    removedEntities.incrementAndGet();
                    return null;
                }
            }
        }

        final NbtPath brainMemoriesPath = ENTITIES_BRAIN_MEMORIES_PATH.get(dataVersion);
        final NbtPath memoryPosPath = NbtPath.of("value.pos");
        if(brainMemoriesPath.exists(compoundTag)) {
            CompoundTag memoriesTag = brainMemoriesPath.getTag(compoundTag);
            for (NamedTag memory : memoriesTag) {
                int[] pos = memoryPosPath.getIntArray(memory.getTag());
                if(pos == null)
                    continue;

                int newY = pos[1] + offsetY;
                if (worldHeight.isWithin(newY)) {
                    pos[1] = newY;
                } else
                    memoriesTag.remove(memory.getName());
            }

        }

        if(compoundTag.containsKey("Passengers")) {
            ListTag<CompoundTag> passengers = compoundTag.getListTag("Passengers").asCompoundTagList();
            Iterator<CompoundTag> passangerIterator = passengers.iterator();
            while (passangerIterator.hasNext()) {
                CompoundTag passenger = passangerIterator.next();
                Boolean res = offsetTags(passenger, offsetY, dataVersion, worldHeight, removedEntities);

                if(res == null)
                    passangerIterator.remove();
            }

            compoundTag.put("Passengers", passengers);
        }


        if (posCheck != null && location == null)
            return true;

        if (location != null) {
            if (location < worldHeight.getMinHeight() || location > worldHeight.getMaxHeight() - 1) {
                removedEntities.incrementAndGet();
                return null;
            }

            return true;
        }

        return false;

    }

    static Integer offsetIntTag(CompoundTag compoundTag, String tagName, int offset, Integer newLocation) {
        if (compoundTag.containsKey(tagName)) {
            int y = compoundTag.getInt(tagName);
            y += offset;
            compoundTag.putInt(tagName, y);
            return y;
        }

        return newLocation;
    }

    static Integer offsetCompoundTag(CompoundTag compoundTag, String compoundName, String tagName, int offset, Integer newLocation) {
        Integer res = null;

        if (compoundTag.containsKey(compoundName)) {
            CompoundTag comp = compoundTag.getCompoundTag(compoundName);
            res = offsetIntTag(comp, tagName, offset, newLocation);
            compoundTag.put(compoundName, comp);

            return res;
        }

        return newLocation;

    }
}
