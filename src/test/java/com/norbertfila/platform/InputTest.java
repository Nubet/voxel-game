package com.norbertfila.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.glfw.GLFW.*;

import org.junit.jupiter.api.Test;

class InputTest {
    @Test
    void mouseButtonsTriggerOncePerPressAndRemainIndependent() {
        Input input = new Input();
        input.updateButtons(true, false, 0);
        assertTrue(input.leftMousePressed());
        assertTrue(input.leftMouseDown());
        assertFalse(input.rightMousePressed());
        input.updateButtons(true, true, 0);
        assertFalse(input.leftMousePressed());
        assertTrue(input.rightMousePressed());
        input.updateButtons(true, true, 0);
        assertFalse(input.leftMousePressed());
        assertFalse(input.rightMousePressed());
        input.updateButtons(false, false, 0);
        input.updateButtons(true, true, 0);
        assertTrue(input.leftMousePressed());
        assertTrue(input.rightMousePressed());
    }

    @Test
    void slotKeysTriggerOnNewPressIncludingWhileAnotherKeyIsHeld() {
        Input input = new Input();
        for (int slot = 0; slot < 9; slot++) {
            input.updateButtons(false, false, 1 << slot);
            assertEquals(slot, input.hotbarSlotPressed());
            input.updateButtons(false, false, 1 << slot);
            assertEquals(-1, input.hotbarSlotPressed());
        }
        input.updateButtons(false, false, (1 << 8) | (1 << 2));
        assertEquals(2, input.hotbarSlotPressed());
        input.updateButtons(false, false, 0);
        assertEquals(-1, input.hotbarSlotPressed());
        input.updateButtons(false, false, (1 << 5) | 1);
        assertEquals(0, input.hotbarSlotPressed());
    }

    @Test
    void callbackRetainsTapReleasedBeforePollingAndIgnoresKeyRepeat() {
        try (Input input = new Input()) {
            input.keyEvent(GLFW_KEY_SPACE, GLFW_PRESS, .001);
            input.keyEvent(GLFW_KEY_SPACE, GLFW_REPEAT, .002);
            input.keyEvent(GLFW_KEY_SPACE, GLFW_RELEASE, .003);
            input.keyEvent(GLFW_KEY_W, GLFW_PRESS, .004);

            var tick = input.nextPlayerTick();
            assertTrue(tick.jumpPressed());
            assertEquals(.001, tick.jumpPressedAtSeconds());
            assertFalse(tick.jumpHeld());
            assertFalse(input.nextPlayerTick().jumpPressed());
        }
    }

    @Test
    void pauseOrModeChangeDiscardsPendingPlayerEvents() {
        Input input = new Input();
        input.keyEvent(GLFW_KEY_SPACE, GLFW_PRESS, 1);

        input.clearPlayerInput();

        assertFalse(input.nextPlayerTick().jumpPressed());
        input.close();
        input.close();
    }
}
