package com.norbertfila.assets;

import static org.lwjgl.stb.STBImage.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.Objects;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

public final class PlayerSkin {
    public static final int SIZE = 64;
    private final byte[] rgba;

    public PlayerSkin(byte[] rgba) {
        if (Objects.requireNonNull(rgba).length != SIZE * SIZE * 4)
            throw new IllegalArgumentException("Expected 64x64 RGBA skin");
        this.rgba = rgba.clone();
    }

    public byte[] rgba() {
        return rgba.clone();
    }

    public static PlayerSkin loadDefault() {
        try (var stream = PlayerSkin.class.getResourceAsStream("/assets/player/player.png")) {
            if (stream == null) throw new IOException("Default player skin not found");
            return read(stream);
        } catch (IOException failure) {
            throw new UncheckedIOException("Could not load default player skin", failure);
        }
    }

    public static PlayerSkin read(InputStream stream) throws IOException {
        byte[] encoded = Objects.requireNonNull(stream).readAllBytes();
        ByteBuffer input = MemoryUtil.memAlloc(encoded.length);
        try (var stack = MemoryStack.stackPush()) {
            input.put(encoded).flip();
            var width = stack.mallocInt(1);
            var height = stack.mallocInt(1);
            var channels = stack.mallocInt(1);
            stbi_set_flip_vertically_on_load_thread(0);
            ByteBuffer pixels = stbi_load_from_memory(input, width, height, channels, 4);
            if (pixels == null) throw new IOException("Invalid skin image: " + stbi_failure_reason());
            try {
                if (width.get(0) != SIZE || height.get(0) != SIZE || channels.get(0) != 4)
                    throw new IOException("Expected 64x64 RGBA skin");
                byte[] rgba = new byte[SIZE * SIZE * 4];
                pixels.duplicate().get(rgba);
                return new PlayerSkin(rgba);
            } finally {
                stbi_image_free(pixels);
            }
        } finally {
            MemoryUtil.memFree(input);
        }
    }
}
