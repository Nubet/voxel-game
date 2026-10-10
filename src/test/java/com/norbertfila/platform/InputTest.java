package com.norbertfila.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class InputTest {
    @Test
    void mouseButtonsTriggerOncePerPressAndRemainIndependent() {
        Input input = new Input(0);
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
        Input input = new Input(0);
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
}
