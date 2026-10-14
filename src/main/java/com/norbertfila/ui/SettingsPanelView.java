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
        canvas.rectangle(
                SettingsPanel.SLIDER_X,
                SettingsPanel.SLIDER_Y,
                SettingsPanel.SLIDER_WIDTH,
                SettingsPanel.SLIDER_HEIGHT,
                .18f,
                .21f,
                .27f,
                1);
        canvas.rectangle(
                SettingsPanel.SLIDER_X,
                SettingsPanel.SLIDER_Y,
                (int) (SettingsPanel.SLIDER_WIDTH * panel.sliderFraction()),
                SettingsPanel.SLIDER_HEIGHT,
                .22f,
                .67f,
                .78f,
                1);
        int knobX = SettingsPanel.SLIDER_X + (int) (SettingsPanel.SLIDER_WIDTH * panel.sliderFraction());
        canvas.rectangle(
                knobX - 6, SettingsPanel.SLIDER_Y - 6, 12, SettingsPanel.SLIDER_HEIGHT + 12, .85f, .94f, .96f, 1);
        canvas.rectangle(
                SettingsPanel.MINUS_X,
                SettingsPanel.BUTTON_Y,
                SettingsPanel.BUTTON_SIZE,
                SettingsPanel.BUTTON_SIZE,
                panel.canDecrease() ? .15f : .07f,
                panel.canDecrease() ? .18f : .08f,
                panel.canDecrease() ? .23f : .1f,
                1);
        canvas.rectangle(
                SettingsPanel.PLUS_X,
                SettingsPanel.BUTTON_Y,
                SettingsPanel.BUTTON_SIZE,
                SettingsPanel.BUTTON_SIZE,
                panel.canIncrease() ? .15f : .07f,
                panel.canIncrease() ? .18f : .08f,
                panel.canIncrease() ? .23f : .1f,
                1);
        canvas.rectangle(
                SettingsPanel.MINUS_X + 5,
                SettingsPanel.BUTTON_Y + 9,
                10,
                3,
                panel.canDecrease() ? .85f : .3f,
                panel.canDecrease() ? .94f : .3f,
                panel.canDecrease() ? .96f : .3f,
                1);
        canvas.rectangle(
                SettingsPanel.PLUS_X + 5,
                SettingsPanel.BUTTON_Y + 9,
                10,
                3,
                panel.canIncrease() ? .85f : .3f,
                panel.canIncrease() ? .94f : .3f,
                panel.canIncrease() ? .96f : .3f,
                1);
        canvas.rectangle(
                SettingsPanel.PLUS_X + 9,
                SettingsPanel.BUTTON_Y + 5,
                3,
                11,
                panel.canIncrease() ? .85f : .3f,
                panel.canIncrease() ? .94f : .3f,
                panel.canIncrease() ? .96f : .3f,
                1);
        BitmapFont font = BitmapFont.DEFAULT;
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
