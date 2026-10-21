package com.norbertfila.ui;

/** Minecraft's row of ten hearts above the left half of the hotbar; each heart is two health points. */
public final class HealthBar {
    private static final int HEARTS = 10;
    private static final int HEART_SPACING = 8;
    private static final int GAP_ABOVE_HOTBAR = 2;

    public void render(UiCanvas canvas, UiViewport viewport, float health, float maxHealth, boolean hurt) {
        if (!Float.isFinite(health) || !Float.isFinite(maxHealth) || maxHealth <= 0)
            throw new IllegalArgumentException("Invalid health");
        int width = (int) viewport.width(), height = (int) viewport.height();
        int x = (width - HudMetrics.HOTBAR_WIDTH) / 2;
        int y = height - HudMetrics.HOTBAR_HEIGHT - GAP_ABOVE_HOTBAR - UiAtlas.HEART_FULL.height();
        if (x < 0 || y < 0) return;
        int points = (int) Math.ceil(Math.clamp(health, 0, maxHealth) / maxHealth * 2 * HEARTS);
        for (int heart = 0; heart < HEARTS; heart++) {
            int left = x + heart * HEART_SPACING;
            var container = hurt ? UiAtlas.HEART_FLASH : UiAtlas.HEART_EMPTY;
            canvas.sprite(container, left, y, container.width(), container.height());
            int filled = points - heart * 2;
            if (filled >= 2) canvas.sprite(UiAtlas.HEART_FULL, left, y, 9, 9);
            else if (filled == 1) canvas.sprite(UiAtlas.HEART_HALF, left, y, 9, 9);
        }
    }
}
