package com.norbertfila.ui.screens;

import com.norbertfila.i18n.*;
import com.norbertfila.player.Inventory;
import com.norbertfila.player.InventorySnapshot;
import com.norbertfila.ui.*;
import com.norbertfila.world.BlockId;
import com.norbertfila.world.BlockRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class InventoryScreen implements Screen {
    private final Translations translations;
    public static final int CELL = 28, PANEL_WIDTH = 268, PANEL_HEIGHT = 192;
    private static final BitmapFont FONT = BitmapFont.DEFAULT;
    private static final List<Byte> CATALOG = catalog();
    private final boolean creative;
    private InventorySnapshot inventory = InventorySnapshot.EMPTY;
    private int selectedSlot, panelX, panelY, focus;
    private double pointerX = -1, pointerY = -1;
    private boolean pointerReady;
    private boolean keyboardFocus;

    public InventoryScreen(boolean creative) {
        this(creative, new Translations(Language.ENGLISH));
    }

    public InventoryScreen(boolean creative, Translations translations) {
        this.translations = Objects.requireNonNull(translations);
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
        panelY = Math.max(0, ((int) viewport.height() - panelHeight()) / 2);
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
                panelX + 8 + column * CELL, panelY + 36 + row * CELL + (row == rows() ? 12 : 0), CELL - 2, CELL - 2);
    }

    private int rows() {
        return creative ? Math.max(3, (CATALOG.size() + 8) / 9) : 3;
    }

    private int catalogCells() {
        return rows() * 9;
    }

    private int cells() {
        return catalogCells() + 9;
    }

    private int panelHeight() {
        return PANEL_HEIGHT + (rows() - 3) * CELL;
    }

    @Override
    public Action updatePointer(boolean down, boolean pressed, double x, double y) {
        return updatePointer(new Pointer(down, pressed, false, false, false, x, y));
    }

    @Override
    public Action updatePointer(Pointer pointer) {
        if (pointer.x() != pointerX || pointer.y() != pointerY) keyboardFocus = false;
        pointerX = pointer.x();
        pointerY = pointer.y();
        if (!pointer.leftDown() && !pointer.rightDown()) pointerReady = true;
        if (!pointerReady || (!pointer.leftPressed() && !pointer.rightPressed())) return Action.NONE;
        for (int cell = 0; cell < cells(); cell++)
            if (bounds(cell).contains(pointerX, pointerY)) {
                focus = cell;
                keyboardFocus = false;
                return activate(cell, pointer.rightPressed(), pointer.shift());
            }
        return Action.NONE;
    }

    private Action activate(int cell, boolean right, boolean quick) {
        if (creative && cell < catalogCells())
            return cell < CATALOG.size() ? new Action.CreativePick(CATALOG.get(cell), selectedSlot) : Action.NONE;
        return new Action.SlotClick(inventorySlot(cell), right, quick);
    }

    private int inventorySlot(int cell) {
        return cell >= catalogCells() ? cell - catalogCells() : cell + 9;
    }

    private Action drop(boolean wholeStack) {
        int cell = keyboardFocus ? focus : -1;
        if (!keyboardFocus)
            for (int candidate = 0; candidate < cells(); candidate++)
                if (bounds(candidate).contains(pointerX, pointerY)) {
                    cell = candidate;
                    break;
                }
        int slot = cell >= 0 && (!creative || cell >= catalogCells()) ? inventorySlot(cell) : Inventory.CARRIED;
        return new Action.DropSlot(slot, wholeStack);
    }

    @Override
    public Action keyPress(UiKey key) {
        return switch (key) {
            case BACK -> Action.CLOSE;
            case NEXT, INCREASE, UP -> {
                keyboardFocus = true;
                focus = (focus + 1) % cells();
                yield Action.NONE;
            }
            case PREVIOUS, DECREASE, DOWN -> {
                keyboardFocus = true;
                focus = Math.floorMod(focus - 1, cells());
                yield Action.NONE;
            }
            case ACTIVATE -> activate(focus, false, false);
            case DROP_ONE -> drop(false);
            case DROP_STACK -> drop(true);
            default -> Action.NONE;
        };
    }

    @Override
    public void cancelInteraction() {
        pointerReady = false;
        keyboardFocus = false;
        pointerX = pointerY = -1;
    }

    @Override
    public void render(UiCanvas canvas, UiViewport viewport) {
        canvas.rectangle(0, 0, (int) viewport.width(), (int) viewport.height(), 0, 0, 0, .5f);
        canvas.rectangle(panelX, panelY, PANEL_WIDTH, panelHeight(), .16f, .18f, .21f, 1);
        canvas.textWithShadow(
                FONT,
                creative
                        ? translations.text("inventory.creative", selectedSlot + 1)
                        : translations.text("inventory.title"),
                panelX + 8,
                panelY + 10,
                1,
                1,
                1,
                1);
        String hovered = null;
        for (int cell = 0; cell < cells(); cell++) {
            var box = bounds(cell);
            int slot = inventorySlot(cell);
            boolean selected = cell >= catalogCells() && slot == selectedSlot;
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
            byte block = creative && cell < catalogCells()
                    ? (cell < CATALOG.size() ? CATALOG.get(cell) : BlockId.AIR)
                    : inventory.block(slot);
            int count = creative && cell < catalogCells() ? 0 : inventory.count(slot);
            ItemIcons.render(canvas, box.x(), box.y(), CELL - 2, block, count);
            if (hover && block != BlockId.AIR)
                hovered = translations.text(
                        "block." + BlockRegistry.DEFAULT.definition(block).name());
        }
        canvas.textWithShadow(
                FONT,
                hovered == null ? translations.text(creative ? "inventory.creativeHint" : "inventory.hint") : hovered,
                panelX + 8,
                panelY + panelHeight() - 22,
                1,
                1,
                1,
                1);
        canvas.textWithShadow(
                FONT, translations.text("inventory.dropHint"), panelX + 8, panelY + panelHeight() - 12, 1, 1, 1, 1);
        if (inventory.count(Inventory.CARRIED) > 0
                && Double.isFinite(pointerX)
                && Double.isFinite(pointerY)
                && pointerX >= 0
                && pointerY >= 0)
            ItemIcons.render(
                    canvas,
                    (int) pointerX - 12,
                    (int) pointerY - 12,
                    26,
                    inventory.block(Inventory.CARRIED),
                    inventory.count(Inventory.CARRIED));
    }
}
