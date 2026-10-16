package com.norbertfila.assets;

import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;

/** Periodic binary cloud mask, shared by the flat texture and the extruded mesh. */
public final class CloudPattern {
    private final int size;
    private final byte[] alpha;

    private CloudPattern(int size, byte[] alpha) {
        this.size = size;
        this.alpha = alpha;
    }

    public static CloudPattern loadDefault() {
        try (var stream = CloudPattern.class.getResourceAsStream("/assets/textures/environment/clouds.png")) {
            return load(stream);
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot load cloud pattern", failure);
        }
    }

    static CloudPattern load(InputStream stream) throws IOException {
        if (stream == null) throw new IOException("Missing cloud pattern");
        var image = ImageIO.read(stream);
        if (image == null || image.getWidth() != image.getHeight() || image.getWidth() < 2 || image.getWidth() > 256)
            throw new IOException("Cloud pattern must be a square image between 2 and 256 pixels");
        int size = image.getWidth();
        byte[] alpha = new byte[size * size];
        for (int z = 0; z < size; z++)
            for (int x = 0; x < size; x++) alpha[z * size + x] = (byte) (image.getRGB(x, z) >>> 24 >= 128 ? 255 : 0);
        return new CloudPattern(size, alpha);
    }

    public int size() {
        return size;
    }

    public boolean filled(int x, int z) {
        return alpha[Math.floorMod(z, size) * size + Math.floorMod(x, size)] != 0;
    }

    public byte[] alpha() {
        return alpha.clone();
    }
}
