package com.davixdevelop.worldshifter.utils;

import com.davixdevelop.worldshifter.model.WorldHeight;
import io.github.ensgijs.nbt.io.NamedTag;
import io.github.ensgijs.nbt.query.NbtPath;
import io.github.ensgijs.nbt.tag.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class EntityUtils {

    /**
     * Potential entity origin tags
     */
    public final static List<NbtPath> ORIGIN_TAGS = Arrays.asList(
            NbtPath.of("Pos"),
            NbtPath.of("y"),
            NbtPath.of( "posY" ),
            NbtPath.of( "TileY" ),
            NbtPath.of( "Paper.Origin" )
    );

    /**
     * Potential relative to the entity origin tags
     */
    public final static List<NbtPath> RELATIVE_TAGS = Arrays.asList(
            NbtPath.of("FlowerPos.Y"),
            NbtPath.of( "HivePos.Y" ),
            NbtPath.of( "beam_target.Y" ),
            NbtPath.of( "TreasurePosY" ),
            NbtPath.of( "AY" ),
            NbtPath.of( "AYP" ),
            NbtPath.of( "HomePosY" ),
            NbtPath.of( "TravelPosY" ),
            NbtPath.of( "BoundY" )
    );

    public final static NbtPath ENTITIES_BRAIN_MEMORIES_PATH = NbtPath.of("Brain.memories");
    public final static NbtPath ENTITIES_BRAIN_MEMORY_POS_PATH = NbtPath.of("value.pos");

    public static TreeMap<Integer, ListTag<CompoundTag>> offsetEntities(ListTag<CompoundTag> entities, int offsetY, int dataVersion, WorldHeight dataWorldHeight, AtomicInteger removedEntities, boolean isMultiWorld) {
        TreeMap<Integer, ListTag<CompoundTag>> entitiesPerWorld = new TreeMap<>();

        int i = 0;
        for(CompoundTag entity : entities) {
            boolean include = false;
            int worldIndex = 0;

            //First find the entity origins and get their world index
            for(NbtPath posPath : ORIGIN_TAGS) {
                Tag<?> originTag = getTag(entity, posPath);

                Double origin = (originTag instanceof IntTag intTag) ?
                        Double.valueOf(intTag.asDouble()) :
                        ((originTag instanceof DoubleTag doubleTag) ? doubleTag.asDouble() : null);
                if(origin != null) {
                    worldIndex = dataWorldHeight.getWorldIndexFromY(origin + offsetY);

                    //Only include entities if multi world is enabled, or world index of entity origin is 0
                    if(isMultiWorld || worldIndex == 0) {
                        entitiesPerWorld.computeIfAbsent(worldIndex, ind -> ListTag.createUnchecked(CompoundTag.class).asCompoundTagList());

                        include = true;
                        break;
                    }else
                        removedEntities.incrementAndGet();
                }

            }

            if(include) {
                offsetEntity(entity, dataWorldHeight, offsetY, worldIndex, isMultiWorld);
                ListTag<CompoundTag> ent = entitiesPerWorld.get(worldIndex);
                ent.add(entity);
            }

            i++;
        }

        return entitiesPerWorld;
    }

    /**
     * Get the tag in the compound tag
     * @param compoundTag The compound tag where the tag is
     * @param path The nbt path to the tag
     * @return Get the tag, else null
     */
    public static Tag<?> getTag(CompoundTag compoundTag, NbtPath path) {
        if(path.exists(compoundTag)) {
            Object tag = path.get(compoundTag);

            if(tag != null) {
                if (tag instanceof ListTag<?> listTag) {
                    return listTag.get(1);
                } else if(tag instanceof IntTag intTag) {
                    return intTag;
                } else if(tag instanceof DoubleTag doubleTag) {
                    return doubleTag;
                }else if(tag instanceof IntArrayTag arrayTag) {
                    return arrayTag;
                } else if(tag instanceof LongArrayTag longArrayTag) {
                    return longArrayTag;
                }else {
                    return null;
                }
            }
        }

        return null;
    }

    static void offsetEntity(CompoundTag compoundTag, WorldHeight worldHeight, int offsetY, int worldIndex, boolean isMultiWorld) {
        for(NbtPath posPath : ORIGIN_TAGS) {
            Tag<?> tag = getTag(compoundTag, posPath);
            if(tag != null)
                offsetTag(tag, worldHeight, offsetY, worldIndex, isMultiWorld);
        }

        for(NbtPath relativePath : RELATIVE_TAGS) {
            Tag<?> tag = getTag(compoundTag, relativePath);
            if(tag != null)
                offsetTag(tag, worldHeight, offsetY, worldIndex, isMultiWorld);
        }

        if(ENTITIES_BRAIN_MEMORIES_PATH.exists(compoundTag)) {
            CompoundTag memoriesTag = ENTITIES_BRAIN_MEMORIES_PATH.getTag(compoundTag);
            for(NamedTag namedTag : memoriesTag) {
                Tag<?> tag = namedTag.getTag();
                if(tag instanceof CompoundTag memory) {
                    if (ENTITIES_BRAIN_MEMORY_POS_PATH.exists(memory)) {
                        Tag<?> posPathTag = ENTITIES_BRAIN_MEMORY_POS_PATH.getTag(memory);
                        offsetTag(posPathTag, worldHeight, offsetY, worldIndex, isMultiWorld);
                    }
                }
            }
        }

        if(compoundTag.containsKey("Passengers")) {
            ListTag<CompoundTag> passengers = compoundTag.getCompoundList("Passengers");
            for(CompoundTag passenger : passengers) {
                offsetEntity(passenger, worldHeight, offsetY, worldIndex, isMultiWorld);
            }
        }

    }

    static void offsetTag(Tag<?> tag, WorldHeight worldHeight, int offsetY, int worldIndex, boolean isMultiWorld) {
        switch (tag) {
            case IntTag intTag:
                int y = offsetInt(intTag.asInt(), worldHeight, offsetY, worldIndex, isMultiWorld);
                //Set the new value to the tag
                intTag.setValue(y);
                break;
            case DoubleTag doubleTag:
                double y1 = offsetDouble(doubleTag.asDouble(), worldHeight, offsetY, worldIndex, isMultiWorld);
                //Set the new value to the tag
                doubleTag.setValue(y1);
                break;
            case IntArrayTag intArrayTag:
                int[] val = intArrayTag.getValue();
                if (val == null || val.length < 2)
                    return;
                val[1] = offsetInt(val[1], worldHeight, offsetY, worldIndex, isMultiWorld);
                intArrayTag.setValue(val);
                break;
            default:
                break;
        }
    }

    static int offsetInt(int y, WorldHeight worldHeight, int offsetY, int worldIndex, boolean isMultiWorld) {
        y += offsetY; //Add the offset to it
        if(isMultiWorld) {
            //Calc the world index of the offset tag value
            int tagWorldIndex = worldHeight.getWorldIndexFromY(y);
            //Make the tag value fit into the world height
            y = worldHeight.calcBlockOffset(tagWorldIndex, y);

            //If the tag world index is different from the entity origin world index
            // add world height multiplied by difference between tag world index
            // and entity origin world index
            if(worldIndex != tagWorldIndex)
                y += worldHeight.getWorldHeight() * (tagWorldIndex - worldIndex);
        }

        return y;
    }

    static double offsetDouble(double y, WorldHeight worldHeight, int offsetY, int worldIndex, boolean isMultiWorld) {
        y += offsetY; //Add the offset to it
        if(isMultiWorld) {
            //Calc the world index of the offset tag value
            int tagWorldIndex = worldHeight.getWorldIndexFromY(y);
            //Make the tag value fit into the world height
            y = worldHeight.calcBlockOffset(tagWorldIndex, y);

            //If the tag world index is different from the entity origin world index
            // add world height multiplied by difference between tag world index
            // and entity origin world index
            if(worldIndex != tagWorldIndex)
                y += worldHeight.getWorldHeight() * (tagWorldIndex - worldIndex);
        }

        return y;
    }
}
