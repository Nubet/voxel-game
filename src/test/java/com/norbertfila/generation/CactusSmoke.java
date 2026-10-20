package com.norbertfila.generation;

import static org.lwjgl.opengl.GL46C.*;

import com.norbertfila.assets.AssetLoader;
import com.norbertfila.platform.Window;
import com.norbertfila.render.BlockTextureArray;
import com.norbertfila.render.TerrainRenderer;
import com.norbertfila.world.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class CactusSmoke {
    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]);
        Files.createDirectories(output);
        var world = new World();
        for (int cy = 0; cy <= 1; cy++) world.putChunk(new ChunkPos(0, cy, 0), new Chunk());
        for (int z = 2; z <= 13; z++)
            for (int x = 2; x <= 13; x++) world.setBlock(new BlockPos(x, 13, z), BlockId.SAND, 64);
        for (int y = 14; y <= 16; y++) world.setBlock(new BlockPos(7, y, 7), BlockId.CACTUS, 64);
        world.setBlock(new BlockPos(4, 14, 7), BlockId.CACTUS, 64);
        try (var window = new Window(VegetationSmoke.WIDTH, VegetationSmoke.HEIGHT, "Cactus regression");
                var textures = new BlockTextureArray(new AssetLoader(BlockRegistry.DEFAULT).loadBlockTextures());
                var renderer = new TerrainRenderer(textures)) {
            VegetationSmoke.upload(renderer, world);
            for (int sx : new int[] {-1, 1})
                for (int sz : new int[] {-1, 1}) {
                    var eye = new Vector3f(7.5f + sx * 8, 17.5f, 7.5f + sz * 8);
                    var vp = new Matrix4f()
                            .perspective(
                                    (float) Math.toRadians(45),
                                    VegetationSmoke.WIDTH / (float) VegetationSmoke.HEIGHT,
                                    .1f,
                                    100)
                            .lookAt(eye, new Vector3f(7.5f, 15.5f, 7.5f), new Vector3f(0, 1, 0));
                    byte[] pixels = VegetationSmoke.draw(renderer, vp, eye);
                    VegetationSmoke.write(output.resolve("cactus-" + sx + "-" + sz + ".png"), pixels);
                    for (int i = 0; i < 24; i++) {
                        var point =
                                vp.transformProject(new Vector3f(7.5f + sx * .40f, 14.2f + i * .1f, 7.5f + sz * .38f));
                        int x = Math.round((point.x + 1) * VegetationSmoke.WIDTH / 2);
                        int y = Math.round((point.y + 1) * VegetationSmoke.HEIGHT / 2);
                        int offset = (y * VegetationSmoke.WIDTH + x) * 4;
                        int r = Byte.toUnsignedInt(pixels[offset]),
                                g = Byte.toUnsignedInt(pixels[offset + 1]),
                                b = Byte.toUnsignedInt(pixels[offset + 2]);
                        if (g <= r || g <= b)
                            throw new AssertionError("Open cactus corner: " + sx + "," + sz + " sample=" + i);
                    }
                }
            if (glGetError() != GL_NO_ERROR) throw new AssertionError("Cactus OpenGL error");
            System.out.println(
                    "PASS cactus corners from four sides and stacked chunk boundary GPU=" + glGetString(GL_RENDERER));
        }
    }
}
