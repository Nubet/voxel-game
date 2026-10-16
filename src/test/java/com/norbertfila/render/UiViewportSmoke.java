package com.norbertfila.render;

import static org.lwjgl.glfw.GLFW.glfwSetWindowSize;
import static org.lwjgl.opengl.GL46C.*;

import com.norbertfila.chat.ChatHistory;
import com.norbertfila.core.GameSettings;
import com.norbertfila.core.Hotbar;
import com.norbertfila.diagnostics.FrameMetrics;
import com.norbertfila.i18n.Language;
import com.norbertfila.platform.Window;
import com.norbertfila.player.Inventory;
import com.norbertfila.player.PlayerState;
import com.norbertfila.ui.BitmapFont;
import com.norbertfila.ui.Button;
import com.norbertfila.ui.ChatOverlay;
import com.norbertfila.ui.DebugOverlay;
import com.norbertfila.ui.Hud;
import com.norbertfila.ui.ItemIcons;
import com.norbertfila.ui.Screen;
import com.norbertfila.ui.ScreenManager;
import com.norbertfila.ui.Slider;
import com.norbertfila.ui.UiAtlas;
import com.norbertfila.ui.UiBounds;
import com.norbertfila.ui.UiCanvas;
import com.norbertfila.ui.UiEvent;
import com.norbertfila.ui.UiKey;
import com.norbertfila.ui.UiViewport;
import com.norbertfila.ui.WorldUiFixtures;
import com.norbertfila.ui.crosshair.CrosshairRenderer;
import com.norbertfila.ui.crosshair.CrosshairSettings;
import com.norbertfila.ui.crosshair.CrosshairStyle;
import com.norbertfila.ui.screens.ChatScreen;
import com.norbertfila.ui.screens.CreateWorldScreen;
import com.norbertfila.ui.screens.CrosshairSettingsScreen;
import com.norbertfila.ui.screens.InventoryScreen;
import com.norbertfila.ui.screens.MainMenuScreen;
import com.norbertfila.ui.screens.PauseScreen;
import com.norbertfila.ui.screens.SaveErrorScreen;
import com.norbertfila.ui.screens.SessionStatusScreen;
import com.norbertfila.ui.screens.SettingsScreen;
import com.norbertfila.ui.screens.WorldErrorScreen;
import com.norbertfila.ui.screens.WorldSelectionScreen;
import com.norbertfila.world.BlockId;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
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
                    {2560, 1440, 2560, 1440},
                    {3840, 2160, 3840, 2160},
                    {1280, 720, 1280, 720, 1},
                    {1280, 720, 1280, 720, 2},
                    {1280, 720, 1280, 720, 3},
                    {1280, 720, 1280, 720, 4},
                    {180, 100, 180, 100}
                };
                for (int i = 0; i < cases.length; i++) {
                    int[] size = cases[i];
                    UiViewport viewport =
                            new UiViewport(size[0], size[1], size[2], size[3], size.length > 4 ? size[4] : 0);
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
                    int centerX = (int) viewport.width() / 2 * viewport.scale();
                    int centerY = (int) viewport.height() / 2 * viewport.scale();
                    check(rgb(pixels, viewport, centerX, centerY) == 0xffffff, "Crosshair missing");
                    if (viewport.width() >= 480) checkSlots(pixels, viewport);
                    write(output.resolve("inventory-" + i + "-hud.png"), pixels, viewport);
                    checkDebugOverlay(ui, canvas, hotbar, viewport, output.resolve("debug-f3-" + i + ".png"));
                    checkChat(ui, canvas, viewport, output.resolve("chat-" + i + ".png"));
                    if (args.length > 1 && args[1].equals("--crosshair-only"))
                        checkCrosshair(ui, canvas, viewport, output, i);
                    screens.open(panel);
                    screens.layout(viewport);
                    screens.updatePointer(false, false, 0, 0);
                    var sliderBounds = panel.slider().bounds();
                    screens.updatePointer(true, true, sliderBounds.x() + .25, sliderBounds.y() + 1);
                    check(panel.renderDistance() == GameSettings.MIN_RENDER_DISTANCE, "Wrong slider value");
                    clear();
                    render(ui, canvas, screens, hotbar, viewport);
                    pixels = capture(viewport);
                    int s = viewport.scale();
                    var panelBounds = panel.panelBounds();
                    check(
                            rgb(pixels, viewport, panelBounds.x() * s, (panelBounds.y() + 1) * s) != 0,
                            "Panel left edge missing");
                    if (panelBounds.x() > 0)
                        check(
                                rgb(pixels, viewport, panelBounds.x() * s - 1, (panelBounds.y() + 1) * s) == 0,
                                "Panel left edge stretched");
                    int panelRight = panelBounds.x() + panelBounds.width();
                    if (panelRight < viewport.width()) {
                        check(
                                rgb(pixels, viewport, panelRight * s - 1, (panelBounds.y() + 1) * s) != 0,
                                "Panel right edge missing");
                        check(
                                rgb(pixels, viewport, panelRight * s, (panelBounds.y() + 1) * s) == 0,
                                "Panel right edge stretched");
                    }
                    write(output.resolve("inventory-" + i + "-settings.png"), pixels, viewport);
                    check(glGetError() == GL_NO_ERROR, "Offscreen GL error");
                    System.out.println("PASS window=" + size[0] + "x" + size[1] + " framebuffer=" + size[2] + "x"
                            + size[3] + " scale=" + s + " GUI=" + viewport.width() + "x" + viewport.height());
                }
                if (args.length > 1
                        && (args[1].equals("--debug-only")
                                || args[1].equals("--chat-only")
                                || args[1].equals("--crosshair-only"))) {
                    System.out.println(args[1] + " GPU smoke PASS: " + cases.length + " viewport/DPI cases");
                    return;
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
                checkItemSprites(ui, canvas, texture, output);
                checkSessionScreens(ui, canvas, texture, output);
                checkWorldScreens(ui, canvas, texture, output);
                checkLanguages(ui, canvas, texture, output);
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
        System.out.println("UI-09 GPU smoke PASS");
    }

    private static void render(
            UiRenderer ui, UiCanvas canvas, ScreenManager screens, Hotbar hotbar, UiViewport viewport) {
        canvas.clear();
        if (screens.isOpen()) screens.render(canvas, viewport);
        else {
            new Hud().render(canvas, hotbar, viewport);
            new CrosshairRenderer().render(canvas, viewport);
        }
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
        byte[] atlas = UiAtlas.defaultAtlas().pixels();
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
        screens.layout(viewport);
        screens.updatePointer(false, false, 0, 0);
        for (int i = 0; i < 16 && !screen.backButton().focused(); i++) screens.keyPress(UiKey.NEXT);
        check(screen.backButton().focused(), "Back missing from keyboard navigation");
        clear();
        render(ui, canvas, screens, hotbar, viewport);
        ByteBuffer pixels = capture(viewport);
        check(
                (rgb(
                                        pixels,
                                        viewport,
                                        screen.backButton().bounds().x() - 1,
                                        screen.backButton().bounds().y() - 1)
                                & 0xffff00)
                        == 0xf5cc00,
                "Back focus not rendered");
        write(output.resolve("ui05-settings-back-focus.png"), pixels, viewport);
        double x = screen.backButton().bounds().x() + 2,
                y = screen.backButton().bounds().y() + 2;
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
        screens.layout(viewport);
        screens.updatePointer(false, false, 0, 0);
        var settingsSlider = screen.slider().bounds();
        screens.updatePointer(true, true, settingsSlider.x(), settingsSlider.y() + 1);
        check(screen.slider().dragging(), "Settings slider did not capture");
        screens.cancelInteraction();
        screens.updatePointer(true, true, settingsSlider.x() + settingsSlider.width(), settingsSlider.y() + 1);
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

    private static void checkChat(UiRenderer ui, UiCanvas canvas, UiViewport viewport, Path output) throws Exception {
        var history = new ChatHistory();
        history.add("Zażółć gęślą jaźń: chat wrapping across narrow viewports.", ChatHistory.Kind.PLAYER, 0);
        history.add("Unknown command. Try /help.", ChatHistory.Kind.ERROR, 0);
        var overlay = new ChatOverlay();
        canvas.clear();
        overlay.render(canvas, viewport, history, false, 0, 20);
        check(canvas.vertexCount() == 0, "Expired chat is still visible");
        var screen = new ChatScreen(history, "/give @s stone 64", text -> {});
        overlay.render(canvas, viewport, history, true, 0, 20);
        screen.render(canvas, viewport);
        var geometry = FloatBuffer.allocate(canvas.floatCount());
        canvas.writeTo(geometry);
        for (int i = 0; i < geometry.position(); i += UiCanvas.FLOATS_PER_VERTEX) {
            check(geometry.get(i) >= 0 && geometry.get(i) <= viewport.width(), "Chat exceeds viewport width");
            check(geometry.get(i + 1) >= 0 && geometry.get(i + 1) <= viewport.height(), "Chat exceeds viewport height");
        }
        clear();
        ui.render(canvas, viewport);
        check(glGetError() == GL_NO_ERROR, "Chat GL error");
        var pixels = capture(viewport);
        check(
                rgb(pixels, viewport, 5 * viewport.scale(), ((int) viewport.height() - 25) * viewport.scale()) != 0,
                "Chat input missing");
        write(output, pixels, viewport);
    }

    private static void checkCrosshair(UiRenderer ui, UiCanvas canvas, UiViewport viewport, Path output, int index)
            throws Exception {
        var renderer = new CrosshairRenderer();
        var pixels = BufferUtils.createByteBuffer(viewport.framebufferWidth() * viewport.framebufferHeight() * 4);
        for (var style : CrosshairStyle.values()) {
            for (int scale : new int[] {1, 4}) {
                for (int opacity : new int[] {0, 50, 100}) {
                    canvas.clear();
                    renderer.render(
                            canvas, viewport, style, new CrosshairSettings(true, true, true, scale, opacity), 1);
                    clear();
                    ui.render(canvas, viewport);
                    glReadPixels(
                            0,
                            0,
                            viewport.framebufferWidth(),
                            viewport.framebufferHeight(),
                            GL_RGBA,
                            GL_UNSIGNED_BYTE,
                            pixels);
                    int offset = style == CrosshairStyle.BLOCK ? 3 : 0;
                    int px = (int) Math.ceil((viewport.width() / 2 + offset * scale) * viewport.scale()) - 1;
                    int py = (int) Math.ceil(viewport.height() / 2 * viewport.scale()) - 1;
                    int value = 0;
                    // Odd-sized shapes centered on an even framebuffer can straddle pixel centers.
                    for (int dy = -1; dy <= 1; dy++)
                        for (int dx = -1; dx <= 1; dx++)
                            value = Math.max(value, rgb(pixels, viewport, px + dx, py + dy));
                    int expected = style == CrosshairStyle.HIDDEN ? 0 : Math.round(255 * opacity / 100f);
                    for (int shift : new int[] {0, 8, 16})
                        check(
                                Math.abs(((value >> shift) & 255) - expected) <= 1,
                                "Crosshair pixel/alpha mismatch: " + style + " scale=" + scale + " opacity=" + opacity
                                        + " actual=" + Integer.toHexString(value));
                    if (style == CrosshairStyle.BLOCK)
                        check(
                                rgb(pixels, viewport, viewport.framebufferWidth() / 2, viewport.framebufferHeight() / 2)
                                        == 0,
                                "Block crosshair center must be empty");
                    if (opacity == 100 && scale == 1)
                        write(output.resolve("crosshair-" + index + "-" + style + ".png"), pixels, viewport);
                    check(glGetError() == GL_NO_ERROR, "Crosshair GL error");
                }
            }
        }
        if (viewport.width() >= 304 && viewport.height() >= 240) {
            for (var language : Language.values()) {
                var settings = new GameSettings();
                settings.translations().setLanguage(language);
                settings.setCrosshair(new CrosshairSettings(true, true, true, 4, 100));
                var screen = new CrosshairSettingsScreen(settings);
                for (int variant = 0; variant < 4; variant++) {
                    canvas.clear();
                    screen.render(canvas, viewport);
                    clear();
                    ui.render(canvas, viewport);
                    glReadPixels(
                            0,
                            0,
                            viewport.framebufferWidth(),
                            viewport.framebufferHeight(),
                            GL_RGBA,
                            GL_UNSIGNED_BYTE,
                            pixels);
                    write(
                            output.resolve("crosshair-settings-" + index + "-" + language + "-" + variant + ".png"),
                            pixels,
                            viewport);
                    if (variant == 0) {
                        screen.keyPress(UiKey.PREVIOUS);
                        screen.keyPress(UiKey.PREVIOUS);
                    }
                    screen.keyPress(UiKey.ACTIVATE);
                }
            }
        }
    }

    private static void checkDebugOverlay(
            UiRenderer ui, UiCanvas canvas, Hotbar hotbar, UiViewport viewport, Path output) throws Exception {
        clear();
        canvas.clear();
        canvas.rectangle(0, 0, (int) viewport.width(), (int) viewport.height(), 0, 0, 1, 1);
        new DebugOverlay()
                .render(
                        canvas,
                        viewport,
                        new DebugOverlay.Data(
                                new FrameMetrics.OverlaySnapshot(120, 8.33, 12, 60),
                                new PlayerState.Position(-.25, 72, 16),
                                -90,
                                -25,
                                "CREATIVE",
                                false,
                                2.5,
                                100,
                                40,
                                12,
                                3,
                                8,
                                32,
                                4),
                        0);
        var geometry = FloatBuffer.allocate(canvas.floatCount());
        canvas.writeTo(geometry);
        int panelX = (int) Math.floor(geometry.get(6 * UiCanvas.FLOATS_PER_VERTEX) * viewport.scale());
        int panelY = (int) Math.floor(geometry.get(6 * UiCanvas.FLOATS_PER_VERTEX + 1) * viewport.scale());
        int panels = 0;
        for (int i = 0; i < geometry.capacity(); i += 6 * UiCanvas.FLOATS_PER_VERTEX)
            if (Math.abs(geometry.get(i + 5) - .82f) < .001f) panels++;
        if (viewport.framebufferWidth() >= 640) check(panels == 4, "F3 must show all four sections");
        new Hud().render(canvas, hotbar, viewport);
        ui.render(canvas, viewport);
        var pixels = capture(viewport);
        int background = rgb(pixels, viewport, panelX + 1, panelY + 1);
        check((background & 255) < 100 && (background >> 16) > 0, "Debug panel background missing");
        check(glGetError() == GL_NO_ERROR, "Debug overlay GL error");
        write(output, pixels, viewport);
    }

    private static ByteBuffer capture(UiViewport viewport) {
        ByteBuffer pixels =
                BufferUtils.createByteBuffer(viewport.framebufferWidth() * viewport.framebufferHeight() * 4);
        glReadPixels(
                0, 0, viewport.framebufferWidth(), viewport.framebufferHeight(), GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        return pixels;
    }

    private static void checkSlots(ByteBuffer pixels, UiViewport viewport) {
        int s = viewport.scale();
        int left = ((int) viewport.width() - 182) / 2;
        int top = (int) viewport.height() - 22;
        int selected = 0;
        for (int slot = 0; slot < Hotbar.SLOTS; slot++) {
            int color = rgb(pixels, viewport, (left + 1 + slot * 20 + 5) * s, (top + 1) * s);
            check(color == 0x404a59, "Missing slot border " + slot);
            int frame = rgb(pixels, viewport, (left + slot * 20 + 5) * s, (top - 1) * s);
            if (frame == 0xf5cc4c || frame == 0xf5cc4d) selected++;
        }
        check(selected == 1, "Expected one selection, got " + selected);
    }

    private static void checkWorldScreens(UiRenderer ui, UiCanvas canvas, int texture, Path output) throws Exception {
        int[][] cases = {
            {320, 240, 320, 240, 0},
            {1280, 720, 1280, 720, 0},
            {1920, 1080, 1920, 1080, 0},
            {640, 360, 1280, 720, 0},
            {1280, 720, 1920, 1080, 0},
            {1281, 721, 1281, 721, 0},
            {640, 480, 1280, 960, 1},
            {640, 480, 1280, 960, 2},
            {640, 480, 1280, 960, 3},
            {640, 480, 1280, 960, 4}
        };
        for (int index = 0; index < cases.length; index++) {
            int[] size = cases[index];
            var viewport = new UiViewport(size[0], size[1], size[2], size[3], size[4]);
            glBindTexture(GL_TEXTURE_2D, texture);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, size[2], size[3], 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
            glViewport(0, 0, size[2], size[3]);
            clear();
            canvas.clear();
            canvas.text(BitmapFont.DEFAULT, BitmapFont.POLISH_GLYPHS, 8, 8, 1, 1, 1, 1);
            ui.render(canvas, viewport);
            var pixels = capture(viewport);
            byte[] atlas = UiAtlas.defaultAtlas().pixels();
            int x = 8, scale = viewport.scale();
            for (int cp : BitmapFont.POLISH_GLYPHS.codePoints().toArray()) {
                int glyph = BitmapFont.DEFAULT.glyph(cp);
                for (int py = 0; py < BitmapFont.GLYPH_HEIGHT; py++)
                    for (int px = 0; px < BitmapFont.DEFAULT.glyphWidth(cp); px++) {
                        int alpha =
                                atlas[((glyph / 16 * 16 + py) * UiAtlas.WIDTH + glyph % 16 * 16 + px) * 4 + 3] & 255;
                        for (int dy = 0; dy < scale; dy++)
                            for (int dx = 0; dx < scale; dx++)
                                check(
                                        rgb(pixels, viewport, (x + px) * scale + dx, (8 + py) * scale + dy)
                                                == (alpha == 0 ? 0 : 0xffffff),
                                        "Polish glyph pixel mismatch " + cp + " case " + index);
                    }
                x += BitmapFont.DEFAULT.advance(cp);
            }
            write(output.resolve("world04-polish-" + index + ".png"), pixels, viewport);
            var screens = new ScreenManager();
            var form = new CreateWorldScreen();
            screens.open(form);
            screens.layout(viewport);
            "Zażółć gęślą jaźń ".repeat(4).codePoints().forEach(cp -> screens.input(new UiEvent.Character(cp)));
            screens.keyPress(UiKey.NEXT);
            "-9223372036854775808".codePoints().forEach(cp -> screens.input(new UiEvent.Character(cp)));
            clear();
            canvas.clear();
            screens.render(canvas, viewport);
            ui.render(canvas, viewport);
            pixels = capture(viewport);
            var seed = form.seedField().bounds();
            check(
                    (rgb(pixels, viewport, seed.x() * scale, seed.y() * scale) & 0xffff00) == 0xf5cc00,
                    "Seed focus border missing");
            write(output.resolve("world04-create-" + index + ".png"), pixels, viewport);
            screens.keyPress(UiKey.ACTIVATE);
            screens.keyPress(UiKey.ACTIVATE);
            check(
                    screens.pollAction() instanceof Screen.Action.CreateWorld request
                            && request.seed().orElseThrow() == Long.MIN_VALUE
                            && request.name().codePointCount(0, request.name().length()) <= 64,
                    "Create request mismatch");
            check(screens.pollAction() == null, "Duplicate Create request");
            var selection = new WorldSelectionScreen(WorldUiFixtures.entries(1000));
            screens.open(selection);
            screens.layout(viewport);
            screens.keyPress(UiKey.END);
            check(selection.firstVisible() == 1000 - selection.visibleRows(), "Last row inaccessible");
            clear();
            canvas.clear();
            screens.render(canvas, viewport);
            ui.render(canvas, viewport);
            write(output.resolve("world04-list-" + index + ".png"), capture(viewport), viewport);
            screens.updatePointer(false, false, 0, 0);
            var play = selection.playButton().bounds();
            double wx = (play.x() + 2) / viewport.width() * viewport.windowWidth();
            double wy = (play.y() + 2) / viewport.height() * viewport.windowHeight();
            screens.updatePointer(true, true, viewport.cursorX(wx), viewport.cursorY(wy));
            screens.updatePointer(false, false, viewport.cursorX(wx), viewport.cursorY(wy));
            check(
                    screens.pollAction() instanceof Screen.Action.OpenWorld request
                            && request.id().equals(selection.selectedId().orElseThrow()),
                    "HiDPI Play click missed");
            screens.open(new WorldErrorScreen("Błąd sesji świata", "Payload integrity check failed"));
            screens.layout(viewport);
            clear();
            canvas.clear();
            screens.render(canvas, viewport);
            ui.render(canvas, viewport);
            write(output.resolve("world05-error-" + index + ".png"), capture(viewport), viewport);
            screens.keyPress(UiKey.BACK);
            check(!screens.isOpen() && screens.blocksGameplay(), "World error Back did not close safely");
            check(glGetError() == GL_NO_ERROR, "WORLD-04/05 GL error");
        }
        System.out.println(
                "PASS WORLD-04: 18 Polish glyph pixel checks, Unicode form/long seed/one-shot Create, 1000 visible-only rows and HiDPI Play in 10 viewport/scale cases");
    }

    private static void checkLanguages(UiRenderer ui, UiCanvas canvas, int texture, Path output) throws Exception {
        for (int[] size : new int[][] {{320, 240}, {1280, 720}, {1920, 1080}}) {
            var viewport = new UiViewport(size[0], size[1], size[0], size[1]);
            glBindTexture(GL_TEXTURE_2D, texture);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, size[0], size[1], 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
            glViewport(0, 0, size[0], size[1]);
            var settings = new GameSettings();
            var screen = new SettingsScreen(settings);
            var screens = new ScreenManager();
            screens.open(screen);
            screens.layout(viewport);
            var box = screen.languageButton().bounds();
            var back = screen.backButton().bounds();
            check(back.y() + back.height() <= viewport.height(), "Settings Back clipped");
            check(box.y() + box.height() <= viewport.height(), "Language button clipped");
            ByteBuffer previous = null;
            for (Language language : Language.values()) {
                check(settings.translations().language() == language, "Language cycle order");
                clear();
                render(ui, canvas, screens, new Hotbar(), viewport);
                ByteBuffer pixels = capture(viewport);
                check(previous == null || !previous.equals(pixels), "Language did not change rendered pixels");
                write(output.resolve("i18n-settings-" + language.code() + "-" + size[0] + ".png"), pixels, viewport);
                previous = pixels;
                screens.updatePointer(false, false, 0, 0);
                screens.updatePointer(true, true, box.x() + 1, box.y() + 1);
                screens.updatePointer(false, false, box.x() + 1, box.y() + 1);
            }
            check(settings.translations().language() == Language.ENGLISH, "Language cycle did not wrap");
            check(glGetError() == GL_NO_ERROR, "Language screen GL error");
        }
        System.out.println("PASS i18n: live EN/PL switching and unclipped controls at min/720p/1080p");
    }

    private static void checkSessionScreens(UiRenderer ui, UiCanvas canvas, int texture, Path output) throws Exception {
        for (int[] size : new int[][] {{320, 240}, {1280, 720}, {1920, 1080}}) {
            var viewport = new UiViewport(size[0], size[1], size[0], size[1]);
            glBindTexture(GL_TEXTURE_2D, texture);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, size[0], size[1], 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
            glViewport(0, 0, size[0], size[1]);
            var menu = new MainMenuScreen(true);
            var screens = new ScreenManager();
            screens.open(menu);
            screens.layout(viewport);
            clear();
            render(ui, canvas, screens, new Hotbar(), viewport);
            var box = menu.playButton().bounds();
            int s = viewport.scale();
            int focus = rgb(capture(viewport), viewport, (box.x() - 1) * s, (box.y() - 1) * s);
            check((focus & 0xffff00) == 0xf5cc00, "Main menu Play focus missing");
            write(output.resolve("ui09-menu-" + size[0] + ".png"), capture(viewport), viewport);
            screens.keyPress(UiKey.ACTIVATE);
            check(screens.pollAction() == Screen.Action.PLAY, "Main menu did not request Play");
            var pause = new PauseScreen();
            screens.open(pause);
            screens.layout(viewport);
            screens.keyPress(UiKey.NEXT);
            screens.keyPress(UiKey.NEXT);
            clear();
            render(ui, canvas, screens, new Hotbar(), viewport);
            box = pause.menuButton().bounds();
            check(
                    (rgb(capture(viewport), viewport, (box.x() - 1) * s, (box.y() - 1) * s) & 0xffff00) == 0xf5cc00,
                    "Back to menu focus missing");
            write(output.resolve("ui09-pause-" + size[0] + ".png"), capture(viewport), viewport);
            screens.keyPress(UiKey.ACTIVATE);
            check(screens.pollAction() == Screen.Action.BACK_TO_MENU, "Pause did not request Back to menu");
            screens.open(new SessionStatusScreen("Saving world..."));
            screens.keyPress(UiKey.BACK);
            screens.keyPress(UiKey.ACTIVATE);
            check(screens.pollAction() == null && screens.blocksGameplay(), "Saving screen accepted gameplay input");
            clear();
            render(ui, canvas, screens, new Hotbar(), viewport);
            write(output.resolve("ui09-saving-" + size[0] + ".png"), capture(viewport), viewport);
            screens.open(new SaveErrorScreen());
            screens.layout(viewport);
            screens.keyPress(UiKey.BACK);
            check(screens.pollAction() == null, "Escape discarded a failed save");
            clear();
            render(ui, canvas, screens, new Hotbar(), viewport);
            write(output.resolve("world03-save-error-" + size[0] + ".png"), capture(viewport), viewport);
            screens.keyPress(UiKey.ACTIVATE);
            check(screens.pollAction() == Screen.Action.RETRY_SAVE, "Save-error retry action missing");
            screens.keyPress(UiKey.NEXT);
            screens.keyPress(UiKey.ACTIVATE);
            check(screens.pollAction() == Screen.Action.DISCARD_CHANGES, "Save-error discard action missing");
            check(glGetError() == GL_NO_ERROR, "Session screen GL error");
        }
        System.out.println("PASS UI-09: main menu/error, pause/back-to-menu, saving screen at min/720p/1080p");
    }

    private static void checkItemSprites(UiRenderer ui, UiCanvas canvas, int texture, Path output) throws Exception {
        byte[] atlas = UiAtlas.defaultAtlas().pixels();
        for (int scale = 1; scale <= 4; scale++) {
            var viewport = new UiViewport(320, 240, 320 * scale, 240 * scale, scale);
            glBindTexture(GL_TEXTURE_2D, texture);
            glTexImage2D(
                    GL_TEXTURE_2D,
                    0,
                    GL_RGBA8,
                    viewport.framebufferWidth(),
                    viewport.framebufferHeight(),
                    0,
                    GL_RGBA,
                    GL_UNSIGNED_BYTE,
                    (ByteBuffer) null);
            glViewport(0, 0, viewport.framebufferWidth(), viewport.framebufferHeight());
            clear();
            canvas.clear();
            canvas.rectangle(0, 0, 320, 240, 0, 0, 1, 1);
            for (int id = 1; id <= UiAtlas.LAST_ITEM; id++) {
                int x = 8 + (id - 1) % 8 * 36;
                int y = 8 + (id - 1) / 8 * 36;
                ItemIcons.render(canvas, x, y, 20, (byte) id, 1);
                ItemIcons.render(canvas, x, y + 110, 20, (byte) id, 64);
            }
            ui.render(canvas, viewport);
            check(glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_WIDTH) == UiAtlas.WIDTH, "Wrong atlas width");
            check(glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_HEIGHT) == UiAtlas.HEIGHT, "Wrong atlas height");
            check(glGetTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S) == GL_CLAMP_TO_EDGE, "Atlas S must clamp");
            check(glGetTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T) == GL_CLAMP_TO_EDGE, "Atlas T must clamp");
            ByteBuffer pixels = capture(viewport);
            for (int id = 1; id <= UiAtlas.LAST_ITEM; id++) {
                var region = UiAtlas.item((byte) id);
                int x = 10 + (id - 1) % 8 * 36;
                int y = 10 + (id - 1) / 8 * 36;
                for (int py = 0; py < 16 * scale; py++)
                    for (int px = 0; px < 16 * scale; px++) {
                        int offset = ((region.y() + py / scale) * UiAtlas.WIDTH + region.x() + px / scale) * 4;
                        double alpha = Byte.toUnsignedInt(atlas[offset + 3]) / 255.0;
                        int actual = rgb(pixels, viewport, x * scale + px, y * scale + py);
                        for (int channel = 0; channel < 3; channel++) {
                            int source = Byte.toUnsignedInt(atlas[offset + channel]);
                            int background = channel == 2 ? 255 : 0;
                            int expected = (int) Math.round(source * alpha + background * (1 - alpha));
                            int value = actual >> (16 - channel * 8) & 255;
                            check(
                                    Math.abs(value - expected) <= 1,
                                    "Item pixel/alpha/nearest mismatch, id=" + id + " scale=" + scale);
                        }
                    }
            }
            check(glGetError() == GL_NO_ERROR, "Item atlas GL error");
            write(output.resolve("ui08-items-scale-" + scale + ".png"), pixels, viewport);

            var inventory = new Inventory();
            byte[] blocks = {
                BlockId.GRASS,
                BlockId.DIRT,
                BlockId.LOG,
                BlockId.GLASS,
                BlockId.WATER,
                BlockId.SHORT_GRASS,
                BlockId.FERN,
                BlockId.DANDELION_YELLOW,
                BlockId.POPPY
            };
            for (int slot = 0; slot < blocks.length; slot++) inventory.creativePick(slot, blocks[slot]);
            var hotbar = new Hotbar();
            hotbar.inventory(inventory.snapshot());
            clear();
            canvas.clear();
            new Hud().render(canvas, hotbar, viewport);
            ui.render(canvas, viewport);
            checkSlots(capture(viewport), viewport);
            write(output.resolve("ui08-populated-hud-scale-" + scale + ".png"), capture(viewport), viewport);
        }
        System.out.println(
                "PASS UI-08: 21 icons, per-pixel alpha/orientation/nearest at scales1..4, populated HUD/counts");
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
