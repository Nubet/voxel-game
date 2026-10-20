package com.norbertfila.render;

import static org.lwjgl.opengl.GL46C.*;

import com.norbertfila.assets.AssetLoader;
import com.norbertfila.light.BlockLightState;
import com.norbertfila.light.BlockLighting;
import com.norbertfila.meshing.ChunkMesh;
import com.norbertfila.meshing.WaterMesher;
import com.norbertfila.platform.Window;
import com.norbertfila.world.*;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;

/** A torch on a stone floor at night next to a wall; the CPU field must not pass light through the wall. */
public final class BlockLightSmoke {
    private static final int SIZE = 512;
    private static final Path OUTPUT = Path.of("build/block-light-smoke");

    public static void main(String[] args) throws Exception {
        Files.createDirectories(OUTPUT);
        try (var window = new Window(SIZE, SIZE, "Block light regression");
                var textures = new BlockTextureArray(new AssetLoader(BlockRegistry.DEFAULT).loadBlockTextures());
                var terrain = new TerrainRenderer(textures)) {
            glViewport(0, 0, SIZE, SIZE);
            var world = new World();
            var position = new ChunkPos(0, 0, 0);
            world.putChunk(position, new Chunk());
            for (int x = 0; x < 16; x++)
                for (int z = 0; z < 16; z++) world.setBlock(new BlockPos(x, 0, z), BlockId.STONE, 64);
            for (int y = 1; y < 15; y++)
                for (int z = 0; z < 16; z++) world.setBlock(new BlockPos(12, y, z), BlockId.STONE, 64);
            world.setBlock(new BlockPos(8, 1, 8), BlockId.TORCH, 64);
            var neighborhood = world.blockNeighborhood(position);
            terrain.upload(
                    position, new WaterMesher().mesh(neighborhood.center(), neighborhood, 0, 0, 0, ChunkMesh.ALL));
            var light = new BlockLighting(world).update(new BlockPos(8, 1, 8), new BlockPos(8, 2, 8), 0);
            check(light.level(8, 1, 8) == 14 && light.level(11, 1, 8) == 11, "Torch light did not spread");
            check(light.level(13, 1, 8) == 0, "Torch light passed through the wall");
            var view = new Matrix4f()
                    .perspective((float) Math.toRadians(70), 1, .1f, 64)
                    .lookAt(0, 0, 0, 0, -1, -.001f, 0, 0, -1);
            terrain.environment(SkyModel.at(54000));
            terrain.blockLight(BlockLightState.NONE);
            clear();
            terrain.render(view, 8.5, 14, 8.5, 0);
            var dark = capture();
            write(dark, "night");
            terrain.blockLight(light);
            clear();
            terrain.render(view, 8.5, 14, 8.5, 0);
            var lit = capture();
            write(lit, "night-torch");
            check(
                    brightness(lit, SIZE / 2, SIZE / 2 - 30) > 3 * brightness(dark, SIZE / 2, SIZE / 2 - 30),
                    "Torch did not light the floor");
            int litSide = brightness(lit, 160, SIZE / 2);
            check(litSide > 2 * brightness(dark, 160, SIZE / 2), "Torch did not reach three blocks away");
            check(glGetError() == GL_NO_ERROR, "OpenGL error");
        }
    }

    private static int brightness(ByteBuffer pixels, int x, int y) {
        int i = (y * SIZE + x) * 4;
        return Byte.toUnsignedInt(pixels.get(i))
                + Byte.toUnsignedInt(pixels.get(i + 1))
                + Byte.toUnsignedInt(pixels.get(i + 2));
    }

    private static void clear() {
        glClearColor(0, 0, 0, 1);
        glDepthMask(true);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
    }

    private static ByteBuffer capture() {
        var pixels = BufferUtils.createByteBuffer(SIZE * SIZE * 4);
        glReadPixels(0, 0, SIZE, SIZE, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        return pixels;
    }

    private static void write(ByteBuffer pixels, String name) throws Exception {
        var image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < SIZE; y++)
            for (int x = 0; x < SIZE; x++) {
                int offset = ((SIZE - 1 - y) * SIZE + x) * 4;
                image.setRGB(
                        x,
                        y,
                        Byte.toUnsignedInt(pixels.get(offset)) << 16
                                | Byte.toUnsignedInt(pixels.get(offset + 1)) << 8
                                | Byte.toUnsignedInt(pixels.get(offset + 2)));
            }
        ImageIO.write(image, "png", OUTPUT.resolve(name + ".png").toFile());
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
