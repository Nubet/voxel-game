package com.norbertfila.render;

import static com.norbertfila.player.visual.model.PlayerModel.Part.*;
import static org.lwjgl.opengl.GL46C.*;

import com.norbertfila.assets.PlayerSkin;
import com.norbertfila.assets.SkinLoader;
import com.norbertfila.platform.Window;
import com.norbertfila.player.PlayerController;
import com.norbertfila.player.PlayerState;
import com.norbertfila.player.WaterContact;
import com.norbertfila.player.visual.PlayerRenderStateExtractor;
import com.norbertfila.player.visual.model.CuboidMesh.Face;
import com.norbertfila.player.visual.model.PartTransform;
import com.norbertfila.player.visual.model.PlayerModel;
import com.norbertfila.player.visual.model.PlayerPose;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import javax.imageio.ImageIO;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;

public final class PlayerModelSmoke {
    private static final int SIZE = 512;

    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]);
        Files.createDirectories(output);
        var debugSkin = directionalSkin();
        var defaultSkin = SkinLoader.loadDefault();
        try (Window window = new Window(SIZE, SIZE, "Player model smoke");
                SkinManager skins = new SkinManager(
                        Executors.newSingleThreadExecutor(),
                        () -> new SkinLoader.Loaded(debugSkin, "directional", ""));
                SkinManager defaults = new SkinManager(
                        Executors.newSingleThreadExecutor(),
                        () -> new SkinLoader.Loaded(defaultSkin, SkinLoader.BUNDLED_PATH, ""));
                PlayerRenderer renderer = new PlayerRenderer(skins);
                PlayerRenderer defaultRenderer = new PlayerRenderer(defaults)) {
            awaitSkin(skins);
            awaitSkin(defaults);
            SkinManagerSmoke.checkLifecycle();
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
                for (var face : Face.values()) {
                    checkFace(renderer, debugSkin, face, sentinelVao, output.resolve("directional-" + face + ".png"));
                    checkFace(
                            defaultRenderer,
                            defaultSkin,
                            face,
                            sentinelVao,
                            output.resolve("default-" + face + ".png"));
                }
                Matrix4f front =
                        new Matrix4f().ortho(-1.3f, 1.3f, -1.3f, 1.3f, .1f, 20).lookAt(0, 1, -5, 0, 1, 0, 0, 1, 0);
                clear(1);
                render(renderer, front, PlayerPose.NEUTRAL, sentinelVao);
                var neutral = capture();
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
                clear(1);
                render(defaultRenderer, front, PlayerPose.NEUTRAL, sentinelVao);
                var defaultImage = capture();
                check(!defaultImage.equals(neutral), "Default skin was not used");
                write(defaultImage, output.resolve("default.png"));
                clear(1);
                render(defaultRenderer, angled, pose, sentinelVao);
                write(capture(), output.resolve("default-articulated.png"));
                clear(0);
                var occluded = capture();
                render(renderer, front, pose, sentinelVao);
                check(occluded.equals(capture()), "Model ignored foreground depth");
                checkSnapshots(renderer, output);
                glBindFramebuffer(GL_FRAMEBUFFER, 0);
                com.norbertfila.core.PlayerGameHookSmoke.run(window, skins, output);
                renderer.close();
                renderer.close();
                defaultRenderer.close();
                defaultRenderer.close();
                check(glGetError() == GL_NO_ERROR, "Player model GL error");
                System.out.println(
                        "PASS all six faces x six parts x 16 directional/default samples, nearest filtering, articulation, deterministic images, depth, state and cleanup; GPU="
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

    static void awaitSkin(SkinManager skins) throws InterruptedException {
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (skins.status().state() == SkinManager.State.LOADING && System.nanoTime() < deadline) {
            skins.update();
            Thread.sleep(1);
        }
        check(
                skins.texture() != 0 && skins.status().state() != SkinManager.State.LOADING,
                "Skin upload timed out or failed: " + skins.status());
    }

    private static void checkSnapshots(PlayerRenderer renderer, Path output) throws Exception {
        var player = new PlayerController();
        var extractor = new PlayerRenderStateExtractor();
        var water = new WaterContact.Result(0, false, true);
        var vp = new Matrix4f().ortho(-1.3f, 1.3f, -1.3f, 1.3f, .1f, 20).lookAt(0, 0, 0, 0, 0, 1, 0, 1, 0);
        ByteBuffer reference = null;
        for (double origin : new double[] {0, -1_000_000_000.25}) {
            player.setPosition(new PlayerState.Position(origin, 0, origin));
            var state = extractor.extract(player, -90, 0, water, false);
            clear(1);
            check(renderer.render(vp, state, .5, origin, 1, origin - 5), "Visible snapshot was culled");
            var image = capture();
            if (reference == null) reference = image;
            else check(reference.equals(image), "Large negative coordinates lost sub-block precision");
            write(image, output.resolve(origin == 0 ? "snapshot-origin.png" : "snapshot-negative.png"));
            clear(1);
            var empty = capture();
            check(!renderer.render(vp, state, .5, origin + 100, 1, origin - 5), "Off-frustum snapshot drawn");
            check(empty.equals(capture()), "Culled snapshot wrote pixels");
            clear(0);
            empty = capture();
            renderer.render(vp, state, .5, origin, 1, origin - 5);
            check(empty.equals(capture()), "Snapshot ignored opaque occlusion");
            for (double yaw : new double[] {-90, 0, 90, 180}) {
                var turned = extractor.extract(player, yaw, 0, water, true);
                double angle = Math.toRadians(yaw);
                double cx = origin + Math.cos(angle) * 5, cz = origin + Math.sin(angle) * 5;
                var facing = new Matrix4f()
                        .ortho(-1.3f, 1.3f, -1.3f, 1.3f, .1f, 20)
                        .lookAt(0, 0, 0, (float) -Math.cos(angle), 0, (float) -Math.sin(angle), 0, 1, 0);
                clear(1);
                renderer.render(facing, turned, 1, cx, 1, cz);
                check(reference.equals(capture()), "Wrong gameplay-to-model yaw mapping: " + yaw);
            }
        }
    }

    private static void render(PlayerRenderer renderer, Matrix4f vp, PlayerPose pose, int vao) {
        glDisable(GL_DEPTH_TEST);
        glDepthMask(false);
        glDepthFunc(GL_GREATER);
        glEnable(GL_BLEND);
        glEnable(GL_CULL_FACE);
        glBindVertexArray(vao);
        int texture = pose == PlayerPose.NEUTRAL ? 0 : glCreateTextures(GL_TEXTURE_2D);
        int sampler = glCreateSamplers();
        int array = glCreateTextures(GL_TEXTURE_2D_ARRAY);
        glBindTextureUnit(0, texture);
        glBindTextureUnit(0, array);
        glBindSampler(0, sampler);
        glActiveTexture(GL_TEXTURE3);
        try {
            renderer.render(vp, new Matrix4f(), pose);
            check(
                    !glIsEnabled(GL_DEPTH_TEST)
                            && !glGetBoolean(GL_DEPTH_WRITEMASK)
                            && glGetInteger(GL_DEPTH_FUNC) == GL_GREATER
                            && glIsEnabled(GL_BLEND)
                            && glIsEnabled(GL_CULL_FACE)
                            && glGetInteger(GL_VERTEX_ARRAY_BINDING) == vao
                            && glGetInteger(GL_CURRENT_PROGRAM) == 0
                            && glGetInteger(GL_ACTIVE_TEXTURE) == GL_TEXTURE3
                            && glGetIntegeri(GL_TEXTURE_BINDING_2D, 0) == texture
                            && glGetIntegeri(GL_TEXTURE_BINDING_2D_ARRAY, 0) == array
                            && glGetIntegeri(GL_SAMPLER_BINDING, 0) == sampler,
                    "Leaked render state");
        } finally {
            glBindTextureUnit(0, 0);
            glBindSampler(0, 0);
            glDeleteSamplers(sampler);
            glDeleteTextures(texture);
            glDeleteTextures(array);
        }
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

    private static PlayerSkin directionalSkin() {
        byte[] rgba = new byte[64 * 64 * 4];
        for (var part : PlayerModel.PARTS) {
            for (var face : Face.values()) {
                int[] rect = rectangle(part, face);
                for (int y = 0; y < rect[3]; y++)
                    for (int x = 0; x < rect[2]; x++) {
                        int i = ((rect[1] + y) * 64 + rect[0] + x) * 4;
                        rgba[i] = (byte) (30 + part.part().ordinal() * 32);
                        rgba[i + 1] = (byte) (30 + face.ordinal() * 32);
                        rgba[i + 2] = (byte) (40 + (x >= rect[2] / 2 ? 80 : 0) + (y >= rect[3] / 2 ? 120 : 0));
                        rgba[i + 3] = (byte) 255;
                    }
            }
        }
        return new PlayerSkin(rgba);
    }

    private static int[] rectangle(PlayerModel.PartDefinition part, Face face) {
        int u = part.uvOrigin().u(), v = part.uvOrigin().v();
        int w = (int) part.cuboid().width(),
                h = (int) part.cuboid().height(),
                d = (int) part.cuboid().depth();
        return switch (face) {
            case FRONT -> new int[] {u + d, v + d, w, h};
            case BACK -> new int[] {u + 2 * d + w, v + d, w, h};
            case RIGHT -> new int[] {u, v + d, d, h};
            case LEFT -> new int[] {u + d + w, v + d, d, h};
            case TOP -> new int[] {u + d, v, w, d};
            case BOTTOM -> new int[] {u + d + w, v, w, d};
        };
    }

    private static void checkFace(PlayerRenderer renderer, PlayerSkin skin, Face face, int vao, Path path)
            throws Exception {
        Vector3f normal = switch (face) {
            case FRONT -> new Vector3f(0, 0, -1);
            case BACK -> new Vector3f(0, 0, 1);
            case RIGHT -> new Vector3f(1, 0, 0);
            case LEFT -> new Vector3f(-1, 0, 0);
            case TOP -> new Vector3f(0, 1, 0);
            case BOTTOM -> new Vector3f(0, -1, 0);
        };
        Vector3f up = switch (face) {
            case TOP -> new Vector3f(0, 0, 1);
            case BOTTOM -> new Vector3f(0, 0, -1);
            default -> new Vector3f(0, 1, 0);
        };
        var right = new Vector3f(up).cross(normal);
        var vp = new Matrix4f()
                .ortho(-1.5f, 1.5f, -1.5f, 1.5f, .1f, 20)
                .lookAt(new Vector3f(normal).mul(5), new Vector3f(), up);
        var pose = PlayerPose.NEUTRAL;
        var bodyShift = new Vector3f();
        for (var part : PlayerModel.PARTS) {
            int index = part.part().ordinal();
            var box = part.cuboid();
            var center = new Vector3f(box.x() + box.width() / 2, box.y() + box.height() / 2, box.z() + box.depth() / 2);
            PlayerModel.partMatrix(part.part(), PlayerPose.NEUTRAL, new Matrix4f())
                    .transformPosition(center);
            var desired = new Vector3f(right).mul((index % 3 - 1) * .85f).fma(index < 3 ? .6f : -.6f, up);
            var shift = desired.sub(center).mul(16);
            if (part.parent() != null) shift.sub(bodyShift);
            if (part.part() == BODY) bodyShift.set(shift);
            pose = pose.with(part.part(), new PartTransform(shift.x, shift.y, shift.z, 0, 0, 0));
        }
        clear(1);
        render(renderer, vp, pose, vao);
        var pixels = capture();
        var rgba = skin.rgba();
        for (var part : PlayerModel.PARTS) {
            int index = part.part().ordinal();
            int[] rect = rectangle(part, face);
            for (float fy : new float[] {.31f, .45f, .55f, .69f})
                for (float fx : new float[] {.31f, .45f, .55f, .69f}) {
                    float sx = (index % 3 - 1) * .85f + (fx - .5f) * rect[2] / 16;
                    float sy = (index < 3 ? .6f : -.6f) + (.5f - fy) * rect[3] / 16;
                    int offset = (((int) ((sy + 1.5f) / 3 * SIZE)) * SIZE + (int) ((sx + 1.5f) / 3 * SIZE)) * 4;
                    float textureY = face == Face.BOTTOM ? 1 - fy : fy;
                    int texel = ((rect[1] + (int) (textureY * rect[3])) * 64 + rect[0] + (int) (fx * rect[2])) * 4;
                    for (int c = 0; c < 3; c++) {
                        int expected = Byte.toUnsignedInt(rgba[texel + c]);
                        if (Byte.toUnsignedInt(rgba[texel + 3]) < 128) expected = new int[] {15, 20, 31}[c];
                        check(
                                Math.abs(Byte.toUnsignedInt(pixels.get(offset + c)) - expected) <= 1,
                                "Wrong texture direction/part/texel: " + path + " " + part.part() + " " + face + " "
                                        + fx + "," + fy + " channel=" + c);
                    }
                }
        }
        write(pixels, path);
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
