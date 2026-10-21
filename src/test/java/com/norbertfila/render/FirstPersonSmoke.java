package com.norbertfila.render;

import static org.lwjgl.opengl.GL46C.*;

import com.norbertfila.assets.SkinLoader;
import com.norbertfila.platform.Window;
import com.norbertfila.player.visual.PlayerAppearance;
import com.norbertfila.world.BlockId;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import javax.imageio.ImageIO;
import org.lwjgl.BufferUtils;

public final class FirstPersonSmoke {
    private static final int WIDTH = 640, HEIGHT = 360;

    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]);
        Files.createDirectories(output);
        var skin = SkinLoader.loadDefault();
        try (var window = new Window(WIDTH, HEIGHT, "First person smoke");
                var skins = new SkinManager(
                        Executors.newSingleThreadExecutor(),
                        () -> new SkinLoader.Loaded(skin, SkinLoader.BUNDLED_PATH, ""));
                var players = new PlayerRenderer(skins);
                var blockTextures = new BlockTextureArray(
                        new com.norbertfila.assets.AssetLoader(com.norbertfila.world.BlockRegistry.DEFAULT)
                                .loadBlockTextures());
                var items = new ItemEntityRenderer(blockTextures)) {
            PlayerModelSmoke.awaitSkin(skins);
            glViewport(0, 0, WIDTH, HEIGHT);
            var hand = new FirstPersonRenderer(players, items);
            clear();
            hand.render(WIDTH, HEIGHT, PlayerAppearance.DEFAULT, BlockId.AIR, 0, 0);
            var arm = capture();
            check(pixels(arm, WIDTH / 2, WIDTH, 0, HEIGHT / 2) > 2000, "Empty hand shows no arm");
            check(pixels(arm, 0, WIDTH / 2, 0, HEIGHT) < 200, "Arm reaches the left half of the screen");
            write(arm, output.resolve("arm.png"));
            for (float swing : new float[] {.25f, .5f, .75f}) {
                clear();
                hand.render(WIDTH, HEIGHT, PlayerAppearance.DEFAULT, BlockId.AIR, swing, 0);
                var swung = capture();
                check(!swung.equals(arm), "Swing does not move the arm");
                write(swung, output.resolve("arm-swing-" + (int) (swing * 100) + ".png"));
            }
            for (byte block : new byte[] {BlockId.GRASS, BlockId.POPPY, BlockId.COBBLE}) {
                for (float swing : new float[] {0, .5f}) {
                    clear();
                    hand.render(WIDTH, HEIGHT, PlayerAppearance.DEFAULT, block, swing, 0);
                    var held = capture();
                    check(pixels(held, WIDTH / 2, WIDTH, 0, HEIGHT) > 300, "Held block " + block + " not visible");
                    write(held, output.resolve("block-" + block + "-swing-" + (int) (swing * 100) + ".png"));
                }
            }
            for (var tool : new com.norbertfila.items.Tool[] {
                com.norbertfila.items.Tool.DIAMOND_SWORD, com.norbertfila.items.Tool.IRON_PICKAXE
            }) {
                for (float swing : new float[] {0, .5f}) {
                    clear();
                    hand.render(WIDTH, HEIGHT, PlayerAppearance.DEFAULT, tool.id(), swing, 0);
                    var held = capture();
                    check(pixels(held, WIDTH / 2, WIDTH, 0, HEIGHT) > 300, "Held " + tool + " not visible");
                    write(held, output.resolve(tool.itemName() + "-swing-" + (int) (swing * 100) + ".png"));
                }
            }
            System.out.println("PASS first-person arm, held blocks and tools");
        }
    }

    static void clear() {
        glClearColor(0, 0, 0, 1);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
    }

    static ByteBuffer capture() {
        var pixels = BufferUtils.createByteBuffer(WIDTH * HEIGHT * 4);
        glReadPixels(0, 0, WIDTH, HEIGHT, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        return pixels;
    }

    /** Counts non-black pixels in a rectangle whose rows start at the bottom of the screen. */
    static int pixels(ByteBuffer image, int fromX, int toX, int fromY, int toY) {
        int count = 0;
        for (int y = fromY; y < toY; y++)
            for (int x = fromX; x < toX; x++) {
                int i = (y * WIDTH + x) * 4;
                if ((image.get(i) | image.get(i + 1) | image.get(i + 2)) != 0) count++;
            }
        return count;
    }

    static void write(ByteBuffer pixels, Path path) throws Exception {
        var image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < HEIGHT; y++)
            for (int x = 0; x < WIDTH; x++) {
                int i = (y * WIDTH + x) * 4;
                image.setRGB(
                        x,
                        HEIGHT - y - 1,
                        Byte.toUnsignedInt(pixels.get(i)) << 16
                                | Byte.toUnsignedInt(pixels.get(i + 1)) << 8
                                | Byte.toUnsignedInt(pixels.get(i + 2)));
            }
        ImageIO.write(image, "png", path.toFile());
    }

    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
