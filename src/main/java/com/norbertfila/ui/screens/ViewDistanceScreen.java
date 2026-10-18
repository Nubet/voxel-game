package com.norbertfila.ui.screens;

import com.norbertfila.core.GameSettings;
import com.norbertfila.i18n.Translations;
import com.norbertfila.ui.*;
import com.norbertfila.world.Chunk;
import java.util.Objects;

public final class ViewDistanceScreen implements Screen {
    private static final int WIDTH = 304, HEIGHT = 240;
    private final GameSettings settings;
    private final Translations translations;
    private final Slider detail = new Slider(
            new UiBounds(12, 48, 280, 12),
            GameSettings.MIN_RENDER_DISTANCE,
            GameSettings.MAX_RENDER_DISTANCE,
            1,
            GameSettings.DEFAULT_RENDER_DISTANCE);
    private final Slider simulation = new Slider(
            new UiBounds(12, 100, 280, 12),
            GameSettings.MIN_SIMULATION_DISTANCE,
            GameSettings.MAX_RENDER_DISTANCE,
            1,
            GameSettings.DEFAULT_SIMULATION_DISTANCE);
    private final Slider landscape = new Slider(
            new UiBounds(12, 152, 280, 12), 0, GameSettings.MAX_FAR_DISTANCE, 1, GameSettings.DEFAULT_FAR_DISTANCE);
    private final Button back = new Button(new UiBounds(220, 212, 76, 20), "");
    private int x, y, focus = -1;
    private boolean pointerReady = true;

    public ViewDistanceScreen(GameSettings settings) {
        this.settings = Objects.requireNonNull(settings);
        translations = settings.translations();
        sync();
    }

    @Override
    public Action updatePointer(boolean down, boolean pressed, double cursorX, double cursorY) {
        sync();
        if (!down) pointerReady = true;
        if (!pointerReady) return Action.NONE;
        if (pressed) {
            if (detail.bounds().contains(cursorX, cursorY)) focus(0);
            else if (simulation.bounds().contains(cursorX, cursorY)) focus(1);
            else if (landscape.bounds().contains(cursorX, cursorY)) focus(2);
            else if (back.bounds().contains(cursorX, cursorY)) focus(3);
            else focus(-1);
        }
        if (detail.updatePointer(down, pressed, cursorX, cursorY)) settings.setRenderDistance(detail.value());
        sync();
        if (simulation.updatePointer(down, pressed, cursorX, cursorY))
            settings.setSimulationDistance(simulation.value());
        if (landscape.updatePointer(down, pressed, cursorX, cursorY)) settings.setFarDistance(landscape.value());
        return back.updatePointer(down, pressed, cursorX, cursorY) ? Action.CLOSE : Action.NONE;
    }

    @Override
    public Action keyPress(UiKey key) {
        Objects.requireNonNull(key);
        if (key == UiKey.BACK) return Action.CLOSE;
        sync();
        if (key == UiKey.NEXT || key == UiKey.PREVIOUS) {
            int direction = key == UiKey.NEXT ? 1 : -1;
            int current = focus < 0 ? (direction > 0 ? -1 : 4) : focus;
            focus(Math.floorMod(current + direction, 4));
        } else {
            switch (focus) {
                case 0 -> {
                    if (detail.keyPress(key)) settings.setRenderDistance(detail.value());
                }
                case 1 -> {
                    if (simulation.keyPress(key)) settings.setSimulationDistance(simulation.value());
                }
                case 2 -> {
                    if (landscape.keyPress(key)) settings.setFarDistance(landscape.value());
                }
                case 3 -> {
                    if (back.keyPress(key)) return Action.CLOSE;
                }
                default -> {}
            }
        }
        return Action.NONE;
    }

    private void sync() {
        detail.setValue(settings.renderDistance());
        simulation.setRange(GameSettings.MIN_SIMULATION_DISTANCE, settings.renderDistance(), 1);
        simulation.setValue(settings.simulationDistance());
        landscape.setValue(settings.farDistance());
        back.setLabel(translations.text("action.back"));
    }

    private void focus(int index) {
        focus = index;
        detail.setFocused(index == 0);
        simulation.setFocused(index == 1);
        landscape.setFocused(index == 2);
        back.setFocused(index == 3);
    }

    @Override
    public void cancelInteraction() {
        focus = -1;
        pointerReady = false;
        detail.cancelInteraction();
        simulation.cancelInteraction();
        landscape.cancelInteraction();
        back.cancelInteraction();
    }

    @Override
    public void layout(UiViewport viewport) {
        int nextX = Math.max(0, ((int) Math.ceil(viewport.width()) - WIDTH) / 2);
        int nextY = Math.max(0, ((int) Math.ceil(viewport.height()) - HEIGHT) / 2);
        if (x != nextX || y != nextY) cancelInteraction();
        x = nextX;
        y = nextY;
        detail.setPosition(x + 12, y + 48);
        simulation.setPosition(x + 12, y + 100);
        landscape.setPosition(x + 12, y + 152);
        back.setPosition(x + 220, y + 212);
    }

    @Override
    public void render(UiCanvas canvas, UiViewport viewport) {
        layout(viewport);
        sync();
        canvas.rectangle(x, y, WIDTH, HEIGHT, .06f, .08f, .11f, .94f);
        text(canvas, translations.text("distance.title"), 8, false);
        distance(canvas, "distance.detail", settings.renderDistance(), 28);
        text(canvas, translations.text("distance.detail.help"), 64, true);
        distance(canvas, "distance.simulation", settings.simulationDistance(), 80);
        text(canvas, translations.text("distance.simulation.help"), 116, true);
        if (settings.farDistance() == 0) text(canvas, translations.text("distance.landscape.off"), 132, false);
        else distance(canvas, "distance.landscape", settings.farDistance(), 132);
        text(canvas, translations.text("distance.landscape.help"), 168, true);
        if (settings.farDistance() > 0 && settings.farDistance() <= settings.renderDistance())
            text(canvas, translations.text("distance.inactive"), 184, true);
        else if (settings.farDistance() > settings.renderDistance() && settings.renderDistance() <= 4)
            text(canvas, translations.text("distance.popIn"), 184, true);
        detail.render(canvas);
        simulation.render(canvas);
        landscape.render(canvas);
        back.render(canvas, BitmapFont.DEFAULT);
    }

    private void distance(UiCanvas canvas, String key, int chunks, int offsetY) {
        text(canvas, translations.text(key, chunks, chunks * Chunk.SIZE), offsetY, false);
    }

    private void text(UiCanvas canvas, String value, int offsetY, boolean muted) {
        float color = muted ? .72f : 1;
        canvas.textWithShadow(BitmapFont.DEFAULT, value, x + 12, y + offsetY, color, color, color, 1);
    }

    public Slider detailSlider() {
        return detail;
    }

    public Slider simulationSlider() {
        return simulation;
    }

    public Slider landscapeSlider() {
        return landscape;
    }

    public Button backButton() {
        return back;
    }
}
