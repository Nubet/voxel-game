package com.norbertfila.ui;

public interface Screen {
    sealed interface Action {
        Action NONE = Simple.NONE, CLOSE = Simple.CLOSE;

        enum Simple implements Action {
            NONE,
            CLOSE
        }

        record SlotClick(int slot, boolean right, boolean quickMove) implements Action {}

        record CreativePick(byte block, int slot) implements Action {}
    }

    record Pointer(
            boolean leftDown,
            boolean leftPressed,
            boolean rightDown,
            boolean rightPressed,
            boolean shift,
            double x,
            double y) {}

    default Action updatePointer(Pointer pointer) {
        return updatePointer(pointer.leftDown(), pointer.leftPressed(), pointer.x(), pointer.y());
    }

    void render(UiCanvas canvas, UiViewport viewport);

    Action updatePointer(boolean down, boolean pressed, double x, double y);

    Action keyPress(UiKey key);

    void cancelInteraction();
}
