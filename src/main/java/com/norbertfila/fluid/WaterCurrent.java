package com.norbertfila.fluid;

import com.norbertfila.world.*;
import java.util.Objects;

public final class WaterCurrent {
    private WaterCurrent() {}

    public static Result sample(World world, BlockPos position) {
        Objects.requireNonNull(world);
        Objects.requireNonNull(position);
        boolean available = true;
        int own = read(world, position.x(), position.y(), position.z());
        if (own == WaterSurface.UNKNOWN) return new Result(0, 0, 0, false);
        if (own < WaterState.SOURCE) return new Result(0, 0, 0, true);
        int fluid = world.getBlock(position).orElseThrow();
        double x = 0, z = 0;
        for (int direction = 0; direction < 4; direction++) {
            int dx = direction == 0 ? -1 : direction == 1 ? 1 : 0;
            int dz = direction == 2 ? -1 : direction == 3 ? 1 : 0;
            int neighbor = read(world, (long) position.x() + dx, position.y(), (long) position.z() + dz);
            if (neighbor == WaterSurface.UNKNOWN) available = false;
            if (neighbor < WaterState.SOURCE) continue;
            long nx = (long) position.x() + dx, nz = (long) position.z() + dz;
            if (world.getBlock(new BlockPos((int) nx, position.y(), (int) nz)).orElse(BlockId.AIR) != fluid) continue;
            int delta = WaterState.distance((byte) neighbor) - WaterState.distance((byte) own);
            x += dx * delta;
            z += dz * delta;
        }
        // AIR is not a drain until it becomes water; routing remains exclusively the simulator's decision.
        double y = own == WaterState.FALLING ? -1 : 0;
        double length = Math.sqrt(x * x + y * y + z * z);
        if (length > 1) {
            x /= length;
            y /= length;
            z /= length;
        }
        return new Result(x, y, z, available);
    }

    private static int read(World world, long x, long y, long z) {
        if (x < Integer.MIN_VALUE
                || x > Integer.MAX_VALUE
                || y < Integer.MIN_VALUE
                || y > Integer.MAX_VALUE
                || z < Integer.MIN_VALUE
                || z > Integer.MAX_VALUE) return WaterSurface.SOLID;
        BlockPos position = new BlockPos((int) x, (int) y, (int) z);
        if (!world.hasChunk(Coordinates.worldToChunk(position))) return WaterSurface.UNKNOWN;
        return world.getWaterState(position).orElseThrow();
    }

    public record Result(double x, double y, double z, boolean available) {}
}
