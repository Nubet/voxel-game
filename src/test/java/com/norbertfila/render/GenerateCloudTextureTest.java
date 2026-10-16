package com.norbertfila.render;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.assets.CloudPattern;
import java.util.ArrayDeque;
import java.util.HashSet;
import org.junit.jupiter.api.Test;

class GenerateCloudTextureTest {
    private static final int[][] NEIGHBORS = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

    @Test
    void shippedMaskMatchesDeterministicGenerator() {
        var generated = GenerateCloudTexture.generate();
        var repeated = GenerateCloudTexture.generate();
        CloudPattern shipped = CloudPattern.loadDefault();
        assertEquals(generated.getWidth(), shipped.size());
        for (int z = 0; z < shipped.size(); z++)
            for (int x = 0; x < shipped.size(); x++) {
                assertEquals(generated.getRGB(x, z), repeated.getRGB(x, z));
                assertEquals(generated.getRGB(x, z) != 0, shipped.filled(x, z));
            }
    }

    @Test
    void formationsHaveVariedSizesAndConnectedBranchesWithoutMergingAcrossTheTile() {
        CloudPattern pattern = CloudPattern.loadDefault();
        int size = pattern.size();
        int[] labels = new int[size * size];
        int formations = 0, coverage = 0;
        int smallest = Integer.MAX_VALUE, largest = 0;
        var areas = new HashSet<Integer>();
        var queue = new ArrayDeque<Integer>();
        for (int z = 0; z < size; z++)
            for (int x = 0; x < size; x++) {
                if (!pattern.filled(x, z) || labels[z * size + x] != 0) continue;
                int label = ++formations;
                int area = 0, minX = 0, maxX = 0, minZ = 0, maxZ = 0;
                labels[z * size + x] = label;
                queue.add(z * size + x);
                while (!queue.isEmpty()) {
                    int pixel = queue.removeFirst();
                    int px = pixel % size, pz = pixel / size;
                    int dx = Math.floorMod(px - x + size / 2, size) - size / 2;
                    int dz = Math.floorMod(pz - z + size / 2, size) - size / 2;
                    minX = Math.min(minX, dx);
                    maxX = Math.max(maxX, dx);
                    minZ = Math.min(minZ, dz);
                    maxZ = Math.max(maxZ, dz);
                    area++;
                    int neighbors = 0;
                    for (int[] offset : NEIGHBORS) {
                        int nx = Math.floorMod(px + offset[0], size), nz = Math.floorMod(pz + offset[1], size);
                        if (!pattern.filled(nx, nz)) continue;
                        neighbors++;
                        if (labels[nz * size + nx] == 0) {
                            labels[nz * size + nx] = label;
                            queue.add(nz * size + nx);
                        }
                    }
                    assertTrue(neighbors >= 2, "Isolated pixel or narrow spur");
                }
                int width = maxX - minX + 1, depth = maxZ - minZ + 1;
                assertTrue(area >= 24 && area <= 220, "Formation grew outside the intended size range: " + area);
                assertTrue(width >= 4 && width <= 24 && depth >= 4 && depth <= 24);
                assertTrue(Math.max(width, depth) <= 3.5 * Math.min(width, depth), "Formation is too elongated");
                areas.add(area);
                smallest = Math.min(smallest, area);
                largest = Math.max(largest, area);
                coverage += area;
            }
        assertTrue(formations >= 12 && formations <= 16, "Formations merged or disappeared");
        assertTrue(largest >= smallest * 2 && areas.size() >= 6, "Formation sizes are too uniform");
        double density = coverage / (double) (size * size);
        assertTrue(density >= .18 && density <= .32, "Expected 68–82% empty sky, got coverage " + density);
        int concaveCorners = 0;
        for (int z = 0; z < size; z++)
            for (int x = 0; x < size; x++) {
                int label = labels[z * size + x];
                if (label == 0) continue;
                if (pattern.filled(x + 1, z) && pattern.filled(x, z + 1) && !pattern.filled(x + 1, z + 1))
                    concaveCorners++;
                for (int dz = -2; dz <= 2; dz++)
                    for (int dx = -2; dx <= 2; dx++) {
                        int other = labels[Math.floorMod(z + dz, size) * size + Math.floorMod(x + dx, size)];
                        assertTrue(other == 0 || other == label, "Formations touch across the repeating tile");
                    }
            }
        assertTrue(concaveCorners >= formations / 2, "Contours lost their connected lobes and branches");
    }
}
