package com.norbertfila.generation;

import static org.lwjgl.opengl.GL46C.*;

import com.norbertfila.assets.AssetLoader;
import com.norbertfila.platform.Window;
import com.norbertfila.render.BlockTextureArray;
import com.norbertfila.render.TerrainRenderer;
import com.norbertfila.vegetation.PlantGrowthSystem;
import com.norbertfila.world.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class PlantGrowthSmoke {
    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]);
        Files.createDirectories(output);
        var world = new World();
        var chunk = new Chunk();
        chunk.set(8, 0, 8, BlockId.SAND);
        chunk.set(9, 0, 8, BlockId.WATER);
        world.putChunk(new ChunkPos(0, 0, 0), chunk);
        var growth = new PlantGrowthSystem(world, 64, 42);
        world.setBlockChangeListener(growth::onBlockChanged);
        var edits = new WorldEdit(world, 64);
        edits.setBlock(new BlockPos(8, 1, 8), BlockId.REED);
        growth.update(0, false);
        var eye = new Vector3f(8.5f, 2, 18);
        var vp = new Matrix4f()
                .ortho(-4, 4, -2.25f, 2.25f, .1f, 100)
                .lookAt(eye, new Vector3f(8.5f, 2, 8.5f), new Vector3f(0, 1, 0));
        try (var window = new Window(VegetationSmoke.WIDTH, VegetationSmoke.HEIGHT, "Sugar cane growth");
                var textures = new BlockTextureArray(new AssetLoader(BlockRegistry.DEFAULT).loadBlockTextures());
                var renderer = new TerrainRenderer(textures)) {
            System.out.println("GPU=" + glGetString(GL_RENDERER) + " GL=" + glGetString(GL_VERSION));
            for (int height = 1; height <= 3; height++) {
                capture(renderer, world, vp, eye, height, output.resolve("sugar-cane-height-" + height + ".png"));
                if (height < 3) {
                    var result =
                            growth.update(growth.snapshot().plants().getFirst().nextGrowthTick(), true);
                    check(
                            result.changedBlocks() == 1 && !result.dirtyChunks().isEmpty(),
                            "Growth did not dirty terrain");
                }
            }
            long harvestedAt = growth.snapshot().plants().getFirst().startedAt() + 10000;
            growth.advanceClock(harvestedAt);
            edits.setBlock(new BlockPos(8, 2, 8), BlockId.AIR);
            check(growth.update(harvestedAt, true).changedBlocks() == 0, "Harvest reused old growth time");
            capture(renderer, world, vp, eye, 1, output.resolve("sugar-cane-harvested.png"));
            for (int i = 0; i < 2; i++)
                check(
                        growth.update(growth.snapshot().plants().getFirst().nextGrowthTick(), true)
                                        .changedBlocks()
                                == 1,
                        "Harvested cane did not regrow");
            capture(renderer, world, vp, eye, 3, output.resolve("sugar-cane-regrown.png"));
            check(glGetError() == GL_NO_ERROR, "OpenGL error");
        }
        System.out.println("PASS sugar cane growth, terrain remeshing, cutout pixels, harvest and regrowth");
    }

    private static void capture(
            TerrainRenderer renderer, World world, Matrix4f vp, Vector3f eye, int height, Path output)
            throws Exception {
        VegetationSmoke.upload(renderer, world);
        byte[] pixels = VegetationSmoke.draw(renderer, vp, eye);
        for (int segment = 1; segment <= 3; segment++) {
            var center = vp.transformProject(new Vector3f(8.5f, segment + .5f, 8.5f));
            int px = Math.round((center.x + 1) * VegetationSmoke.WIDTH / 2);
            int py = Math.round((center.y + 1) * VegetationSmoke.HEIGHT / 2);
            int visible = 0;
            for (int y = py - 20; y < py + 20; y++)
                for (int x = px - 20; x < px + 20; x++) {
                    int offset = (y * VegetationSmoke.WIDTH + x) * 4;
                    if (Byte.toUnsignedInt(pixels[offset]) != 32
                            || Byte.toUnsignedInt(pixels[offset + 1]) != 64
                            || Byte.toUnsignedInt(pixels[offset + 2]) != 96) visible++;
                }
            check(
                    segment <= height ? visible > 20 && visible < 1600 : visible == 0,
                    "Unexpected cane pixels at segment " + segment + " height " + height);
        }
        VegetationSmoke.write(output, pixels);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
