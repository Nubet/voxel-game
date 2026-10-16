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

public final class OreSmoke {
    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]);
        Files.createDirectories(output);
        var world = new World();
        world.putChunk(new ChunkPos(0, 0, 0), new Chunk());
        byte[] ores = {BlockId.COAL_ORE, BlockId.IRON_ORE, BlockId.GOLD_ORE, BlockId.DIAMOND_ORE, BlockId.LAPIS_ORE};
        for (int i = 0; i < ores.length; i++) world.setBlock(new BlockPos(2 + i * 2, 2, 4), ores[i], 8);
        try (var window = new Window(VegetationSmoke.WIDTH, VegetationSmoke.HEIGHT, "Ore acceptance");
                var textures = new BlockTextureArray(new AssetLoader(BlockRegistry.DEFAULT).loadBlockTextures());
                var renderer = new TerrainRenderer(textures)) {
            VegetationSmoke.upload(renderer, world);
            var eye = new Vector3f(6.5f, 7, 14);
            var vp = new Matrix4f()
                    .perspective(
                            (float) Math.toRadians(55),
                            VegetationSmoke.WIDTH / (float) VegetationSmoke.HEIGHT,
                            .1f,
                            100)
                    .lookAt(eye, new Vector3f(6.5f, 2, 4), new Vector3f(0, 1, 0));
            var pixels = VegetationSmoke.draw(renderer, vp, eye);
            int visible = 0;
            for (int i = 0; i < pixels.length; i += 4)
                if (Byte.toUnsignedInt(pixels[i]) != 32
                        || Byte.toUnsignedInt(pixels[i + 1]) != 64
                        || Byte.toUnsignedInt(pixels[i + 2]) != 96) visible++;
            if (visible < 1000 || glGetError() != GL_NO_ERROR) throw new AssertionError("Ore rendering failed");
            VegetationSmoke.write(output.resolve("ores.png"), pixels);
            System.out.println("PASS five ore blocks GPU=" + glGetString(GL_RENDERER));
        }
    }
}
