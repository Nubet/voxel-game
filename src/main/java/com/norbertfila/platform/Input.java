package com.norbertfila.platform;

import static org.lwjgl.glfw.GLFW.*;

public final class Input {
    private final long window;
    private final double[] cursorX = new double[1];
    private final double[] cursorY = new double[1];
    private boolean mouseInitialized;
    private double lastCursorX;
    private double lastCursorY;
    private float mouseDeltaX;
    private float mouseDeltaY;
    private boolean moveForward;
    private boolean moveBackward;
    private boolean moveLeft;
    private boolean moveRight;
    private boolean moveUp;
    private boolean moveDown;
    private boolean sprint;
    private boolean previousToggleMode;
    private boolean previousJump;

    public Input(long window) {
        this.window = window;
    }

    public void update() {
        previousToggleMode = toggleMode();
        previousJump = jumpHeld();
        glfwGetCursorPos(window, cursorX, cursorY);
        if (!mouseInitialized) {
            lastCursorX = cursorX[0];
            lastCursorY = cursorY[0];
            mouseInitialized = true;
        }
        mouseDeltaX = (float) (cursorX[0] - lastCursorX);
        mouseDeltaY = (float) (cursorY[0] - lastCursorY);
        lastCursorX = cursorX[0];
        lastCursorY = cursorY[0];

        moveForward = isPressed(GLFW_KEY_W);
        moveBackward = isPressed(GLFW_KEY_S);
        moveLeft = isPressed(GLFW_KEY_A);
        moveRight = isPressed(GLFW_KEY_D);
        moveUp = isPressed(GLFW_KEY_SPACE);
        moveDown = isPressed(GLFW_KEY_LEFT_SHIFT);
        sprint = isPressed(GLFW_KEY_LEFT_CONTROL);
    }

    public float mouseDeltaX() {
        return mouseDeltaX;
    }

    public float mouseDeltaY() {
        return mouseDeltaY;
    }

    public boolean moveForward() {
        return moveForward;
    }

    public boolean moveBackward() {
        return moveBackward;
    }

    public boolean moveLeft() {
        return moveLeft;
    }

    public boolean moveRight() {
        return moveRight;
    }

    public boolean moveUp() {
        return moveUp;
    }

    public boolean moveDown() {
        return moveDown;
    }

    public boolean sprint() {
        return sprint;
    }

    public boolean toggleModePressed() {
        return toggleMode() && !previousToggleMode;
    }

    public boolean jumpPressed() {
        return jumpHeld() && !previousJump;
    }

    private boolean toggleMode() {
        return isPressed(GLFW_KEY_F4);
    }

    private boolean jumpHeld() {
        return isPressed(GLFW_KEY_SPACE);
    }

    private boolean isPressed(int key) {
        return glfwGetKey(window, key) == GLFW_PRESS;
    }
}
