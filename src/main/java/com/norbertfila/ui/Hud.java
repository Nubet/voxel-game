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
        canvas.rectangle(centerX - 8, centerY - 2, 16, 4, 0, 0, 0, .8f);
        canvas.rectangle(centerX - 2, centerY - 8, 4, 16, 0, 0, 0, .8f);
        canvas.rectangle(centerX - 7, centerY - 1, 14, 2, 1, 1, 1, 1);
        canvas.rectangle(centerX - 1, centerY - 7, 2, 14, 1, 1, 1, 1);
        int gap = Math.min(2, width / (Hotbar.SLOTS * 24));
        int size = Math.max(1, Math.min(20, (width - 2 * gap) / Hotbar.SLOTS - gap));
        int startX = (width - Hotbar.SLOTS * size - (Hotbar.SLOTS - 1) * gap) / 2;
        int y = Math.max(0, height - size - 16);
        for (int slot = 0; slot < Hotbar.SLOTS; slot++) {
            int x = startX + slot * (size + gap);
            boolean selected = slot == hotbar.selectedSlot();
            canvas.rectangle(x, y, size, size, selected ? .96f : .25f, selected ? .8f : .29f, selected ? .3f : .35f, 1);
            if (size < 8) continue;
            canvas.rectangle(x + 2, y + 2, size - 4, size - 4, .06f, .08f, .11f, .92f);
            byte block = hotbar.block(slot);
            if (block != BlockId.AIR) {
                item(canvas, x, y, size, block, hotbar.count(slot));
            }
            if (size < 18) continue;
            String label = Integer.toString(slot + 1);
            canvas.textWithShadow(FONT, label, x + 2, y + 2, .5, .94f, .94f, .94f, 1);
        }
    }

    public static void item(UiCanvas canvas, int x, int y, int size, byte block, int count) {
        if (block == BlockId.AIR) return;
        float[] color = BLOCK_COLORS[Byte.toUnsignedInt(block)];
        canvas.rectangle(x + size / 4, y + 4, size / 2, Math.max(1, size / 2), color[0], color[1], color[2], 1);
        if (count > 1 && size >= 24) {
            String text = Integer.toString(count);
            canvas.textWithShadow(FONT, text, x + size - FONT.width(text) - 2, y + 5, 1, 1, 1, 1);
        }
    }
}
