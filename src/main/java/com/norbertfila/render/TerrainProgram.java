package com.norbertfila.render;

import static com.norbertfila.render.ShaderPrograms.uniform;
import static org.lwjgl.opengl.GL46C.*;

import com.norbertfila.light.BlockLightVolume;
import com.norbertfila.world.RenderLayer;
import java.nio.FloatBuffer;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

/** One linked terrain program; solid and water programs share the fragment shader and uniform names. */
final class TerrainProgram implements AutoCloseable {
    private final int id;
    private final int viewProjection;
    private final int cameraBlock;
    private final int cameraFraction;
    private final int renderLayer;
    private final int waterTime;
    private final int waterFog;
    private final int distanceFogRange;
    private final int distanceFogColor;
    private final int daylight;
    private float light = 1;
    private final int coverageOrigin;
    private final int coverageSize;
    private final int placedLightOrigin, placedLightSize, heldLightOrigin, heldLightSize;

    TerrainProgram(String vertexShader) {
        int linked = ShaderPrograms.create(vertexShader, "/shaders/terrain.frag");
        try {
            viewProjection = uniform(linked, "uVP");
            cameraBlock = uniform(linked, "uCameraBlock");
            cameraFraction = uniform(linked, "uCameraFraction");
            renderLayer = uniform(linked, "uRenderLayer");
            // Solid and far shaders have zero flow, so drivers can optimize away the water clock.
            waterTime = glGetUniformLocation(linked, "uWaterTime");
            waterFog = uniform(linked, "uWaterFog");
            distanceFogRange = uniform(linked, "uDistanceFogRange");
            distanceFogColor = uniform(linked, "uDistanceFogColor");
            daylight = uniform(linked, "uDaylight");
            coverageOrigin = uniform(linked, "uCoverageOrigin");
            coverageSize = uniform(linked, "uCoverageSize");
            placedLightOrigin = uniform(linked, "uPlacedLightOrigin");
            placedLightSize = uniform(linked, "uPlacedLightSize");
            heldLightOrigin = uniform(linked, "uHeldLightOrigin");
            heldLightSize = uniform(linked, "uHeldLightSize");
            int sampler = uniform(linked, "uBlockTextures");
            int fogRange = uniform(linked, "uWaterFogRange");
            glUseProgram(linked);
            glUniform1i(sampler, 0);
            // Every program sets these, even far terrain that never samples them, because two sampler types left on
            // the same default unit make draws fail.
            glUniform1i(uniform(linked, "uPlacedLight"), BlockLightTextures.PLACED_UNIT);
            glUniform1i(uniform(linked, "uHeldLight"), BlockLightTextures.HELD_UNIT);
            glUniform2f(fogRange, UnderwaterVisibility.CLEAR_DISTANCE, UnderwaterVisibility.FOG_DENSITY);
            glUniform3f(
                    distanceFogColor,
                    UnderwaterVisibility.SKY_RED,
                    UnderwaterVisibility.SKY_GREEN,
                    UnderwaterVisibility.SKY_BLUE);
            id = linked;
        } catch (RuntimeException | Error failure) {
            glDeleteProgram(linked);
            throw failure;
        }
    }

    void use(Matrix4f viewProjectionMatrix, float underwater, float time, double x, double y, double z) {
        glUseProgram(id);
        // Integer block plus fraction keeps chunk origins exact in the shader far from the world origin.
        double blockX = Math.floor(x), blockY = Math.floor(y), blockZ = Math.floor(z);
        glUniform3i(cameraBlock, (int) blockX, (int) blockY, (int) blockZ);
        glUniform3f(cameraFraction, (float) (x - blockX), (float) (y - blockY), (float) (z - blockZ));
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer matrix = stack.mallocFloat(16);
            viewProjectionMatrix.get(matrix);
            glUniformMatrix4fv(viewProjection, false, matrix);
        }
        glUniform1f(waterTime, time);
        glUniform4f(
                waterFog,
                UnderwaterVisibility.FOG_RED * light,
                UnderwaterVisibility.FOG_GREEN * light,
                UnderwaterVisibility.FOG_BLUE * light,
                underwater);
        glUniform1i(coverageSize, 0);
    }

    void blockLight(BlockLightTextures textures, double cameraX, double cameraY, double cameraZ) {
        volume(placedLightOrigin, placedLightSize, textures.placed(), cameraX, cameraY, cameraZ);
        volume(heldLightOrigin, heldLightSize, textures.held(), cameraX, cameraY, cameraZ);
    }

    private static void volume(
            int origin, int size, BlockLightVolume volume, double cameraX, double cameraY, double cameraZ) {
        glUniform3f(origin, (float) (volume.originX() - cameraX), (float) (volume.originY() - cameraY), (float)
                (volume.originZ() - cameraZ));
        glUniform3i(size, volume.sizeX(), volume.sizeY(), volume.sizeZ());
    }

    void renderLayer(RenderLayer layer) {
        glUniform1i(renderLayer, layer.ordinal());
    }

    void distanceFog(DistanceFog fog) {
        glProgramUniform2f(id, distanceFogRange, fog.start(), fog.end());
    }

    void environment(SkyState sky) {
        light = sky.light();
        glProgramUniform1f(id, daylight, light);
        var color = sky.horizon();
        glProgramUniform3f(id, distanceFogColor, color.red(), color.green(), color.blue());
    }

    /**
     * Discards fragments in chunk columns whose bit is set in the coverage buffer bound at binding 2; {@code originX}
     * and {@code originZ} locate the first column relative to the camera.
     */
    void coverage(float originX, float originZ, int size) {
        glUniform2f(coverageOrigin, originX, originZ);
        glUniform1i(coverageSize, size);
    }

    @Override
    public void close() {
        glDeleteProgram(id);
    }
}
