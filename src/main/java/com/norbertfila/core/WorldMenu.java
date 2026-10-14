package com.norbertfila.core;

import com.norbertfila.persistence.world.*;
import com.norbertfila.ui.*;
import com.norbertfila.ui.screens.*;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class WorldMenu implements Screen {
    public enum State {
        MAIN,
        SCANNING,
        SELECTING,
        FORM,
        CREATING,
        ERROR,
        OPENING,
        CLOSED
    }

    private static final Logger LOG = Logger.getLogger(WorldMenu.class.getName());
    private final WorldIo io;
    private final WorldCatalog catalog;
    private final String generatorId;
    private final int generatorVersion;
    private State state = State.MAIN;
    private Screen screen = new MainMenuScreen(false);
    private CreateWorldScreen form;
    private CompletableFuture<List<WorldEntry>> listing;
    private CompletableFuture<WorldEntry> creating;
    private UUID selected;
    private boolean changed, closing, cancelledScan;
    private State errorReturn;

    public WorldMenu(WorldIo io, WorldCatalog catalog, String generatorId, int generatorVersion) {
        this.io = Objects.requireNonNull(io);
        this.catalog = Objects.requireNonNull(catalog);
        this.generatorId = Objects.requireNonNull(generatorId);
        this.generatorVersion = generatorVersion;
    }

    public State state() {
        return state;
    }

    public Screen screen() {
        return screen;
    }

    private void show(State next, Screen view) {
        screen.cancelInteraction();
        screen = view;
        screen.cancelInteraction();
        state = next;
        changed = true;
    }

    private void scan() {
        cancelledScan = false;
        listing = io.list(catalog);
        show(State.SCANNING, new SessionStatusScreen("Odczytywanie światów..."));
    }

    private void error(String title, Throwable failure, State back) {
        LOG.log(Level.WARNING, title, failure);
        errorReturn = back;
        show(
                State.ERROR,
                new WorldErrorScreen(
                        title,
                        Objects.toString(
                                failure.getMessage(), failure.getClass().getSimpleName())));
    }

    @Override
    public Action update() {
        changed = false;
        if (listing != null && listing.isDone()) {
            var completed = listing;
            listing = null;
            try {
                var entries = completed.join();
                if (!closing && !cancelledScan) show(State.SELECTING, new WorldSelectionScreen(entries));
            } catch (CompletionException failure) {
                if (!closing && !cancelledScan) error("Błąd listy światów", failure.getCause(), State.MAIN);
            }
            if (cancelledScan && !closing) show(State.MAIN, new MainMenuScreen(false));
        }
        if (creating != null && creating.isDone()) {
            var completed = creating;
            creating = null;
            try {
                completed.join();
                form = null;
                if (!closing) scan();
            } catch (CompletionException failure) {
                if (!closing) error("Nie utworzono świata", failure.getCause(), State.FORM);
                else LOG.log(Level.WARNING, "World creation failed during shutdown", failure.getCause());
            }
        }
        if (closing && listing == null && creating == null) state = State.CLOSED;
        if (state == State.CLOSED) return Action.QUIT;
        return state == State.OPENING ? new Action.OpenWorld(selected) : Action.NONE;
    }

    public void sessionEnded(Throwable failure) {
        if (state != State.OPENING) throw new IllegalStateException("No selected session");
        selected = null;
        if (failure == null) scan();
        else error("Błąd sesji świata", failure, State.SELECTING);
    }

    private void handle(Action action) {
        if (changed || closing) return;
        if (action == Action.QUIT) {
            requestClose();
            return;
        }
        if (action == Action.CLOSE) {
            switch (state) {
                case SELECTING -> show(State.MAIN, new MainMenuScreen(false));
                case FORM -> {
                    form = null;
                    scan();
                }
                case ERROR -> {
                    if (errorReturn == State.FORM) {
                        form.resumeEditing();
                        show(State.FORM, form);
                    } else if (errorReturn == State.SELECTING) scan();
                    else show(State.MAIN, new MainMenuScreen(false));
                }
                default -> {}
            }
        } else if (state == State.MAIN && action == Action.PLAY) scan();
        else if (state == State.SELECTING && action instanceof Action.ShowCreateWorld) {
            form = new CreateWorldScreen();
            show(State.FORM, form);
        } else if (state == State.FORM && action instanceof Action.CreateWorld request) {
            long seed =
                    request.seed().orElseGet(() -> ThreadLocalRandom.current().nextLong());
            var metadata = new WorldMetadata(
                    new WorldIdentity(UUID.randomUUID(), seed, generatorId, generatorVersion),
                    request.name(),
                    Instant.now());
            creating = io.create(catalog, metadata);
            show(State.CREATING, new SessionStatusScreen("Tworzenie świata..."));
        } else if (state == State.SELECTING && action instanceof Action.OpenWorld request) {
            selected = request.id();
            show(State.OPENING, new SessionStatusScreen("Otwieranie świata..."));
        }
    }

    @Override
    public Action requestClose() {
        closing = true;
        changed = true;
        return Action.NONE;
    }

    @Override
    public Action keyPress(UiKey key) {
        if (changed || closing) return Action.NONE;
        if (state == State.SCANNING && key == UiKey.BACK) {
            cancelledScan = true;
            show(State.SCANNING, new SessionStatusScreen("Anulowanie..."));
        } else handle(screen.keyPress(key));
        return Action.NONE;
    }

    @Override
    public Action updatePointer(boolean down, boolean pressed, double x, double y) {
        return updatePointer(new Pointer(down, pressed, false, false, false, x, y));
    }

    @Override
    public Action updatePointer(Pointer pointer) {
        if (!changed && !closing) handle(screen.updatePointer(pointer));
        return Action.NONE;
    }

    @Override
    public boolean acceptsText() {
        return !changed && !closing && screen.acceptsText();
    }

    @Override
    public void textInput(int codePoint) {
        if (acceptsText()) screen.textInput(codePoint);
    }

    @Override
    public Action scroll(double amount, double x, double y) {
        if (!changed && !closing) handle(screen.scroll(amount, x, y));
        return Action.NONE;
    }

    @Override
    public void cancelInteraction() {
        screen.cancelInteraction();
    }

    @Override
    public void layout(UiViewport viewport) {
        screen.layout(viewport);
    }

    @Override
    public void render(UiCanvas canvas, UiViewport viewport) {
        screen.render(canvas, viewport);
    }
}
