package com.norbertfila.render;

import static com.norbertfila.player.visual.model.PlayerModel.Part.*;
import static org.lwjgl.opengl.GL46C.*;

import com.norbertfila.platform.Window;
import com.norbertfila.player.visual.model.PartTransform;
import com.norbertfila.player.visual.model.PlayerPose;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;

public final class PlayerModelSmoke {
    private static final int SIZE = 512;

    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]);
        Files.createDirectories(output);
        try (Window window = new Window(SIZE, SIZE, "Player model smoke");
                PlayerDebugRenderer renderer = new PlayerDebugRenderer()) {
            int fbo = glCreateFramebuffers(), color = glCreateTextures(GL_TEXTURE_2D);
            int depth = glCreateRenderbuffers(), sentinelVao = glCreateVertexArrays();
            try {
                glTextureStorage2D(color, 1, GL_RGBA8, SIZE, SIZE);
                glNamedRenderbufferStorage(depth, GL_DEPTH_COMPONENT24, SIZE, SIZE);
                glNamedFramebufferTexture(fbo, GL_COLOR_ATTACHMENT0, color, 0);
                glNamedFramebufferRenderbuffer(fbo, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, depth);
                check(glCheckNamedFramebufferStatus(fbo, GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE, "Incomplete FBO");
                glBindFramebuffer(GL_FRAMEBUFFER, fbo);
                glViewport(0, 0, SIZE, SIZE);
                Matrix4f front =
                        new Matrix4f().ortho(-1.3f, 1.3f, -1.3f, 1.3f, .1f, 20).lookAt(0, 1, -5, 0, 1, 0, 0, 1, 0);
                clear(1);
                render(renderer, front, PlayerPose.NEUTRAL, sentinelVao);
                var neutral = capture();
                checkColors(neutral);
                write(neutral, output.resolve("neutral.png"));
                clear(1);
                render(renderer, front, PlayerPose.NEUTRAL, sentinelVao);
                check(neutral.equals(capture()), "Neutral render is not deterministic");
                var pose = PlayerPose.NEUTRAL
                        .with(HEAD, new PartTransform(0, 0, 0, .1f, -.5f, 0))
                        .with(RIGHT_ARM, new PartTransform(0, 0, 0, 1.1f, 0, -.2f))
                        .with(LEFT_ARM, new PartTransform(0, 0, 0, -.5f, 0, .15f))
                        .with(RIGHT_LEG, new PartTransform(0, 0, 0, -.35f, 0, 0))
                        .with(LEFT_LEG, new PartTransform(0, 0, 0, .35f, 0, 0));
                clear(1);
                render(renderer, front, pose, sentinelVao);
                check(!neutral.equals(capture()), "Pose did not change the image");
                Matrix4f angled =
                        new Matrix4f().ortho(-1.3f, 1.3f, -1.3f, 1.3f, .1f, 20).lookAt(3, 2.3f, -5, 0, 1, 0, 0, 1, 0);
                clear(1);
                render(renderer, angled, pose, sentinelVao);
                var articulated = capture();
                write(articulated, output.resolve("articulated.png"));
                clear(1);
                render(renderer, angled, pose, sentinelVao);
                check(articulated.equals(capture()), "Articulated render is not deterministic");
                clear(0);
                var occluded = capture();
                render(renderer, front, pose, sentinelVao);
                check(occluded.equals(capture()), "Model ignored foreground depth");
                renderer.close();
                renderer.close();
                check(glGetError() == GL_NO_ERROR, "Player model GL error");
                System.out.println("PASS six colors, articulation, deterministic images, depth, state and cleanup; GPU="
                        + glGetString(GL_RENDERER) + "; output=" + output);
            } finally {
                glBindVertexArray(0);
                glBindFramebuffer(GL_FRAMEBUFFER, 0);
                glDeleteVertexArrays(sentinelVao);
                glDeleteRenderbuffers(depth);
                glDeleteTextures(color);
                glDeleteFramebuffers(fbo);
            }
        }
    }

    private static void render(PlayerDebugRenderer renderer, Matrix4f vp, PlayerPose pose, int vao) {
        glDisable(GL_DEPTH_TEST);
        glDepthMask(false);
        glDepthFunc(GL_GREATER);
        glEnable(GL_BLEND);
        glEnable(GL_CULL_FACE);
        glBindVertexArray(vao);
        renderer.render(vp, new Matrix4f(), pose);
        check(
                !glIsEnabled(GL_DEPTH_TEST)
                        && !glGetBoolean(GL_DEPTH_WRITEMASK)
                        && glGetInteger(GL_DEPTH_FUNC) == GL_GREATER
                        && glIsEnabled(GL_BLEND)
                        && glIsEnabled(GL_CULL_FACE)
                        && glGetInteger(GL_VERTEX_ARRAY_BINDING) == vao
                        && glGetInteger(GL_CURRENT_PROGRAM) == 0,
                "Leaked render state");
    }

    private static void clear(double depth) {
        glDepthMask(true);
        glClearColor(.06f, .08f, .12f, 1);
        glClearDepth(depth);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
    }

    private static ByteBuffer capture() {
        var pixels = BufferUtils.createByteBuffer(SIZE * SIZE * 4);
        glReadPixels(0, 0, SIZE, SIZE, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        return pixels;
    }

    private static void checkColors(ByteBuffer pixels) {
        float[][] colors = {
            {1, .75f, .35f}, {.15f, .8f, .8f}, {.95f, .3f, .25f}, {.35f, .85f, .3f}, {.3f, .4f, 1}, {.8f, .3f, .9f}
        };
        for (float[] color : colors) {
            int count = 0;
            float sum = color[0] + color[1] + color[2];
            for (int i = 0; i < pixels.capacity(); i += 4) {
                int r = Byte.toUnsignedInt(pixels.get(i));
                int g = Byte.toUnsignedInt(pixels.get(i + 1));
                int b = Byte.toUnsignedInt(pixels.get(i + 2));
                float total = r + g + b;
                if (total > 0
                        && Math.abs(r / total - color[0] / sum) < .01f
                        && Math.abs(g / total - color[1] / sum) < .01f
                        && Math.abs(b / total - color[2] / sum) < .01f) count++;
            }
            check(count > 100, "Missing part color");
        }
    }

    private static void write(ByteBuffer pixels, Path path) throws Exception {
        var image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < SIZE; y++)
            for (int x = 0; x < SIZE; x++) {
                int i = (y * SIZE + x) * 4;
                image.setRGB(
                        x,
                        SIZE - y - 1,
                        Byte.toUnsignedInt(pixels.get(i)) << 16
                                | Byte.toUnsignedInt(pixels.get(i + 1)) << 8
                                | Byte.toUnsignedInt(pixels.get(i + 2)));
            }
        check(ImageIO.write(image, "png", path.toFile()), "PNG writer unavailable");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
