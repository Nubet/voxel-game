package com.norbertfila.ui;

/** Minecraft's attack cooldown bar: a 16x4 strip under the crosshair while a swing recharges. */
public final class AttackIndicator {
    static final int WIDTH = 16, HEIGHT = 4;
    private static final int GAP_BELOW_CROSSHAIR = 2;

    public void render(UiCanvas canvas, UiViewport viewport, double charge, int scale) {
        if (!Double.isFinite(charge) || charge < 0 || charge > 1 || scale < 1)
            throw new IllegalArgumentException("Invalid attack indicator state");
        if (charge >= 1) return;
        float width = WIDTH * scale, height = HEIGHT * scale;
        float x = (float) viewport.width() / 2 - width / 2;
        float y = (float) viewport.height() / 2 + HudMetrics.CROSSHAIR_SIZE * scale / 2f + GAP_BELOW_CROSSHAIR * scale;
        canvas.rectangle(x, y, width, height, .08f, .08f, .1f, .75f);
        float inset = scale;
        canvas.rectangle(
                x + inset, y + inset, (float) ((width - 2 * inset) * charge), height - 2 * inset, .9f, .9f, .9f, 1);
    }
}
