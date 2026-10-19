package com.norbertfila.generation;

import static org.lwjgl.opengl.GL46C.*;

import com.norbertfila.assets.AssetLoader;
import com.norbertfila.core.Camera;
import com.norbertfila.fluid.FluidScheduler;
import com.norbertfila.fluid.FluidSimulator;
import com.norbertfila.platform.Window;
import com.norbertfila.render.BlockTextureArray;
import com.norbertfila.render.TerrainRenderer;
import com.norbertfila.world.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.joml.Vector3f;

public final class ClassicWaterSmoke {
    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]);
        Files.createDirectories(output);
        var terrain = new TerrainGenerator(123, TerrainConfig.defaults());
        var world = new World();
        long start = System.nanoTime();
        for (int z = 298; z <= 307; z++)
            for (int x = -116; x <= -104; x++)
                for (int y = 0; y <= 10; y++) {
                    var position = new ChunkPos(x, y, z);
                    world.putChunk(position, terrain.generate(position).toChunk());
                }
        System.out.printf(
                "seed=123 chunks=%d generationMs=%.1f%n",
                world.residentPositions().size(), (System.nanoTime() - start) / 1e6);
        checkSourceWalls(world, terrain);
        try (var window = new Window(VegetationSmoke.WIDTH, VegetationSmoke.HEIGHT, "Classic water regression");
                var textures = new BlockTextureArray(new AssetLoader(BlockRegistry.DEFAULT).loadBlockTextures());
                var renderer = new TerrainRenderer(textures)) {
            VegetationSmoke.upload(renderer, world);
            var camera = new Camera();
            camera.setPosition(-1725.23, 88.53, 4891.5);
            camera.restoreLook(1314.3f, -51.9f);
            VegetationSmoke.write(
                    output.resolve("reported-camera.png"),
                    VegetationSmoke.draw(
                            renderer,
                            camera.viewProjection(VegetationSmoke.WIDTH, VegetationSmoke.HEIGHT),
                            new Vector3f(camera.position())));
            var fluids = new FluidSimulator(world, 64, FluidScheduler.DEFAULT_MAX_UPDATES_PER_TICK, true);
            fluids.setSimulationArea(new ChunkPos(-109, 4, 303), 4, 0);
            for (var position : world.residentPositions()) fluids.onChunkChanged(position, 0);
            int changed = 0;
            for (int tick = 1; tick <= 240; tick++) changed += fluids.tick(tick).changedBlocks();
            System.out.printf("simulatedTicks=240 changed=%d pending=%d%n", changed, fluids.pendingUpdates());
            checkSourceWalls(world, terrain);
            VegetationSmoke.upload(renderer, world);
            VegetationSmoke.write(
                    output.resolve("reported-camera-simulated.png"),
                    VegetationSmoke.draw(
                            renderer,
                            camera.viewProjection(VegetationSmoke.WIDTH, VegetationSmoke.HEIGHT),
                            new Vector3f(camera.position())));
            if (glGetError() != GL_NO_ERROR) throw new AssertionError("Water regression OpenGL error");
            System.out.println("PASS seed-123 generation/simulation/render GPU=" + glGetString(GL_RENDERER));
        }
    }

    private static void checkSourceWalls(World world, TerrainGenerator terrain) {
        int longest = 0;
        BlockPos worst = null;
        for (int z = 4770; z < 4926; z++)
            for (int x = -1854; x < -1666; x++) {
                int run = 0;
                for (int y = 1; y < 160; y++) {
                    var p = new BlockPos(x, y, z);
                    boolean exposed = world.getBlock(p).orElse(BlockId.AIR) == BlockId.WATER
                            && world.getWaterState(p).orElse(WaterState.EMPTY) == WaterState.SOURCE
                            && (world.getBlock(new BlockPos(x - 1, y, z)).orElse(BlockId.STONE) == BlockId.AIR
                                    || world.getBlock(new BlockPos(x + 1, y, z)).orElse(BlockId.STONE) == BlockId.AIR
                                    || world.getBlock(new BlockPos(x, y, z - 1)).orElse(BlockId.STONE) == BlockId.AIR
                                    || world.getBlock(new BlockPos(x, y, z + 1)).orElse(BlockId.STONE) == BlockId.AIR);
                    run = exposed ? run + 1 : 0;
                    if (run > longest) {
                        longest = run;
                        worst = p;
                    }
                }
            }
        System.out.printf(
                "longestExposedSourceWall=%d top=%s summary=%s%n",
                longest, worst, worst == null ? null : terrain.sample(worst.x(), worst.z()));
        if (longest > 5) throw new AssertionError("Unsupported source-water wall: " + longest + " at " + worst);
    }
}
