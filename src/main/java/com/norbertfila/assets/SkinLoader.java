package com.norbertfila.assets;

import static org.lwjgl.stb.STBImage.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

public final class SkinLoader {
    public static final Path EXTERNAL_PATH = Path.of("skins/player.png");
    public static final String BUNDLED_PATH = "assets/player/player.png";
    private PlayerSkin bundled;

    public record Loaded(PlayerSkin skin, String source, String diagnostic) {}

    public Loaded load() {
        return load(EXTERNAL_PATH);
    }

    public Loaded load(Path path) {
        try (var stream = Files.newInputStream(path)) {
            return new Loaded(read(stream), path.toString(), "");
        } catch (IOException | SecurityException failure) {
            if (bundled == null) bundled = loadDefault();
            return new Loaded(bundled, BUNDLED_PATH, path + ": " + failure);
        }
    }

    public static PlayerSkin loadDefault() {
        try (var stream = SkinLoader.class.getResourceAsStream("/" + BUNDLED_PATH)) {
            if (stream == null) throw new IOException("Default player skin not found");
            return read(stream);
        } catch (IOException failure) {
            throw new UncheckedIOException("Could not load default player skin", failure);
        }
    }

    public static PlayerSkin read(InputStream stream) throws IOException {
        byte[] encoded = Objects.requireNonNull(stream).readNBytes(1024 * 1024 + 1);
        if (encoded.length == 0 || encoded.length > 1024 * 1024)
            throw new IOException("Skin PNG must be 1 byte to 1 MiB");
        byte[] signature = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
        if (encoded.length < signature.length) throw new IOException("Expected PNG skin");
        for (int i = 0; i < signature.length; i++)
            if (encoded[i] != signature[i]) throw new IOException("Expected PNG skin");
        ByteBuffer input = MemoryUtil.memAlloc(encoded.length);
        try (var stack = MemoryStack.stackPush()) {
            input.put(encoded).flip();
            var width = stack.mallocInt(1);
            var height = stack.mallocInt(1);
            var channels = stack.mallocInt(1);
            if (!stbi_info_from_memory(input, width, height, channels)
                    || width.get(0) != PlayerSkin.SIZE
                    || height.get(0) != PlayerSkin.SIZE
                    || channels.get(0) != 4)
                throw new IOException("Expected 64x64 RGBA skin (64x32 legacy skins are unsupported)");
            stbi_set_flip_vertically_on_load_thread(0);
            ByteBuffer pixels = stbi_load_from_memory(input, width, height, channels, 4);
            if (pixels == null) throw new IOException("Invalid skin PNG: " + stbi_failure_reason());
            try {
                byte[] rgba = new byte[PlayerSkin.SIZE * PlayerSkin.SIZE * 4];
                // LWJGL frees at the buffer's current position, which must remain the allocation address.
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
