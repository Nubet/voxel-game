package com.norbertfila.generation;

import static org.lwjgl.opengl.GL46C.*;

import com.norbertfila.assets.AssetLoader;
import com.norbertfila.meshing.*;
import com.norbertfila.platform.Window;
import com.norbertfila.render.*;
import com.norbertfila.world.*;
import java.nio.file.*;
import java.util.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class JungleSmoke {
    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]);
        Files.createDirectories(output);
        try (var window = new Window(VegetationSmoke.WIDTH, VegetationSmoke.HEIGHT, "Jungle acceptance");
                var textures = new BlockTextureArray(new AssetLoader(BlockRegistry.DEFAULT).loadBlockTextures())) {
            scene(new JungleScene(false), textures, output, "flat");
            scene(new JungleScene(true), textures, output, "slope");
            if (glGetError() != GL_NO_ERROR) throw new AssertionError("Jungle OpenGL error");
        }
    }

    static void scene(TerrainSource source, BlockTextureArray textures, Path output, String name) throws Exception {
        var world = new World();
        var times = new long[3];
        for (int round = -1; round < times.length; round++) {
            long start = System.nanoTime();
            for (int z = -2; z <= 2; z++)
                for (int x = -2; x <= 2; x++)
                    // Include all possible crowns, not only sections containing ground.
                    for (int y = 0; y < 7; y++) {
                        var p = new ChunkPos(x, y, z);
                        world.putChunk(p, source.generate(p).toChunk());
                    }
            if (round >= 0) times[round] = System.nanoTime() - start;
        }
        Arrays.sort(times);
        var mesher = new WaterMesher();
        long start = System.nanoTime(), quads = 0, bytes = 0;
        for (var p : world.residentPositions()) {
            var n = world.blockNeighborhood(p);
            var mesh = mesher.mesh(n.center(), n, p.x(), p.y(), p.z(), ChunkMesh.ALL);
            quads += mesh.quadCount();
            bytes += mesh.bytes();
        }
        String report = "%s seed=%d sections=%d generationMedianMs=%.2f meshMs=%.2f quads=%d meshBytes=%d%n"
                .formatted(
                        name,
                        source.seed(),
                        world.residentCount(),
                        times[1] / 1e6,
                        (System.nanoTime() - start) / 1e6,
                        quads,
                        bytes);
        System.out.print(report);
        Files.writeString(output.resolve(name + "-metrics.txt"), report);
        try (var renderer = new TerrainRenderer(textures)) {
            VegetationSmoke.upload(renderer, world);
            for (String view : List.of("ground", "canopy")) {
                int height = source.surfaceHeight(0, 0);
                var eye =
                        view.equals("ground") ? new Vector3f(0, height + 2.7f, 25) : new Vector3f(65, height + 45, 65);
                var target = new Vector3f(0, height + (view.equals("ground") ? 3 : 8), 0);
                var vp = new Matrix4f()
                        .perspective((float) Math.toRadians(65), 960f / 540, .1f, 500)
                        .lookAt(eye, target, new Vector3f(0, 1, 0));
                VegetationSmoke.write(
                        output.resolve(name + "-" + view + ".png"), VegetationSmoke.draw(renderer, vp, eye));
            }
        }
    }
}
