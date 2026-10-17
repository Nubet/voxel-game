package com.norbertfila.render;

import static org.lwjgl.opengl.GL46C.*;

import com.norbertfila.assets.AssetLoader;
import com.norbertfila.core.Hotbar;
import com.norbertfila.interaction.BlockBreakEvent;
import com.norbertfila.interaction.BlockBreaking;
import com.norbertfila.items.ItemRenderState;
import com.norbertfila.items.ItemSnapshot;
import com.norbertfila.meshing.ChunkMesh;
import com.norbertfila.meshing.WaterMesher;
import com.norbertfila.platform.Window;
import com.norbertfila.ui.Hud;
import com.norbertfila.ui.UiCanvas;
import com.norbertfila.ui.UiViewport;
import com.norbertfila.world.*;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
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
                BlockEffectsRenderer effects = new BlockEffectsRenderer(textures);
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
                faces(textures, outline, effects, ui, output);
                occlusion(textures, outline, effects);
                precision(textures, outline, effects, output);
                particles(effects, output);
                items(textures, output);
                itemSideOrientation(output);
                effectCleanup(textures);
                cleanup();
                check(glGetError() == GL_NO_ERROR, "Block feedback GL error");
            } finally {
                glBindFramebuffer(GL_FRAMEBUFFER, 0);
                glDeleteRenderbuffers(depth);
                glDeleteTextures(color);
                glDeleteFramebuffers(framebuffer);
            }
        }
        System.out.println(
                "PASS block feedback: outline/cracks on six faces, progressive damage, particles/expiry, occlusion, depth/state/cleanup, HUD and large coordinates");
    }

    private static void faces(
            BlockTextureArray textures,
            BlockOutlineRenderer outline,
            BlockEffectsRenderer effects,
            UiRenderer ui,
            Path output)
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
                int previousChanged = 0;
                for (double fraction : new double[] {.05, .5, .95}) {
                    draw(terrain, view);
                    var progress = new BlockBreaking.Progress(block, BlockId.STONE, fraction);
                    effects.render(
                            view.projection(), Optional.of(progress), List.of(), 0, view.x(), view.y(), view.z());
                    check(Arrays.equals(depth, depth()), "Cracks wrote depth");
                    byte[] damaged = pixels();
                    int damagedPixels = differences(before, damaged);
                    check(
                            damagedPixels > previousChanged,
                            "Cracks did not grow on face " + index + " stage " + fraction);
                    previousChanged = damagedPixels;
                    canvas.clear();
                    var viewport = new UiViewport(WIDTH, HEIGHT, WIDTH, HEIGHT, 2);
                    var hud = new Hud();
                    hud.render(canvas, new Hotbar(), viewport);
                    ui.render(canvas, viewport);
                    write(output.resolve("mining-face-" + index + "-" + fraction + ".png"), pixels());
                }
            }
            check(terrain.memoryStats().allocated() == allocated, "Selection changed terrain GPU allocations");
        }
    }

    private static void occlusion(
            BlockTextureArray textures, BlockOutlineRenderer outline, BlockEffectsRenderer effects) {
        BlockPos block = new BlockPos(0, 0, 0);
        try (TerrainRenderer terrain = terrain(textures, block, new BlockPos(0, 0, 2))) {
            View view = view(block, 0, 0, 1);
            draw(terrain, view);
            byte[] before = pixels();
            outline.render(view.projection(), block, view.x(), view.y(), view.z());
            check(Arrays.equals(before, pixels()), "Outline visible through occluding block");
            effects.render(
                    view.projection(),
                    Optional.of(new BlockBreaking.Progress(block, BlockId.STONE, .95)),
                    List.of(new BlockBreakEvent(0, block, BlockId.STONE)),
                    .15,
                    view.x(),
                    view.y(),
                    view.z());
            check(Arrays.equals(before, pixels()), "Block effects visible through occluding block");
        }
    }

    private static void precision(
            BlockTextureArray textures, BlockOutlineRenderer outline, BlockEffectsRenderer effects, Path output)
            throws Exception {
        byte[] reference = null;
        for (BlockPos block : new BlockPos[] {
            new BlockPos(0, 0, 0),
            new BlockPos(-17, -18, -33),
            new BlockPos(1_500_000_000, 128, 1_500_000_000),
            new BlockPos(-1_500_000_000, -32, -1_500_000_000)
        }) {
            try (TerrainRenderer terrain = terrain(textures, block)) {
                View view = view(block, 1, 1, 1);
                draw(terrain, view);
                outline.render(view.projection(), block, view.x(), view.y(), view.z());
                effects.render(
                        view.projection(),
                        Optional.of(new BlockBreaking.Progress(block, BlockId.STONE, .75)),
                        List.of(),
                        0,
                        view.x(),
                        view.y(),
                        view.z());
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

    private static void particles(BlockEffectsRenderer effects, Path output) throws Exception {
        glUseProgram(0);
        glBindVertexArray(0);
        var block = new BlockPos(0, 0, 0);
        var view = view(block, 1, 1, 1);
        var events = List.of(new BlockBreakEvent(0, block, BlockId.DIRT));
        for (double seconds : new double[] {0, .2, .5, .7}) {
            glDepthMask(true);
            glClearColor(.4f, .6f, .8f, 1);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            byte[] before = pixels();
            float[] beforeDepth = depth();
            glDisable(GL_DEPTH_TEST);
            glDepthMask(false);
            glDepthFunc(GL_GREATER);
            glEnable(GL_BLEND);
            glEnable(GL_CULL_FACE);
            glBlendFuncSeparate(GL_ONE, GL_ZERO, GL_ZERO, GL_ONE);
            glActiveTexture(GL_TEXTURE3);
            int program = glGetInteger(GL_CURRENT_PROGRAM), vao = glGetInteger(GL_VERTEX_ARRAY_BINDING);
            effects.render(view.projection(), Optional.empty(), events, seconds, view.x(), view.y(), view.z());
            check(
                    !glIsEnabled(GL_DEPTH_TEST) && !glGetBoolean(GL_DEPTH_WRITEMASK),
                    "Effects did not restore depth state");
            check(glGetInteger(GL_DEPTH_FUNC) == GL_GREATER, "Effects did not restore depth function");
            check(glIsEnabled(GL_BLEND) && glIsEnabled(GL_CULL_FACE), "Effects did not restore blend/cull state");
            check(
                    glGetInteger(GL_BLEND_SRC_RGB) == GL_ONE
                            && glGetInteger(GL_BLEND_DST_RGB) == GL_ZERO
                            && glGetInteger(GL_BLEND_SRC_ALPHA) == GL_ZERO
                            && glGetInteger(GL_BLEND_DST_ALPHA) == GL_ONE,
                    "Effects did not restore blend function");
            check(glGetInteger(GL_ACTIVE_TEXTURE) == GL_TEXTURE3, "Effects did not restore active texture");
            check(
                    glGetInteger(GL_CURRENT_PROGRAM) == program && glGetInteger(GL_VERTEX_ARRAY_BINDING) == vao,
                    "Effects did not restore program/VAO");
            check(Arrays.equals(beforeDepth, depth()), "Particles wrote depth");
            int changed = differences(before, pixels());
            check(seconds >= .65 ? changed == 0 : changed > 20, "Missing or expired particles at " + seconds);
            write(output.resolve("debris-" + seconds + ".png"), pixels());
            glEnable(GL_DEPTH_TEST);
            glDepthMask(true);
            glDepthFunc(GL_LESS);
            glDisable(GL_BLEND);
            glActiveTexture(GL_TEXTURE0);
        }
    }

    private static void effectCleanup(BlockTextureArray textures) throws Exception {
        var effects = new BlockEffectsRenderer(textures);
        int vao = field(effects, "vao"), vbo = field(effects, "vbo");
        int cracks = field(effects, "cracks"), debris = field(effects, "debris");
        effects.close();
        effects.close();
        check(
                !glIsVertexArray(vao) && !glIsBuffer(vbo) && !glIsProgram(cracks) && !glIsProgram(debris),
                "Effect GPU resources leaked");
        textures.bind(0);
        try {
            effects.render(new Matrix4f(), Optional.empty(), List.of(), 0, 0, 0, 0);
            throw new AssertionError("Closed effects renderer accepted drawing");
        } catch (IllegalStateException expected) {
            check(expected.getMessage().contains("closed"), "Unexpected closed-renderer failure");
        }
    }

    private static void items(BlockTextureArray textures, Path output) throws Exception {
        glUseProgram(0);
        glBindVertexArray(0);
        var renderer = new ItemEntityRenderer(textures);
        int program = field(renderer, "program"), vao = field(renderer, "vao");
        try {
            byte[] reference = null;
            for (int origin : new int[] {0, 30_000_000}) {
                var view = view(new BlockPos(origin, 0, origin), 0, 0, 1);
                for (int id : new int[] {BlockId.COBBLE, BlockId.POPPY, BlockId.SNOW_LAYER, BlockId.PINE_LOG}) {
                    glDepthMask(true);
                    glClearColor(.4f, .6f, .8f, 1);
                    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
                    byte[] before = pixels();
                    var item = new ItemSnapshot(1, id, 7, origin + .5, .5, origin + .5, 0, 0, 0, 0, 60, true);
                    var state = new ItemRenderState(item, item.x(), item.y(), item.z(), false);
                    glDisable(GL_DEPTH_TEST);
                    glDepthMask(false);
                    glDepthFunc(GL_GREATER);
                    glEnable(GL_CULL_FACE);
                    glDisable(GL_BLEND);
                    glActiveTexture(GL_TEXTURE3);
                    check(
                            renderer.render(view.projection(), List.of(state), 0, view.x(), view.y(), view.z()) == 1,
                            "Item was culled");
                    check(differences(before, pixels()) > 20, "Missing item geometry/texture");
                    check(
                            !glIsEnabled(GL_DEPTH_TEST)
                                    && !glGetBoolean(GL_DEPTH_WRITEMASK)
                                    && glGetInteger(GL_DEPTH_FUNC) == GL_GREATER
                                    && glIsEnabled(GL_CULL_FACE)
                                    && !glIsEnabled(GL_BLEND)
                                    && glGetInteger(GL_ACTIVE_TEXTURE) == GL_TEXTURE3
                                    && glGetInteger(GL_CURRENT_PROGRAM) == 0
                                    && glGetInteger(GL_VERTEX_ARRAY_BINDING) == 0,
                            "Item renderer leaked GL state");
                    if (id == BlockId.COBBLE) {
                        if (reference == null) reference = pixels();
                        else check(Arrays.equals(reference, pixels()), "Item lost camera-relative precision");
                    }
                    write(output.resolve("item-" + id + "-" + origin + ".png"), pixels());
                    check(
                            renderer.render(view.projection(), List.of(state), 0, view.x() + 100, view.y(), view.z())
                                    == 0,
                            "Item range culling failed");
                    check(
                            renderer.render(view.projection(), List.of(state), 0, view.x() + 10, view.y(), view.z())
                                    == 0,
                            "Item frustum culling failed");
                    glDepthMask(true);
                    glDepthFunc(GL_LESS);
                    glEnable(GL_DEPTH_TEST);
                    glActiveTexture(GL_TEXTURE0);
                }
            }
            var view = view(new BlockPos(0, 0, 0), 0, 0, 1);
            try (var terrain = terrain(textures, new BlockPos(0, 0, 1))) {
                draw(terrain, view);
                byte[] before = pixels();
                var item = new ItemSnapshot(1, BlockId.COBBLE, 1, .5, .5, .5, 0, 0, 0, 0, 0, true);
                renderer.render(
                        view.projection(),
                        List.of(new ItemRenderState(item, .5, .5, .5, false)),
                        0,
                        view.x(),
                        view.y(),
                        view.z());
                check(Arrays.equals(before, pixels()), "Item shows through terrain");
            }
            glUseProgram(0);
            glBindVertexArray(0);
        } finally {
            renderer.close();
            renderer.close();
        }
        check(!glIsProgram(program) && !glIsVertexArray(vao), "Item renderer leaked resources");
        check(glGetError() == GL_NO_ERROR, "Item renderer GL error");
        System.out.println(
                "PASS dropped items: cube/cross textures, culling, occlusion, GL state/cleanup and large coordinates");
    }

    private static void itemSideOrientation(Path output) throws Exception {
        try (var textures = new BlockTextureArray(new AssetLoader(BlockRegistry.DEFAULT).loadBlockTextures());
                var renderer = new ItemEntityRenderer(textures)) {
            textures.bind(0);
            var pattern = BufferUtils.createByteBuffer(16 * 16 * 4);
            for (int y = 0; y < 16; y++)
                for (int x = 0; x < 16; x++)
                    pattern.put((byte) (y < 8 ? 255 : 0))
                            .put((byte) (y < 8 ? 0 : 255))
                            .put((byte) 0)
                            .put((byte) 255);
            pattern.flip();
            glTexSubImage3D(
                    GL_TEXTURE_2D_ARRAY,
                    0,
                    0,
                    0,
                    BlockRegistry.PODZOL_SIDE_TEXTURE,
                    16,
                    16,
                    1,
                    GL_RGBA,
                    GL_UNSIGNED_BYTE,
                    pattern);
            var projection = new Matrix4f().perspective((float) Math.toRadians(60), WIDTH / (float) HEIGHT, .1f, 100);
            for (int age : new int[] {0, 63, 126, 189}) {
                glDepthMask(true);
                glClearColor(.4f, .6f, .8f, 1);
                glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
                var item = new ItemSnapshot(1, BlockId.PODZOL, 1, .5, .5, .5, 0, 0, 0, 0, age, true);
                double bob = .12 + Math.sin(age / 60d * 3 + .37) * .035;
                check(
                        renderer.render(
                                        projection,
                                        List.of(new ItemRenderState(item, .5, .5, .5, false)),
                                        0,
                                        .5,
                                        .5 + bob,
                                        1.5)
                                == 1,
                        "Orientation fixture was culled");
                byte[] image = pixels();
                int top = rgb(image, (HEIGHT / 2 + 12) * WIDTH + WIDTH / 2);
                int bottom = rgb(image, (HEIGHT / 2 - 12) * WIDTH + WIDTH / 2);
                check(
                        (top >> 8 & 255) > 100 && (top >> 16 & 255) == 0,
                        "Dropped block side top is vertically inverted at age " + age);
                check(
                        (bottom >> 16 & 255) > 100 && (bottom >> 8 & 255) == 0,
                        "Dropped block side bottom is vertically inverted at age " + age);
                write(output.resolve("item-side-orientation-" + age + ".png"), image);
            }
            check(glGetError() == GL_NO_ERROR, "Item side orientation GL error");
            System.out.println("PASS dropped block side UV orientation through four quarter turns");
        }
    }

    private static int differences(byte[] first, byte[] second) {
        int count = 0;
        for (int pixel = 0; pixel < WIDTH * HEIGHT; pixel++) if (rgb(first, pixel) != rgb(second, pixel)) count++;
        return count;
    }

    private static int field(Object renderer, String name) throws Exception {
        var field = renderer.getClass().getDeclaredField(name);
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
