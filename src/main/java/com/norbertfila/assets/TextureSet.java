package com.norbertfila.assets;

import java.util.List;
import java.util.Objects;

public final class TextureSet {
    private final List<Texture> layers;

    TextureSet(List<Texture> layers) {
        this.layers = List.copyOf(layers);
    }

    public int layerCount() {
        return layers.size();
    }

    public Texture layer(int index) {
        return layers.get(index);
    }

    public List<Texture> layers() {
        return layers;
    }

    public static final class Texture {
        private final int width;
        private final int height;
        private final byte[] rgba;

        Texture(int width, int height, byte[] rgba) {
            if (width <= 0 || height <= 0 || rgba.length != width * height * 4)
                throw new IllegalArgumentException("RGBA data length must match positive image dimensions");
            this.width = width;
            this.height = height;
            this.rgba = Objects.requireNonNull(rgba).clone();
        }

        public int width() {
            return width;
        }

        public int height() {
            return height;
        }

        public byte[] rgba() {
            return rgba.clone();
        }

        public int rgbaAt(int x, int y, int channel) {
            if (x < 0 || x >= width || y < 0 || y >= height || channel < 0 || channel >= 4)
                throw new IndexOutOfBoundsException();
            return Byte.toUnsignedInt(rgba[(y * width + x) * 4 + channel]);
        }
    }
}
