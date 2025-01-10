package net.lanzr.time_reward.inventory;

import net.minecraft.util.StringRepresentable;

import javax.annotation.Nullable;

public enum ContainerTypes implements StringRepresentable {
    REWARD_CHEST(27, 3, 9, 27),;
    private final String name;
    public final int size;
    public final int rowSize;
    public final int xSize;
    public final int ySize;


    ContainerTypes(int size, int rowLength, int xSize, int ySize) {
        this(null, size, rowLength, xSize, ySize);
    }

    ContainerTypes(@Nullable String name, int size, int rowSize, int xSize, int ySize) {
        this.name = name == null ? "unknown" : name;
        this.size = size;
        this.rowSize = rowSize;
        this.xSize = xSize;
        this.ySize = ySize;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }
}
