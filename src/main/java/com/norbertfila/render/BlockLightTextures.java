package com.norbertfila.render;

import static org.lwjgl.opengl.GL46C.*;

import com.norbertfila.light.BlockLightState;
import com.norbertfila.light.BlockLightVolume;
import org.lwjgl.system.MemoryUtil;

/** GPU copies of the published block light volumes; each upload happens only when a new volume is published. */
final class BlockLightTextures implements AutoCloseable {
    static final int PLACED_UNIT = 1, HELD_UNIT = 2;
    private final Volume placed = new Volume(), held = new Volume();

    void update(BlockLightState state) {
        placed.upload(state.placed());
        held.upload(state.held());
    }

    void bind() {
        glBindTextureUnit(PLACED_UNIT, placed.texture);
        glBindTextureUnit(HELD_UNIT, held.texture);
    }

    BlockLightVolume placed() {
        return placed.current;
    }

    BlockLightVolume held() {
        return held.current;
    }

    @Override
    public void close() {
        placed.close();
        held.close();
    }

    private static final class Volume {
        private int texture, sizeX, sizeY, sizeZ;
        private BlockLightVolume current = BlockLightVolume.EMPTY;

        void upload(BlockLightVolume volume) {
            if (volume == current) return;
            current = volume;
            if (volume.isEmpty()) return;
            if (texture == 0 || sizeX != volume.sizeX() || sizeY != volume.sizeY() || sizeZ != volume.sizeZ()) {
                close();
                texture = glCreateTextures(GL_TEXTURE_3D);
                glTextureStorage3D(texture, 1, GL_R8UI, volume.sizeX(), volume.sizeY(), volume.sizeZ());
                glTextureParameteri(texture, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
                glTextureParameteri(texture, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
                sizeX = volume.sizeX();
                sizeY = volume.sizeY();
                sizeZ = volume.sizeZ();
            }
            var pixels = MemoryUtil.memAlloc(volume.levels().length);
            int alignment = glGetInteger(GL_UNPACK_ALIGNMENT);
            try {
                pixels.put(volume.levels()).flip();
                // Rows are one byte per cell and the held volume has an odd width.
                glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
                glTextureSubImage3D(texture, 0, 0, 0, 0, sizeX, sizeY, sizeZ, GL_RED_INTEGER, GL_UNSIGNED_BYTE, pixels);
            } finally {
                glPixelStorei(GL_UNPACK_ALIGNMENT, alignment);
                MemoryUtil.memFree(pixels);
            }
        }

        void close() {
            if (texture != 0) glDeleteTextures(texture);
            texture = 0;
        }
    }
}
