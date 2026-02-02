package com.davixdevelop.worldshifter.model;

public class ChunkPos implements Comparable<ChunkPos> {

    private final int x;
    private final int z;

    public ChunkPos(int x, int z) {
        this.x = x;
        this.z = z;
    }

    public int getX() {
        return x;
    }

    public int getZ() {
        return z;
    }

    @Override
    public String toString() {
        return x + "," + z;
    }

    @Override
    public int compareTo(ChunkPos o) {
        if(z != o.z)
            return Integer.compare(z, o.z);
        return Integer.compare(x, o.x);
    }

    @Override
    public boolean equals(Object obj) {
        if(this == obj) return true;
        if(obj instanceof ChunkPos chunkPos) {
            return x == chunkPos.x && z == chunkPos.z;
        }

        return false;
    }

    @Override
    public int hashCode() {
        return 31 * x * z;
    }
}
