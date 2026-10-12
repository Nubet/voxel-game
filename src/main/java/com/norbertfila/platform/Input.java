package com.norbertfila.platform;

import static org.lwjgl.glfw.GLFW.*;

import com.norbertfila.player.PlayerInput;
import com.norbertfila.player.PlayerInputBuffer;
import org.lwjgl.glfw.GLFWKeyCallback;
import org.lwjgl.glfw.GLFWScrollCallback;

public final class Input implements AutoCloseable {
    private final long window;
    private final PlayerInputBuffer playerInput = new PlayerInputBuffer();
    private GLFWKeyCallback keyCallback;
    private GLFWScrollCallback scrollCallback;
    private boolean focused;
    private final double[] cursorX = new double[1];
    private final double[] cursorY = new double[1];
    private boolean mouseInitialized;
    private double lastCursorX;
    private double lastCursorY;
    private float mouseDeltaX;
    private float mouseDeltaY;
    private boolean toggleMode;
    private boolean settings;
    private boolean leftMouse;
    private boolean previousLeftMouse;
    private boolean rightMouse;
    private boolean previousRightMouse;
    private int hotbarKeys;
    private int previousHotbarKeys;
    private double scrollDeltaY;
    private boolean previousToggleMode;
    private boolean previousSettings;

    public Input(long window) {
        if (window == 0) throw new IllegalArgumentException("Input requires a window");
        this.window = window;
        keyCallback =
                GLFWKeyCallback.create((handle, key, scanCode, action, mods) -> keyEvent(key, action, glfwGetTime()));
        glfwSetKeyCallback(window, keyCallback);
        scrollCallback = GLFWScrollCallback.create((handle, xOffset, yOffset) -> scrollDeltaY += yOffset);
        glfwSetScrollCallback(window, scrollCallback);
    }

    Input() {
        window = 0;
    }

    void keyEvent(int key, int action, double timeSeconds) {
        if (key == GLFW_KEY_SPACE && action == GLFW_PRESS) playerInput.pressJump(timeSeconds);
    }

    void scrollEvent(double yOffset) {
        scrollDeltaY += yOffset;
    }

    public PlayerInput nextPlayerTick() {
        return playerInput.nextTick();
    }

    public PlayerInput peekPlayerTick() {
        return playerInput.peekTick();
    }

    public void clearPlayerInput() {
        playerInput.clear();
    }

    public boolean focused() {
        return focused;
    }

    public void update() {
        previousToggleMode = toggleMode;
        previousSettings = settings;
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

        playerInput.updateHeld(new PlayerInput(
                isPressed(GLFW_KEY_W),
                isPressed(GLFW_KEY_S),
                isPressed(GLFW_KEY_A),
                isPressed(GLFW_KEY_D),
                isPressed(GLFW_KEY_SPACE),
                isPressed(GLFW_KEY_LEFT_SHIFT),
                isPressed(GLFW_KEY_LEFT_CONTROL)));
        toggleMode = isPressed(GLFW_KEY_F4);
        settings = isPressed(GLFW_KEY_F10);
        int slots = 0;
        boolean extendedHotbar = isPressed(GLFW_KEY_RIGHT_SHIFT);
        for (int key = GLFW_KEY_1; key <= GLFW_KEY_9; key++) {
            if (!isPressed(key)) continue;
            int slot = hotbarSlot(key, extendedHotbar);
            if (slot >= 0) slots |= 1 << slot;
        }
        updateButtons(
                glfwGetMouseButton(window, GLFW_MOUSE_BUTTON_LEFT) == GLFW_PRESS,
                glfwGetMouseButton(window, GLFW_MOUSE_BUTTON_RIGHT) == GLFW_PRESS,
                slots);
        focused = glfwGetWindowAttrib(window, GLFW_FOCUSED) == GLFW_TRUE;
        if (!focused) {
            clearPlayerInput();
            resetMouseTracking();
        }
    }

    void updateButtons(boolean left, boolean right, int slots) {
        previousLeftMouse = leftMouse;
        previousRightMouse = rightMouse;
        previousHotbarKeys = hotbarKeys;
        leftMouse = left;
        rightMouse = right;
        hotbarKeys = slots;
    }

    public boolean rightMousePressed() {
        return rightMouse && !previousRightMouse;
    }

    public int hotbarSlotPressed() {
        int pressed = hotbarKeys & ~previousHotbarKeys;
        return pressed == 0 ? -1 : Integer.numberOfTrailingZeros(pressed);
    }

    public int hotbarScrollSteps() {
        int steps = (int) scrollDeltaY;
        scrollDeltaY -= steps;
        return steps;
    }

    static int hotbarSlot(int key, boolean extended) {
        if (key < GLFW_KEY_1 || key > GLFW_KEY_9) return -1;
        int slot = key - GLFW_KEY_1;
        if (!extended) return slot;
        return slot < 7 ? slot + 9 : -1;
    }

    public float mouseDeltaX() {
        return mouseDeltaX;
    }

    public float mouseDeltaY() {
        return mouseDeltaY;
    }

    public boolean toggleModePressed() {
        return toggleMode && !previousToggleMode;
    }

    public boolean settingsPressed() {
        return settings && !previousSettings;
    }

    public boolean leftMouseDown() {
        return leftMouse;
    }

    public boolean leftMousePressed() {
        return leftMouse && !previousLeftMouse;
    }

    public double cursorX() {
        return cursorX[0];
    }

    public double cursorY() {
        return cursorY[0];
    }

    public void resetMouseTracking() {
        mouseInitialized = false;
        mouseDeltaX = 0;
        mouseDeltaY = 0;
    }

    private boolean isPressed(int key) {
        return glfwGetKey(window, key) == GLFW_PRESS;
    }

    @Override
    public void close() {
        if (keyCallback != null) {
            glfwSetKeyCallback(window, null);
            keyCallback.free();
            keyCallback = null;
        }
        if (scrollCallback != null) {
            glfwSetScrollCallback(window, null);
            scrollCallback.free();
            scrollCallback = null;
        }
    }
}
