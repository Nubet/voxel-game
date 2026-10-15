package com.norbertfila.platform;

import static org.lwjgl.glfw.GLFW.*;

import com.norbertfila.player.PlayerInput;
import com.norbertfila.player.PlayerInputBuffer;
import com.norbertfila.ui.UiEvent;
import com.norbertfila.ui.UiKey;
import java.util.ArrayDeque;
import org.lwjgl.glfw.GLFWCharCallback;
import org.lwjgl.glfw.GLFWKeyCallback;
import org.lwjgl.glfw.GLFWScrollCallback;

public final class Input implements AutoCloseable {
    private static final int MAX_UI_KEYS = 64;
    private final long window;
    private final PlayerInputBuffer playerInput = new PlayerInputBuffer();
    private final ArrayDeque<UiEvent> uiEvents = new ArrayDeque<>();
    private GLFWKeyCallback keyCallback;
    private GLFWCharCallback charCallback;
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
    private boolean zoom;
    private int hotbarKeys;
    private int previousHotbarKeys;
    private double scrollDeltaY;
    private boolean previousToggleMode;
    private boolean previousSettings;
    private boolean inventoryPressed;
    private boolean shift;

    public Input(long window) {
        if (window == 0) throw new IllegalArgumentException("Input requires a window");
        this.window = window;
        keyCallback = GLFWKeyCallback.create(
                (handle, key, scanCode, action, mods) -> keyEvent(key, action, mods, glfwGetTime()));
        glfwSetKeyCallback(window, keyCallback);
        charCallback = GLFWCharCallback.create((handle, codePoint) -> characterEvent(codePoint));
        glfwSetCharCallback(window, charCallback);
        scrollCallback = GLFWScrollCallback.create((handle, xOffset, yOffset) -> scrollDeltaY += yOffset);
        glfwSetScrollCallback(window, scrollCallback);
    }

    Input() {
        window = 0;
    }

    void keyEvent(int key, int action, int mods, double timeSeconds) {
        if (key == GLFW_KEY_E && action == GLFW_PRESS) inventoryPressed = true;
        if (key == GLFW_KEY_SPACE && action == GLFW_PRESS) playerInput.pressJump(timeSeconds);
        if (action != GLFW_PRESS && action != GLFW_REPEAT) return;
        UiKey uiKey = switch (key) {
            case GLFW_KEY_Q ->
                action == GLFW_PRESS ? ((mods & GLFW_MOD_CONTROL) != 0 ? UiKey.DROP_STACK : UiKey.DROP_ONE) : null;
            case GLFW_KEY_TAB ->
                action == GLFW_PRESS ? ((mods & GLFW_MOD_SHIFT) != 0 ? UiKey.PREVIOUS : UiKey.NEXT) : null;
            case GLFW_KEY_ENTER, GLFW_KEY_KP_ENTER -> action == GLFW_PRESS ? UiKey.ACTIVATE : null;
            case GLFW_KEY_SPACE -> action == GLFW_PRESS ? UiKey.SPACE : null;
            case GLFW_KEY_LEFT -> UiKey.DECREASE;
            case GLFW_KEY_RIGHT -> UiKey.INCREASE;
            case GLFW_KEY_UP -> UiKey.UP;
            case GLFW_KEY_DOWN -> UiKey.DOWN;
            case GLFW_KEY_HOME -> UiKey.HOME;
            case GLFW_KEY_END -> UiKey.END;
            case GLFW_KEY_PAGE_UP -> UiKey.PAGE_UP;
            case GLFW_KEY_PAGE_DOWN -> UiKey.PAGE_DOWN;
            case GLFW_KEY_BACKSPACE -> UiKey.BACKSPACE;
            case GLFW_KEY_DELETE -> UiKey.DELETE;
            case GLFW_KEY_ESCAPE -> action == GLFW_PRESS ? UiKey.BACK : null;
            default -> null;
        };
        if (uiKey != null && uiEvents.size() < MAX_UI_KEYS) uiEvents.addLast(new UiEvent.Key(uiKey));
    }

    void characterEvent(int codePoint) {
        if (Character.isValidCodePoint(codePoint)
                && !(codePoint >= 0xD800 && codePoint <= 0xDFFF)
                && uiEvents.size() < MAX_UI_KEYS) uiEvents.addLast(new UiEvent.Character(codePoint));
    }

    public UiEvent pollUiEvent() {
        return uiEvents.pollFirst();
    }

    public boolean inventoryPressed() {
        boolean pressed = inventoryPressed;
        inventoryPressed = false;
        return pressed;
    }

    public boolean shiftHeld() {
        return shift;
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
        focused = glfwGetWindowAttrib(window, GLFW_FOCUSED) == GLFW_TRUE;
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
        shift = isPressed(GLFW_KEY_LEFT_SHIFT) || isPressed(GLFW_KEY_RIGHT_SHIFT);
        int slots = 0;
        for (int key = GLFW_KEY_1; key <= GLFW_KEY_9; key++) {
            if (!isPressed(key)) continue;
            int slot = hotbarSlot(key);
            if (slot >= 0) slots |= 1 << slot;
        }
        updateButtons(
                glfwGetMouseButton(window, GLFW_MOUSE_BUTTON_LEFT) == GLFW_PRESS,
                glfwGetMouseButton(window, GLFW_MOUSE_BUTTON_RIGHT) == GLFW_PRESS,
                slots,
                focused && isPressed(GLFW_KEY_X));
        if (!focused) {
            inventoryPressed = false;
            uiEvents.clear();
            scrollDeltaY = 0;
            clearPlayerInput();
            resetMouseTracking();
        }
    }

    void updateButtons(boolean left, boolean right, int slots) {
        updateButtons(left, right, slots, false);
    }

    void updateButtons(boolean left, boolean right, int slots, boolean zoom) {
        previousLeftMouse = leftMouse;
        previousRightMouse = rightMouse;
        previousHotbarKeys = hotbarKeys;
        leftMouse = left;
        rightMouse = right;
        hotbarKeys = slots;
        this.zoom = zoom;
    }

    public boolean rightMousePressed() {
        return rightMouse && !previousRightMouse;
    }

    public boolean rightMouseDown() {
        return rightMouse;
    }

    public boolean zoomHeld() {
        return zoom;
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

    public double consumeScroll() {
        double amount = scrollDeltaY;
        scrollDeltaY = 0;
        return amount;
    }

    static int hotbarSlot(int key) {
        if (key < GLFW_KEY_1 || key > GLFW_KEY_9) return -1;
        return key - GLFW_KEY_1;
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

    public void discardTransientInput() {
        uiEvents.clear();
        clearPlayerInput();
        inventoryPressed = false;
        scrollDeltaY = 0;
        previousLeftMouse = leftMouse;
        previousRightMouse = rightMouse;
        previousHotbarKeys = hotbarKeys;
        previousToggleMode = toggleMode;
        previousSettings = settings;
        resetMouseTracking();
    }

    private boolean isPressed(int key) {
        return glfwGetKey(window, key) == GLFW_PRESS;
    }

    @Override
    public void close() {
        if (charCallback != null) {
            glfwSetCharCallback(window, null);
            charCallback.free();
            charCallback = null;
        }
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
