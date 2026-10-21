package com.norbertfila.render;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.mob.MobRenderState;
import com.norbertfila.mob.MobType;
import java.util.List;
import org.junit.jupiter.api.Test;

class EntityFireRendererTest {
    @Test
    void onlyBurningMobsGetStackedFlamesFacingTheCamera() {
        var calm = new MobRenderState(1, MobType.ZOMBIE, 0, 0, 0, 0, 0, 0, 0, false, 0);
        assertEquals(0, EntityFireRenderer.vertices(List.of(calm), 1, 0, 0, 5).length);
        var burning = new MobRenderState(2, MobType.ZOMBIE, 0, 64, 0, 0, 64, 0, 0, true, 0);
        float[] vertices = EntityFireRenderer.vertices(List.of(burning), 1, 0, 64, 5);
        int perVertex = EntityFireRenderer.FLOATS_PER_VERTEX;
        assertEquals(3 * 6 * perVertex, vertices.length);
        for (int i = 0; i < vertices.length; i += perVertex) {
            assertTrue(vertices[i + 2] > -5 && vertices[i + 2] < -4.4, "flames sit in front of the body");
            assertTrue(Math.abs(vertices[i]) <= .43);
        }
        assertEquals(0, vertices[1], 1e-6);
        assertEquals(1.95, vertices[vertices.length - perVertex * 2 + 1], .6);
    }
}
