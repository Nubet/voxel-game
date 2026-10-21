package com.norbertfila.assets;

import static org.lwjgl.stb.STBImage.*;

import com.norbertfila.items.Tool;
import com.norbertfila.world.BlockRegistry;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.logging.Logger;
import org.lwjgl.system.MemoryStack;

public final class AssetLoader {
    public static final int TEXTURE_SIZE = 16;
    private static final Logger LOG = Logger.getLogger(AssetLoader.class.getName());
    private static final int RGBA_BYTES = TEXTURE_SIZE * TEXTURE_SIZE * 4;
    private final BlockRegistry registry;
    private final ResourceSource resources;

    public AssetLoader(BlockRegistry registry) {
        this(registry, resource -> AssetLoader.class.getResourceAsStream(resource));
    }

    AssetLoader(BlockRegistry registry, ResourceSource resources) {
        this.registry = java.util.Objects.requireNonNull(registry);
        this.resources = java.util.Objects.requireNonNull(resources);
    }

    public TextureSet loadBlockTextures() {
        int maxLayer = registry.textureLayers().values().stream()
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0);
        ArrayList<TextureSet.Texture> layers = new ArrayList<>(java.util.Collections.nCopies(maxLayer + 1, null));
        layers.set(BlockRegistry.FALLBACK_TEXTURE, checkerboard());
        for (var entry : registry.textureLayers().entrySet()) {
            if (entry.getValue() == BlockRegistry.FALLBACK_TEXTURE) continue;
            String resource = "/assets/textures/blocks/" + entry.getKey() + ".png";
            layers.set(entry.getValue(), load(resource));
        }
        for (int index = 0; index < layers.size(); index++)
            if (layers.get(index) == null) layers.set(index, checkerboard());
        return new TextureSet(layers);
    }

    /** One layer per tool, in {@link Tool} order. */
    public TextureSet loadToolTextures() {
        var layers = new ArrayList<TextureSet.Texture>();
        for (var tool : Tool.values()) layers.add(load("/assets/textures/items/" + tool.itemName() + ".png"));
        return new TextureSet(layers);
    }

    private TextureSet.Texture load(String resource) {
        try (InputStream stream = resources.open(resource)) {
            if (stream == null) throw new IOException("Resource not found");
            byte[] encoded = stream.readAllBytes();
            try (MemoryStack stack = MemoryStack.stackPush()) {
                ByteBuffer input = stack.malloc(encoded.length);
                input.put(encoded).flip();
                var width = stack.mallocInt(1);
                var height = stack.mallocInt(1);
                var channels = stack.mallocInt(1);
                ByteBuffer pixels = stbi_load_from_memory(input, width, height, channels, 4);
                if (pixels == null) throw new IOException(stbi_failure_reason());
                try {
                    if (width.get(0) != TEXTURE_SIZE || height.get(0) != TEXTURE_SIZE)
                        throw new IOException("Expected 16x16 PNG, got " + width.get(0) + "x" + height.get(0));
                    byte[] rgba = new byte[RGBA_BYTES];
                    pixels.duplicate().get(rgba);
                    return new TextureSet.Texture(TEXTURE_SIZE, TEXTURE_SIZE, rgba);
                } finally {
                    stbi_image_free(pixels);
                }
            }
        } catch (IOException | RuntimeException failure) {
            LOG.warning("Using fallback texture for " + resource + ": " + failure.getMessage());
            return checkerboard();
        }
    }

    private static TextureSet.Texture checkerboard() {
        byte[] rgba = new byte[RGBA_BYTES];
        for (int y = 0; y < TEXTURE_SIZE; y++)
            for (int x = 0; x < TEXTURE_SIZE; x++) {
                boolean magenta = (x / 4 + y / 4) % 2 == 0;
                int offset = (y * TEXTURE_SIZE + x) * 4;
                rgba[offset] = (byte) (magenta ? 255 : 0);
                rgba[offset + 1] = 0;
                rgba[offset + 2] = (byte) (magenta ? 255 : 0);
                rgba[offset + 3] = (byte) 255;
            }
        return new TextureSet.Texture(TEXTURE_SIZE, TEXTURE_SIZE, rgba);
    }

    @FunctionalInterface
    interface ResourceSource {
        InputStream open(String resource) throws IOException;
    }
}
