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
        int knobX = SettingsPanel.SLIDER_X + (int) (SettingsPanel.SLIDER_WIDTH * panel.sliderFraction());
        assertEquals(knobX - 6, data.get(knobOffset));
        assertEquals(distance == GameSettings.MIN_RENDER_DISTANCE ? .07f : .15f, data.get(4 * 48 + 2));
        assertEquals(distance == GameSettings.MAX_RENDER_DISTANCE ? .07f : .15f, data.get(5 * 48 + 2));
        assertEquals(distance, panel.renderDistance());
        assertTrue(panel.open());
    }
}
