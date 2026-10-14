package com.norbertfila.ui;

import com.norbertfila.core.Hotbar;
import com.norbertfila.world.BlockId;

public final class Hud {
    private static final BitmapFont FONT = BitmapFont.DEFAULT;
    private static final float[][] BLOCK_COLORS = {
        {0, 0, 0},
        {.28f, .68f, .21f},
        {.53f, .34f, .19f},
        {.53f, .55f, .57f},
        {.82f, .72f, .48f},
        {.45f, .43f, .4f},
        {.17f, .16f, .15f},
        {.92f, .94f, .98f},
        {.44f, .27f, .13f},
        {.24f, .52f, .2f},
        {.61f, .4f, .24f},
        {.46f, .48f, .5f},
        {.62f, .28f, .18f},
        {.42f, .73f, .82f},
        {.79f, .68f, .46f},
        {.4f, .42f, .44f},
        {.25f, .4f, .8f},
        {.3f, .65f, .2f},
        {.2f, .5f, .2f},
        {.95f, .85f, .15f},
        {.95f, .95f, .95f},
        {.85f, .15f, .15f}
    };

    public void render(UiCanvas canvas, Hotbar hotbar, UiViewport viewport) {
        int width = (int) viewport.width(), height = (int) viewport.height();
        int centerX = width / 2, centerY = height / 2;
        int radius = HudMetrics.CROSSHAIR_SIZE / 2;
        canvas.rectangle(centerX - radius, centerY - 1, HudMetrics.CROSSHAIR_SIZE, 3, 0, 0, 0, .8f);
        canvas.rectangle(centerX - 1, centerY - radius, 3, HudMetrics.CROSSHAIR_SIZE, 0, 0, 0, .8f);
        canvas.rectangle(centerX - radius, centerY, HudMetrics.CROSSHAIR_SIZE, 1, 1, 1, 1, 1);
        canvas.rectangle(centerX, centerY - radius, 1, HudMetrics.CROSSHAIR_SIZE, 1, 1, 1, 1);
        int spacing = Math.max(1, Math.min(HudMetrics.SLOT_SPACING, (width - 4) / Hotbar.SLOTS));
        int barWidth = spacing == HudMetrics.SLOT_SPACING ? HudMetrics.HOTBAR_WIDTH : Hotbar.SLOTS * spacing + 2;
        int startX = (width - barWidth) / 2;
        int y = Math.max(1, height - HudMetrics.HOTBAR_HEIGHT);
        canvas.rectangle(startX, y, barWidth, HudMetrics.HOTBAR_HEIGHT, .12f, .14f, .17f, 1);
        for (int slot = 0; slot < Hotbar.SLOTS; slot++) {
            int x = startX + 1 + slot * spacing;
            canvas.rectangle(x, y + 1, spacing, 20, .25f, .29f, .35f, 1);
            if (spacing < 8) continue;
            canvas.rectangle(x + 1, y + 2, spacing - 2, 18, .06f, .08f, .11f, .92f);
            byte block = hotbar.block(slot);
            if (block != BlockId.AIR) {
                item(canvas, x, y + 1, spacing, block, hotbar.count(slot));
            }
            if (spacing < 18) continue;
            String label = Integer.toString(slot + 1);
            canvas.textWithShadow(FONT, label, x + 2, y + 2, .5, .94f, .94f, .94f, 1);
        }
        int selectedX = startX - 1 + hotbar.selectedSlot() * spacing;
        int frameWidth = spacing + HudMetrics.SLOT_FRAME_WIDTH - HudMetrics.SLOT_SPACING;
        int frameHeight = HudMetrics.SLOT_FRAME_HEIGHT;
        canvas.rectangle(selectedX, y - 1, frameWidth, 1, .96f, .8f, .3f, 1);
        canvas.rectangle(selectedX, y - 1, 1, frameHeight, .96f, .8f, .3f, 1);
        canvas.rectangle(selectedX + frameWidth - 1, y - 1, 1, frameHeight, .96f, .8f, .3f, 1);
        canvas.rectangle(selectedX, y + frameHeight - 2, frameWidth, 1, .96f, .8f, .3f, 1);
    }

    public static void item(UiCanvas canvas, int x, int y, int size, byte block, int count) {
        if (block == BlockId.AIR) return;
        float[] color = BLOCK_COLORS[Byte.toUnsignedInt(block)];
        int iconSize = Math.max(1, Math.min(HudMetrics.ITEM_SIZE, size - 4));
        canvas.rectangle(
                x + (size - iconSize) / 2,
                y + (size - iconSize) / 2,
                iconSize,
                iconSize,
                color[0],
                color[1],
                color[2],
                1);
        if (count > 1 && size >= 24) {
            String text = Integer.toString(count);
            canvas.textWithShadow(FONT, text, x + size - FONT.width(text) - 2, y + 5, 1, 1, 1, 1);
        }
    }
}
