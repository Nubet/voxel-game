package com.norbertfila.ui;

import com.norbertfila.core.SettingsPanel;

public final class SettingsPanelView {
    public void render(UiCanvas canvas, SettingsPanel panel) {
        canvas.rectangle(
                SettingsPanel.PANEL_X,
                SettingsPanel.PANEL_Y,
                SettingsPanel.PANEL_WIDTH,
                SettingsPanel.PANEL_HEIGHT,
                .06f,
                .08f,
                .11f,
                .94f);
        panel.slider().render(canvas);
        BitmapFont font = BitmapFont.DEFAULT;
        panel.decreaseButton().render(canvas, font);
        panel.increaseButton().render(canvas, font);
        canvas.textWithShadow(font, "Settings", SettingsPanel.PANEL_X + 12, SettingsPanel.PANEL_Y + 10, 1, 1, 1, 1);
        canvas.textWithShadow(
                font,
                "Render distance: " + panel.renderDistance() + " chunks",
                SettingsPanel.PANEL_X + 12,
                SettingsPanel.PANEL_Y + 44,
                1,
                1,
                1,
                1);
    }
}
