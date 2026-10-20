package com.norbertfila.render;

import static org.lwjgl.opengl.GL46C.*;

import java.io.IOException;
import javax.imageio.ImageIO;
import org.lwjgl.system.MemoryUtil;

final class EnvironmentTexture {
    private EnvironmentTexture() {}

    static int load(String name) {
        return loadResource("/assets/textures/environment/" + name + ".png");
    }

    /** Loads a classpath PNG as a nearest-filtered RGBA texture. */
    static int loadResource(String resource) {
        try (var stream = EnvironmentTexture.class.getResourceAsStream(resource)) {
            if (stream == null) throw new IOException("Missing " + resource);
            var image = ImageIO.read(stream);
            if (image == null
                    || image.getWidth() < 1
                    || image.getWidth() > 512
                    || image.getHeight() < 1
                    || image.getHeight() > 512) throw new IOException("Invalid texture: " + resource);
            int width = image.getWidth(), height = image.getHeight();
            var pixels = MemoryUtil.memAlloc(width * height * 4);
            int texture = 0;
            try {
                for (int y = 0; y < height; y++)
                    for (int x = 0; x < width; x++) {
                        int argb = image.getRGB(x, y);
                        pixels.put((byte) (argb >> 16))
                                .put((byte) (argb >> 8))
                                .put((byte) argb)
                                .put((byte) (argb >> 24));
                    }
                pixels.flip();
                texture = glCreateTextures(GL_TEXTURE_2D);
                glTextureStorage2D(texture, 1, GL_RGBA8, width, height);
                glTextureSubImage2D(texture, 0, 0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
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
            throw new IllegalStateException("Cannot load texture: " + resource, failure);
        }
    }
}
