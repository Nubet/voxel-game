package com.norbertfila.ui;

import com.norbertfila.core.Hotbar;

public final class Hud {
    public void render(UiCanvas canvas, Hotbar hotbar, UiViewport viewport) {
        int width = (int) viewport.width(), height = (int) viewport.height();
        int radius = HudMetrics.CROSSHAIR_SIZE / 2;
        canvas.sprite(
                UiAtlas.CROSSHAIR,
                width / 2 - radius,
                height / 2 - radius,
                HudMetrics.CROSSHAIR_SIZE,
                HudMetrics.CROSSHAIR_SIZE);
        int spacing = Math.max(1, Math.min(HudMetrics.SLOT_SPACING, (width - 4) / Hotbar.SLOTS));
        int barWidth = spacing == HudMetrics.SLOT_SPACING ? HudMetrics.HOTBAR_WIDTH : Hotbar.SLOTS * spacing + 2;
        int startX = (width - barWidth) / 2;
        int y = Math.max(1, height - HudMetrics.HOTBAR_HEIGHT);
        canvas.sprite(UiAtlas.HOTBAR, startX, y, barWidth, HudMetrics.HOTBAR_HEIGHT);
        for (int slot = 0; slot < Hotbar.SLOTS; slot++) {
            int x = startX + 1 + slot * spacing;
            if (spacing < 8) continue;
            ItemIcons.render(canvas, x, y + 1, spacing, hotbar.block(slot), hotbar.count(slot));
            if (spacing >= 18) canvas.numberWithShadow(slot + 1, x + 2, y + 2);
        }
        canvas.sprite(
                UiAtlas.SELECTION,
                startX - 1 + hotbar.selectedSlot() * spacing,
                y - 1,
                spacing + HudMetrics.SLOT_FRAME_WIDTH - HudMetrics.SLOT_SPACING,
                HudMetrics.SLOT_FRAME_HEIGHT);
    }
}
