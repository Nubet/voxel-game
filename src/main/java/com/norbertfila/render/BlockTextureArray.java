package com.norbertfila.render;

import static org.lwjgl.opengl.GL46C.*;

import com.norbertfila.assets.TextureSet;
import java.nio.ByteBuffer;
import java.util.Objects;
import org.lwjgl.system.MemoryStack;

public final class BlockTextureArray implements AutoCloseable {
    private final int texture;
    private final int maxTextureUnits;
    private boolean closed;

    public BlockTextureArray(TextureSet textures) {
        Objects.requireNonNull(textures);
        if (textures.layerCount() == 0)
            throw new IllegalArgumentException("Texture array must have at least one layer");
        maxTextureUnits = glGetInteger(GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS);
        if (glGetInteger(GL_MAX_ARRAY_TEXTURE_LAYERS) < textures.layerCount())
            throw new IllegalArgumentException("Texture set exceeds GL_MAX_ARRAY_TEXTURE_LAYERS");

        int created = glGenTextures();
        if (created == 0) throw new IllegalStateException("Could not create block texture array");
        int previous = glGetInteger(GL_TEXTURE_BINDING_2D_ARRAY);
        try {
            glBindTexture(GL_TEXTURE_2D_ARRAY, created);
            glTexStorage3D(
                    GL_TEXTURE_2D_ARRAY,
                    1,
                    GL_RGBA8,
                    textures.layer(0).width(),
                    textures.layer(0).height(),
                    textures.layerCount());
            glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_S, GL_REPEAT);
            glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_T, GL_REPEAT);
            try (MemoryStack stack = MemoryStack.stackPush()) {
                ByteBuffer pixels = stack.malloc(
                        textures.layer(0).width() * textures.layer(0).height() * 4);
                for (int layer = 0; layer < textures.layerCount(); layer++) {
                    TextureSet.Texture image = textures.layer(layer);
                    if (image.width() != textures.layer(0).width()
                            || image.height() != textures.layer(0).height())
                        throw new IllegalArgumentException("Texture array layers must have identical dimensions");
                    pixels.clear();
                    pixels.put(image.rgba()).flip();
                    glTexSubImage3D(
                            GL_TEXTURE_2D_ARRAY,
                            0,
                            0,
                            0,
                            layer,
                            image.width(),
                            image.height(),
                            1,
                            GL_RGBA,
                            GL_UNSIGNED_BYTE,
                            pixels);
                }
            }
            texture = created;
        } catch (RuntimeException | Error failure) {
            glDeleteTextures(created);
            throw failure;
        } finally {
            glBindTexture(GL_TEXTURE_2D_ARRAY, previous);
        }
    }

    public void bind(int textureUnit) {
        if (closed) throw new IllegalStateException("Block texture array is closed");
        if (textureUnit < 0 || textureUnit >= maxTextureUnits)
            throw new IllegalArgumentException("Texture unit is outside the available range: " + textureUnit);
        glActiveTexture(GL_TEXTURE0 + textureUnit);
        glBindTexture(GL_TEXTURE_2D_ARRAY, texture);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        glDeleteTextures(texture);
    }
}
