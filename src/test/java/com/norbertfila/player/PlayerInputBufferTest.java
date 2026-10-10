package com.norbertfila.player;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlayerInputBufferTest {
    @Test
    void tapBetweenTicksSurvivesReleaseAndIsNotRepeatedDuringCatchUp() {
        PlayerInputBuffer input = new PlayerInputBuffer();
        FixedSimulation simulation = new FixedSimulation(1.0 / 60, 5);
        List<PlayerInput> ticks = new ArrayList<>();
        assertEquals(0, simulation.advance(.005, dt -> ticks.add(input.nextTick())));
        input.updateHeld(new PlayerInput(true, false, false, false, true, false, false));
        input.pressJump(.006);
        input.updateHeld(new PlayerInput(true, false, false, false, false, false, false));
        assertEquals(0, simulation.advance(.005, dt -> ticks.add(input.nextTick())));

        assertEquals(3, simulation.advance(.05, dt -> ticks.add(input.nextTick())));

        assertTrue(ticks.getFirst().jumpPressed());
        assertEquals(.006, ticks.getFirst().jumpPressedAtSeconds());
        assertTrue(ticks.stream().allMatch(PlayerInput::forward));
        assertTrue(ticks.stream().noneMatch(PlayerInput::jumpHeld));
        assertEquals(1, ticks.stream().filter(PlayerInput::jumpPressed).count());
    }

    @Test
    void multipleTapsRetainSeparateTimesAndHeldInputRemainsContinuous() {
        PlayerInputBuffer input = new PlayerInputBuffer();
        PlayerInput held = new PlayerInput(false, false, false, true, true, true, true);
        input.updateHeld(held);
        input.pressJump(.01);
        input.pressJump(.02);

        PlayerInput first = input.nextTick();
        PlayerInput second = input.nextTick();
        assertEquals(.01, first.jumpPressedAtSeconds());
        assertEquals(.02, second.jumpPressedAtSeconds());
        assertTrue(first.jumpPressed());
        assertTrue(second.jumpPressed());
        assertTrue(first.jumpHeld() && second.jumpHeld());
        assertTrue(first.sneakHeld() && second.sprintHeld());
        assertEquals(held, input.nextTick());
    }

    @Test
    void stalledSimulationKeepsOnlyTheSixteenMostRecentPresses() {
        PlayerInputBuffer input = new PlayerInputBuffer();
        for (int i = 0; i < 40; i++) input.pressJump(i);

        for (int i = 24; i < 40; i++) {
            PlayerInput tick = input.nextTick();
            assertTrue(tick.jumpPressed());
            assertEquals(i, tick.jumpPressedAtSeconds());
        }
        assertEquals(PlayerInput.NONE, input.nextTick());
        input.pressJump(41);
        input.updateHeld(new PlayerInput(true, false, false, false, true, false, false));
        input.clear();
        assertEquals(PlayerInput.NONE, input.nextTick());
    }

    @Test
    void invalidEventsCannotEnterTheBuffer() {
        PlayerInputBuffer input = new PlayerInputBuffer();
        assertThrows(IllegalArgumentException.class, () -> input.pressJump(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> input.pressJump(-1));
        assertThrows(
                IllegalArgumentException.class,
                () -> input.updateHeld(new PlayerInput(false, false, false, false, false, false, false, true, 1)));
        assertEquals(PlayerInput.NONE, input.nextTick());
    }
}
