package com.norbertfila.render;

import static org.lwjgl.opengl.GL46C.*;

import com.norbertfila.meshing.ChunkMesh;
import com.norbertfila.meshing.QuadMesh;
import com.norbertfila.streaming.ChunkManager.MeshResult;
import com.norbertfila.streaming.RegionIndex;
import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.RenderLayer;
import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

/**
 * Draws chunk quad meshes by vertex pulling: each mesh part is a shader storage buffer of quad records, and the vertex
 * shaders rebuild the vertices. Positions are camera-relative so far-away chunks keep full float precision.
 */
public final class TerrainRenderer implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger(TerrainRenderer.class.getName());
    private static final long DEFAULT_GPU_BUDGET_BYTES = 256L * 1024 * 1024;
    private static final long ARENA_BYTES = 1024L * 1024;
    private final GpuMemoryBudget memoryBudget;
    private final GpuMemoryBudget arenaBudget;
    private final QuadArenas solidArenas;
    private final QuadArenas waterArenas;
    // Solid and water parts of a chunk are separate GPU meshes so either can be replaced alone.
    private final GpuMeshPool solidMeshes;
    private final GpuMeshPool waterMeshes;
    private final List<ChunkPos> visibleSolids = new ArrayList<>();
    private final List<ChunkPos> visibleWater = new ArrayList<>();
    private final UploadQueue uploads = new UploadQueue();
    private final RegionIndex regionIndex;
    private final RegionCuller regionCuller = new RegionCuller();
    private final BlockTextureArray blockTextures;
    private final Program solidProgram;
    private final Program waterProgram;
    private final int emptyVertexArray;
    private final long startedAt = System.nanoTime();
    private boolean closed;

    public TerrainRenderer(BlockTextureArray blockTextures) {
        this(blockTextures, 8, DEFAULT_GPU_BUDGET_BYTES);
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
        Program solid = null;
        try {
            solid = new Program("/shaders/terrain_solid.vert");
            waterProgram = new Program("/shaders/terrain_water.vert");
        } catch (RuntimeException | Error failure) {
            if (solid != null) solid.close();
            throw failure;
        }
        solidProgram = solid;
        // Vertex pulling reads no attributes, but the core profile still requires a bound vertex array.
        emptyVertexArray = glGenVertexArrays();
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
     * Draws visible terrain. {@code viewProjection} must place the camera at the origin; {@code frustum} is built from
     * the absolute view projection and only selects chunks.
     */
    public RenderStats render(
            Matrix4f viewProjection,
            Frustum frustum,
            double cameraX,
            double cameraY,
            double cameraZ,
            float underwater) {
        if (!Double.isFinite(cameraX) || !Double.isFinite(cameraY) || !Double.isFinite(cameraZ))
            throw new IllegalArgumentException("Camera position must be finite");
        if (!Float.isFinite(underwater) || underwater < 0 || underwater > 1)
            throw new IllegalArgumentException("Underwater visibility must be in [0, 1]");
        RegionCuller.Result culling = regionCuller.cull(frustum, regionIndex);
        visibleSolids.clear();
        visibleWater.clear();
        for (ChunkPos position : culling.visible()) {
            if (solidMeshes.get(position) != null) visibleSolids.add(position);
            if (waterMeshes.get(position) != null) visibleWater.add(position);
        }
        // Chunk-center ordering approximates back-to-front blending; overlapping water inside one chunk can still sort
        // incorrectly.
        visibleWater.sort(backToFront(cameraX, cameraY, cameraZ));
        float time = (float) ((System.nanoTime() - startedAt) / 1e9 % 4096);
        blockTextures.bind(0);
        glBindVertexArray(emptyVertexArray);
        int draws = 0;
        try {
            restoreTerrainState();
            solidProgram.use(viewProjection, underwater, time);
            draws += draw(solidProgram, solidMeshes, visibleSolids, RenderLayer.OPAQUE, cameraX, cameraY, cameraZ);
            draws += draw(solidProgram, solidMeshes, visibleSolids, RenderLayer.CUTOUT, cameraX, cameraY, cameraZ);
            if (!visibleWater.isEmpty()) {
                glEnable(GL_BLEND);
                glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
                glDepthMask(false);
                glDisable(GL_CULL_FACE);
                waterProgram.use(viewProjection, underwater, time);
                draws += draw(
                        waterProgram, waterMeshes, visibleWater, RenderLayer.TRANSLUCENT, cameraX, cameraY, cameraZ);
            }
        } finally {
            restoreTerrainState();
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, 0);
            glBindVertexArray(0);
            endFrame();
        }
        int visible = culling.visible().size();
        return new RenderStats(visible, regionIndex.size() - visible, draws);
    }

    /** Hands replaced or removed meshes to the arenas, which reuse their ranges once the GPU is done with them. */
    private void endFrame() {
        solidMeshes.drainDeletions(Integer.MAX_VALUE);
        waterMeshes.drainDeletions(Integer.MAX_VALUE);
        solidArenas.endFrame();
        waterArenas.endFrame();
    }

    private static int draw(
            Program program,
            GpuMeshPool pool,
            List<ChunkPos> positions,
            RenderLayer layer,
            double cameraX,
            double cameraY,
            double cameraZ) {
        glUniform1i(program.renderLayer, layer.ordinal());
        int draws = 0, boundBuffer = -1;
        for (ChunkPos position : positions) {
            GpuMesh mesh = pool.get(position);
            int quads = mesh.ranges().count(layer);
            if (quads == 0) continue;
            // Subtract in double precision so chunk origins far from zero stay exact relative to the camera.
            glUniform3f(
                    program.chunkOrigin,
                    (float) ((double) position.x() * Chunk.SIZE - cameraX),
                    (float) ((double) position.y() * Chunk.SIZE - cameraY),
                    (float) ((double) position.z() * Chunk.SIZE - cameraZ));
            if (mesh.buffer() != boundBuffer) {
                glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, mesh.buffer());
                boundBuffer = mesh.buffer();
            }
            glDrawArrays(
                    GL_TRIANGLES,
                    (mesh.firstRecord() + mesh.ranges().first(layer)) * QuadMesh.VERTICES_PER_QUAD,
                    quads * QuadMesh.VERTICES_PER_QUAD);
            draws++;
        }
        return draws;
    }

    private static void restoreTerrainState() {
        glEnable(GL_DEPTH_TEST);
        glDepthMask(true);
        glDisable(GL_BLEND);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
    }

    static Comparator<ChunkPos> backToFront(double cameraX, double cameraY, double cameraZ) {
        if (!Double.isFinite(cameraX) || !Double.isFinite(cameraY) || !Double.isFinite(cameraZ))
            throw new IllegalArgumentException("Camera position must be finite");
        return Comparator.<ChunkPos>comparingDouble(position -> distanceSquared(position, cameraX, cameraY, cameraZ))
                .reversed()
                .thenComparingInt(ChunkPos::x)
                .thenComparingInt(ChunkPos::y)
                .thenComparingInt(ChunkPos::z);
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

    public record RenderStats(int visible, int culled, int drawCalls) {}

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

    /** One linked terrain program; solid and water programs share the fragment shader and uniform names. */
    private static final class Program implements AutoCloseable {
        private final int id;
        private final int viewProjection;
        private final int chunkOrigin;
        private final int renderLayer;
        private final int waterTime;
        private final int waterFog;

        Program(String vertexShader) {
            int vertex = 0, fragment = 0, linked = 0;
            try {
                vertex = compile(GL_VERTEX_SHADER, vertexShader);
                fragment = compile(GL_FRAGMENT_SHADER, "/shaders/terrain.frag");
                linked = glCreateProgram();
                glAttachShader(linked, vertex);
                glAttachShader(linked, fragment);
                glLinkProgram(linked);
                if (glGetProgrami(linked, GL_LINK_STATUS) == GL_FALSE)
                    throw new IllegalStateException("Shader program link failed: " + glGetProgramInfoLog(linked));
                viewProjection = uniform(linked, "uVP");
                chunkOrigin = uniform(linked, "uChunkOrigin");
                renderLayer = uniform(linked, "uRenderLayer");
                waterTime = uniform(linked, "uWaterTime");
                waterFog = uniform(linked, "uWaterFog");
                int sampler = uniform(linked, "uBlockTextures");
                int fogRange = uniform(linked, "uWaterFogRange");
                glUseProgram(linked);
                glUniform1i(sampler, 0);
                glUniform2f(fogRange, UnderwaterVisibility.CLEAR_DISTANCE, UnderwaterVisibility.FOG_DENSITY);
                id = linked;
            } catch (RuntimeException | Error failure) {
                if (linked != 0) glDeleteProgram(linked);
                throw failure;
            } finally {
                if (vertex != 0) glDeleteShader(vertex);
                if (fragment != 0) glDeleteShader(fragment);
            }
        }

        void use(Matrix4f viewProjectionMatrix, float underwater, float time) {
            glUseProgram(id);
            try (MemoryStack stack = MemoryStack.stackPush()) {
                FloatBuffer matrix = stack.mallocFloat(16);
                viewProjectionMatrix.get(matrix);
                glUniformMatrix4fv(viewProjection, false, matrix);
            }
            glUniform1f(waterTime, time);
            glUniform4f(
                    waterFog,
                    UnderwaterVisibility.FOG_RED,
                    UnderwaterVisibility.FOG_GREEN,
                    UnderwaterVisibility.FOG_BLUE,
                    underwater);
        }

        private static int uniform(int program, String name) {
            int location = glGetUniformLocation(program, name);
            if (location < 0) throw new IllegalStateException("Shader uniform not found: " + name);
            return location;
        }

        @Override
        public void close() {
            glDeleteProgram(id);
        }
    }

    private static int compile(int type, String resource) {
        int shader = glCreateShader(type);
        try {
            glShaderSource(shader, loadSource(resource));
            glCompileShader(shader);
            if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
                throw new IllegalStateException(
                        "Shader compilation failed for " + resource + ": " + glGetShaderInfoLog(shader));
            }
            return shader;
        } catch (RuntimeException | Error failure) {
            glDeleteShader(shader);
            throw failure;
        }
    }

    private static String loadSource(String resource) {
        try (InputStream stream = TerrainRenderer.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException("Shader resource not found: " + resource);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot read shader resource: " + resource, failure);
        }
    }
}
