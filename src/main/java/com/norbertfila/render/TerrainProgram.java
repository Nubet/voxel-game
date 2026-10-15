package com.norbertfila.render;

import static org.lwjgl.opengl.GL46C.*;

import com.norbertfila.world.RenderLayer;
import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
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
    private final int nearClip;

    TerrainProgram(String vertexShader) {
        int vertex = 0, fragment = 0, linked = 0;
        try {
            vertex = compile(GL_VERTEX_SHADER, vertexShader);
            fragment = compile(GL_FRAGMENT_SHADER, "/shaders/terrain.frag");
            linked = glCreateProgram();
            glAttachShader(linked, vertex);
            glAttachShader(linked, fragment);
            glLinkProgram(linked);
            if (glGetProgrami(linked, GL_LINK_STATUS) == GL_FALSE)
                throw new IllegalStateException("Shader program link failed: " + glGetProgramInfoLog(linked));
            viewProjection = uniform(linked, "uVP");
            cameraBlock = uniform(linked, "uCameraBlock");
            cameraFraction = uniform(linked, "uCameraFraction");
            renderLayer = uniform(linked, "uRenderLayer");
            waterTime = uniform(linked, "uWaterTime");
            waterFog = uniform(linked, "uWaterFog");
            nearClip = uniform(linked, "uNearClip");
            int sampler = uniform(linked, "uBlockTextures");
            int fogRange = uniform(linked, "uWaterFogRange");
            glUseProgram(linked);
            glUniform1i(sampler, 0);
            glUniform2f(fogRange, UnderwaterVisibility.CLEAR_DISTANCE, UnderwaterVisibility.FOG_DENSITY);
            id = linked;
        } catch (RuntimeException | Error failure) {
            if (linked != 0) glDeleteProgram(linked);
            throw failure;
        } finally {
            if (vertex != 0) glDeleteShader(vertex);
            if (fragment != 0) glDeleteShader(fragment);
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
                UnderwaterVisibility.FOG_RED,
                UnderwaterVisibility.FOG_GREEN,
                UnderwaterVisibility.FOG_BLUE,
                underwater);
        glUniform4f(nearClip, 1, 1, 0, 0);
    }

    void renderLayer(RenderLayer layer) {
        glUniform1i(renderLayer, layer.ordinal());
    }

    /** Discards fragments whose camera-relative x and z lie strictly inside the given rectangle. */
    void nearClip(float minX, float minZ, float maxX, float maxZ) {
        glUniform4f(nearClip, minX, minZ, maxX, maxZ);
    }

    private static int uniform(int program, String name) {
        int location = glGetUniformLocation(program, name);
        if (location < 0) throw new IllegalStateException("Shader uniform not found: " + name);
        return location;
    }

    @Override
    public void close() {
        glDeleteProgram(id);
    }

    private static int compile(int type, String resource) {
        int shader = glCreateShader(type);
        try {
            glShaderSource(shader, loadSource(resource));
            glCompileShader(shader);
            if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
                throw new IllegalStateException(
                        "Shader compilation failed for " + resource + ": " + glGetShaderInfoLog(shader));
            }
            return shader;
        } catch (RuntimeException | Error failure) {
            glDeleteShader(shader);
            throw failure;
        }
    }

    private static String loadSource(String resource) {
        try (InputStream stream = TerrainProgram.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException("Shader resource not found: " + resource);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot read shader resource: " + resource, failure);
        }
    }
}
