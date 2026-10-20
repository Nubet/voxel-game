package com.norbertfila.mob.spawn;

import com.norbertfila.mob.MobCategory;
import com.norbertfila.mob.MobPopulation;
import com.norbertfila.mob.PlayerPosition;
import com.norbertfila.mob.PopulationKey;
import com.norbertfila.mob.SpawnZone;
import com.norbertfila.world.BlockPos;
import com.norbertfila.world.BlockRegistry;
import com.norbertfila.world.Chunk;
import com.norbertfila.world.WorldBounds;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * Minecraft-style natural spawning: random positions in the chunk columns around players, then packs of mobs
 * around each position that pass the category's {@link SpawnRules}.
 */
public final class NaturalSpawner {
    private record Column(int x, int z) {}

    private final SpawnSettings settings;
    private final SpawnTable table;
    private final Map<MobCategory, SpawnRules> rules;

    public NaturalSpawner(SpawnSettings settings, SpawnTable table, Map<MobCategory, SpawnRules> rules) {
        this.settings = Objects.requireNonNull(settings);
        this.table = Objects.requireNonNull(table);
        this.rules = Map.copyOf(rules);
    }

    public static NaturalSpawner overworld() {
        return new NaturalSpawner(
                SpawnSettings.DEFAULT, SpawnTable.OVERWORLD, Map.of(MobCategory.HOSTILE, SpawnRules.HOSTILE));
    }

    public SpawnSettings settings() {
        return settings;
    }

    public int spawn(
            SpawnEnvironment environment,
            MobPopulation population,
            List<PlayerPosition> players,
            RandomGenerator random) {
        if (players.isEmpty()) return 0;
        List<Column> columns = activeColumns(environment, players);
        var context = new SpawnContext(environment, population, players, settings, random, columns.size());
        int spawned = 0;
        for (var entry : rules.entrySet()) {
            if (!hasRoom(context, entry.getKey())) continue;
            shuffle(columns, random);
            int limit = Math.min(settings.columnsPerCycle(), columns.size());
            for (int i = 0; i < limit; i++)
                spawned += spawnInColumn(context, entry.getKey(), entry.getValue(), columns.get(i));
        }
        return spawned;
    }

    private List<Column> activeColumns(SpawnEnvironment environment, List<PlayerPosition> players) {
        Set<Column> seen = new LinkedHashSet<>();
        var columns = new ArrayList<Column>();
        int radius = settings.chunkRadius();
        for (var player : players) {
            int centerX = Math.floorDiv((int) Math.floor(player.x()), Chunk.SIZE);
            int centerZ = Math.floorDiv((int) Math.floor(player.z()), Chunk.SIZE);
            for (int dz = -radius; dz <= radius; dz++)
                for (int dx = -radius; dx <= radius; dx++) {
                    var column = new Column(centerX + dx, centerZ + dz);
                    if (seen.add(column) && environment.columnActive(column.x(), column.z())) columns.add(column);
                }
        }
        return columns;
    }

    public int activeColumnCount(SpawnEnvironment environment, List<PlayerPosition> players) {
        return activeColumns(environment, players).size();
    }

    private static boolean hasRoom(SpawnContext context, MobCategory category) {
        for (var zone : SpawnZone.values()) if (context.hasRoom(new PopulationKey(category, zone))) return true;
        return false;
    }

    private int spawnInColumn(SpawnContext context, MobCategory category, SpawnRules categoryRules, Column column) {
        var random = context.random();
        int x = column.x() * Chunk.SIZE + random.nextInt(Chunk.SIZE);
        int z = column.z() * Chunk.SIZE + random.nextInt(Chunk.SIZE);
        var top = context.environment().surfaceTop(x, z);
        if (top.isEmpty()) return 0;
        int maxY = Math.min(top.getAsInt() + 1, WorldBounds.MAX_Y_EXCLUSIVE - 1);
        int y = WorldBounds.MIN_Y + random.nextInt(maxY - WorldBounds.MIN_Y + 1);
        var start = context.environment().block(new BlockPos(x, y, z));
        if (start.isEmpty() || BlockRegistry.DEFAULT.occludesFullFace((byte) start.getAsInt())) return 0;
        int spawned = 0;
        for (int group = 0; group < settings.groupsPerColumn(); group++) {
            var entry = table.pick(category, random);
            if (entry.isEmpty()) return spawned;
            int size = entry.get().minGroup()
                    + random.nextInt(entry.get().maxGroup() - entry.get().minGroup() + 1);
            int px = x, pz = z, inGroup = 0;
            for (int step = 0; step < settings.attemptsPerGroup() && inGroup < size; step++) {
                px += random.nextInt(settings.groupSpread() + 1) - random.nextInt(settings.groupSpread() + 1);
                pz += random.nextInt(settings.groupSpread() + 1) - random.nextInt(settings.groupSpread() + 1);
                var attempt = new SpawnAttempt(context, entry.get(), new BlockPos(px, y, pz));
                if (!categoryRules.allows(attempt)) continue;
                float yaw = random.nextFloat() * 360;
                var zone = attempt.zone().orElseThrow();
                if (context.population()
                        .spawn(entry.get().type(), zone, attempt.x(), attempt.y(), attempt.z(), yaw)
                        .isEmpty()) return spawned;
                inGroup++;
                spawned++;
            }
        }
        return spawned;
    }

    private static void shuffle(List<Column> columns, RandomGenerator random) {
        for (int i = columns.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            columns.set(i, columns.set(j, columns.get(i)));
        }
    }
}
