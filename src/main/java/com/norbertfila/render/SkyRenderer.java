package com.norbertfila.render;

import static com.norbertfila.render.ShaderPrograms.uniform;
import static org.lwjgl.opengl.GL46C.*;

import java.io.IOException;
import javax.imageio.ImageIO;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

public final class SkyRenderer implements AutoCloseable {
    private final int program, vao, sun, moon;
    private final int inverseViewProjection, zenith, horizon, sunDirection, twilight, stars, underwater, waterLight;
    private final int overcast, cloudColor, celestialVisibility;
    private final Matrix4f inverse = new Matrix4f();
    private boolean closed;

    public SkyRenderer() {
        int createdProgram = 0, createdVao = 0, createdSun = 0, createdMoon = 0;
        try {
            createdProgram = ShaderPrograms.create("/shaders/sky.vert", "/shaders/sky.frag");
            inverseViewProjection = uniform(createdProgram, "uInverseVP");
            zenith = uniform(createdProgram, "uZenith");
            horizon = uniform(createdProgram, "uHorizon");
            sunDirection = uniform(createdProgram, "uSunDirection");
            twilight = uniform(createdProgram, "uTwilight");
            stars = uniform(createdProgram, "uStars");
            underwater = uniform(createdProgram, "uUnderwater");
            waterLight = uniform(createdProgram, "uWaterLight");
            overcast = uniform(createdProgram, "uOvercast");
            cloudColor = uniform(createdProgram, "uCloudColor");
            celestialVisibility = uniform(createdProgram, "uCelestialVisibility");
            glProgramUniform1i(createdProgram, uniform(createdProgram, "uSun"), 0);
            glProgramUniform1i(createdProgram, uniform(createdProgram, "uMoon"), 1);
            createdVao = glCreateVertexArrays();
            createdSun = texture("sun");
            createdMoon = texture("moon");
        } catch (RuntimeException | Error failure) {
            glDeleteTextures(createdSun);
            glDeleteTextures(createdMoon);
            glDeleteVertexArrays(createdVao);
            glDeleteProgram(createdProgram);
            throw failure;
        }
        program = createdProgram;
        vao = createdVao;
        sun = createdSun;
        moon = createdMoon;
    }

    public void render(Matrix4f relativeViewProjection, SkyState sky, float water) {
        if (closed) throw new IllegalStateException("Sky renderer is closed");
        if (!Float.isFinite(water) || water < 0 || water > 1)
            throw new IllegalArgumentException("Underwater visibility must be in [0, 1]");
        glDisable(GL_DEPTH_TEST);
        glDepthMask(false);
        glDisable(GL_BLEND);
        glDisable(GL_CULL_FACE);
        try (var stack = MemoryStack.stackPush()) {
            glUseProgram(program);
            relativeViewProjection.invert(inverse);
            glUniformMatrix4fv(inverseViewProjection, false, inverse.get(stack.mallocFloat(16)));
            glUniform3f(
                    zenith,
                    sky.zenith().red(),
                    sky.zenith().green(),
                    sky.zenith().blue());
            glUniform3f(
                    horizon,
                    sky.horizon().red(),
                    sky.horizon().green(),
                    sky.horizon().blue());
            glUniform3f(sunDirection, sky.sunX(), sky.sunY(), 0);
            glUniform1f(twilight, sky.twilight());
            glUniform1f(stars, sky.stars());
            glUniform1f(underwater, water);
            glUniform1f(waterLight, sky.light());
            glUniform1f(overcast, sky.overcast());
            glUniform3f(
                    cloudColor,
                    sky.clouds().red(),
                    sky.clouds().green(),
                    sky.clouds().blue());
            glUniform1f(celestialVisibility, sky.celestialVisibility());
            glBindTextureUnit(0, sun);
            glBindTextureUnit(1, moon);
            glBindVertexArray(vao);
            glDrawArrays(GL_TRIANGLES, 0, 3);
        } finally {
            glBindTextureUnit(0, 0);
            glBindTextureUnit(1, 0);
            glBindVertexArray(0);
            glUseProgram(0);
            TerrainRenderer.restoreTerrainState();
        }
    }

    private static int texture(String name) {
        String resource = "/assets/textures/environment/" + name + ".png";
        try (var stream = SkyRenderer.class.getResourceAsStream(resource)) {
            if (stream == null) throw new IOException("Missing " + resource);
            var image = ImageIO.read(stream);
            if (image == null
                    || image.getWidth() < 1
                    || image.getWidth() > 512
                    || image.getHeight() != image.getWidth()) throw new IOException("Invalid sky texture: " + resource);
            int size = image.getWidth();
            var pixels = MemoryUtil.memAlloc(size * size * 4);
            int texture = 0;
            try {
                for (int y = 0; y < size; y++) {
                    for (int x = 0; x < size; x++) {
                        int argb = image.getRGB(x, y);
                        pixels.put((byte) (argb >> 16))
                                .put((byte) (argb >> 8))
                                .put((byte) argb)
                                .put((byte) (argb >> 24));
                    }
                }
                pixels.flip();
                texture = glCreateTextures(GL_TEXTURE_2D);
                glTextureStorage2D(texture, 1, GL_RGBA8, size, size);
                glTextureSubImage2D(texture, 0, 0, 0, size, size, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
                glTextureParameteri(texture, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
                glTextureParameteri(texture, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
                glTextureParameteri(texture, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
                glTextureParameteri(texture, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
                return texture;
            } catch (RuntimeException | Error failure) {
                glDeleteTextures(texture);
                throw failure;
            } finally {
                MemoryUtil.memFree(pixels);
            }
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot load sky texture: " + resource, failure);
        }
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        glDeleteTextures(sun);
        glDeleteTextures(moon);
        glDeleteVertexArrays(vao);
        glDeleteProgram(program);
    }
}
