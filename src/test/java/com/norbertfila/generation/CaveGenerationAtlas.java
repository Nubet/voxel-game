package com.norbertfila.generation;

import com.norbertfila.world.*;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import javax.imageio.ImageIO;

final class CaveGenerationAtlas {
    static void write(TerrainGenerator terrain, CavePlan plan, Path file) throws Exception {
        var chunks = new HashMap<ChunkPos, ChunkData>();
        var air = new HashSet<BlockPos>();
        var carver = new CaveCarver(terrain);
        for (var segment : plan.allSegments())
            for (int y = (int) Math.floor(segment.y() - segment.boundRadiusY());
                    y <= segment.y() + segment.boundRadiusY();
                    y++)
                for (int z = (int) Math.floor(segment.z() - segment.boundRadiusXZ());
                        z <= segment.z() + segment.boundRadiusXZ();
                        z++)
                    for (int x = (int) Math.floor(segment.x() - segment.boundRadiusXZ());
                            x <= segment.x() + segment.boundRadiusXZ();
                            x++) {
                        if (!carver.contains(segment, x + .5, y + .5, z + .5) || y >= terrain.surfaceHeight(x, z))
                            continue;
                        var p = new BlockPos(x, y, z);
                        var local = Coordinates.worldToLocal(p);
                        byte block = chunks.computeIfAbsent(Coordinates.worldToChunk(p), terrain::generate)
                                .get(local.x(), local.y(), local.z());
                        if (block == BlockId.AIR || BlockId.isFluid(block)) air.add(p);
                    }
        if (air.isEmpty()) throw new AssertionError("Empty cave atlas");
        int minX = air.stream().mapToInt(BlockPos::x).min().orElseThrow();
        int minY = air.stream().mapToInt(BlockPos::y).min().orElseThrow();
        int minZ = air.stream().mapToInt(BlockPos::z).min().orElseThrow();
        int maxX = air.stream().mapToInt(BlockPos::x).max().orElseThrow();
        int maxY = air.stream().mapToInt(BlockPos::y).max().orElseThrow();
        int maxZ = air.stream().mapToInt(BlockPos::z).max().orElseThrow();
        var image = new BufferedImage(1200, 650, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(20, 24, 31));
            graphics.fillRect(0, 0, 1200, 650);
            double scale = Math.min(540.0 / (maxX - minX + 1), 530.0 / (maxZ - minZ + 1));
            double sideScale = Math.min(540.0 / (maxX - minX + 1), 500.0 / (maxY - minY + 1));
            for (var p : air.stream()
                    .sorted(java.util.Comparator.comparingInt(BlockPos::y))
                    .toList()) {
                var local = Coordinates.worldToLocal(p);
                byte block = chunks.get(Coordinates.worldToChunk(p)).get(local.x(), local.y(), local.z());
                graphics.setColor(
                        block == BlockId.WATER
                                ? new Color(35, 130, 255)
                                : block == BlockId.LAVA
                                        ? Color.ORANGE
                                        : Color.getHSBColor(
                                                .08f + .65f * (p.y() - minY) / Math.max(1, maxY - minY), .65f, .95f));
                graphics.fillRect(
                        25 + (int) ((p.x() - minX) * scale),
                        60 + (int) ((p.z() - minZ) * scale),
                        (int) Math.ceil(scale),
                        (int) Math.ceil(scale));
                graphics.fillRect(
                        625 + (int) ((p.x() - minX) * sideScale),
                        580 - (int) ((p.y() - minY) * sideScale),
                        (int) Math.ceil(sideScale),
                        (int) Math.ceil(sideScale));
            }
            graphics.setColor(Color.WHITE);
            graphics.drawString("TOP / XZ — color encodes height; water blue, lava orange", 25, 30);
            graphics.drawString("SIDE / XY — generated voxels, stone hidden", 625, 30);
            graphics.drawString(
                    "seed=" + terrain.seed() + "  x=" + minX + ".." + maxX + " y=" + minY + ".." + maxY + " z=" + minZ
                            + ".." + maxZ,
                    25,
                    625);
        } finally {
            graphics.dispose();
        }
        ImageIO.write(image, "png", file.toFile());
    }
}
