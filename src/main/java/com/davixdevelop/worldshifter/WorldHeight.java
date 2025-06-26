package com.davixdevelop.worldshifter;

public class WorldHeight {
    private int minHeight;
    private int maxHeight;
    private int minSection;
    private int maxSection;

    public WorldHeight(int min, int max) {
        minHeight = min;
        maxHeight = max;
        minSection = minHeight / 16;
        maxSection = maxHeight / 16;
    }

    public int getMinHeight() {
        return minHeight;
    }

    public int getMaxHeight() {
        return maxHeight;
    }

    public int getMinSection() {
        return minSection;
    }

    public int getMaxSection() {
        return maxSection;
    }
}
