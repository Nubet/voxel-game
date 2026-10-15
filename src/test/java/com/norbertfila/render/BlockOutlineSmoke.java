package com.norbertfila.render;

import static org.lwjgl.opengl.GL46C.*;

import com.norbertfila.assets.AssetLoader;
import com.norbertfila.meshing.ChunkMesh;
import com.norbertfila.meshing.WaterMesher;
import com.norbertfila.platform.Window;
import com.norbertfila.ui.UiCanvas;
import com.norbertfila.ui.UiViewport;
import com.norbertfila.world.*;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import javax.imageio.ImageIO;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;

public final class BlockOutlineSmoke {
    private static final int WIDTH = 640, HEIGHT = 360;

    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]);
        check(Files.isDirectory(output), "Output directory must exist");
        try (Window window = new Window(WIDTH, HEIGHT, "TARGET-02 block outline");
                BlockTextureArray textures =
                        new BlockTextureArray(new AssetLoader(BlockRegistry.DEFAULT).loadBlockTextures());
                BlockOutlineRenderer outline = new BlockOutlineRenderer();
                UiRenderer ui = new UiRenderer()) {
            System.out.println("GPU=" + glGetString(GL_RENDERER) + " GL=" + glGetString(GL_VERSION));
            int framebuffer = glCreateFramebuffers(),
                    color = glCreateTextures(GL_TEXTURE_2D),
                    depth = glCreateRenderbuffers();
            try {
                glTextureStorage2D(color, 1, GL_RGBA8, WIDTH, HEIGHT);
                glNamedRenderbufferStorage(depth, GL_DEPTH_COMPONENT24, WIDTH, HEIGHT);
                glNamedFramebufferTexture(framebuffer, GL_COLOR_ATTACHMENT0, color, 0);
                glNamedFramebufferRenderbuffer(framebuffer, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, depth);
                check(
                        glCheckNamedFramebufferStatus(framebuffer, GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE,
                        "Incomplete framebuffer");
                glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
                glViewport(0, 0, WIDTH, HEIGHT);
                faces(textures, outline, ui, output);
                occlusion(textures, outline);
                precision(textures, outline, output);
                cleanup();
                check(glGetError() == GL_NO_ERROR, "Outline GL error");
            } finally {
                glBindFramebuffer(GL_FRAMEBUFFER, 0);
                glDeleteRenderbuffers(depth);
                glDeleteTextures(color);
                glDeleteFramebuffers(framebuffer);
            }
        }
        System.out.println(
                "PASS TARGET-02: six faces, occlusion, unchanged depth, GL state/cleanup, HUD and large/negative coordinates");
    }

    private static void faces(BlockTextureArray textures, BlockOutlineRenderer outline, UiRenderer ui, Path output)
            throws Exception {
        BlockPos block = new BlockPos(0, 0, 0);
        try (TerrainRenderer terrain = terrain(textures, block)) {
            long allocated = terrain.memoryStats().allocated();
            int index = 0;
            for (int[] side : new int[][] {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}}) {
                View view = view(block, side[0], side[1], side[2]);
                draw(terrain, view);
                byte[] before = pixels();
                float[] depth = depth();
                int previousProgram = glGetInteger(GL_CURRENT_PROGRAM);
                int markerVao = glCreateVertexArrays();
                try {
                    glBindVertexArray(markerVao);
                    glDisable(GL_DEPTH_TEST);
                    glDepthMask(false);
                    glDepthFunc(GL_GREATER);
                    glEnable(GL_BLEND);
                    glLineWidth(2);
                    outline.render(view.projection(), block, view.x(), view.y(), view.z());
                    check(!glIsEnabled(GL_DEPTH_TEST) && !glGetBoolean(GL_DEPTH_WRITEMASK), "Depth state not restored");
                    check(
                            glGetInteger(GL_DEPTH_FUNC) == GL_GREATER && glIsEnabled(GL_BLEND),
                            "Depth/blend state not restored");
                    check(glGetFloat(GL_LINE_WIDTH) == 2, "Line width not restored");
                    check(glGetInteger(GL_CURRENT_PROGRAM) == previousProgram, "Program not restored");
                    check(glGetInteger(GL_VERTEX_ARRAY_BINDING) == markerVao, "VAO not restored");
                } finally {
                    glBindVertexArray(0);
                    glDeleteVertexArrays(markerVao);
                    glLineWidth(1);
                    glDepthFunc(GL_LESS);
                    glDepthMask(true);
                }
                check(Arrays.equals(depth, depth()), "Outline wrote depth");
                byte[] after = pixels();
                int changed = 0, first = -1;
                for (int pixel = 0; pixel < WIDTH * HEIGHT; pixel++)
                    if (rgb(before, pixel) != rgb(after, pixel)) {
                        check(rgb(after, pixel) == 0, "Outline fragment is not black");
                        changed++;
                        if (first < 0) first = pixel;
                    }
                check(changed > 100, "Missing cube face outline: " + index);
                write(output.resolve("outline-face-" + index++ + ".png"), after);
                var canvas = new UiCanvas();
                canvas.rectangle(first % WIDTH, HEIGHT - 1 - first / WIDTH, 2, 2, 1, 1, 1, 1);
                ui.render(canvas, new UiViewport(WIDTH, HEIGHT, WIDTH, HEIGHT, 1));
                check(rgb(pixels(), first) == 0xffffff, "HUD did not draw over outline");
            }
            check(terrain.memoryStats().allocated() == allocated, "Selection changed terrain GPU allocations");
        }
    }

    private static void occlusion(BlockTextureArray textures, BlockOutlineRenderer outline) {
        BlockPos block = new BlockPos(0, 0, 0);
        try (TerrainRenderer terrain = terrain(textures, block, new BlockPos(0, 0, 2))) {
            View view = view(block, 0, 0, 1);
            draw(terrain, view);
            byte[] before = pixels();
            outline.render(view.projection(), block, view.x(), view.y(), view.z());
            check(Arrays.equals(before, pixels()), "Outline visible through occluding block");
        }
    }

    private static void precision(BlockTextureArray textures, BlockOutlineRenderer outline, Path output)
            throws Exception {
        byte[] reference = null;
        for (BlockPos block : new BlockPos[] {
            new BlockPos(0, 0, 0),
            new BlockPos(-17, -18, -33),
            new BlockPos(1_500_000_000, 1_500_000_000, 1_500_000_000),
            new BlockPos(-1_500_000_000, -1_500_000_000, -1_500_000_000)
        }) {
            try (TerrainRenderer terrain = terrain(textures, block)) {
                View view = view(block, 1, 1, 1);
                draw(terrain, view);
                outline.render(view.projection(), block, view.x(), view.y(), view.z());
                byte[] image = pixels();
                if (reference == null) reference = image;
                else check(Arrays.equals(reference, image), "Camera-relative precision lost at " + block);
                write(output.resolve("outline-position-" + block.x() + ".png"), image);
            }
        }
    }

    private static void cleanup() throws Exception {
        var renderer = new BlockOutlineRenderer();
        int vao = field(renderer, "vao"), vbo = field(renderer, "vbo"), program = field(renderer, "program");
        renderer.close();
        renderer.close();
        check(!glIsVertexArray(vao) && !glIsBuffer(vbo) && !glIsProgram(program), "Outline GPU resources leaked");
        try {
            renderer.render(new Matrix4f(), new BlockPos(0, 0, 0), 0, 0, 0);
            throw new AssertionError("Closed renderer accepted drawing");
        } catch (IllegalStateException expected) {
            check(expected.getMessage().contains("closed"), "Unexpected closed-renderer failure");
        }
    }

    private static int field(BlockOutlineRenderer renderer, String name) throws Exception {
        var field = BlockOutlineRenderer.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.getInt(renderer);
    }

    private static TerrainRenderer terrain(BlockTextureArray textures, BlockPos... blocks) {
        World world = new World();
        for (BlockPos block : blocks) {
            ChunkPos position = Coordinates.worldToChunk(block);
            if (!world.hasChunk(position)) world.putChunk(position, new Chunk());
            world.setBlock(block, BlockId.STONE, 64);
        }
        var terrain = new TerrainRenderer(textures);
        try {
            var mesher = new WaterMesher();
            for (ChunkPos position : world.residentPositions()) {
                var neighbors = world.blockNeighborhood(position);
                terrain.upload(
                        position,
                        mesher.mesh(
                                neighbors.center(),
                                neighbors,
                                position.x(),
                                position.y(),
                                position.z(),
                                ChunkMesh.ALL));
            }
            return terrain;
        } catch (RuntimeException | Error failure) {
            terrain.close();
            throw failure;
        }
    }

    private static View view(BlockPos block, int dx, int dy, int dz) {
        double x = block.x() + .5 + dx * 3.5, y = block.y() + .5 + dy * 3.5, z = block.z() + .5 + dz * 3.5;
        var projection = new Matrix4f()
                .perspective((float) Math.toRadians(60), WIDTH / (float) HEIGHT, .1f, 100)
                .lookAt(
                        new Vector3f(),
                        new Vector3f((float) (block.x() + .5 - x), (float) (block.y() + .5 - y), (float)
                                (block.z() + .5 - z)),
                        dx == 0 && dz == 0 ? new Vector3f(0, 0, 1) : new Vector3f(0, 1, 0));
        return new View(projection, x, y, z);
    }

    private static void draw(TerrainRenderer terrain, View view) {
        glDepthMask(true);
        glDepthFunc(GL_LESS);
        glClearColor(.4f, .6f, .8f, 1);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        terrain.render(view.projection(), view.x(), view.y(), view.z(), 0);
    }

    private static float[] depth() {
        var buffer = BufferUtils.createFloatBuffer(WIDTH * HEIGHT);
        glReadPixels(0, 0, WIDTH, HEIGHT, GL_DEPTH_COMPONENT, GL_FLOAT, buffer);
        float[] result = new float[buffer.remaining()];
        buffer.get(result);
        return result;
    }

    private static byte[] pixels() {
        var buffer = BufferUtils.createByteBuffer(WIDTH * HEIGHT * 4);
        glReadPixels(0, 0, WIDTH, HEIGHT, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
        byte[] result = new byte[buffer.remaining()];
        buffer.get(result);
        return result;
    }

    private static int rgb(byte[] pixels, int pixel) {
        return Byte.toUnsignedInt(pixels[pixel * 4]) << 16
                | Byte.toUnsignedInt(pixels[pixel * 4 + 1]) << 8
                | Byte.toUnsignedInt(pixels[pixel * 4 + 2]);
    }

    private static void write(Path path, byte[] pixels) throws Exception {
        var image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < HEIGHT; y++)
            for (int x = 0; x < WIDTH; x++) image.setRGB(x, HEIGHT - 1 - y, rgb(pixels, y * WIDTH + x));
        ImageIO.write(image, "png", path.toFile());
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private record View(Matrix4f projection, double x, double y, double z) {}
}
