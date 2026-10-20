package com.norbertfila.render;

import static org.lwjgl.opengl.GL46C.*;

import com.norbertfila.light.BlockLightState;
import com.norbertfila.light.BlockLightVolume;
import com.norbertfila.mob.MobRenderState;
import com.norbertfila.mob.MobType;
import com.norbertfila.platform.Window;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;

public final class MobSmoke {
    private static final int SIZE = 512;
    private static final Path OUTPUT = Path.of("build/mob-smoke");

    public static void main(String[] args) throws Exception {
        Files.createDirectories(OUTPUT);
        try (var window = new Window(SIZE, SIZE, "Mob regression");
                var renderer = new MobRenderer();
                var hitboxes = new HitboxRenderer()) {
            glViewport(0, 0, SIZE, SIZE);
            var view = new Matrix4f()
                    .perspective((float) Math.toRadians(60), 1, .1f, 128)
                    .lookAt(0, 1.2f, 4.5f, 0, 1, 0, 0, 1, 0);
            var fog = new DistanceFog(200, 260);
            var mobs = List.of(
                    mob(1, MobType.CREEPER, -1.4, 160),
                    mob(2, MobType.ZOMBIE, 0, 180),
                    mob(3, MobType.SKELETON, 1.4, 200));
            clear();
            renderer.render(view, List.of(), 0, 0, 0, 0, fog, 0, BlockLightState.NONE);
            check(total(capture()) == 0, "Empty mob list drew pixels");
            renderer.environment(SkyModel.DAY);
            glEnable(GL_CULL_FACE);
            glEnable(GL_BLEND);
            glDisable(GL_DEPTH_TEST);
            renderer.render(view, mobs, 1, 0, 0, 0, fog, 0, BlockLightState.NONE);
            check(glIsEnabled(GL_CULL_FACE) && glIsEnabled(GL_BLEND) && !glIsEnabled(GL_DEPTH_TEST), "State leaked");
            var day = capture();
            check(columnsWithPixels(day, 0, SIZE / 3) > 20, "Creeper not visible");
            check(columnsWithPixels(day, SIZE / 3, 2 * SIZE / 3) > 20, "Zombie not visible");
            check(columnsWithPixels(day, 2 * SIZE / 3, SIZE) > 10, "Skeleton not visible");
            write(day, "day");
            hitboxes.render(view, Hitboxes.of(mobs, List.of(), 1), 0, 0, 0);
            check(glIsEnabled(GL_CULL_FACE) && glIsEnabled(GL_BLEND), "Hitboxes leaked state");
            var boxed = capture();
            check(total(boxed) > total(day), "Hitboxes not visible");
            write(boxed, "hitboxes");
            clear();
            renderer.environment(SkyModel.at(com.norbertfila.simulation.WorldTime.TICKS_PER_DAY * .75));
            renderer.render(view, mobs, 1, 0, 0, 0, fog, 0, BlockLightState.NONE);
            var night = capture();
            check(total(night) < total(day) / 2, "Night did not darken mobs");
            write(night, "night");
            clear();
            var torch = new BlockLightVolume(-8, -8, -8, 16, 16, 16, filled(16 * 16 * 16, (byte) 14));
            renderer.render(view, mobs, 1, 0, 0, 0, fog, 0, new BlockLightState(torch, BlockLightVolume.EMPTY));
            var torchLit = capture();
            check(total(torchLit) > total(night) * 3, "Torch light did not brighten mobs at night");
            write(torchLit, "night-torch");
            clear();
            renderer.render(view, mobs, 1, 1 << 24, 0, 0, fog, 0, BlockLightState.NONE);
            check(total(capture()) == 0, "Mobs behind the camera offset were drawn");
            check(glGetError() == GL_NO_ERROR, "OpenGL error");
        }
    }

    private static MobRenderState mob(long id, MobType type, double x, float yaw) {
        return new MobRenderState(id, type, x, 0, 0, x, 0, 0, yaw);
    }

    private static byte[] filled(int length, byte level) {
        var levels = new byte[length];
        java.util.Arrays.fill(levels, level);
        return levels;
    }

    private static int columnsWithPixels(ByteBuffer pixels, int from, int to) {
        int columns = 0;
        for (int x = from; x < to; x++)
            for (int y = 0; y < SIZE; y++) {
                int i = (y * SIZE + x) * 4;
                if (pixels.get(i) != 0 || pixels.get(i + 1) != 0 || pixels.get(i + 2) != 0) {
                    columns++;
                    break;
                }
            }
        return columns;
    }

    private static void clear() {
        glClearColor(0, 0, 0, 1);
        glDepthMask(true);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
    }

    private static ByteBuffer capture() {
        var pixels = BufferUtils.createByteBuffer(SIZE * SIZE * 4);
        glReadPixels(0, 0, SIZE, SIZE, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        return pixels;
    }

    private static long total(ByteBuffer pixels) {
        long result = 0;
        for (int i = 0; i < pixels.capacity(); i += 4)
            result += Byte.toUnsignedInt(pixels.get(i))
                    + Byte.toUnsignedInt(pixels.get(i + 1))
                    + Byte.toUnsignedInt(pixels.get(i + 2));
        return result;
    }

    private static void write(ByteBuffer pixels, String name) throws Exception {
        var image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < SIZE; y++)
            for (int x = 0; x < SIZE; x++) {
                int offset = ((SIZE - 1 - y) * SIZE + x) * 4;
                image.setRGB(
                        x,
                        y,
                        Byte.toUnsignedInt(pixels.get(offset)) << 16
                                | Byte.toUnsignedInt(pixels.get(offset + 1)) << 8
                                | Byte.toUnsignedInt(pixels.get(offset + 2)));
            }
        ImageIO.write(image, "png", OUTPUT.resolve(name + ".png").toFile());
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
