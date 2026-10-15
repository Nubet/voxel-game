package com.norbertfila.ui.screens;

import com.norbertfila.i18n.*;
import com.norbertfila.persistence.world.WorldEntry;
import com.norbertfila.ui.*;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class WorldSelectionScreen implements Screen {
    private final Translations translations;
    public static final int ROW_HEIGHT = 36;
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm", Locale.ROOT).withZone(ZoneOffset.UTC);
    private final List<WorldEntry> entries;
    private final Button play = new Button(new UiBounds(0, 0, 88, 20), "");
    private final Button create = new Button(new UiBounds(0, 0, 88, 20), "");
    private final Button back = new Button(new UiBounds(0, 0, 88, 20), "");
    private UiBounds list = new UiBounds(8, 36, 288, 144);
    private int first, selected, focus, armed = -1;
    private UUID lastClick;
    private double lastClickTime = Double.NaN, scrollRemainder, dragOffset;
    private boolean pointerReady, dragging, submitted;

    public WorldSelectionScreen(List<WorldEntry> entries) {
        this(entries, new Translations(Language.ENGLISH));
    }

    public WorldSelectionScreen(List<WorldEntry> entries, Translations translations) {
        this.translations = Objects.requireNonNull(translations);
        this.entries = entries.stream()
                .sorted(Comparator.comparing(WorldEntry::lastSavedAt).reversed().thenComparing(WorldEntry::id))
                .toList();
        selected = this.entries.isEmpty() ? -1 : 0;
        sync();
    }

    public List<WorldEntry> entries() {
        return entries;
    }

    public Optional<UUID> selectedId() {
        return selected < 0
                ? Optional.empty()
                : Optional.of(entries.get(selected).id());
    }

    public int firstVisible() {
        return first;
    }

    public int visibleRows() {
        return list.height() / ROW_HEIGHT;
    }

    public UiBounds listBounds() {
        return list;
    }

    public Button playButton() {
        return play;
    }

    public Button createButton() {
        return create;
    }

    public Button backButton() {
        return back;
    }

    private void sync() {
        play.setEnabled(!submitted && selected >= 0 && entries.get(selected).status() == WorldEntry.Status.READY);
    }

    @Override
    public void layout(UiViewport viewport) {
        int width = Math.min(600, Math.max(288, (int) viewport.width() - 16));
        int height = Math.min(480, Math.max(224, (int) viewport.height() - 16));
        int x = Math.max(0, ((int) viewport.width() - width) / 2);
        int y = Math.max(0, ((int) viewport.height() - height) / 2);
        UiBounds next = new UiBounds(x + 8, y + 28, width - 16, (height - 68) / ROW_HEIGHT * ROW_HEIGHT);
        if (!list.equals(next)) {
            cancelInteraction();
            list = next;
            first = Math.clamp(first, 0, Math.max(0, entries.size() - visibleRows()));
        }
        int gap = (width - 264) / 4;
        play.setPosition(x + gap, y + height - 28);
        create.setPosition(x + gap * 2 + 88, y + height - 28);
        back.setPosition(x + gap * 3 + 176, y + height - 28);
    }

    private void focus(int index) {
        focus = index;
        play.setFocused(index == 1);
        create.setFocused(index == 2);
        back.setFocused(index == 3);
    }

    private Action open() {
        if (!play.enabled() || submitted) return Action.NONE;
        submitted = true;
        sync();
        return new Action.OpenWorld(entries.get(selected).id());
    }

    private Action create() {
        submitted = true;
        return new Action.ShowCreateWorld();
    }

    @Override
    public Action keyPress(UiKey key) {
        Objects.requireNonNull(key);
        if (submitted) return Action.NONE;
        if (key == UiKey.BACK) return Action.CLOSE;
        if (key == UiKey.NEXT || key == UiKey.PREVIOUS) {
            int next = focus;
            if (next < 0 && key == UiKey.PREVIOUS) next = 0;
            do next = Math.floorMod(next + (key == UiKey.NEXT ? 1 : -1), 4);
            while (next == 1 && !play.enabled());
            focus(next);
        } else if (key == UiKey.ACTIVATE) {
            return switch (focus) {
                case 0, 1 -> open();
                case 2 -> create();
                case 3 -> Action.CLOSE;
                default -> Action.NONE;
            };
        } else if (focus == 0 && !entries.isEmpty()) {
            int next = switch (key) {
                case UP -> selected - 1;
                case DOWN -> selected + 1;
                case HOME -> 0;
                case END -> entries.size() - 1;
                case PAGE_UP -> selected - visibleRows();
                case PAGE_DOWN -> selected + visibleRows();
                default -> selected;
            };
            selected = Math.clamp(next, 0, entries.size() - 1);
            first = Math.clamp(first, Math.max(0, selected - visibleRows() + 1), selected);
            first = Math.min(first, Math.max(0, entries.size() - visibleRows()));
            lastClick = null;
            armed = -1;
            sync();
        }
        return Action.NONE;
    }

    @Override
    public Action scroll(double amount, double x, double y) {
        if (submitted || !Double.isFinite(amount) || !list.contains(x, y) || amount == 0) return Action.NONE;
        scrollRemainder = Math.clamp(scrollRemainder - amount * 3, -entries.size(), entries.size());
        int steps = (int) scrollRemainder;
        scrollRemainder -= steps;
        first = Math.clamp(first + steps, 0, Math.max(0, entries.size() - visibleRows()));
        armed = -1;
        lastClick = null;
        dragging = false;
        return Action.NONE;
    }

    @Override
    public Action updatePointer(boolean down, boolean pressed, double x, double y) {
        return updatePointer(new Pointer(down, pressed, false, false, false, x, y));
    }

    @Override
    public Action updatePointer(Pointer p) {
        if (submitted) return Action.NONE;
        if (!p.leftDown()) pointerReady = true;
        if (!pointerReady) return Action.NONE;
        boolean inside = list.contains(p.x(), p.y());
        boolean track = inside && p.x() >= list.x() + list.width() - 8 && entries.size() > visibleRows();
        int row = inside && !track ? first + (int) (p.y() - list.y()) / ROW_HEIGHT : -1;
        if (row >= entries.size()) row = -1;
        if (p.leftPressed()) {
            focus(
                    inside
                            ? 0
                            : play.enabled() && play.bounds().contains(p.x(), p.y())
                                    ? 1
                                    : create.bounds().contains(p.x(), p.y())
                                            ? 2
                                            : back.bounds().contains(p.x(), p.y()) ? 3 : -1);
            armed = row;
            dragging = track;
            if (track) {
                double top =
                        list.y() + (list.height() - thumbHeight()) * first / (double) (entries.size() - visibleRows());
                dragOffset = p.y() >= top && p.y() < top + thumbHeight() ? p.y() - top : thumbHeight() / 2.0;
            }
            if (row < 0 || !entries.get(row).id().equals(lastClick)) lastClick = null;
        }
        if (dragging) {
            first = (int) Math.round(Math.clamp((p.y() - list.y() - dragOffset) / (list.height() - thumbHeight()), 0, 1)
                    * Math.max(0, entries.size() - visibleRows()));
            lastClick = null;
            if (!p.leftDown()) dragging = false;
        }
        if (!p.leftDown() && armed >= 0) {
            int clicked = armed;
            armed = -1;
            if (row == clicked) {
                selected = clicked;
                sync();
                UUID id = entries.get(selected).id();
                boolean twice = id.equals(lastClick)
                        && p.timeSeconds() >= lastClickTime
                        && p.timeSeconds() - lastClickTime <= .35;
                lastClick = id;
                lastClickTime = p.timeSeconds();
                if (twice) return open();
            }
        }
        if (play.updatePointer(p.leftDown(), p.leftPressed(), p.x(), p.y())) return open();
        if (create.updatePointer(p.leftDown(), p.leftPressed(), p.x(), p.y())) return create();
        return back.updatePointer(p.leftDown(), p.leftPressed(), p.x(), p.y()) ? Action.CLOSE : Action.NONE;
    }

    @Override
    public void cancelInteraction() {
        pointerReady = false;
        dragging = false;
        armed = -1;
        lastClick = null;
        scrollRemainder = 0;
        play.cancelInteraction();
        create.cancelInteraction();
        back.cancelInteraction();
        focus(0);
    }

    private int thumbHeight() {
        return Math.max(8, list.height() * visibleRows() / entries.size());
    }

    @Override
    public void render(UiCanvas canvas, UiViewport viewport) {
        layout(viewport);
        var font = BitmapFont.DEFAULT;
        play.setLabel(translations.text("action.play"));
        create.setLabel(translations.text("action.create"));
        back.setLabel(translations.text("action.back"));
        canvas.textWithShadow(font, translations.text("world.select"), list.x(), list.y() - 22, 1, 1, 1, 1);
        canvas.rectangle(list.x(), list.y(), list.width(), list.height(), .04f, .05f, .07f, 1);
        if (entries.isEmpty())
            canvas.text(font, translations.text("world.empty"), list.x() + 8, list.y() + 8, .7f, .75f, .8f, 1);
        for (int row = 0; row < visibleRows() && first + row < entries.size(); row++) {
            int index = first + row, y = list.y() + row * ROW_HEIGHT;
            var entry = entries.get(index);
            if (index == selected)
                canvas.rectangle(list.x(), y, list.width() - 8, ROW_HEIGHT, focus == 0 ? .25f : .15f, .22f, .15f, 1);
            canvas.text(font, font.ellipsize(entry.name(), list.width() - 24), list.x() + 6, y + 1, 1, 1, 1, 1);
            boolean ready = entry.status() == WorldEntry.Status.READY;
            String detail = ready
                    ? DATE.format(entry.lastSavedAt()) + " UTC"
                    : switch (entry.status()) {
                        case INCOMPLETE -> translations.text("world.incomplete");
                        case CORRUPTED -> translations.text("world.corrupted");
                        case UNSUPPORTED_VERSION -> translations.text("world.unsupported");
                        case UNREADABLE -> translations.text("world.unreadable");
                        case READY -> throw new AssertionError();
                    };
            canvas.text(
                    font,
                    font.ellipsize(detail, list.width() - 24),
                    list.x() + 6,
                    y + 18,
                    ready ? .6f : 1,
                    .65f,
                    ready ? .7f : .4f,
                    1);
        }
        if (entries.size() > visibleRows()) {
            int trackX = list.x() + list.width() - 6;
            int thumbHeight = thumbHeight();
            int thumbY = list.y() + (list.height() - thumbHeight) * first / (entries.size() - visibleRows());
            canvas.rectangle(trackX, list.y(), 4, list.height(), .15f, .18f, .23f, 1);
            canvas.rectangle(trackX, thumbY, 4, thumbHeight, .65f, .7f, .75f, 1);
        }
        play.render(canvas, font);
        create.render(canvas, font);
        back.render(canvas, font);
    }
}
