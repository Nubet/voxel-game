package com.norbertfila.ui;

import com.norbertfila.persistence.world.WorldMetadata;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.UUID;

public interface Screen {
    sealed interface Action {
        Action NONE = Simple.NONE,
                CLOSE = Simple.CLOSE,
                PLAY = Simple.PLAY,
                QUIT = Simple.QUIT,
                BACK_TO_MENU = Simple.BACK_TO_MENU,
                RETRY_SAVE = Simple.RETRY_SAVE,
                DISCARD_CHANGES = Simple.DISCARD_CHANGES;

        enum Simple implements Action {
            NONE,
            CLOSE,
            PLAY,
            QUIT,
            BACK_TO_MENU,
            RETRY_SAVE,
            DISCARD_CHANGES
        }

        record SlotClick(int slot, boolean right, boolean quickMove) implements Action {}

        record CreativePick(byte block, int slot) implements Action {}

        record OpenWorld(UUID id) implements Action {
            public OpenWorld {
                Objects.requireNonNull(id);
            }
        }

        record ShowCreateWorld() implements Action {}

        record CreateWorld(String name, OptionalLong seed) implements Action {
            public CreateWorld {
                name = WorldMetadata.normalizeName(name);
                Objects.requireNonNull(seed);
            }
        }
    }

    record Pointer(
            boolean leftDown,
            boolean leftPressed,
            boolean rightDown,
            boolean rightPressed,
            boolean shift,
            double x,
            double y,
            double timeSeconds) {
        public Pointer(
                boolean leftDown,
                boolean leftPressed,
                boolean rightDown,
                boolean rightPressed,
                boolean shift,
                double x,
                double y) {
            this(leftDown, leftPressed, rightDown, rightPressed, shift, x, y, Double.NaN);
        }
    }

    default Action updatePointer(Pointer pointer) {
        return updatePointer(pointer.leftDown(), pointer.leftPressed(), pointer.x(), pointer.y());
    }

    void render(UiCanvas canvas, UiViewport viewport);

    default void layout(UiViewport viewport) {}

    Action updatePointer(boolean down, boolean pressed, double x, double y);

    Action keyPress(UiKey key);

    default boolean acceptsText() {
        return false;
    }

    default void textInput(int codePoint) {}

    default Action scroll(double amount, double x, double y) {
        return Action.NONE;
    }

    void cancelInteraction();
}
