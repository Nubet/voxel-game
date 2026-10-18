package com.norbertfila.render;

import static org.lwjgl.opengl.GL46C.*;

import com.norbertfila.meshing.ChunkMesh;
import com.norbertfila.meshing.QuadMesh;
import com.norbertfila.streaming.ChunkManager.MeshResult;
import com.norbertfila.streaming.RegionIndex;
import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.RenderLayer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.joml.Matrix4f;

/**
 * Draws chunk quad meshes by vertex pulling: each mesh part is a shader storage buffer of quad records, and the vertex
 * shaders rebuild the vertices. Positions are camera-relative so far-away chunks keep full float precision.
 */
public final class TerrainRenderer implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger(TerrainRenderer.class.getName());
    private static final long DEFAULT_GPU_BUDGET_BYTES = 256L * 1024 * 1024;
    private static final long ARENA_BYTES = 1024L * 1024;
    private static final int TRANSLUCENT_SOLID_PASS = 3;
    private static final int OPAQUE_FLUID_PASS = 4;
    private final GpuMemoryBudget memoryBudget;
    private final GpuMemoryBudget arenaBudget;
    private final QuadArenas solidArenas;
    private final QuadArenas waterArenas;
    // Solid and water parts of a chunk are separate GPU meshes so either can be replaced alone.
    private final GpuMeshPool solidMeshes;
    private final GpuMeshPool waterMeshes;
    private final Frustum frustum = new Frustum();
    private final BackToFront translucentOrder = new BackToFront();
    private final List<ChunkPos> visibleChunks = new ArrayList<>();
    private long cullNanos;
    private final List<ChunkPos> visibleSolids = new ArrayList<>();
    private final List<ChunkPos> visibleTranslucent = new ArrayList<>();
    private final UploadQueue uploads = new UploadQueue();
    private final RegionIndex regionIndex;
    private final RegionCuller regionCuller = new RegionCuller();
    private final BlockTextureArray blockTextures;
    private final TerrainProgram solidProgram;
    private final TerrainProgram waterProgram;
    private final int emptyVertexArray;
    // Draw commands for the current frame; the direct path issues the same commands one by one for A/B checks.
    private final DrawCommands commands = new DrawCommands();
    private int[] distinctBuffers = new int[8];
    private final IndirectDraws indirect;
    private final FarTerrainRenderer far;
    private final long startedAt = System.nanoTime();
    private boolean closed;

    public TerrainRenderer(BlockTextureArray blockTextures) {
        this(blockTextures, 8, DEFAULT_GPU_BUDGET_BYTES);
    }

    public void viewDistances(int detailChunks, int landscapeChunks) {
        var fog = DistanceFog.forDistances(detailChunks, landscapeChunks);
        solidProgram.distanceFog(fog);
        waterProgram.distanceFog(fog);
        far.distanceFog(fog);
    }

    public TerrainRenderer(BlockTextureArray blockTextures, int regionEdge, long gpuBudgetBytes) {
        this.blockTextures = Objects.requireNonNull(blockTextures);
        regionIndex = new RegionIndex(regionEdge);
        memoryBudget = new GpuMemoryBudget(gpuBudgetBytes);
        arenaBudget = new GpuMemoryBudget(gpuBudgetBytes);
        solidMeshes = new GpuMeshPool(memoryBudget);
        waterMeshes = new GpuMeshPool(memoryBudget);
        solidArenas = new QuadArenas(
                QuadArenas.OPENGL, QuadMesh.Format.SOLID.ints() * Integer.BYTES, ARENA_BYTES, arenaBudget);
        waterArenas = new QuadArenas(
                QuadArenas.OPENGL, QuadMesh.Format.WATER.ints() * Integer.BYTES, ARENA_BYTES, arenaBudget);
        TerrainProgram solid = null;
        try {
            solid = new TerrainProgram("/shaders/terrain_solid.vert");
            waterProgram = new TerrainProgram("/shaders/terrain_water.vert");
        } catch (RuntimeException | Error failure) {
            if (solid != null) solid.close();
            throw failure;
        }
        solidProgram = solid;
        // Vertex pulling reads no attributes, but the core profile still requires a bound vertex array.
        emptyVertexArray = glGenVertexArrays();
        indirect = new IndirectDraws(multiDraw());
        far = new FarTerrainRenderer(blockTextures);
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
    }

    public void upload(ChunkPos position, ChunkMesh mesh) {
        Objects.requireNonNull(position);
        Objects.requireNonNull(mesh);
        if (mesh.solid() != null) upload(solidMeshes, solidArenas, position, mesh.solid());
        if (mesh.water() != null) upload(waterMeshes, waterArenas, position, mesh.water());
        if (solidMeshes.get(position) != null || waterMeshes.get(position) != null) regionIndex.add(position);
        else regionIndex.remove(position);
    }

    /** Replaces the part only after the new records are on the GPU; on failure the previous mesh stays. */
    private static void upload(GpuMeshPool pool, QuadArenas arenas, ChunkPos position, QuadMesh mesh) {
        if (mesh.quadCount() == 0) {
            pool.remove(position);
            return;
        }
        QuadArenas.Slice slice = arenas.allocate(mesh.records());
        if (slice == null) throw new IllegalStateException("GPU geometry budget exhausted");
        try {
            pool.put(
                    position,
                    new GpuMesh(
                            slice.buffer(),
                            slice.firstRecord(),
                            mesh.format(),
                            mesh.ranges(),
                            mesh.bytes(),
                            () -> arenas.release(slice)));
        } catch (RuntimeException | Error failure) {
            arenas.release(slice);
            throw failure;
        }
    }

    public FarTerrainRenderer far() {
        return far;
    }

    public BlockTextureArray blockTextures() {
        return blockTextures;
    }

    public void enqueueUpload(MeshResult result) {
        uploads.enqueue(result);
    }

    public UploadStats processUploads(long byteBudget, long timeBudgetNanos, Predicate<MeshResult> isCurrent) {
        Objects.requireNonNull(isCurrent);
        if (byteBudget <= 0 || timeBudgetNanos <= 0) {
            throw new IllegalArgumentException("Upload budgets must be positive");
        }
        long start = System.nanoTime();
        long bytesUploaded = 0, slowestUpload = 0;
        int uploadCount = 0;
        List<MeshResult> uploaded = new ArrayList<>();
        List<MeshResult> failed = new ArrayList<>();
        List<MeshResult> discarded = new ArrayList<>();
        while (true) {
            MeshResult request = uploads.poll(byteBudget - bytesUploaded, uploadCount == 0, result -> {
                if (isCurrent.test(result)) return true;
                discarded.add(result);
                return false;
            });
            if (request == null) break;
            long uploadStarted = System.nanoTime();
            try {
                upload(request.position(), request.mesh());
                bytesUploaded += request.bytes();
                uploaded.add(request);
            } catch (RuntimeException failure) {
                failed.add(request);
                LOG.log(
                        Level.SEVERE,
                        "GPU upload failed position=" + request.position() + " bytes=" + request.bytes()
                                + " queueDepth=" + uploads.size(),
                        failure);
            }
            slowestUpload = Math.max(slowestUpload, System.nanoTime() - uploadStarted);
            uploadCount++;
            if (bytesUploaded >= byteBudget || System.nanoTime() - start >= timeBudgetNanos) break;
        }
        return new UploadStats(
                bytesUploaded,
                uploads.size(),
                System.nanoTime() - start,
                uploadCount,
                slowestUpload,
                List.copyOf(uploaded),
                List.copyOf(failed),
                List.copyOf(discarded));
    }

    public void remove(ChunkPos position) {
        uploads.remove(Objects.requireNonNull(position));
        solidMeshes.remove(position);
        waterMeshes.remove(position);
        regionIndex.remove(position);
    }

    /**
     * Draws visible terrain. {@code viewProjection} must place the camera at the origin; culling and drawing both
     * work relative to the camera position.
     */
    public RenderStats render(
            Matrix4f viewProjection, double cameraX, double cameraY, double cameraZ, float underwater) {
        RenderStats opaque = renderOpaque(viewProjection, cameraX, cameraY, cameraZ, underwater);
        int waterCalls = renderWater(viewProjection, cameraX, cameraY, cameraZ, underwater);
        return new RenderStats(
                opaque.visible(), opaque.culled(), opaque.drawCalls() + waterCalls, opaque.draws(), opaque.cullNanos());
    }

    public RenderStats renderOpaque(
            Matrix4f viewProjection, double cameraX, double cameraY, double cameraZ, float underwater) {
        if (!Double.isFinite(cameraX) || !Double.isFinite(cameraY) || !Double.isFinite(cameraZ))
            throw new IllegalArgumentException("Camera position must be finite");
        if (!Float.isFinite(underwater) || underwater < 0 || underwater > 1)
            throw new IllegalArgumentException("Underwater visibility must be in [0, 1]");
        long cullStarted = System.nanoTime();
        frustum.set(viewProjection);
        visibleChunks.clear();
        regionCuller.cull(frustum, regionIndex, cameraX, cameraY, cameraZ, visibleChunks);
        visibleSolids.clear();
        visibleTranslucent.clear();
        for (ChunkPos position : visibleChunks) {
            if (solidMeshes.get(position) != null) visibleSolids.add(position);
            GpuMesh solid = solidMeshes.get(position);
            if (waterMeshes.get(position) != null
                    || solid != null && solid.ranges().translucent() > 0) visibleTranslucent.add(position);
        }
        // Chunk-center ordering approximates back-to-front blending; overlapping translucent faces inside one chunk can
        // still sort
        // incorrectly.
        translucentOrder.camera(cameraX, cameraY, cameraZ);
        visibleTranslucent.sort(translucentOrder);
        cullNanos = System.nanoTime() - cullStarted;
        commands.clear();
        addGroupedByBuffer(RenderLayer.OPAQUE, solidMeshes, visibleSolids);
        addGroupedByBuffer(RenderLayer.CUTOUT, solidMeshes, visibleSolids);
        for (ChunkPos position : visibleChunks) {
            GpuMesh fluid = waterMeshes.get(position);
            if (fluid != null) add(OPAQUE_FLUID_PASS, RenderLayer.OPAQUE, fluid, position);
        }
        for (ChunkPos position : visibleTranslucent) {
            GpuMesh water = waterMeshes.get(position), solid = solidMeshes.get(position);
            if (water != null) add(RenderLayer.TRANSLUCENT, water, position);
            if (solid != null) add(TRANSLUCENT_SOLID_PASS, RenderLayer.TRANSLUCENT, solid, position);
        }
        float time = (float) ((System.nanoTime() - startedAt) / 1e9 % 4096);
        blockTextures.bind(0);
        glBindVertexArray(emptyVertexArray);
        int calls = 0;
        try {
            indirect.bind(commands);
            restoreTerrainState();
            solidProgram.use(viewProjection, underwater, time, cameraX, cameraY, cameraZ);
            solidProgram.renderLayer(RenderLayer.OPAQUE);
            calls += indirect.submit(commands, RenderLayer.OPAQUE.ordinal());
            solidProgram.renderLayer(RenderLayer.CUTOUT);
            calls += indirect.submit(commands, RenderLayer.CUTOUT.ordinal());
            glDisable(GL_CULL_FACE);
            waterProgram.use(viewProjection, underwater, time, cameraX, cameraY, cameraZ);
            waterProgram.renderLayer(RenderLayer.OPAQUE);
            calls += indirect.submit(commands, OPAQUE_FLUID_PASS);
        } finally {
            restoreTerrainState();
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, 0);
            glBindVertexArray(0);
        }
        int visible = visibleChunks.size();
        return new RenderStats(visible, regionIndex.size() - visible, calls, commands.draws(), cullNanos);
    }

    /** Draws translucent terrain after the scene background is complete. */
    public int renderWater(Matrix4f viewProjection, double cameraX, double cameraY, double cameraZ, float underwater) {
        try {
            if (!visibleTranslucent.isEmpty()) {
                blockTextures.bind(0);
                glBindVertexArray(emptyVertexArray);
                indirect.bind(commands);
                restoreTerrainState();
                glEnable(GL_BLEND);
                glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
                glDepthMask(false);
                glDisable(GL_CULL_FACE);
                float time = (float) ((System.nanoTime() - startedAt) / 1e9 % 4096);
                int calls = 0, activePass = -1;
                for (int run = 0; run < commands.runs(); run++) {
                    int pass = commands.runPass(run);
                    if (pass != RenderLayer.TRANSLUCENT.ordinal() && pass != TRANSLUCENT_SOLID_PASS) continue;
                    if (activePass != pass) {
                        TerrainProgram program = pass == TRANSLUCENT_SOLID_PASS ? solidProgram : waterProgram;
                        program.use(viewProjection, underwater, time, cameraX, cameraY, cameraZ);
                        program.renderLayer(RenderLayer.TRANSLUCENT);
                        activePass = pass;
                    }
                    calls += indirect.submitRun(commands, run);
                }
                return calls;
            }
            return 0;
        } finally {
            restoreTerrainState();
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, 0);
            glBindVertexArray(0);
            endFrame();
        }
    }

    /** Opaque and cutout order does not matter for blending, so draws are grouped to make one run per arena. */
    private void addGroupedByBuffer(RenderLayer layer, GpuMeshPool pool, List<ChunkPos> positions) {
        int distinct = 0;
        for (ChunkPos position : positions) {
            int buffer = pool.get(position).buffer();
            int index = 0;
            while (index < distinct && distinctBuffers[index] != buffer) index++;
            if (index < distinct) continue;
            if (distinct == distinctBuffers.length) distinctBuffers = Arrays.copyOf(distinctBuffers, distinct * 2);
            distinctBuffers[distinct++] = buffer;
        }
        for (int index = 0; index < distinct; index++) {
            int buffer = distinctBuffers[index];
            for (ChunkPos position : positions) {
                GpuMesh mesh = pool.get(position);
                if (mesh.buffer() == buffer) add(layer, mesh, position);
            }
        }
    }

    private void add(RenderLayer layer, GpuMesh mesh, ChunkPos position) {
        add(layer.ordinal(), layer, mesh, position);
    }

    private void add(int pass, RenderLayer layer, GpuMesh mesh, ChunkPos position) {
        int quads = mesh.ranges().count(layer);
        if (quads == 0) return;
        commands.add(
                pass,
                mesh.buffer(),
                (mesh.firstRecord() + mesh.ranges().first(layer)) * QuadMesh.VERTICES_PER_QUAD,
                quads * QuadMesh.VERTICES_PER_QUAD,
                position);
    }

    /** Hands replaced or removed meshes to the arenas, which reuse their ranges once the GPU is done with them. */
    private void endFrame() {
        solidMeshes.drainDeletions(Integer.MAX_VALUE);
        waterMeshes.drainDeletions(Integer.MAX_VALUE);
        solidArenas.endFrame();
        waterArenas.endFrame();
    }

    static void restoreTerrainState() {
        glEnable(GL_DEPTH_TEST);
        glDepthMask(true);
        glDisable(GL_BLEND);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
    }

    /** False when {@code -Dvoxel.render.directDraw=true} asks for one GL call per draw, the A/B reference path. */
    static boolean multiDraw() {
        return !Boolean.getBoolean("voxel.render.directDraw");
    }

    static Comparator<ChunkPos> backToFront(double cameraX, double cameraY, double cameraZ) {
        if (!Double.isFinite(cameraX) || !Double.isFinite(cameraY) || !Double.isFinite(cameraZ))
            throw new IllegalArgumentException("Camera position must be finite");
        BackToFront order = new BackToFront();
        order.camera(cameraX, cameraY, cameraZ);
        return order;
    }

    /** Farthest chunk centers first, ties broken by coordinates; reused every frame to avoid allocation. */
    private static final class BackToFront implements Comparator<ChunkPos> {
        private double x, y, z;

        void camera(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public int compare(ChunkPos left, ChunkPos right) {
            int distance = Double.compare(distanceSquared(right, x, y, z), distanceSquared(left, x, y, z));
            if (distance != 0) return distance;
            if (left.x() != right.x()) return Integer.compare(left.x(), right.x());
            if (left.y() != right.y()) return Integer.compare(left.y(), right.y());
            return Integer.compare(left.z(), right.z());
        }
    }

    private static double distanceSquared(ChunkPos position, double x, double y, double z) {
        double dx = (double) position.x() * Chunk.SIZE + Chunk.SIZE * .5 - x;
        double dy = (double) position.y() * Chunk.SIZE + Chunk.SIZE * .5 - y;
        double dz = (double) position.z() * Chunk.SIZE + Chunk.SIZE * .5 - z;
        return dx * dx + dy * dy + dz * dz;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        solidMeshes.close();
        waterMeshes.close();
        solidArenas.close();
        waterArenas.close();
        solidProgram.close();
        waterProgram.close();
        glDeleteVertexArrays(emptyVertexArray);
        indirect.close();
        far.close();
    }

    public record UploadStats(
            long bytesUploaded,
            int queueDepth,
            long uploadNanos,
            int uploadCount,
            long slowestUploadNanos,
            List<MeshResult> uploaded,
            List<MeshResult> failed,
            List<MeshResult> discarded) {}

    /** {@code drawCalls} counts GL calls issued; {@code draws} counts mesh ranges drawn, the same on both paths. */
    public record RenderStats(int visible, int culled, int drawCalls, int draws, long cullNanos) {}

    public MemoryStats memoryStats() {
        return new MemoryStats(
                memoryBudget.allocatedBytes(),
                memoryBudget.peakBytes(),
                memoryBudget.uploadedBytes(),
                memoryBudget.deletedBytes(),
                solidArenas.capacityBytes() + waterArenas.capacityBytes(),
                solidArenas.arenaCount() + waterArenas.arenaCount(),
                solidArenas.fragmentationBytes() + waterArenas.fragmentationBytes());
    }

    /**
     * {@code allocated} counts bytes of live meshes; {@code arenaBytes} is the GPU memory reserved for them across
     * {@code arenas} buffers.
     */
    public record MemoryStats(
            long allocated, long peak, long uploaded, long deleted, long arenaBytes, int arenas, long fragmentation) {}
}
