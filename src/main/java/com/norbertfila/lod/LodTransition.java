package com.norbertfila.lod;

import com.norbertfila.meshing.MeshData;
import com.norbertfila.world.BlockId;
import java.util.List;
import java.util.Objects;

public final class LodTransition {
    private LodTransition() {}

    public static LodLevel selectLevel(
            List<LodLevel> levels, LodLevel current, long distanceSquared, int hysteresisChunks) {
        Objects.requireNonNull(levels);
        if (levels.isEmpty() || hysteresisChunks < 0)
            throw new IllegalArgumentException("Invalid LOD transition configuration");
        LodLevel target = null;
        int targetIndex = -1;
        for (int index = 0; index < levels.size(); index++) {
            if (levels.get(index).contains(distanceSquared)) {
                target = levels.get(index);
                targetIndex = index;
                break;
            }
        }
        if (current == null) return target;
        int currentIndex = levels.indexOf(current);
        if (currentIndex < 0) throw new IllegalArgumentException("Current LOD level is not in levels");
        if (targetIndex < 0 || targetIndex == currentIndex) return targetIndex < 0 ? current : current;
        long hysteresis = hysteresisChunks;
        if (targetIndex > currentIndex) {
            long switchDistance = current.maxRadiusChunks() + hysteresis;
            return distanceSquared <= switchDistance * switchDistance ? current : target;
        }
        long switchDistance = Math.max(0, current.minRadiusChunks() - hysteresis);
        return distanceSquared >= switchDistance * switchDistance ? current : target;
    }

    public static MeshData buildSeam(FarTerrainTile fine, FarTerrainTile coarse, Edge edge) {
        Objects.requireNonNull(fine);
        Objects.requireNonNull(coarse);
        Objects.requireNonNull(edge);
        if (fine.tileSize() != coarse.tileSize()
                || fine.sampleStep() > coarse.sampleStep()
                || coarse.sampleStep() % fine.sampleStep() != 0
                || !adjacent(fine, coarse, edge)) {
            throw new IllegalArgumentException("Tiles are not compatible for a seam");
        }
        int segments = fine.tileSize() / fine.sampleStep();
        Floats out = new Floats(segments * 6 * MeshData.VERTEX_STRIDE);
        for (int segment = 0; segment < segments; segment++) {
            int start = segment * fine.sampleStep();
            int end = start + fine.sampleStep();
            float fineStart = fineHeight(fine, edge, segment);
            float fineEnd = fineHeight(fine, edge, segment + 1);
            float coarseStart = coarseHeight(coarse, fine, edge, start);
            float coarseEnd = coarseHeight(coarse, fine, edge, end);
            int[][] positions = {
                position(fine, edge, start, fineStart),
                position(fine, edge, end, fineEnd),
                position(fine, edge, end, coarseEnd),
                position(fine, edge, start, coarseStart)
            };
            for (int corner : new int[] {0, 1, 2, 0, 2, 3})
                addVertex(out, positions[corner], fineMaterial(fine, edge, segment));
        }
        return new MeshData(out.toArray());
    }

    private static boolean adjacent(FarTerrainTile fine, FarTerrainTile coarse, Edge edge) {
        return switch (edge) {
            case POSITIVE_X ->
                (long) coarse.originX() == (long) fine.originX() + fine.tileSize()
                        && coarse.originZ() == fine.originZ();
            case NEGATIVE_X ->
                (long) coarse.originX() + coarse.tileSize() == fine.originX() && coarse.originZ() == fine.originZ();
            case POSITIVE_Z ->
                (long) coarse.originZ() == (long) fine.originZ() + fine.tileSize()
                        && coarse.originX() == fine.originX();
            case NEGATIVE_Z ->
                (long) coarse.originZ() + coarse.tileSize() == fine.originZ() && coarse.originX() == fine.originX();
        };
    }

    private static float fineHeight(FarTerrainTile tile, Edge edge, int segment) {
        return switch (edge) {
            case POSITIVE_X -> tile.heightAt(tile.sampleCount() - 1, segment);
            case NEGATIVE_X -> tile.heightAt(0, segment);
            case POSITIVE_Z -> tile.heightAt(segment, tile.sampleCount() - 1);
            case NEGATIVE_Z -> tile.heightAt(segment, 0);
        };
    }

    private static byte fineMaterial(FarTerrainTile tile, Edge edge, int segment) {
        int last = tile.sampleCount() - 1;
        return switch (edge) {
            case POSITIVE_X -> tile.materialAt(last, segment);
            case NEGATIVE_X -> tile.materialAt(0, segment);
            case POSITIVE_Z -> tile.materialAt(segment, last);
            case NEGATIVE_Z -> tile.materialAt(segment, 0);
        };
    }

    private static float coarseHeight(FarTerrainTile coarse, FarTerrainTile fine, Edge edge, int tangent) {
        int world = (edge == Edge.POSITIVE_X || edge == Edge.NEGATIVE_X ? fine.originZ() : fine.originX()) + tangent;
        int relative =
                world - (edge == Edge.POSITIVE_X || edge == Edge.NEGATIVE_X ? coarse.originZ() : coarse.originX());
        int sample = Math.floorDiv(relative, coarse.sampleStep());
        return switch (edge) {
            case POSITIVE_X -> coarse.heightAt(0, sample);
            case NEGATIVE_X -> coarse.heightAt(coarse.sampleCount() - 1, sample);
            case POSITIVE_Z -> coarse.heightAt(sample, 0);
            case NEGATIVE_Z -> coarse.heightAt(sample, coarse.sampleCount() - 1);
        };
    }

    private static int[] position(FarTerrainTile tile, Edge edge, int tangent, float height) {
        return switch (edge) {
            case POSITIVE_X ->
                new int[] {tile.originX() + tile.tileSize(), Math.round(height), tile.originZ() + tangent};
            case NEGATIVE_X -> new int[] {tile.originX(), Math.round(height), tile.originZ() + tangent};
            case POSITIVE_Z ->
                new int[] {tile.originX() + tangent, Math.round(height), tile.originZ() + tile.tileSize()};
            case NEGATIVE_Z -> new int[] {tile.originX() + tangent, Math.round(height), tile.originZ()};
        };
    }

    private static void addVertex(Floats out, int[] position, byte material) {
        float shade = material == BlockId.GRASS ? .5f : material == BlockId.DIRT ? .46f : .48f;
        out.add(position[0]);
        out.add(position[1]);
        out.add(position[2]);
        out.add(shade);
        out.add(shade);
        out.add(shade);
    }

    public enum Edge {
        POSITIVE_X,
        NEGATIVE_X,
        POSITIVE_Z,
        NEGATIVE_Z;
    }

    private static final class Floats {
        private final float[] values;
        private int size;

        Floats(int capacity) {
            values = new float[capacity];
        }

        void add(float value) {
            values[size++] = value;
        }

        float[] toArray() {
            return values;
        }
    }
}
