package com.norbertfila.world;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WaterStateTest {
    @Test
    void sourcesAndFallingWaterRestartHorizontalDistance() {
        assertEquals(0, WaterState.distance(WaterState.SOURCE));
        assertEquals(0, WaterState.distance(WaterState.FALLING));
        assertNotEquals(WaterState.SOURCE, WaterState.FALLING);
        assertEquals(WaterState.SOURCE, WaterState.defaultFor(BlockId.WATER));
        assertEquals(WaterState.EMPTY, WaterState.defaultFor(BlockId.AIR));
        assertEquals(WaterState.EMPTY, WaterState.defaultFor(BlockId.STONE));
        assertThrows(IllegalArgumentException.class, () -> WaterState.distance(WaterState.EMPTY));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7})
    void flowingWaterPreservesItsDistance(int distance) {
        byte state = WaterState.flowing(distance);
        assertEquals(distance, WaterState.distance(state));
        assertNotEquals(WaterState.SOURCE, state);
        assertNotEquals(WaterState.FALLING, state);
        assertDoesNotThrow(() -> WaterState.validate(BlockId.WATER, state));
    }

    @ParameterizedTest
    @ValueSource(ints = {Integer.MIN_VALUE, -1, 0, 8, 256, Integer.MAX_VALUE})
    void rejectsDistancesBeforeNarrowingToByte(int distance) {
        assertThrows(IllegalArgumentException.class, () -> WaterState.flowing(distance));
    }

    @Test
    void onlyCanonicalWaterAndEmptyStatesAreAccepted() {
        for (int value = Byte.MIN_VALUE; value <= Byte.MAX_VALUE; value++) {
            byte state = (byte) value;
            if (value >= 0 && value <= 8) assertDoesNotThrow(() -> WaterState.validate(BlockId.WATER, state));
            else assertThrows(IllegalArgumentException.class, () -> WaterState.validate(BlockId.WATER, state));
            if (state == WaterState.EMPTY) assertDoesNotThrow(() -> WaterState.validate(BlockId.STONE, state));
            else assertThrows(IllegalArgumentException.class, () -> WaterState.validate(BlockId.STONE, state));
        }
        assertThrows(IllegalArgumentException.class, () -> WaterState.validate((byte) 255, WaterState.EMPTY));
    }
}
