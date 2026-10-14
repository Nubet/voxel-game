package com.norbertfila.render;

import static org.lwjgl.glfw.GLFW.glfwSetWindowSize;
import static org.lwjgl.opengl.GL46C.*;

import com.norbertfila.core.GameSettings;
import com.norbertfila.core.Hotbar;
import com.norbertfila.core.SettingsPanel;
import com.norbertfila.platform.Window;
import com.norbertfila.ui.UiViewport;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.lwjgl.BufferUtils;

public final class UiViewportSmoke {
    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]);
        check(Files.isDirectory(output), "Output directory must exist");
        try (Window window = new Window(1280, 720, "UI-01 viewport smoke");
                UiRenderer ui = new UiRenderer()) {
            System.out.println("GPU=" + glGetString(GL_RENDERER) + " GL=" + glGetString(GL_VERSION) + " OS="
                    + System.getProperty("os.name") + " Java=" + Runtime.version());
            int[] ww = new int[1], wh = new int[1], fw = new int[1], fh = new int[1];
            for (int[] size : new int[][] {{640, 360}, {1281, 721}, {1280, 720}}) {
                glfwSetWindowSize(window.handle(), size[0], size[1]);
                window.pollEvents();
                window.windowSize(ww, wh);
                window.framebufferSize(fw, fh);
                check(ww[0] == size[0] && wh[0] == size[1], "Window resize dimensions");
                UiViewport viewport = new UiViewport(ww[0], wh[0], fw[0], fh[0]);
                glViewport(0, 0, fw[0], fh[0]);
                clear();
                ui.render(new SettingsPanel(new GameSettings()), new Hotbar(), viewport);
                check(glGetError() == GL_NO_ERROR, "Window resize render error");
                window.swapBuffers();
            }
            int framebuffer = glGenFramebuffers();
            int texture = glGenTextures();
            try {
                glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
                glBindTexture(GL_TEXTURE_2D, texture);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
                int[][] cases = {
                    {640, 360, 640, 360},
                    {1280, 720, 1280, 720},
                    {1920, 1080, 1920, 1080},
                    {1281, 721, 1281, 721},
                    {640, 360, 1280, 720},
                    {1280, 720, 1920, 1080},
                    {640, 480, 1280, 720},
                    {180, 100, 180, 100}
                };
                UiViewport reference = new UiViewport(640, 360, 640, 360);
                ByteBuffer referenceHud = null, referencePanel = null;
                for (int i = 0; i < cases.length; i++) {
                    int[] size = cases[i];
                    UiViewport viewport = new UiViewport(size[0], size[1], size[2], size[3]);
                    glTexImage2D(
                            GL_TEXTURE_2D, 0, GL_RGBA8, size[2], size[3], 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer)
                                    null);
                    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0);
                    check(
                            glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE,
                            "Incomplete framebuffer");
                    glViewport(0, 0, size[2], size[3]);
                    SettingsPanel panel = new SettingsPanel(new GameSettings());
                    Hotbar hotbar = new Hotbar();
                    hotbar.select(Hotbar.SLOTS - 1);
                    clear();
                    ui.render(panel, hotbar, viewport);
                    ByteBuffer pixels = capture(viewport);
                    if (i == 0) referenceHud = pixels;
                    if (i == 1) checkDoubleScale(referenceHud, pixels, reference, viewport);
                    int centerX = (int) viewport.width() / 2 * viewport.scale();
                    int centerY = (int) viewport.height() / 2 * viewport.scale();
                    check(rgb(pixels, viewport, centerX, centerY) == 0xffffff, "Crosshair missing");
                    if (viewport.width() >= 480) checkSlots(pixels, viewport);
                    write(output.resolve("ui01-" + i + "-hud.png"), pixels, viewport);
                    panel.toggle();
                    double windowX = (SettingsPanel.SLIDER_X + .25) * viewport.scale() * size[0] / size[2];
                    double windowY = (SettingsPanel.SLIDER_Y + 1) * viewport.scale() * size[1] / size[3];
                    check(
                            panel.update(true, true, viewport.cursorX(windowX), viewport.cursorY(windowY)),
                            "Slider missed");
                    check(panel.renderDistance() == GameSettings.MIN_RENDER_DISTANCE, "Wrong slider value");
                    clear();
                    ui.render(panel, hotbar, viewport);
                    pixels = capture(viewport);
                    if (i == 0) referencePanel = pixels;
                    if (i == 1) checkDoubleScale(referencePanel, pixels, reference, viewport);
                    int s = viewport.scale();
                    check(rgb(pixels, viewport, 24 * s, 25 * s) != 0, "Panel left edge missing");
                    check(rgb(pixels, viewport, 24 * s - 1, 25 * s) == 0, "Panel left edge stretched");
                    if (viewport.width() > 344) {
                        check(rgb(pixels, viewport, 344 * s - 1, 25 * s) != 0, "Panel right edge missing");
                        check(rgb(pixels, viewport, 344 * s, 25 * s) == 0, "Panel right edge stretched");
                    }
                    write(output.resolve("ui01-" + i + "-panel.png"), pixels, viewport);
                    check(glGetError() == GL_NO_ERROR, "Offscreen GL error");
                    System.out.println("PASS window=" + size[0] + "x" + size[1] + " framebuffer=" + size[2] + "x"
                            + size[3] + " scale=" + s + " GUI=" + viewport.width() + "x" + viewport.height());
                }
            } finally {
                glBindFramebuffer(GL_FRAMEBUFFER, 0);
                glDeleteTextures(texture);
                glDeleteFramebuffers(framebuffer);
            }
        }
        System.out.println("UI-01 GPU smoke PASS");
    }

    private static void clear() {
        glClearColor(0, 0, 0, 1);
        glClear(GL_COLOR_BUFFER_BIT);
    }

    private static ByteBuffer capture(UiViewport viewport) {
        ByteBuffer pixels =
                BufferUtils.createByteBuffer(viewport.framebufferWidth() * viewport.framebufferHeight() * 4);
        glReadPixels(
                0, 0, viewport.framebufferWidth(), viewport.framebufferHeight(), GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        return pixels;
    }

    private static void checkDoubleScale(
            ByteBuffer reference, ByteBuffer scaled, UiViewport original, UiViewport viewport) {
        for (int y = 0; y < viewport.framebufferHeight(); y++)
            for (int x = 0; x < viewport.framebufferWidth(); x++)
                check(
                        rgb(scaled, viewport, x, y) == rgb(reference, original, x / 2, y / 2),
                        "Scale 2 differs from exact pixel doubling");
    }

    private static void checkSlots(ByteBuffer pixels, UiViewport viewport) {
        int y = ((int) viewport.height() - 17) * viewport.scale();
        int slots = 0, selected = 0;
        boolean previous = false;
        for (int x = 0; x < viewport.framebufferWidth(); x++) {
            int color = rgb(pixels, viewport, x, y);
            boolean selection = color == 0xf5cc4c || color == 0xf5cc4d;
            boolean border = color == 0x404a59 || selection;
            if (border && !previous) {
                slots++;
                if (selection) selected++;
            }
            previous = border;
        }
        check(
                slots == Hotbar.SLOTS && selected == 1,
                "Expected 16 slots with one selection, got " + slots + "/" + selected);
    }

    private static int rgb(ByteBuffer pixels, UiViewport viewport, int x, int y) {
        int offset = ((viewport.framebufferHeight() - 1 - y) * viewport.framebufferWidth() + x) * 4;
        return Byte.toUnsignedInt(pixels.get(offset)) << 16
                | Byte.toUnsignedInt(pixels.get(offset + 1)) << 8
                | Byte.toUnsignedInt(pixels.get(offset + 2));
    }

    private static void write(Path path, ByteBuffer pixels, UiViewport viewport) throws Exception {
        BufferedImage image = new BufferedImage(
                viewport.framebufferWidth(), viewport.framebufferHeight(), BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < image.getHeight(); y++)
            for (int x = 0; x < image.getWidth(); x++) image.setRGB(x, y, rgb(pixels, viewport, x, y));
        ImageIO.write(image, "png", path.toFile());
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
