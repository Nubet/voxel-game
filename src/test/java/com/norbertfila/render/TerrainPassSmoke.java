package com.norbertfila.render;

import static org.lwjgl.opengl.GL46C.*;

import com.norbertfila.assets.AssetLoader;
import com.norbertfila.meshing.ChunkMesh;
import com.norbertfila.meshing.WaterMesher;
import com.norbertfila.platform.Window;
import com.norbertfila.world.*;
import java.nio.ByteBuffer;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;

public final class TerrainPassSmoke {
    public static void main(String[] args) {
        try (Window window = new Window(128, 128, "Terrain pass regression");
                BlockTextureArray textures =
                        new BlockTextureArray(new AssetLoader(BlockRegistry.DEFAULT).loadBlockTextures());
                TerrainRenderer renderer = new TerrainRenderer(textures)) {
            ice(textures, false);
            ice(textures, true);
            // A flat texture makes the comparison independent of the animated water clock.
            textures.bind(0);
            ByteBuffer white = BufferUtils.createByteBuffer(4);
            white.putInt(-1).flip();
            glClearTexImage(glGetInteger(GL_TEXTURE_BINDING_2D_ARRAY), 0, GL_RGBA, GL_UNSIGNED_BYTE, white);
            World world = new World();
            ChunkPos position = new ChunkPos(0, 0, 0);
            world.putChunk(position, new Chunk());
            world.setBlock(new BlockPos(0, 0, 0), BlockId.STONE, 64);
            world.setBlock(new BlockPos(1, 0, 0), BlockId.WATER, 64);
            world.setBlock(new BlockPos(2, 0, 0), BlockId.ICE, 64);
            var n = world.blockNeighborhood(position);
            renderer.upload(position, new WaterMesher().mesh(n.center(), n, 0, 0, 0, ChunkMesh.ALL));
            Matrix4f vp = new Matrix4f()
                    .perspective((float) Math.toRadians(70), 1, .1f, 3168)
                    .lookAt(0, 0, 0, 0, -.2f, -1, 0, 1, 0);
            glViewport(0, 0, 128, 128);
            glClearColor(.55f, .76f, .96f, 1);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            var all = renderer.render(vp, 1, 1, 4, 0);
            ByteBuffer reference = capture();
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            var opaque = renderer.renderOpaque(vp, 1, 1, 4, 0);
            int water = renderer.renderWater(vp, 1, 1, 4, 0);
            if (all.drawCalls() == 0 || water == 0 || all.drawCalls() != opaque.drawCalls() + water)
                throw new AssertionError("Missing terrain pass");
            if (!reference.equals(capture())) throw new AssertionError("Split passes changed the image");
            if (!glGetBoolean(GL_DEPTH_WRITEMASK) || glIsEnabled(GL_BLEND) || glGetError() != GL_NO_ERROR)
                throw new AssertionError("Leaked terrain state");
            System.out.println("PASS split terrain passes at maximum far distance");
        }
    }

    private static void ice(BlockTextureArray textures, boolean direct) {
        String previous = System.getProperty("voxel.render.directDraw");
        System.setProperty("voxel.render.directDraw", Boolean.toString(direct));
        try (TerrainRenderer renderer = new TerrainRenderer(textures)) {
            World world = new World();
            ChunkPos position = new ChunkPos(0, 0, 0);
            world.putChunk(position, new Chunk());
            world.setBlock(new BlockPos(0, 0, 0), BlockId.ICE, 64);
            var neighborhood = world.blockNeighborhood(position);
            renderer.upload(
                    position, new WaterMesher().mesh(neighborhood.center(), neighborhood, 0, 0, 0, ChunkMesh.ALL));
            Matrix4f vp = new Matrix4f().perspective((float) Math.toRadians(70), 1, .1f, 100);
            glViewport(0, 0, 128, 128);
            glClearColor(.55f, .76f, .96f, 1);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            var opaque = renderer.renderOpaque(vp, .5, .5, 3, 0);
            ByteBuffer background = capture();
            int translucent = renderer.renderWater(vp, .5, .5, 3, 0);
            if (opaque.drawCalls() != 0 || translucent == 0 || background.equals(capture()))
                throw new AssertionError(
                        "Ice must be visible in the translucent pass without any water; direct=" + direct);
            if (!glGetBoolean(GL_DEPTH_WRITEMASK) || glIsEnabled(GL_BLEND) || glGetError() != GL_NO_ERROR)
                throw new AssertionError("Ice rendering leaked GL state");
            System.out.println("PASS actual ice texture without water; direct=" + direct);
        } finally {
            if (previous == null) System.clearProperty("voxel.render.directDraw");
            else System.setProperty("voxel.render.directDraw", previous);
        }
    }

    private static ByteBuffer capture() {
        ByteBuffer pixels = BufferUtils.createByteBuffer(128 * 128 * 4);
        glReadPixels(0, 0, 128, 128, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        return pixels;
    }
}
