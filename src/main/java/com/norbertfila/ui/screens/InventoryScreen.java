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
    public static final int CELL = 28, PANEL_WIDTH = 268, PANEL_HEIGHT = 208;
    private static final BitmapFont FONT = BitmapFont.DEFAULT;
    private static final List<Byte> CATALOG = catalog();
    private final boolean creative;
    private InventorySnapshot inventory = InventorySnapshot.EMPTY;
    private int selectedSlot, panelX, panelY, focus, page;
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
        panelY = Math.max(0, ((int) viewport.height() - PANEL_HEIGHT) / 2);
    }

    private static List<Byte> catalog() {
        var blocks = new ArrayList<Byte>();
        for (int id = 1; id < 256; id++)
            if (BlockId.isValid((byte) id)) {
                if (id > Byte.toUnsignedInt(BlockId.VINE_EAST) && id <= Byte.toUnsignedInt(BlockId.VINE_NORTH)
                        || id > Byte.toUnsignedInt(BlockId.COCOA_EAST) && id <= Byte.toUnsignedInt(BlockId.COCOA_NORTH)
                        || id == Byte.toUnsignedInt(BlockId.TALL_GRASS_TOP)
                        || id == Byte.toUnsignedInt(BlockId.LARGE_FERN_TOP)) continue;
                BlockRegistry.DEFAULT.definition((byte) id);
                blocks.add((byte) id);
            }
        return List.copyOf(blocks);
    }

    public UiBounds bounds(int cell) {
        int row = cell / 9, column = cell % 9;
        return new UiBounds(
                panelX + 8 + column * CELL, panelY + 36 + row * CELL + (row == 3 ? 12 : 0), CELL - 2, CELL - 2);
    }

    private int catalogCells() {
        return 27;
    }

    private int cells() {
        return catalogCells() + 9;
    }

    private int pages() {
        return (CATALOG.size() + catalogCells() - 1) / catalogCells();
    }

    UiBounds pageBounds(boolean next) {
        return new UiBounds(panelX + (next ? 240 : 176), panelY + 7, 20, 20);
    }

    private void changePage(int delta) {
        page = Math.clamp(page + delta, 0, pages() - 1);
        focus = 0;
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
        if (creative && pointer.leftPressed()) {
            for (boolean next : new boolean[] {false, true})
                if (pageBounds(next).contains(pointerX, pointerY)) {
                    changePage(next ? 1 : -1);
                    return Action.NONE;
                }
        }
        for (int cell = 0; cell < cells(); cell++)
            if (bounds(cell).contains(pointerX, pointerY)) {
                focus = cell;
                keyboardFocus = false;
                return activate(cell, pointer.rightPressed(), pointer.shift());
            }
        return Action.NONE;
    }

    private Action activate(int cell, boolean right, boolean quick) {
        if (creative && cell < catalogCells()) {
            int index = page * catalogCells() + cell;
            return index < CATALOG.size() ? new Action.CreativePick(CATALOG.get(index), selectedSlot) : Action.NONE;
        }
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
            case PAGE_UP, PAGE_DOWN -> {
                if (creative) {
                    changePage(key == UiKey.PAGE_DOWN ? 1 : -1);
                    keyboardFocus = true;
                }
                yield Action.NONE;
            }
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
        canvas.rectangle(panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, .16f, .18f, .21f, 1);
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
        if (creative) {
            for (boolean next : new boolean[] {false, true}) {
                var button = pageBounds(next);
                canvas.rectangle(button.x(), button.y(), button.width(), button.height(), .28f, .3f, .34f, 1);
                canvas.textWithShadow(FONT, next ? ">" : "<", button.x() + 7, button.y() + 5, 1, 1, 1, 1);
            }
            canvas.textWithShadow(
                    FONT,
                    translations.text("inventory.page", page + 1, pages()),
                    panelX + 201,
                    panelY + 12,
                    1,
                    1,
                    1,
                    1);
        }
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
            int index = page * catalogCells() + cell;
            byte block = creative && cell < catalogCells()
                    ? (index < CATALOG.size() ? CATALOG.get(index) : BlockId.AIR)
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
                panelY + PANEL_HEIGHT - 38,
                1,
                1,
                1,
                1);
        canvas.textWithShadow(
                FONT, translations.text("inventory.dropHint"), panelX + 8, panelY + PANEL_HEIGHT - 20, 1, 1, 1, 1);
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
