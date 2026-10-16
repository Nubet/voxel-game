package com.norbertfila.render;

import static org.lwjgl.opengl.GL46C.*;

import com.norbertfila.lod.FarTerrainStreamer;
import com.norbertfila.lod.FarTerrainTile;
import com.norbertfila.lod.FarTileMesh;
import com.norbertfila.lod.FarTilePos;
import com.norbertfila.meshing.QuadMesh;
import com.norbertfila.streaming.ChunkManager;
import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.RenderLayer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;

/**
 * Draws far terrain tiles published by {@link FarTerrainStreamer}. Tiles are uploaded under a per-frame budget, and a
 * tile's previous mesh is drawn until its replacement is on the GPU, and far terrain stays visible in every chunk
 * column near terrain does not fully draw yet, so loading and unloading near chunks never opens a hole.
 *
 * <p>Near and far terrain share a depth range. Coverage hides far geometry in fully drawn near columns.
 */
public final class FarTerrainRenderer implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger(FarTerrainRenderer.class.getName());
    private static final int RECORD_BYTES = QuadMesh.Format.FAR.ints() * Integer.BYTES;
    private static final long ARENA_BYTES = 4L * 1024 * 1024 / RECORD_BYTES * RECORD_BYTES;
    private static final long GPU_BUDGET_BYTES = 256L * 1024 * 1024;
    private static final int SOLID_PASS = 0, WATER_PASS = 1;

    private final BlockTextureArray blockTextures;
    private final GpuMemoryBudget budget = new GpuMemoryBudget(GPU_BUDGET_BYTES);
    private final QuadArenas arenas = new QuadArenas(QuadArenas.OPENGL, RECORD_BYTES, ARENA_BYTES, budget);
    private final TerrainProgram program;
    private final IndirectDraws indirect = new IndirectDraws(TerrainRenderer.multiDraw());
    private final DrawCommands commands = new DrawCommands();
    private final Frustum frustum = new Frustum();
    // Vertex pulling reads no attributes, but the core profile still requires a bound vertex array.
    private final int emptyVertexArray = glGenVertexArrays();
    private final int coverageBufferId = glGenBuffers();
    private IntBuffer coverageStaging = MemoryUtil.memAllocInt(64);
    private ChunkManager.Coverage uploadedCoverage;
    private final Map<FarTilePos, Tile> tiles = new HashMap<>();
    // Published tiles not yet on the GPU, in the order the streamer finished them.
    private final List<FarTileMesh> pending = new ArrayList<>();
    private final List<Tile> visibleWater = new ArrayList<>();
    private final FarthestFirst farthestFirst = new FarthestFirst();
    private final Comparator<Tile> waterOrder = (left, right) -> farthestFirst.compare(left.source, right.source);
    private long residencyRevision = -1;
    private long gpuBytes;
    private boolean closed;

    public FarTerrainRenderer(BlockTextureArray blockTextures) {
        this.blockTextures = Objects.requireNonNull(blockTextures);
        program = new TerrainProgram("/shaders/terrain_far.vert");
    }

    void distanceFog(DistanceFog fog) {
        program.distanceFog(fog);
    }

    /**
     * Matches the GPU tiles to a newly published residency: dropped tiles are released, new ones queued so the tile
     * nearest the camera uploads first.
     */
    public void reconcile(FarTerrainStreamer.Residency residency, double cameraX, double cameraZ) {
        if (residency.revision() == residencyRevision) return;
        residencyRevision = residency.revision();
        Map<FarTilePos, FarTileMesh> published = residency.tiles();
        tiles.entrySet().removeIf(entry -> {
            if (published.containsKey(entry.getKey())) return false;
            entry.getValue().release();
            return true;
        });
        pending.clear();
        for (FarTileMesh mesh : published.values()) {
            Tile tile = tiles.get(mesh.position());
            if (tile == null || tile.source != mesh) pending.add(mesh);
        }
        farthestFirst.camera(cameraX, cameraZ);
        pending.sort(farthestFirst);
    }

    /** Uploads queued tiles until either budget is spent; returns the bytes uploaded. */
    public long upload(long byteBudget, long nanosBudget) {
        long started = System.nanoTime(), bytes = 0;
        while (!pending.isEmpty() && bytes < byteBudget && System.nanoTime() - started < nanosBudget) {
            FarTileMesh mesh = pending.removeLast();
            Tile previous = tiles.get(mesh.position());
            if (mesh.mesh().quadCount() == 0) {
                if (previous != null) previous.release();
                tiles.put(mesh.position(), new Tile(mesh, null));
                continue;
            }
            QuadArenas.Slice slice = arenas.allocate(mesh.mesh().records());
            if (slice == null) {
                LOG.log(Level.WARNING, "Far terrain GPU budget exhausted; keeping " + tiles.size() + " tiles");
                pending.clear();
                break;
            }
            if (previous != null) previous.release();
            tiles.put(mesh.position(), new Tile(mesh, slice));
            gpuBytes += mesh.mesh().bytes();
            bytes += mesh.mesh().bytes();
        }
        return bytes;
    }

    public int pendingUploads() {
        return pending.size();
    }

    /**
     * Draws visible tiles with {@code viewProjection}, which must place the camera at the origin, except in the chunk
     * columns {@code coverage} marks as fully drawn by near terrain.
     */
    public int render(
            Matrix4f viewProjection,
            double cameraX,
            double cameraY,
            double cameraZ,
            float underwater,
            ChunkManager.Coverage coverage) {
        int calls = renderOpaque(viewProjection, cameraX, cameraY, cameraZ, underwater, coverage);
        return calls + renderWater(viewProjection, cameraX, cameraY, cameraZ, underwater, coverage);
    }

    public int renderOpaque(
            Matrix4f viewProjection,
            double cameraX,
            double cameraY,
            double cameraZ,
            float underwater,
            ChunkManager.Coverage coverage) {
        frustum.set(viewProjection);
        commands.clear();
        visibleWater.clear();
        for (Tile tile : tiles.values()) {
            if (tile.slice == null || tile.covered(coverage)) continue;
            FarTileMesh mesh = tile.source;
            float minX = (float) (mesh.tileX() * (double) FarTerrainTile.SIZE - cameraX);
            float minZ = (float) (mesh.tileZ() * (double) FarTerrainTile.SIZE - cameraZ);
            float minY = (float) (mesh.minY() - cameraY), maxY = (float) (mesh.maxY() - cameraY);
            if (!frustum.intersects(minX, minY, minZ, minX + FarTerrainTile.SIZE, maxY, minZ + FarTerrainTile.SIZE))
                continue;
            add(SOLID_PASS, tile, RenderLayer.OPAQUE);
            if (mesh.mesh().ranges().count(RenderLayer.TRANSLUCENT) > 0) visibleWater.add(tile);
        }
        farthestFirst.camera(cameraX, cameraZ);
        visibleWater.sort(waterOrder);
        for (Tile tile : visibleWater) add(WATER_PASS, tile, RenderLayer.TRANSLUCENT);
        if (commands.draws() == 0) {
            return 0;
        }
        int calls = 0;
        blockTextures.bind(0);
        glBindVertexArray(emptyVertexArray);
        try {
            indirect.bind(commands);
            TerrainRenderer.restoreTerrainState();
            program.use(viewProjection, underwater, 0, cameraX, cameraY, cameraZ);
            bindCoverage(coverage);
            program.coverage(
                    (float) ((double) coverage.minChunkX() * Chunk.SIZE - cameraX),
                    (float) ((double) coverage.minChunkZ() * Chunk.SIZE - cameraZ),
                    coverage.size());
            program.renderLayer(RenderLayer.OPAQUE);
            calls += indirect.submit(commands, SOLID_PASS);
        } finally {
            TerrainRenderer.restoreTerrainState();
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, 0);
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 2, 0);
            glBindVertexArray(0);
        }
        return calls;
    }

    /** Draws water prepared by the opaque pass using the same projection and coverage. */
    public int renderWater(
            Matrix4f viewProjection,
            double cameraX,
            double cameraY,
            double cameraZ,
            float underwater,
            ChunkManager.Coverage coverage) {
        try {
            if (!visibleWater.isEmpty()) {
                blockTextures.bind(0);
                glBindVertexArray(emptyVertexArray);
                indirect.bind(commands);
                TerrainRenderer.restoreTerrainState();
                program.use(viewProjection, underwater, 0, cameraX, cameraY, cameraZ);
                bindCoverage(coverage);
                program.coverage(
                        (float) ((double) coverage.minChunkX() * Chunk.SIZE - cameraX),
                        (float) ((double) coverage.minChunkZ() * Chunk.SIZE - cameraZ),
                        coverage.size());
                glEnable(GL_BLEND);
                glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
                glDepthMask(false);
                glDisable(GL_CULL_FACE);
                program.renderLayer(RenderLayer.TRANSLUCENT);
                return indirect.submit(commands, WATER_PASS);
            }
            return 0;
        } finally {
            TerrainRenderer.restoreTerrainState();
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, 0);
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 2, 0);
            glBindVertexArray(0);
            endFrame();
        }
    }

    /** Uploads the coverage bits when they changed; a long holds 64 columns, low int first. */
    private void bindCoverage(ChunkManager.Coverage coverage) {
        if (coverage != uploadedCoverage) {
            long[] bits = coverage.bits();
            int ints = Math.max(1, bits.length * 2);
            if (coverageStaging.capacity() < ints) coverageStaging = MemoryUtil.memRealloc(coverageStaging, ints * 2);
            coverageStaging.clear();
            for (long word : bits) coverageStaging.put((int) word).put((int) (word >>> 32));
            if (bits.length == 0) coverageStaging.put(0);
            coverageStaging.flip();
            glBindBuffer(GL_SHADER_STORAGE_BUFFER, coverageBufferId);
            glBufferData(GL_SHADER_STORAGE_BUFFER, coverageStaging, GL_STREAM_DRAW);
            uploadedCoverage = coverage;
        }
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 2, coverageBufferId);
    }

    private void add(int pass, Tile tile, RenderLayer layer) {
        int quads = tile.source.mesh().ranges().count(layer);
        if (quads == 0) return;
        int first = tile.slice.firstRecord() + tile.source.mesh().ranges().first(layer);
        commands.add(
                pass,
                tile.slice.buffer(),
                first * QuadMesh.VERTICES_PER_QUAD,
                quads * QuadMesh.VERTICES_PER_QUAD,
                tile.origin);
    }

    /** Orders tiles by distance from the camera to the tile center, farthest first; reused to avoid allocation. */
    private static final class FarthestFirst implements Comparator<FarTileMesh> {
        private double x, z;

        void camera(double x, double z) {
            this.x = x;
            this.z = z;
        }

        @Override
        public int compare(FarTileMesh left, FarTileMesh right) {
            return Double.compare(distanceSquared(right), distanceSquared(left));
        }

        private double distanceSquared(FarTileMesh mesh) {
            double dx = (mesh.tileX() + .5) * FarTerrainTile.SIZE - x;
            double dz = (mesh.tileZ() + .5) * FarTerrainTile.SIZE - z;
            return dx * dx + dz * dz;
        }
    }

    private void endFrame() {
        arenas.endFrame();
    }

    public int tileCount() {
        return tiles.size();
    }

    /** Bytes of far records on the GPU and the arena memory reserved for them. */
    public long gpuBytes() {
        return gpuBytes;
    }

    public long arenaBytes() {
        return arenas.capacityBytes();
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        for (Tile tile : tiles.values()) tile.release();
        tiles.clear();
        arenas.close();
        program.close();
        indirect.close();
        glDeleteVertexArrays(emptyVertexArray);
        glDeleteBuffers(coverageBufferId);
        MemoryUtil.memFree(coverageStaging);
    }

    private final class Tile {
        private final FarTileMesh source;
        // Null for a tile without quads.
        private final QuadArenas.Slice slice;
        // Tile origin in chunk units, as the draw table stores it.
        private final ChunkPos origin;

        Tile(FarTileMesh source, QuadArenas.Slice slice) {
            this.source = source;
            this.slice = slice;
            int chunks = FarTerrainTile.SIZE / Chunk.SIZE;
            origin = new ChunkPos(source.tileX() * chunks, 0, source.tileZ() * chunks);
        }

        // Coverage snapshots are immutable, so the answer holds until a new snapshot arrives.
        private ChunkManager.Coverage coverageChecked;
        private boolean covered;

        /** True when near terrain fully draws every chunk column of the tile. */
        boolean covered(ChunkManager.Coverage coverage) {
            if (coverage != coverageChecked) {
                coverageChecked = coverage;
                covered = true;
                int chunks = FarTerrainTile.SIZE / Chunk.SIZE;
                for (int z = 0; z < chunks && covered; z++)
                    for (int x = 0; x < chunks && covered; x++)
                        covered = coverage.drawn(origin.x() + x, origin.z() + z);
            }
            return covered;
        }

        void release() {
            if (slice == null) return;
            arenas.release(slice);
            gpuBytes -= source.mesh().bytes();
        }
    }
}
