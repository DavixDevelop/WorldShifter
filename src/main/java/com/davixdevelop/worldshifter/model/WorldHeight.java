package com.davixdevelop.worldshifter.model;

public class WorldHeight {
    private int minHeight;
    private int maxHeight;
    private int worldHeight;
    private int firstSection;
    private int lastSection;
    private int sectionHeight;

    public WorldHeight(int min, int max) {
        minHeight = min;
        maxHeight = max;
        worldHeight = maxHeight - minHeight;
        firstSection = minHeight / 16;
        lastSection = (maxHeight / 16) - 1; //Make it inclusive
        sectionHeight = (lastSection + 1) - firstSection;
    }

    /**
     * @return The min block height of the world (inclusive)
     */
    public int getMinHeight() {
        return minHeight;
    }

    /**
     * @return The max block height of the world (exclusive)
     */
    public int getMaxHeight() {
        return maxHeight;
    }

    /**
     * @return The world block height
     */
    public int getWorldHeight() {
        return worldHeight;
    }

    /**
     * @return Return the first index of this world height (inclusive)
     */
    public int getFirstSection() {
        return firstSection;
    }

    /**
     * @return The last index of this world height (inclusive)
     */
    public int getLastSection() {
        return lastSection;
    }

    /**
     * @return The max possible amount of sections in this world height
     */
    public int getSectionHeight() {
        return sectionHeight;
    }

    public boolean isWithin(int y) {
        return y >= minHeight && y < maxHeight;
    }

    public boolean isWithin(double y) {
        return y >= minHeight && y < maxHeight;
    }

    public int getWorldIndexFromSection(int sectionY) {
        return Math.floorDiv(sectionY - firstSection, sectionHeight);
    }

    public int getWorldIndexFromY(int y) {
        return getWorldIndexFromSection(y >> 4);
    }

    public int getWorldIndexFromY(double y) {
        return getWorldIndexFromSection((int)Math.floor(y) >> 4);
    }

    public int calcSectionOffset(int worldIndex, int sectionY) {
        return  sectionY - (worldIndex * sectionHeight);
    }

    public int calcBlockOffset(int worldIndex, int y) {
        return y - (worldIndex * worldHeight);
    }

    public double calcBlockOffset(int worldIndex, double y) {
        return y - (worldIndex * worldHeight);
    }
}
