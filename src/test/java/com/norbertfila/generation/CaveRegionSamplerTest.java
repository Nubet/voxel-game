package com.norbertfila.generation;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class CaveRegionSamplerTest {
    @Test
    void parametersVarySmoothlyAcrossPlanningBoundariesAndAcrossTheWorld() {
        var sampler = new CaveRegionSampler(42, CaveConfig.defaults());
        double low = 1, high = 0;
        for (int x = -4096; x <= 4096; x += 128) {
            var a = sampler.sample(x - .01, -128);
            var b = sampler.sample(x + .01, -128);
            assertEquals(a.density(), b.density(), .001);
            assertEquals(a.winding(), b.winding(), .001);
            assertTrue(a.density() >= 0 && a.density() <= 1);
            assertTrue(a.width() > 0 && a.width() < 1);
            low = Math.min(low, a.density());
            high = Math.max(high, a.density());
        }
        assertTrue(high - low > .2);
    }

    @Test
    void configurationRejectsUnboundedGeometry() {
        assertThrows(IllegalArgumentException.class, () -> new CaveConfig(20, 150, 1.3, 2.5, 768));
        assertThrows(IllegalArgumentException.class, () -> new CaveConfig(20, 90, 1.3, Double.NaN, 768));
    }
}
