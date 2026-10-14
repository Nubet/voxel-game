package com.norbertfila.ui;

import com.norbertfila.world.BlockId;

public final class ItemIcons {
    private ItemIcons() {}

    public static void render(UiCanvas canvas, int x, int y, int size, byte block, int count) {
        if (block == BlockId.AIR) return;
        int iconSize = Math.max(1, Math.min(HudMetrics.ITEM_SIZE, size - 4));
        canvas.sprite(UiAtlas.item(block), x + (size - iconSize) / 2, y + (size - iconSize) / 2, iconSize, iconSize);
        if (count > 1 && size >= 18) {
            int textWidth = Integer.toString(count).length() * 4 - 1;
            canvas.numberWithShadow(count, x + size - textWidth - 2, y + size - 9);
        }
    }
}
