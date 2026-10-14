package com.norbertfila.ui.screens;

import com.norbertfila.player.Inventory;
import com.norbertfila.player.InventorySnapshot;
import com.norbertfila.ui.*;
import com.norbertfila.world.BlockId;
import com.norbertfila.world.BlockRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class InventoryScreen implements Screen {
    public static final int CELL = 28, PANEL_WIDTH = 268, PANEL_HEIGHT = 192;
    private static final BitmapFont FONT = BitmapFont.DEFAULT;
    private static final List<Byte> CATALOG = catalog();
    private final boolean creative;
    private InventorySnapshot inventory = InventorySnapshot.EMPTY;
    private int selectedSlot, panelX, panelY, focus;
    private double pointerX = -1, pointerY = -1;
    private boolean pointerReady;

    public InventoryScreen(boolean creative) {
        this.creative = creative;
    }

    public boolean creative() {
        return creative;
    }

    public static List<Byte> catalogBlocks() {
        return CATALOG;
    }

    public void update(InventorySnapshot inventory, int selectedSlot, UiViewport viewport) {
        this.inventory = Objects.requireNonNull(inventory);
        this.selectedSlot = selectedSlot;
        panelX = Math.max(0, ((int) viewport.width() - PANEL_WIDTH) / 2);
        panelY = Math.max(0, ((int) viewport.height() - PANEL_HEIGHT) / 2);
    }

    private static List<Byte> catalog() {
        var blocks = new ArrayList<Byte>();
        for (int id = 1; id < 256; id++)
            if (BlockId.isValid((byte) id)) {
                BlockRegistry.DEFAULT.definition((byte) id);
                blocks.add((byte) id);
            }
        return List.copyOf(blocks);
    }

    public UiBounds bounds(int cell) {
        int row = cell / 9, column = cell % 9;
        return new UiBounds(
                panelX + 8 + column * CELL, panelY + (row == 3 ? 132 : 36 + row * CELL), CELL - 2, CELL - 2);
    }

    @Override
    public Action updatePointer(boolean down, boolean pressed, double x, double y) {
        return updatePointer(new Pointer(down, pressed, false, false, false, x, y));
    }

    @Override
    public Action updatePointer(Pointer pointer) {
        pointerX = pointer.x();
        pointerY = pointer.y();
        if (!pointer.leftDown() && !pointer.rightDown()) pointerReady = true;
        if (!pointerReady || (!pointer.leftPressed() && !pointer.rightPressed())) return Action.NONE;
        for (int cell = 0; cell < 36; cell++)
            if (bounds(cell).contains(pointerX, pointerY)) {
                focus = cell;
                return activate(cell, pointer.rightPressed(), pointer.shift());
            }
        return Action.NONE;
    }

    private Action activate(int cell, boolean right, boolean quick) {
        if (creative && cell < 27)
            return cell < CATALOG.size() ? new Action.CreativePick(CATALOG.get(cell), selectedSlot) : Action.NONE;
        return new Action.SlotClick(cell >= 27 ? cell - 27 : cell + 9, right, quick);
    }

    @Override
    public Action keyPress(UiKey key) {
        return switch (key) {
            case BACK -> Action.CLOSE;
            case NEXT, INCREASE -> {
                focus = (focus + 1) % 36;
                yield Action.NONE;
            }
            case PREVIOUS, DECREASE -> {
                focus = Math.floorMod(focus - 1, 36);
                yield Action.NONE;
            }
            case ACTIVATE -> activate(focus, false, false);
        };
    }

    @Override
    public void cancelInteraction() {
        pointerReady = false;
        pointerX = pointerY = -1;
    }

    @Override
    public void render(UiCanvas canvas, UiViewport viewport) {
        canvas.rectangle(0, 0, (int) viewport.width(), (int) viewport.height(), 0, 0, 0, .5f);
        canvas.rectangle(panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, .16f, .18f, .21f, 1);
        canvas.textWithShadow(
                FONT,
                creative ? "Creative - slot " + (selectedSlot + 1) : "Inventory",
                panelX + 8,
                panelY + 10,
                1,
                1,
                1,
                1);
        String hovered = null;
        for (int cell = 0; cell < 36; cell++) {
            var box = bounds(cell);
            int slot = cell >= 27 ? cell - 27 : cell + 9;
            boolean selected = cell >= 27 && slot == selectedSlot;
            boolean hover = box.contains(pointerX, pointerY);
            canvas.rectangle(
                    box.x(), box.y(), box.width(), box.height(), selected ? .95f : .4f, selected ? .8f : .43f, .3f, 1);
            canvas.rectangle(
                    box.x() + 1,
                    box.y() + 1,
                    box.width() - 2,
                    box.height() - 2,
                    hover || cell == focus ? .28f : .08f,
                    .1f,
                    .13f,
                    1);
            byte block = creative && cell < 27
                    ? (cell < CATALOG.size() ? CATALOG.get(cell) : BlockId.AIR)
                    : inventory.block(slot);
            int count = creative && cell < 27 ? 0 : inventory.count(slot);
            Hud.item(canvas, box.x(), box.y(), CELL - 2, block, count);
            if (hover && block != BlockId.AIR)
                hovered = BlockRegistry.DEFAULT.definition(block).name();
        }
        canvas.textWithShadow(
                FONT,
                hovered == null ? (creative ? "1-9 / wheel: slot   E: Back" : "E / Esc: Back") : hovered,
                panelX + 8,
                panelY + 170,
                1,
                1,
                1,
                1);
        if (inventory.count(Inventory.CARRIED) > 0
                && Double.isFinite(pointerX)
                && Double.isFinite(pointerY)
                && pointerX >= 0
                && pointerY >= 0)
            Hud.item(
                    canvas,
                    (int) pointerX - 12,
                    (int) pointerY - 12,
                    26,
                    inventory.block(Inventory.CARRIED),
                    inventory.count(Inventory.CARRIED));
    }
}
