package com.norbertfila.render;

import static org.lwjgl.glfw.GLFW.glfwSetWindowSize;
import static org.lwjgl.opengl.GL46C.*;

import com.norbertfila.core.GameSettings;
import com.norbertfila.core.Hotbar;
import com.norbertfila.platform.Window;
import com.norbertfila.player.Inventory;
import com.norbertfila.ui.BitmapFont;
import com.norbertfila.ui.Button;
import com.norbertfila.ui.Hud;
import com.norbertfila.ui.Screen;
import com.norbertfila.ui.ScreenManager;
import com.norbertfila.ui.Slider;
import com.norbertfila.ui.UiBounds;
import com.norbertfila.ui.UiCanvas;
import com.norbertfila.ui.UiKey;
import com.norbertfila.ui.UiViewport;
import com.norbertfila.ui.screens.InventoryScreen;
import com.norbertfila.ui.screens.PauseScreen;
import com.norbertfila.ui.screens.SettingsScreen;
import com.norbertfila.world.BlockId;
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
        UiCanvas canvas = new UiCanvas();
        try (Window window = new Window(1280, 720, "Inventory screens smoke");
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
                render(ui, canvas, new ScreenManager(), new Hotbar(), viewport);
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
                    glBindTexture(GL_TEXTURE_2D, texture);
                    glTexImage2D(
                            GL_TEXTURE_2D, 0, GL_RGBA8, size[2], size[3], 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer)
                                    null);
                    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0);
                    check(
                            glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE,
                            "Incomplete framebuffer");
                    glViewport(0, 0, size[2], size[3]);
                    SettingsScreen panel = new SettingsScreen(new GameSettings());
                    ScreenManager screens = new ScreenManager();
                    Hotbar hotbar = new Hotbar();
                    hotbar.select(Hotbar.SLOTS - 1);
                    clear();
                    render(ui, canvas, screens, hotbar, viewport);
                    ByteBuffer pixels = capture(viewport);
                    if (i == 0) referenceHud = pixels;
                    if (i == 1) checkDoubleScaleExceptHotbar(referenceHud, pixels, reference, viewport);
                    int centerX = (int) viewport.width() / 2 * viewport.scale();
                    int centerY = (int) viewport.height() / 2 * viewport.scale();
                    check(rgb(pixels, viewport, centerX, centerY) == 0xffffff, "Crosshair missing");
                    if (viewport.width() >= 480) checkSlots(pixels, viewport);
                    write(output.resolve("inventory-" + i + "-hud.png"), pixels, viewport);
                    screens.open(panel);
                    screens.updatePointer(false, false, 0, 0);
                    double windowX = (SettingsScreen.SLIDER_X + .25) * viewport.scale() * size[0] / size[2];
                    double windowY = (SettingsScreen.SLIDER_Y + 1) * viewport.scale() * size[1] / size[3];
                    screens.updatePointer(true, true, viewport.cursorX(windowX), viewport.cursorY(windowY));
                    check(panel.renderDistance() == GameSettings.MIN_RENDER_DISTANCE, "Wrong slider value");
                    clear();
                    render(ui, canvas, screens, hotbar, viewport);
                    pixels = capture(viewport);
                    if (i == 0) referencePanel = pixels;
                    if (i == 1) checkDoubleScale(referencePanel, pixels, reference, viewport);
                    int s = viewport.scale();
                    check(rgb(pixels, viewport, 24 * s, 25 * s) != 0, "Panel left edge missing");
                    check(rgb(pixels, viewport, 24 * s - 1, 25 * s) == 0, "Panel left edge stretched");
                    int panelRight = SettingsScreen.PANEL_X + SettingsScreen.PANEL_WIDTH;
                    if (viewport.width() > panelRight) {
                        check(rgb(pixels, viewport, panelRight * s - 1, 25 * s) != 0, "Panel right edge missing");
                        check(rgb(pixels, viewport, panelRight * s, 25 * s) == 0, "Panel right edge stretched");
                    }
                    write(output.resolve("inventory-" + i + "-settings.png"), pixels, viewport);
                    check(glGetError() == GL_NO_ERROR, "Offscreen GL error");
                    System.out.println("PASS window=" + size[0] + "x" + size[1] + " framebuffer=" + size[2] + "x"
                            + size[3] + " scale=" + s + " GUI=" + viewport.width() + "x" + viewport.height());
                }
                UiViewport small = new UiViewport(180, 100, 180, 100);
                canvas.clear();
                for (int i = 0; i < 3000; i++) canvas.rectangle(0, 0, 8, 8, 1, 0, 0, 1);
                canvas.rectangle(0, 0, 8, 8, 0, 1, 0, 1);
                clear();
                ui.render(canvas, small);
                check(rgb(capture(small), small, 1, 1) == 0x00ff00, "Large canvas lost painter order");
                canvas.clear();
                clear();
                ui.render(canvas, small);
                check(rgb(capture(small), small, 1, 1) == 0, "Empty canvas drew stale vertices");
                canvas.rectangle(0, 0, 8, 8, 0, 0, 1, 1);
                ui.render(canvas, small);
                check(rgb(capture(small), small, 1, 1) == 0x0000ff, "Reused canvas drew stale vertices");
                check(glGetError() == GL_NO_ERROR, "Canvas growth/reuse GL error");
                checkFont(ui, canvas, small, output);
                checkWidgets(ui, canvas, small, output);
                checkScreens(ui, canvas, texture, output);
                checkInventory(ui, canvas, texture, output);
            } finally {
                glBindFramebuffer(GL_FRAMEBUFFER, 0);
                glDeleteTextures(texture);
                glDeleteFramebuffers(framebuffer);
            }
            int atlas = glGetInteger(GL_TEXTURE_BINDING_2D);
            check(atlas != 0, "Missing atlas binding");
            ui.close();
            check(!glIsTexture(atlas), "Atlas leaked after renderer close");
            check(glGetError() == GL_NO_ERROR, "Renderer cleanup error");
        }
        System.out.println("UI-INVENTORY-01 GPU smoke PASS");
    }

    private static void render(
            UiRenderer ui, UiCanvas canvas, ScreenManager screens, Hotbar hotbar, UiViewport viewport) {
        canvas.clear();
        if (screens.isOpen()) screens.render(canvas, viewport);
        else new Hud().render(canvas, hotbar, viewport);
        ui.render(canvas, viewport);
    }

    private static void checkFont(UiRenderer ui, UiCanvas canvas, UiViewport viewport, Path output) throws Exception {
        BitmapFont font = BitmapFont.DEFAULT;
        canvas.clear();
        canvas.rectangle(0, 0, 180, 100, 0, 0, 1, 1);
        canvas.textWithShadow(font, "F", 8, 8, 1, 1, 1, 1);
        canvas.textWithShadow(font, "F10: Aa? 9", 8, 32, 1, 1, 1, 1);
        canvas.text(font, "Snowman: \u2603", 8, 56, 1, 1, 1, 1);
        clear();
        ui.render(canvas, viewport);
        check(
                glGetTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER) == GL_NEAREST,
                "Atlas minification must be nearest");
        check(
                glGetTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER) == GL_NEAREST,
                "Atlas magnification must be nearest");
        check(glGetTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAX_LEVEL) == 0, "Atlas mipmaps must be disabled");
        ByteBuffer pixels = capture(viewport);
        byte[] atlas = font.atlasPixels();
        int ink = 0, shadow = 0, background = 0;
        for (int y = 0; y <= BitmapFont.GLYPH_HEIGHT; y++)
            for (int x = 0; x <= font.glyphWidth('F'); x++) {
                boolean foreground = opaque(atlas, font, 'F', x, y);
                boolean shaded = opaque(atlas, font, 'F', x - 1, y - 1);
                int expected = foreground ? 0xffffff : shaded ? 0x404040 : 0x0000ff;
                check(
                        rgb(pixels, viewport, 8 + x, 8 + y) == expected,
                        "Font alpha, orientation or shadow differs from atlas");
                if (foreground) ink++;
                else if (shaded) shadow++;
                else background++;
            }
        check(ink > 0 && shadow > 0 && background > 0, "Incomplete font pixel checks");
        check(glGetError() == GL_NO_ERROR, "Font render GL error");
        write(output.resolve("ui05-font.png"), pixels, viewport);
    }

    private static void checkWidgets(UiRenderer ui, UiCanvas canvas, UiViewport viewport, Path output)
            throws Exception {
        Button button = new Button(new UiBounds(20, 10, 80, 24), "Button");
        Slider slider = new Slider(new UiBounds(20, 60, 120, 16), 2, 16, 1, 4);
        renderWidgets(ui, canvas, viewport, button, slider);
        int normal = rgb(capture(viewport), viewport, 21, 11);
        button.updatePointer(false, false, 21, 11);
        renderWidgets(ui, canvas, viewport, button, slider);
        int hovered = rgb(capture(viewport), viewport, 21, 11);
        check(hovered != normal, "Button hover is not visible");
        write(output.resolve("ui05-button-hover.png"), capture(viewport), viewport);
        check(!button.updatePointer(true, true, 21, 11), "Button fired before release");
        renderWidgets(ui, canvas, viewport, button, slider);
        check(rgb(capture(viewport), viewport, 21, 11) != hovered, "Button press is not visible");
        write(output.resolve("ui05-button-pressed.png"), capture(viewport), viewport);
        check(!button.updatePointer(false, false, 150, 40), "Release outside activated button");
        button.updatePointer(true, true, 21, 11);
        check(button.updatePointer(false, false, 21, 11), "Release inside did not activate");
        button.setFocused(true);
        check(button.keyPress(UiKey.ACTIVATE), "Focused button rejected keyboard");
        renderWidgets(ui, canvas, viewport, button, slider);
        check((rgb(capture(viewport), viewport, 19, 9) & 0xffff00) == 0xf5cc00, "Button focus is not visible");
        write(output.resolve("ui05-button-focus.png"), capture(viewport), viewport);
        button.setEnabled(false);
        check(!button.keyPress(UiKey.ACTIVATE), "Disabled button activated");
        renderWidgets(ui, canvas, viewport, button, slider);
        check(rgb(capture(viewport), viewport, 21, 11) != normal, "Disabled button is not visible");
        check(rgb(capture(viewport), viewport, 19, 9) == 0, "Disabled button retained focus border");
        write(output.resolve("ui05-button-disabled.png"), capture(viewport), viewport);
        slider.setFocused(true);
        slider.updatePointer(true, true, 20, 60);
        check(slider.dragging() && slider.value() == 2, "Slider did not capture press");
        slider.updatePointer(true, false, 1000, -500);
        check(slider.value() == 16, "Slider drag did not clamp outside bounds");
        renderWidgets(ui, canvas, viewport, button, slider);
        check((rgb(capture(viewport), viewport, 14, 83) & 0xffff00) == 0xf5cc00, "Slider focus is not visible");
        write(output.resolve("ui05-slider-drag.png"), capture(viewport), viewport);
        slider.updatePointer(false, false, 1000, -500);
        check(!slider.dragging() && slider.keyPress(UiKey.DECREASE) && slider.value() == 15, "Slider keyboard failed");
        slider.cancelInteraction();
        check(!slider.dragging() && !slider.focused(), "Slider cancellation failed");
        check(glGetError() == GL_NO_ERROR, "Widget rendering GL error");
    }

    private static void renderWidgets(
            UiRenderer ui, UiCanvas canvas, UiViewport viewport, Button button, Slider slider) {
        canvas.clear();
        button.render(canvas, BitmapFont.DEFAULT);
        slider.render(canvas);
        clear();
        ui.render(canvas, viewport);
    }

    private static void checkScreens(UiRenderer ui, UiCanvas canvas, int texture, Path output) throws Exception {
        UiViewport viewport = new UiViewport(640, 360, 640, 360);
        glBindTexture(GL_TEXTURE_2D, texture);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, 640, 360, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
        glViewport(0, 0, 640, 360);
        check(
                glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE,
                "Screen fixture framebuffer incomplete");
        GameSettings settings = new GameSettings();
        SettingsScreen screen = new SettingsScreen(settings);
        ScreenManager screens = new ScreenManager();
        Hotbar hotbar = new Hotbar();
        hotbar.select(Hotbar.SLOTS - 1);
        screens.beginFrame();
        screens.open(screen);
        screens.updatePointer(false, false, 0, 0);
        for (int i = 0; i < 9; i++) screens.keyPress(UiKey.NEXT);
        check(screen.backButton().focused(), "Back missing from keyboard navigation");
        clear();
        render(ui, canvas, screens, hotbar, viewport);
        ByteBuffer pixels = capture(viewport);
        check(
                (rgb(pixels, viewport, SettingsScreen.BACK_BOUNDS.x() - 1, SettingsScreen.BACK_BOUNDS.y() - 1)
                                & 0xffff00)
                        == 0xf5cc00,
                "Back focus not rendered");
        write(output.resolve("ui05-settings-back-focus.png"), pixels, viewport);
        double x = SettingsScreen.BACK_BOUNDS.x() + 2, y = SettingsScreen.BACK_BOUNDS.y() + 2;
        screens.updatePointer(true, true, x, y);
        check(screens.isOpen(), "Back activated before release");
        screens.updatePointer(false, false, x, y);
        check(!screens.isOpen() && screens.blocksGameplay(), "Back did not close and consume its frame");
        clear();
        render(ui, canvas, screens, hotbar, viewport);
        pixels = capture(viewport);
        checkSlots(pixels, viewport);
        write(output.resolve("ui05-back-to-hud.png"), pixels, viewport);
        screens.beginFrame();
        check(!screens.blocksGameplay(), "Gameplay remained blocked after close frame");
        screens.open(screen);
        screens.updatePointer(false, false, 0, 0);
        screens.updatePointer(true, true, SettingsScreen.SLIDER_X, SettingsScreen.SLIDER_Y);
        check(screen.slider().dragging(), "Settings slider did not capture");
        screens.cancelInteraction();
        screens.updatePointer(
                true, true, SettingsScreen.SLIDER_X + SettingsScreen.SLIDER_WIDTH, SettingsScreen.SLIDER_Y);
        check(
                !screen.slider().dragging() && settings.renderDistance() == GameSettings.MIN_RENDER_DISTANCE,
                "Cancelled screen reacquired held pointer");
        screens.keyPress(UiKey.BACK);
        check(!screens.isOpen() && screens.blocksGameplay(), "Escape did not close settings");
        screens.beginFrame();
        screens.keyPress(UiKey.ACTIVATE);
        check(!screens.blocksGameplay() && hotbar.selectedSlot() == 8, "Closed screen retained input");
        PauseScreen pause = new PauseScreen();
        screens.open(pause);
        clear();
        render(ui, canvas, screens, hotbar, viewport);
        pixels = capture(viewport);
        check(rgb(pixels, viewport, 160, 100) != 0, "Pause panel missing");
        screens.keyPress(UiKey.BACK);
        check(!screens.isOpen() && screens.blocksGameplay(), "Pause close leaked input");
        check(glGetError() == GL_NO_ERROR, "Screen lifecycle GL error");
    }

    private static boolean opaque(byte[] atlas, BitmapFont font, int character, int x, int y) {
        if (x < 0 || y < 0 || x >= font.glyphWidth(character) || y >= BitmapFont.GLYPH_HEIGHT) return false;
        int atlasX = character % 16 * BitmapFont.CELL_SIZE + x;
        int atlasY = character / 16 * BitmapFont.CELL_SIZE + y;
        return atlas[(atlasY * BitmapFont.ATLAS_WIDTH + atlasX) * 4 + 3] != 0;
    }

    private static void checkInventory(UiRenderer ui, UiCanvas canvas, int texture, Path output) throws Exception {
        int[][] sizes = {
            {640, 360, 640, 360},
            {1280, 720, 1280, 720},
            {1920, 1080, 1920, 1080},
            {1281, 721, 1281, 721},
            {640, 360, 1280, 720},
            {1280, 720, 1920, 1080},
            {640, 480, 1280, 720}
        };
        for (boolean creative : new boolean[] {false, true}) {
            ByteBuffer reference = null;
            for (int i = 0; i < sizes.length; i++) {
                int[] size = sizes[i];
                var viewport = new UiViewport(size[0], size[1], size[2], size[3]);
                glBindTexture(GL_TEXTURE_2D, texture);
                glTexImage2D(
                        GL_TEXTURE_2D, 0, GL_RGBA8, size[2], size[3], 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
                glViewport(0, 0, size[2], size[3]);
                Inventory inventory = new Inventory();
                inventory.creativePick(0, BlockId.POPPY);
                inventory.creativePick(8, BlockId.WATER);
                inventory.click(0, false, true);
                var screen = new InventoryScreen(creative);
                screen.update(inventory.snapshot(), 8, viewport);
                var screens = new ScreenManager();
                screens.open(screen);
                screens.updatePointer(false, false, 0, 0);
                var box = screen.bounds(0);
                double windowX = (box.x() + 5) * viewport.scale() * (double) size[0] / size[2];
                double windowY = (box.y() + 5) * viewport.scale() * (double) size[1] / size[3];
                screens.updatePointer(new Screen.Pointer(
                        false, false, true, true, false, viewport.cursorX(windowX), viewport.cursorY(windowY)));
                var action = screens.pollAction();
                if (creative) {
                    check(
                            action instanceof Screen.Action.CreativePick pick && pick.slot() == 8,
                            "Creative slot mapping");
                    var pick = (Screen.Action.CreativePick) action;
                    inventory.creativePick(pick.slot(), pick.block());
                } else {
                    check(action.equals(new Screen.Action.SlotClick(9, true, false)), "Survival DPI slot mapping");
                    inventory.click(9, true, false);
                    check(
                            inventory.snapshot().count(36) == 32
                                    && inventory.snapshot().count(9) == 32,
                            "Right split lost items");
                }
                screen.update(inventory.snapshot(), 8, viewport);
                clear();
                render(ui, canvas, screens, new Hotbar(), viewport);
                ByteBuffer pixels = capture(viewport);
                for (int cell = 0; cell < 36; cell++) {
                    var bounds = screen.bounds(cell);
                    int color = rgb(
                            pixels,
                            viewport,
                            (bounds.x() + bounds.width() - 1) * viewport.scale(),
                            (bounds.y() + bounds.height() - 1) * viewport.scale());
                    check(
                            (color & 0xffff00) == (cell == 35 ? 0xf2cc00 : 0x666e00),
                            "Inventory slot border missing " + cell);
                }
                if (i == 0) reference = pixels;
                if (i == 1) checkDoubleScale(reference, pixels, new UiViewport(640, 360, 640, 360), viewport);
                write(
                        output.resolve("inventory-" + (creative ? "creative-" : "survival-") + i + ".png"),
                        pixels,
                        viewport);
                screens.cancelInteraction();
                screens.updatePointer(new Screen.Pointer(true, true, false, false, false, box.x() + 1, box.y() + 1));
                check(screens.pollAction() == null, "Cancelled inventory reacquired held pointer");
                screens.keyPress(UiKey.BACK);
                check(!screens.isOpen() && screens.blocksGameplay(), "Inventory close leaked input");
                inventory.returnCarried();
                check(inventory.snapshot().count(36) == 0, "Inventory close failed to return carried stack");
                Hotbar hotbar = new Hotbar();
                hotbar.inventory(inventory.snapshot());
                hotbar.select(8);
                clear();
                render(ui, canvas, screens, hotbar, viewport);
                checkSlots(capture(viewport), viewport);
                check(glGetError() == GL_NO_ERROR, "Inventory GL error");
            }
        }
        System.out.println("PASS inventory: Survival/Creative, 7 DPI/resolution cases, split/catalog/cursor/close/HUD");
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

    private static void checkDoubleScaleExceptHotbar(
            ByteBuffer reference, ByteBuffer scaled, UiViewport original, UiViewport viewport) {
        for (int y = 0; y < viewport.framebufferHeight(); y++)
            for (int x = 0; x < viewport.framebufferWidth(); x++) {
                if (y >= viewport.framebufferHeight() - 40 * viewport.scale()) continue;
                check(
                        rgb(scaled, viewport, x, y) == rgb(reference, original, x / 2, y / 2),
                        "Scale 2 differs outside compact hotbar");
            }
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
                "Expected " + Hotbar.SLOTS + " slots with one selection, got " + slots + "/" + selected);
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
