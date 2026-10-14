package com.norbertfila.ui;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.core.GameSettings;
import com.norbertfila.core.SettingsPanel;
import java.nio.FloatBuffer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SettingsPanelViewTest {
    @ParameterizedTest
    @ValueSource(ints = {GameSettings.MIN_RENDER_DISTANCE, 4, GameSettings.MAX_RENDER_DISTANCE})
    void rendersPanelAndSliderWithoutChangingTheModel(int distance) {
        GameSettings settings = new GameSettings();
        settings.setRenderDistance(distance);
        SettingsPanel panel = new SettingsPanel(settings);
        panel.toggle();
        UiCanvas canvas = new UiCanvas();
        new SettingsPanelView().render(canvas, panel);
        assertTrue(canvas.vertexCount() > 9 * 6);
        FloatBuffer data = FloatBuffer.allocate(canvas.floatCount());
        canvas.writeTo(data);
        assertEquals(SettingsPanel.PANEL_X, data.get(0));
        assertEquals(SettingsPanel.PANEL_Y, data.get(1));
        int knobOffset = 3 * 48;
        int knobX = SettingsPanel.SLIDER_X
                + (int) (SettingsPanel.SLIDER_WIDTH * panel.slider().fraction());
        assertEquals(knobX - 6, data.get(knobOffset));
        assertEquals(
                distance == GameSettings.MIN_RENDER_DISTANCE ? .07f : .15f,
                rectangleRed(data, panel.decreaseButton().bounds()));
        assertEquals(
                distance == GameSettings.MAX_RENDER_DISTANCE ? .07f : .15f,
                rectangleRed(data, panel.increaseButton().bounds()));
        assertEquals(distance, panel.renderDistance());
        assertTrue(panel.open());
    }

    private static float rectangleRed(FloatBuffer data, UiBounds bounds) {
        for (int offset = 0; offset < data.position(); offset += 48)
            if (data.get(offset) == bounds.x() && data.get(offset + 1) == bounds.y()) return data.get(offset + 2);
        throw new AssertionError("Missing control background");
    }
}
