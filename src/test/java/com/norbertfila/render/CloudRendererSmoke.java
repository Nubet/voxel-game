package com.norbertfila.render;

import static org.lwjgl.opengl.GL46C.*;

import com.norbertfila.platform.Window;
import java.nio.ByteBuffer;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;

public final class CloudRendererSmoke {
    private static final int SIZE = 128;

    public static void main(String[] args) {
        try (Window window = new Window(SIZE, SIZE, "Cloud regression");
                CloudRenderer clouds = new CloudRenderer()) {
            glViewport(0, 0, SIZE, SIZE);
            Matrix4f vp = new Matrix4f().ortho(-256, 256, -256, 256, .1f, 800).lookAt(0, 0, 0, 0, 1, 0, 0, 0, -1);
            clear(1);
            clouds.render(vp, 0, 64, 0, 0);
            ByteBuffer reference = capture();
            int filled = 0, empty = 0;
            for (int i = 0; i < reference.capacity(); i += 4) {
                if (Byte.toUnsignedInt(reference.get(i)) > 100) filled++;
                else empty++;
            }
            check(filled > 100 && empty > 100, "Cloud mask not visible");
            check(
                    glGetBoolean(GL_DEPTH_WRITEMASK) && !glIsEnabled(GL_BLEND) && glIsEnabled(GL_CULL_FACE),
                    "Leaked render state");
            clear(1);
            clouds.render(vp, 1024.0 * 1_000_000, 64, -1024.0 * 1_000_000, 0);
            check(reference.equals(capture()), "Large coordinates changed the pattern");
            clear(0);
            ByteBuffer occluded = capture();
            clouds.render(vp, 0, 64, 0, 0);
            check(occluded.equals(capture()), "Clouds ignored foreground depth");
            glClearDepth(1);
            check(glGetError() == GL_NO_ERROR, "Cloud GL error");
            System.out.println("PASS cloud mask, large coordinates, depth occlusion and GL state; GPU="
                    + glGetString(GL_RENDERER));
        }
    }

    private static void clear(double depth) {
        glClearColor(0, 0, 0, 1);
        glClearDepth(depth);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
    }

    private static ByteBuffer capture() {
        ByteBuffer pixels = BufferUtils.createByteBuffer(SIZE * SIZE * 4);
        glReadPixels(0, 0, SIZE, SIZE, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        return pixels;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
