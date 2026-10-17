package com.norbertfila.generation;

import static org.lwjgl.opengl.GL46C.*;

import com.norbertfila.assets.AssetLoader;
import com.norbertfila.i18n.Language;
import com.norbertfila.i18n.Translations;
import com.norbertfila.platform.Window;
import com.norbertfila.player.InventorySnapshot;
import com.norbertfila.render.BlockTextureArray;
import com.norbertfila.render.TerrainRenderer;
import com.norbertfila.render.UiRenderer;
import com.norbertfila.ui.UiCanvas;
import com.norbertfila.ui.UiKey;
import com.norbertfila.ui.UiViewport;
import com.norbertfila.ui.screens.InventoryScreen;
import com.norbertfila.world.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;

public final class BuildingBlocksSmoke {
    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]);
        Files.createDirectories(output);
        var world = new World();
        for (int z = 0; z < 2; z++) for (int x = 0; x < 3; x++) world.putChunk(new ChunkPos(x, 0, z), new Chunk());
        for (int id = 47; id <= 149; id++) {
            int index = id - 47;
            world.setBlock(new BlockPos(index % 11 * 3, 1, index / 11 * 3), (byte) id, 8);
        }
        try (var window = new Window(VegetationSmoke.WIDTH, VegetationSmoke.HEIGHT, "Building blocks");
                var textures = new BlockTextureArray(new AssetLoader(BlockRegistry.DEFAULT).loadBlockTextures());
                var renderer = new TerrainRenderer(textures);
                var ui = new UiRenderer()) {
            VegetationSmoke.upload(renderer, world);
            var eye = new Vector3f(15, 45, 50);
            var vp = new Matrix4f()
                    .ortho(-23, 23, -13, 13, .1f, 100)
                    .lookAt(eye, new Vector3f(15, 1, 13), new Vector3f(0, 1, 0));
            var pixels = VegetationSmoke.draw(renderer, vp, eye);
            for (int id = 47; id <= 149; id++) {
                int index = id - 47;
                var center = vp.transformProject(new Vector3f(index % 11 * 3 + .5f, 1.5f, index / 11 * 3 + .5f));
                int px = Math.round((center.x + 1) * VegetationSmoke.WIDTH / 2);
                int py = Math.round((center.y + 1) * VegetationSmoke.HEIGHT / 2);
                int visible = 0;
                for (int y = py - 3; y <= py + 3; y++)
                    for (int x = px - 3; x <= px + 3; x++) {
                        int offset = (y * VegetationSmoke.WIDTH + x) * 4;
                        if ((pixels[offset] & 255) != 32
                                || (pixels[offset + 1] & 255) != 64
                                || (pixels[offset + 2] & 255) != 96) visible++;
                    }
                if (visible < 3) throw new AssertionError("Missing block " + id);
            }
            VegetationSmoke.write(output.resolve("materials.png"), pixels);
            var viewport = new UiViewport(
                    VegetationSmoke.WIDTH, VegetationSmoke.HEIGHT, VegetationSmoke.WIDTH, VegetationSmoke.HEIGHT);
            for (var language : Language.values()) {
                var screen = new InventoryScreen(true, new Translations(language));
                screen.update(InventorySnapshot.EMPTY, 8, viewport);
                for (int page = 0; page < (InventoryScreen.catalogBlocks().size() + 26) / 27; page++) {
                    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
                    var canvas = new UiCanvas();
                    screen.render(canvas, viewport);
                    ui.render(canvas, viewport);
                    var buffer = BufferUtils.createByteBuffer(VegetationSmoke.WIDTH * VegetationSmoke.HEIGHT * 4);
                    glReadPixels(
                            0, 0, VegetationSmoke.WIDTH, VegetationSmoke.HEIGHT, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
                    pixels = new byte[buffer.remaining()];
                    buffer.get(pixels);
                    VegetationSmoke.write(
                            output.resolve("creative-" + language.name().toLowerCase(java.util.Locale.ROOT) + "-"
                                    + (page + 1) + ".png"),
                            pixels);
                    screen.keyPress(UiKey.PAGE_DOWN);
                }
            }
            if (glGetError() != GL_NO_ERROR) throw new AssertionError("Building blocks OpenGL error");
            System.out.println("PASS 103 building blocks and all Creative pages, GPU=" + glGetString(GL_RENDERER));
        }
    }
}
