package com.norbertfila.generation;

public record Canopy(int x, int y, int z, int radius, int height, long seed, byte leaves) {
    public Canopy {
        if (radius < 1 || radius > 16 || height < 1 || height > 16)
            throw new IllegalArgumentException("Canopy dimensions outside [1, 16]");
        com.norbertfila.world.BlockRegistry.DEFAULT.definition(leaves);
    }

    public boolean contains(int wx, int wy, int wz) {
        return contains(x, y, z, radius, height, seed, wx, wy, wz);
    }

    static boolean contains(int x, int y, int z, int radius, int height, long seed, int wx, int wy, int wz) {
        double dx = (wx - (long) x) / (double) radius,
                dz = (wz - (long) z) / (double) radius,
                dy = (wy - (long) y) / (height + .4);
        double distance = dx * dx + dz * dz + dy * dy;
        return distance <= .72 || distance <= 1 && Math.floorMod(GenerationHash.hash(seed, wx, wz * 31L + wy), 7) != 0;
    }
}
