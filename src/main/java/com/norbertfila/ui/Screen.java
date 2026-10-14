package com.norbertfila.ui;

public interface Screen {
    enum Action {
        NONE,
        CLOSE
    }

    void render(UiCanvas canvas, UiViewport viewport);

    Action updatePointer(boolean down, boolean pressed, double x, double y);

    Action keyPress(UiKey key);

    void cancelInteraction();
}
