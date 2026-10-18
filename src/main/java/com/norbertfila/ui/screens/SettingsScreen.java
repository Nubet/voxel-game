package com.norbertfila.ui.screens;

import com.norbertfila.core.GameSettings;
import com.norbertfila.i18n.Language;
import com.norbertfila.i18n.Translations;
import com.norbertfila.player.visual.model.ModelVariant;
import com.norbertfila.render.CloudMode;
import com.norbertfila.render.SkinManager;
import com.norbertfila.ui.*;
import com.norbertfila.ui.crosshair.CrosshairSettings;
import java.util.Objects;
import java.util.function.Consumer;

public final class SettingsScreen implements Screen {
    public static final int PANEL_X = 8, PANEL_Y = 0, PANEL_WIDTH = 304, PANEL_HEIGHT = 264;
    public static final int CAMERA_X = 144, CAMERA_WIDTH = 152;
    public static final int FOV_Y = 88, ZOOM_FOV_Y = 112, SENSITIVITY_Y = 136, ZOOM_SPEED_Y = 160;
    public static final int FRAME_RATE_Y = 232, GUI_SCALE_Y = 184, LANGUAGE_Y = 208;
    public static final UiBounds BACK_BOUNDS = new UiBounds(220, 208, 76, 20);
    private final GameSettings settings;
    private final Translations translations;
    private final Consumer<CrosshairSettings> saveCrosshair;
    private final Runnable saveDistances;
    private final SkinManager skins;
    private final Consumer<ModelVariant> saveAppearance;
    private Screen child;
    private final Button distances = new Button(new UiBounds(20, 30, 280, 20), "");
    private final Button crosshair = new Button(new UiBounds(204, 2, 92, 18), "");
    private final Button appearance = new Button(new UiBounds(108, 2, 92, 18), "");
    private final Slider fieldOfView = new Slider(
            new UiBounds(CAMERA_X, FOV_Y, CAMERA_WIDTH, 12),
            (int) GameSettings.MIN_FIELD_OF_VIEW,
            (int) GameSettings.MAX_FIELD_OF_VIEW,
            1,
            (int) GameSettings.DEFAULT_FIELD_OF_VIEW);
    private final Slider zoomFieldOfView = new Slider(
            new UiBounds(CAMERA_X, ZOOM_FOV_Y, CAMERA_WIDTH, 12),
            (int) GameSettings.MIN_ZOOM_FIELD_OF_VIEW,
            (int) GameSettings.DEFAULT_FIELD_OF_VIEW,
            1,
            (int) GameSettings.DEFAULT_ZOOM_FIELD_OF_VIEW);
    private final Slider mouseSensitivity =
            new Slider(new UiBounds(CAMERA_X, SENSITIVITY_Y, CAMERA_WIDTH, 12), 1, 100, 1, 11);
    private final Slider zoomSpeed = new Slider(new UiBounds(CAMERA_X, ZOOM_SPEED_Y, CAMERA_WIDTH, 12), 1, 30, 1, 12);
    private final Slider frameRate = new Slider(
            new UiBounds(CAMERA_X, FRAME_RATE_Y, CAMERA_WIDTH, 12),
            GameSettings.MIN_FRAME_RATE,
            GameSettings.MAX_FRAME_RATE + 1,
            1,
            GameSettings.DEFAULT_FRAME_RATE);
    private final Button guiScale = new Button(new UiBounds(100, GUI_SCALE_Y, 100, 20), "");
    private final Button clouds = new Button(new UiBounds(208, GUI_SCALE_Y, 88, 20), "");
    private final Button language = new Button(new UiBounds(CAMERA_X, LANGUAGE_Y, 72, 20), "");
    private final Button back = new Button(BACK_BOUNDS, "");
    private int focusIndex = -1, panelX = PANEL_X, panelY = PANEL_Y;
    private boolean pointerReady = true;

    public SettingsScreen(GameSettings settings) {
        this(settings, ignored -> {});
    }

    public SettingsScreen(GameSettings settings, Consumer<CrosshairSettings> saveCrosshair) {
        this(settings, saveCrosshair, () -> {});
    }

    public SettingsScreen(GameSettings settings, Consumer<CrosshairSettings> saveCrosshair, Runnable saveDistances) {
        this(settings, saveCrosshair, saveDistances, null, ignored -> {});
    }

    public SettingsScreen(
            GameSettings settings,
            Consumer<CrosshairSettings> saveCrosshair,
            Runnable saveDistances,
            SkinManager skins,
            Consumer<ModelVariant> saveAppearance) {
        this.settings = Objects.requireNonNull(settings);
        this.saveCrosshair = Objects.requireNonNull(saveCrosshair);
        this.saveDistances = Objects.requireNonNull(saveDistances);
        this.skins = skins;
        this.saveAppearance = Objects.requireNonNull(saveAppearance);
        translations = settings.translations();
        syncControls();
    }

    @Override
    public Action updatePointer(boolean down, boolean pressed, double x, double y) {
        if (child != null) return childAction(child.updatePointer(down, pressed, x, y));
        syncControls();
        if (!down) pointerReady = true;
        if (!pointerReady) return Action.NONE;
        if (pressed) {
            if (distances.bounds().contains(x, y)) focus(0);
            else if (fieldOfView.bounds().contains(x, y)) focus(1);
            else if (zoomFieldOfView.bounds().contains(x, y)) focus(2);
            else if (mouseSensitivity.bounds().contains(x, y)) focus(3);
            else if (zoomSpeed.bounds().contains(x, y)) focus(4);
            else if (frameRate.bounds().contains(x, y)) focus(5);
            else if (guiScale.bounds().contains(x, y)) focus(6);
            else if (clouds.bounds().contains(x, y)) focus(7);
            else if (language.bounds().contains(x, y)) focus(8);
            else if (appearance.bounds().contains(x, y)) focus(9);
            else if (crosshair.bounds().contains(x, y)) focus(10);
            else if (back.bounds().contains(x, y)) focus(11);
            else focus(-1);
        }
        if (fieldOfView.updatePointer(down, pressed, x, y)) settings.setFieldOfView(fieldOfView.value());
        if (zoomFieldOfView.updatePointer(down, pressed, x, y)) settings.setZoomFieldOfView(zoomFieldOfView.value());
        if (mouseSensitivity.updatePointer(down, pressed, x, y))
            settings.setMouseSensitivity(mouseSensitivity.value() / 100f);
        if (zoomSpeed.updatePointer(down, pressed, x, y)) settings.setZoomSpeed(zoomSpeed.value());
        if (frameRate.updatePointer(down, pressed, x, y)) settings.setFrameRate(frameRateValue());
        if (guiScale.updatePointer(down, pressed, x, y)) cycleGuiScale();
        if (clouds.updatePointer(down, pressed, x, y)) cycleClouds();
        if (language.updatePointer(down, pressed, x, y)) cycleLanguage();
        boolean close = back.updatePointer(down, pressed, x, y);
        if (distances.updatePointer(down, pressed, x, y)) openChild(new ViewDistanceScreen(settings));
        if (crosshair.updatePointer(down, pressed, x, y)) openChild(new CrosshairSettingsScreen(settings));
        if (appearance.updatePointer(down, pressed, x, y)) openChild(new AppearanceScreen(settings, skins));
        syncControls();
        return close ? closeSettings() : Action.NONE;
    }

    @Override
    public Action keyPress(UiKey key) {
        Objects.requireNonNull(key);
        if (child != null) return childAction(child.keyPress(key));
        if (key == UiKey.BACK) return closeSettings();
        syncControls();
        if (key == UiKey.NEXT || key == UiKey.PREVIOUS) {
            int direction = key == UiKey.NEXT ? 1 : -1;
            int current = focusIndex < 0 ? (direction > 0 ? -1 : 12) : focusIndex;
            focus(Math.floorMod(current + direction, 12));
        } else {
            switch (focusIndex) {
                case 0 -> {
                    if (distances.keyPress(key)) openChild(new ViewDistanceScreen(settings));
                }
                case 1 -> {
                    if (fieldOfView.keyPress(key)) settings.setFieldOfView(fieldOfView.value());
                }
                case 2 -> {
                    if (zoomFieldOfView.keyPress(key)) settings.setZoomFieldOfView(zoomFieldOfView.value());
                }
                case 3 -> {
                    if (mouseSensitivity.keyPress(key)) settings.setMouseSensitivity(mouseSensitivity.value() / 100f);
                }
                case 4 -> {
                    if (zoomSpeed.keyPress(key)) settings.setZoomSpeed(zoomSpeed.value());
                }
                case 5 -> {
                    if (frameRate.keyPress(key)) settings.setFrameRate(frameRateValue());
                }
                case 6 -> {
                    if (guiScale.keyPress(key)) cycleGuiScale();
                }
                case 7 -> {
                    if (clouds.keyPress(key)) cycleClouds();
                }
                case 8 -> {
                    if (language.keyPress(key)) cycleLanguage();
                }
                case 9 -> {
                    if (appearance.keyPress(key)) openChild(new AppearanceScreen(settings, skins));
                }
                case 10 -> {
                    if (crosshair.keyPress(key)) openChild(new CrosshairSettingsScreen(settings));
                }
                case 11 -> {
                    if (back.keyPress(key)) return closeSettings();
                }
                default -> {}
            }
        }
        return Action.NONE;
    }

    @Override
    public void cancelInteraction() {
        if (child != null) child.cancelInteraction();
        focusIndex = -1;
        pointerReady = false;
        distances.cancelInteraction();
        crosshair.cancelInteraction();
        appearance.cancelInteraction();
        fieldOfView.cancelInteraction();
        zoomFieldOfView.cancelInteraction();
        mouseSensitivity.cancelInteraction();
        zoomSpeed.cancelInteraction();
        frameRate.cancelInteraction();
        guiScale.cancelInteraction();
        clouds.cancelInteraction();
        language.cancelInteraction();
        back.cancelInteraction();
    }

    @Override
    public void layout(UiViewport viewport) {
        if (child != null) {
            child.layout(viewport);
            return;
        }
        int nextX = Math.max(0, ((int) Math.ceil(viewport.width()) - PANEL_WIDTH) / 2);
        int nextY = Math.max(0, ((int) Math.ceil(viewport.height()) - PANEL_HEIGHT) / 2);
        if (panelX != nextX || panelY != nextY) cancelInteraction();
        panelX = nextX;
        panelY = nextY;
        int dx = panelX - PANEL_X, dy = panelY - PANEL_Y;
        distances.setPosition(panelX + 12, panelY + 30);
        crosshair.setPosition(panelX + 204, panelY + 2);
        appearance.setPosition(panelX + 100, panelY + 2);
        fieldOfView.setPosition(CAMERA_X + dx, FOV_Y + dy);
        zoomFieldOfView.setPosition(CAMERA_X + dx, ZOOM_FOV_Y + dy);
        mouseSensitivity.setPosition(CAMERA_X + dx, SENSITIVITY_Y + dy);
        zoomSpeed.setPosition(CAMERA_X + dx, ZOOM_SPEED_Y + dy);
        frameRate.setPosition(CAMERA_X + dx, FRAME_RATE_Y + dy);
        guiScale.setPosition(100 + dx, GUI_SCALE_Y + dy);
        clouds.setPosition(208 + dx, GUI_SCALE_Y + dy);
        language.setPosition(CAMERA_X + dx, LANGUAGE_Y + dy);
        back.setPosition(BACK_BOUNDS.x() + dx, BACK_BOUNDS.y() + dy);
    }

    private void syncControls() {
        fieldOfView.setValue(Math.round(settings.fieldOfView()));
        zoomFieldOfView.setRange((int) GameSettings.MIN_ZOOM_FIELD_OF_VIEW, Math.round(settings.fieldOfView()), 1);
        zoomFieldOfView.setValue(Math.round(settings.zoomFieldOfView()));
        mouseSensitivity.setValue(Math.round(settings.mouseSensitivity() * 100));
        zoomSpeed.setValue(Math.round(settings.zoomSpeed()));
        frameRate.setValue(frameRateSliderValue());
        guiScale.setLabel(translations.text(
                switch (settings.guiScale()) {
                    case 1 -> "settings.size.small";
                    case 2 -> "settings.size.medium";
                    case 3 -> "settings.size.large";
                    case 4 -> "settings.size.extraLarge";
                    default -> "settings.auto";
                }));
        distances.setLabel(translations.text("distance.title"));
        clouds.setLabel(
                translations.text("settings.clouds." + settings.clouds().name().toLowerCase(java.util.Locale.ROOT)));
        language.setLabel(translations.language().displayName());
        back.setLabel(translations.text("action.back"));
        crosshair.setLabel(translations.text("crosshair.title"));
        appearance.setLabel(translations.text("appearance.title"));
    }

    private void focus(int index) {
        focusIndex = index;
        distances.setFocused(index == 0);
        fieldOfView.setFocused(index == 1);
        zoomFieldOfView.setFocused(index == 2);
        mouseSensitivity.setFocused(index == 3);
        zoomSpeed.setFocused(index == 4);
        frameRate.setFocused(index == 5);
        guiScale.setFocused(index == 6);
        clouds.setFocused(index == 7);
        language.setFocused(index == 8);
        appearance.setFocused(index == 9);
        crosshair.setFocused(index == 10);
        back.setFocused(index == 11);
    }

    private void cycleGuiScale() {
        settings.setGuiScale(
                settings.guiScale() == GameSettings.MAX_GUI_SCALE
                        ? GameSettings.GUI_SCALE_AUTO
                        : settings.guiScale() + 1);
        syncControls();
    }

    private void cycleLanguage() {
        Language[] languages = Language.values();
        translations.setLanguage(languages[(translations.language().ordinal() + 1) % languages.length]);
        syncControls();
    }

    private void cycleClouds() {
        CloudMode[] modes = CloudMode.values();
        settings.setClouds(modes[(settings.clouds().ordinal() + 1) % modes.length]);
        syncControls();
    }

    @Override
    public void render(UiCanvas canvas, UiViewport viewport) {
        if (child != null) {
            child.render(canvas, viewport);
            return;
        }
        layout(viewport);
        syncControls();
        canvas.rectangle(panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, .06f, .08f, .11f, .94f);
        label(canvas, translations.text("settings.title"), 4);
        label(
                canvas,
                translations.text(
                        "distance.summary",
                        settings.renderDistance(),
                        settings.simulationDistance(),
                        settings.farDistance()),
                60);
        label(canvas, translations.text("settings.fov", Math.round(settings.fieldOfView())), FOV_Y - 4);
        label(canvas, translations.text("settings.zoomFov", Math.round(settings.zoomFieldOfView())), ZOOM_FOV_Y - 4);
        label(canvas, translations.text("settings.sensitivity", settings.mouseSensitivity()), SENSITIVITY_Y - 4);
        label(canvas, translations.text("settings.zoomSpeed", Math.round(settings.zoomSpeed())), ZOOM_SPEED_Y - 4);
        label(canvas, frameRateLabel(), FRAME_RATE_Y - 4);
        label(canvas, translations.text("settings.guiScale"), GUI_SCALE_Y + 3);
        label(canvas, translations.text("settings.language"), LANGUAGE_Y + 3);
        distances.render(canvas, BitmapFont.DEFAULT);
        crosshair.render(canvas, BitmapFont.DEFAULT);
        appearance.render(canvas, BitmapFont.DEFAULT);
        fieldOfView.render(canvas);
        zoomFieldOfView.render(canvas);
        mouseSensitivity.render(canvas);
        zoomSpeed.render(canvas);
        frameRate.render(canvas);
        guiScale.render(canvas, BitmapFont.DEFAULT);
        clouds.render(canvas, BitmapFont.DEFAULT);
        language.render(canvas, BitmapFont.DEFAULT);
        back.render(canvas, BitmapFont.DEFAULT);
    }

    private void label(UiCanvas canvas, String value, int offsetY) {
        canvas.textWithShadow(BitmapFont.DEFAULT, value, panelX + 8, panelY + offsetY, 1, 1, 1, 1);
    }

    private int frameRateValue() {
        return frameRate.value() == GameSettings.MAX_FRAME_RATE + 1
                ? GameSettings.UNLIMITED_FRAME_RATE
                : frameRate.value();
    }

    private int frameRateSliderValue() {
        return settings.frameRate() == GameSettings.UNLIMITED_FRAME_RATE
                ? GameSettings.MAX_FRAME_RATE + 1
                : settings.frameRate();
    }

    private String frameRateLabel() {
        return settings.frameRate() == GameSettings.UNLIMITED_FRAME_RATE
                ? translations.text("settings.frameRate.unlimited")
                : translations.text("settings.frameRate", settings.frameRate());
    }

    private void openChild(Screen screen) {
        cancelInteraction();
        child = screen;
        child.cancelInteraction();
    }

    private Action childAction(Action action) {
        if (action != Action.CLOSE) return action;
        if (child instanceof CrosshairSettingsScreen) saveCrosshair.accept(settings.crosshair());
        if (child instanceof ViewDistanceScreen) saveDistances.run();
        if (child instanceof AppearanceScreen) saveAppearance.accept(settings.modelVariant());
        child.cancelInteraction();
        child = null;
        cancelInteraction();
        return Action.NONE;
    }

    private Action closeSettings() {
        saveDistances.run();
        saveAppearance.accept(settings.modelVariant());
        return Action.CLOSE;
    }

    public Button distancesButton() {
        return distances;
    }

    public Button appearanceButton() {
        return appearance;
    }

    public Button crosshairButton() {
        return crosshair;
    }

    public Button cloudsButton() {
        return clouds;
    }

    public Button languageButton() {
        return language;
    }

    public Slider fieldOfViewSlider() {
        return fieldOfView;
    }

    public Slider zoomFieldOfViewSlider() {
        return zoomFieldOfView;
    }

    public Slider mouseSensitivitySlider() {
        return mouseSensitivity;
    }

    public Slider zoomSpeedSlider() {
        return zoomSpeed;
    }

    public Button guiScaleButton() {
        return guiScale;
    }

    public Button backButton() {
        return back;
    }

    public UiBounds panelBounds() {
        return new UiBounds(panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT);
    }
}
