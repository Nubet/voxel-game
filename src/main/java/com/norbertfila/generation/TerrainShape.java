package com.norbertfila.generation;

import com.norbertfila.world.BlockId;
import com.norbertfila.world.WaterState;
import java.util.Arrays;

public final class TerrainShape {
    private final int minY;
    private final byte[] blocks;
    private final int waterfallTop;
    private final int waterfallBottom;

    TerrainShape(int minY, byte[] blocks, int waterfallBottom, int waterfallTop) {
        this.minY = minY;
        this.blocks = blocks.clone();
        this.waterfallTop = waterfallTop;
        this.waterfallBottom = waterfallBottom;
    }

    public int minY() {
        return minY;
    }

    public int maxY() {
        return minY + blocks.length - 1;
    }

    public byte materialAt(int y) {
        return y < minY ? BlockId.STONE : y > maxY() ? BlockId.AIR : blocks[y - minY];
    }

    byte waterState(int y) {
        if (materialAt(y) != BlockId.WATER) return WaterState.EMPTY;
        return y >= waterfallBottom && y < waterfallTop ? WaterState.FALLING : WaterState.SOURCE;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof TerrainShape shape
                && minY == shape.minY
                && waterfallTop == shape.waterfallTop
                && waterfallBottom == shape.waterfallBottom
                && Arrays.equals(blocks, shape.blocks);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * (31 * minY + waterfallBottom) + waterfallTop) + Arrays.hashCode(blocks);
    }
}
