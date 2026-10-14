package com.norbertfila.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.glfw.GLFW.*;

import com.norbertfila.ui.UiEvent;
import com.norbertfila.ui.UiKey;
import org.junit.jupiter.api.Test;

class InputTest {
    @Test
    void characterCallbackAndEditingKeysShareOrderWithoutKeyboardLayoutGuessing() {
        var input = new Input();
        input.keyEvent(GLFW_KEY_A, GLFW_PRESS, 0, 0);
        input.characterEvent('ą');
        input.characterEvent(0x1F332);
        input.keyEvent(GLFW_KEY_BACKSPACE, GLFW_REPEAT, 0, 0);
        input.keyEvent(GLFW_KEY_UP, GLFW_PRESS, 0, 0);
        input.keyEvent(GLFW_KEY_HOME, GLFW_PRESS, 0, 0);
        input.characterEvent(0xD800);
        input.characterEvent(-1);
        assertEquals(new UiEvent.Character('ą'), input.pollUiEvent());
        assertEquals(new UiEvent.Character(0x1F332), input.pollUiEvent());
        assertEquals(new UiEvent.Key(UiKey.BACKSPACE), input.pollUiEvent());
        assertEquals(new UiEvent.Key(UiKey.UP), input.pollUiEvent());
        assertEquals(new UiEvent.Key(UiKey.HOME), input.pollUiEvent());
        assertEquals(null, input.pollUiEvent());
        input.characterEvent('ę');
        input.scrollEvent(.25);
        input.discardTransientInput();
        assertEquals(null, input.pollUiEvent());
        assertEquals(0, input.consumeScroll());
        input.scrollEvent(.25);
        assertEquals(.25, input.consumeScroll());
        assertEquals(0, input.consumeScroll());
    }

    @Test
    void sessionTransitionDiscardsEdgesButRequiresARealNewPressForHeldButtons() {
        var input = new Input();
        input.keyEvent(GLFW_KEY_ENTER, GLFW_PRESS, 0, 1);
        input.keyEvent(GLFW_KEY_SPACE, GLFW_PRESS, 0, 1);
        input.keyEvent(GLFW_KEY_E, GLFW_PRESS, 0, 1);
        input.scrollEvent(3);
        input.updateButtons(true, true, 1);
        input.discardTransientInput();
        assertEquals(null, input.pollUiEvent());
        assertFalse(input.inventoryPressed());
        assertFalse(input.peekPlayerTick().jumpPressed());
        assertEquals(0, input.hotbarScrollSteps());
        assertEquals(-1, input.hotbarSlotPressed());
        assertFalse(input.leftMousePressed());
        assertFalse(input.rightMousePressed());
        assertTrue(input.rightMouseDown());
        input.updateButtons(false, false, 0);
        input.updateButtons(true, true, 1);
        assertTrue(input.rightMousePressed());
        assertTrue(input.leftMousePressed());
    }

    @Test
    void inventoryTapIsRetainedAndRepeatDoesNotToggleAgain() {
        try (Input input = new Input()) {
            input.keyEvent(GLFW_KEY_E, GLFW_PRESS, 0, 0);
            input.keyEvent(GLFW_KEY_E, GLFW_RELEASE, 0, 0);
            assertTrue(input.inventoryPressed());
            assertFalse(input.inventoryPressed());
            input.keyEvent(GLFW_KEY_E, GLFW_REPEAT, 0, 0);
            assertFalse(input.inventoryPressed());
        }
    }

    @Test
    void uiKeysRetainOrderAndTapsButDoNotRepeatButtonActivation() {
        Input input = new Input();
        input.keyEvent(GLFW_KEY_TAB, GLFW_PRESS, 0, 1);
        input.keyEvent(GLFW_KEY_TAB, GLFW_RELEASE, 0, 2);
        input.keyEvent(GLFW_KEY_ENTER, GLFW_PRESS, 0, 3);
        input.keyEvent(GLFW_KEY_ENTER, GLFW_REPEAT, 0, 4);
        input.keyEvent(GLFW_KEY_SPACE, GLFW_PRESS, 0, 5);
        input.keyEvent(GLFW_KEY_SPACE, GLFW_REPEAT, 0, 6);
        input.keyEvent(GLFW_KEY_TAB, GLFW_PRESS, GLFW_MOD_SHIFT, 7);
        input.keyEvent(GLFW_KEY_LEFT, GLFW_PRESS, 0, 8);
        input.keyEvent(GLFW_KEY_LEFT, GLFW_REPEAT, 0, 9);
        assertEquals(new UiEvent.Key(UiKey.NEXT), input.pollUiEvent());
        assertEquals(new UiEvent.Key(UiKey.ACTIVATE), input.pollUiEvent());
        assertEquals(new UiEvent.Key(UiKey.SPACE), input.pollUiEvent());
        assertEquals(new UiEvent.Key(UiKey.PREVIOUS), input.pollUiEvent());
        assertEquals(new UiEvent.Key(UiKey.DECREASE), input.pollUiEvent());
        assertEquals(new UiEvent.Key(UiKey.DECREASE), input.pollUiEvent());
        assertEquals(null, input.pollUiEvent());
        assertTrue(input.nextPlayerTick().jumpPressed());
    }

    @Test
    void uiKeyBacklogIsBounded() {
        Input input = new Input();
        for (int i = 0; i < 1000; i++) input.keyEvent(GLFW_KEY_RIGHT, GLFW_REPEAT, 0, i);
        int count = 0;
        while (input.pollUiEvent() != null) count++;
        assertEquals(64, count);
    }

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
        assertTrue(input.rightMouseDown());
        input.updateButtons(false, false, 0);
        assertFalse(input.rightMouseDown());
        input.updateButtons(true, true, 0);
        assertTrue(input.leftMousePressed());
        assertTrue(input.rightMousePressed());
    }

    @Test
    void zoomIsAHeldStateRatherThanAOneShotEvent() {
        Input input = new Input();

        input.updateButtons(false, false, 0, true);
        assertTrue(input.zoomHeld());
        input.updateButtons(false, false, 0, true);
        assertTrue(input.zoomHeld());
        input.updateButtons(false, false, 0, false);
        assertFalse(input.zoomHeld());
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
    void numberKeysSelectOnlyTheNineHotbarSlots() {
        for (int slot = 0; slot < 9; slot++) assertEquals(slot, Input.hotbarSlot(GLFW_KEY_1 + slot));
        assertEquals(-1, Input.hotbarSlot(GLFW_KEY_0));
        assertEquals(-1, Input.hotbarSlot(GLFW_KEY_RIGHT_SHIFT));
    }

    @Test
    void escapeTapIsRetainedWithoutRepeating() {
        Input input = new Input();
        input.keyEvent(GLFW_KEY_ESCAPE, GLFW_PRESS, 0, 1);
        input.keyEvent(GLFW_KEY_ESCAPE, GLFW_REPEAT, 0, 2);
        input.keyEvent(GLFW_KEY_ESCAPE, GLFW_RELEASE, 0, 3);
        assertEquals(new UiEvent.Key(UiKey.BACK), input.pollUiEvent());
        assertEquals(null, input.pollUiEvent());
    }

    @Test
    void accumulatesWholeScrollStepsAndRetainsFractionalEvents() {
        Input input = new Input();
        input.scrollEvent(.5);
        assertEquals(0, input.hotbarScrollSteps());
        input.scrollEvent(.75);
        assertEquals(1, input.hotbarScrollSteps());
        input.scrollEvent(-2.5);
        assertEquals(-2, input.hotbarScrollSteps());
        assertEquals(0, input.hotbarScrollSteps());
    }

    @Test
    void callbackRetainsTapReleasedBeforePollingAndIgnoresKeyRepeat() {
        try (Input input = new Input()) {
            input.keyEvent(GLFW_KEY_SPACE, GLFW_PRESS, 0, .001);
            input.keyEvent(GLFW_KEY_SPACE, GLFW_REPEAT, 0, .002);
            input.keyEvent(GLFW_KEY_SPACE, GLFW_RELEASE, 0, .003);
            input.keyEvent(GLFW_KEY_W, GLFW_PRESS, 0, .004);

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
        input.keyEvent(GLFW_KEY_SPACE, GLFW_PRESS, 0, 1);

        input.clearPlayerInput();

        assertFalse(input.nextPlayerTick().jumpPressed());
        input.close();
        input.close();
    }
}
