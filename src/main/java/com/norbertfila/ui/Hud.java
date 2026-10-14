package com.norbertfila.ui;

import com.norbertfila.core.Hotbar;
import com.norbertfila.world.BlockId;

public final class Hud {
    private static final int[] DIGITS = {0x3f, 0x06, 0x5b, 0x4f, 0x66, 0x6d, 0x7d, 0x07, 0x7f, 0x6f};
    private static final int[][] SEGMENTS = {
        {0, 0, 6, 1}, {5, 0, 1, 6}, {5, 5, 1, 6}, {0, 10, 6, 1}, {0, 5, 1, 6}, {0, 0, 1, 6}, {0, 5, 6, 1}
    };
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
        {.25f, .4f, .8f}
    };

    public void render(UiCanvas canvas, Hotbar hotbar, UiViewport viewport) {
        int width = (int) viewport.width(), height = (int) viewport.height();
        int centerX = width / 2, centerY = height / 2;
        canvas.rectangle(centerX - 8, centerY - 2, 16, 4, 0, 0, 0, .8f);
        canvas.rectangle(centerX - 2, centerY - 8, 4, 16, 0, 0, 0, .8f);
        canvas.rectangle(centerX - 7, centerY - 1, 14, 2, 1, 1, 1, 1);
        canvas.rectangle(centerX - 1, centerY - 7, 2, 14, 1, 1, 1, 1);
        int gap = Math.min(4, width / (Hotbar.SLOTS * 16));
        int size = Math.max(1, Math.min(48, (width - 2 * gap) / Hotbar.SLOTS - gap));
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
                float[] color = BLOCK_COLORS[Byte.toUnsignedInt(block)];
                canvas.rectangle(
                        x + size / 4, y + 5, size / 2, Math.max(1, size - 23), color[0], color[1], color[2], 1);
            }
            if (size < 18) continue;
            int label = slot + 1;
            int digits = label < 10 ? 1 : 2;
            for (int digit = 0; digit < digits; digit++) {
                int value = digits == 1 ? label : digit == 0 ? label / 10 : label % 10;
                int digitX = x + (digits == 1 ? 5 : 3 + digit * 7);
                for (int segment = 0; segment < SEGMENTS.length; segment++) {
                    if ((DIGITS[value] & (1 << segment)) == 0) continue;
                    int[] shape = SEGMENTS[segment];
                    canvas.rectangle(
                            digitX + shape[0], y + size - 15 + shape[1], shape[2], shape[3], .94f, .94f, .94f, 1);
                }
            }
        }
    }
}
