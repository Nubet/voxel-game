package com.norbertfila.generation;

import static com.norbertfila.generation.GenerationHash.hash;
import static com.norbertfila.generation.OreDefinition.HeightDistribution.*;

import com.norbertfila.world.BlockId;
import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkPos;
import java.util.List;
import java.util.Objects;
import java.util.Random;

final class OreGenerator {
    static final List<OreDefinition> DEFAULT_ORES = List.of(
            new OreDefinition(BlockId.COAL_ORE, -64, 63, 20, 17, UNIFORM, 0xA24BAED4963EE407L),
            new OreDefinition(BlockId.IRON_ORE, -64, -1, 20, 9, UNIFORM, 0x9FB21C651E98DF25L),
            new OreDefinition(BlockId.GOLD_ORE, -64, -33, 2, 9, UNIFORM, 0xC13FA9A902A6328FL),
            new OreDefinition(BlockId.DIAMOND_ORE, -64, -49, 1, 8, UNIFORM, 0x91E10DA5C79E7B1DL),
            new OreDefinition(BlockId.LAPIS_ORE, -64, -34, 1, 7, TRIANGULAR, 0xD1B54A32D192ED03L));
    private final long seed;
    private final List<OreDefinition> ores;

    OreGenerator(long seed) {
        this(seed, DEFAULT_ORES);
    }

    OreGenerator(long seed, List<OreDefinition> ores) {
        this.seed = seed;
        this.ores = List.copyOf(ores);
    }

    boolean mayGenerate(int originY) {
        return ores.stream().anyMatch(ore -> (long) originY + Chunk.SIZE > ore.minY() && originY <= ore.maxY());
    }

    boolean generate(ChunkPos position, byte[] blocks) {
        Objects.requireNonNull(position);
        if (blocks.length != ChunkData.BLOCK_COUNT) throw new IllegalArgumentException("Invalid ore buffer");
        int ox = Math.multiplyExact(position.x(), Chunk.SIZE);
        int oy = Math.multiplyExact(position.y(), Chunk.SIZE);
        int oz = Math.multiplyExact(position.z(), Chunk.SIZE);
        boolean changed = false;
        for (var ore : ores) {
            if ((long) oy + Chunk.SIZE <= ore.minY() || oy > ore.maxY()) continue;
            // Size <= 32 bounds horizontal reach below 16 blocks, so only adjacent source columns can intersect.
            for (long cz = (long) position.z() - 1; cz <= (long) position.z() + 1; cz++)
                for (long cx = (long) position.x() - 1; cx <= (long) position.x() + 1; cx++) {
                    var random = new Random(hash(seed ^ ore.salt(), cx, cz));
                    for (int attempt = 0; attempt < ore.attempts(); attempt++) {
                        double x = cx * Chunk.SIZE + random.nextInt(Chunk.SIZE) + .5;
                        double y = ore.height(random) + .5;
                        double z = cz * Chunk.SIZE + random.nextInt(Chunk.SIZE) + .5;
                        double angle = random.nextDouble() * Math.PI;
                        double dx = Math.sin(angle) * ore.size() / 8;
                        double dz = Math.cos(angle) * ore.size() / 8;
                        double startY = y + random.nextInt(3) - 1;
                        double endY = y + random.nextInt(3) - 1;
                        for (int step = 0; step < ore.size(); step++) {
                            double t = (step + .5) / ore.size();
                            double radius = .75 + Math.sin(Math.PI * t) * random.nextDouble() * ore.size() / 16;
                            changed |= sphere(
                                    blocks,
                                    ore,
                                    ox,
                                    oy,
                                    oz,
                                    x + dx * (2 * t - 1),
                                    startY + (endY - startY) * t,
                                    z + dz * (2 * t - 1),
                                    radius);
                        }
                    }
                }
        }
        return changed;
    }

    private static boolean sphere(
            byte[] blocks, OreDefinition ore, int ox, int oy, int oz, double cx, double cy, double cz, double radius) {
        int minX = (int) Math.max(0, Math.ceil(cx - radius - ox - .5));
        int maxX = (int) Math.min(Chunk.SIZE - 1, Math.floor(cx + radius - ox - .5));
        int minY = (int) Math.max(Math.max(0, ore.minY() - (long) oy), Math.ceil(cy - radius - oy - .5));
        int maxY = (int) Math.min(Math.min(Chunk.SIZE - 1, ore.maxY() - (long) oy), Math.floor(cy + radius - oy - .5));
        int minZ = (int) Math.max(0, Math.ceil(cz - radius - oz - .5));
        int maxZ = (int) Math.min(Chunk.SIZE - 1, Math.floor(cz + radius - oz - .5));
        boolean changed = false;
        for (int y = minY; y <= maxY; y++)
            for (int z = minZ; z <= maxZ; z++)
                for (int x = minX; x <= maxX; x++) {
                    double dx = ox + (double) x + .5 - cx;
                    double dy = oy + (double) y + .5 - cy;
                    double dz = oz + (double) z + .5 - cz;
                    int index = (y * Chunk.SIZE + z) * Chunk.SIZE + x;
                    if (dx * dx + dy * dy + dz * dz < radius * radius && blocks[index] == BlockId.STONE) {
                        blocks[index] = ore.block();
                        changed = true;
                    }
                }
        return changed;
    }
}
