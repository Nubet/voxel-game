package com.norbertfila.render;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.items.ItemRenderState;
import com.norbertfila.items.ItemSnapshot;
import com.norbertfila.mob.MobRenderState;
import com.norbertfila.mob.MobType;
import com.norbertfila.player.PlayerState;
import java.util.List;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class HitboxesTest {
    @Test
    void mobBoxesFollowInterpolationEyeHeightAndFacing() {
        var mob = new MobRenderState(1, MobType.ZOMBIE, 10, 64, 0, 12, 66, 0, 90, false, 0);
        var hitbox = Hitboxes.of(List.of(mob), List.of(), .5).getFirst();
        assertEquals(10.7, hitbox.box().minX(), 1e-9);
        assertEquals(65, hitbox.box().minY(), 1e-9);
        assertEquals(65 + 1.95 * .85, hitbox.eyeY(), 1e-9);
        assertEquals(1, hitbox.lookX(), 1e-9);
        assertEquals(0, hitbox.lookZ(), 1e-9);
        var forward = Hitboxes.of(
                        List.of(new MobRenderState(2, MobType.CREEPER, 0, 0, 0, 0, 0, 0, 0, false, 0)), List.of(), 0)
                .getFirst();
        assertEquals(-1, forward.lookZ(), 1e-9);
    }

    @Test
    void itemsHaveABoxWithoutALookLine() {
        var item = new ItemSnapshot(1, 1, 1, 4, 5, 6, 0, 0, 0, 0, 0, true);
        var hitbox = Hitboxes.of(List.of(), List.of(new ItemRenderState(item, 2, 5, 6, true)), .5)
                .getFirst();
        assertEquals(3 - ItemRenderState.HALF_SIZE, hitbox.box().minX(), 1e-9);
        assertFalse(hitbox.hasLook());
    }

    @Test
    void playerBoxSitsUnderTheEye() {
        var state = PlayerState.at(new PlayerState.Position(0, 0, 0));
        var hitbox = Hitboxes.player(state, new PlayerState.Position(5, 71.62, 5), new Vector3f(0, 0, -1));
        assertEquals(70, hitbox.box().minY(), 1e-9);
        assertEquals(71.62, hitbox.eyeY(), 1e-9);
        assertEquals(-1, hitbox.lookZ());
    }

    @Test
    void verticesAreCameraRelativeWithWhiteBoxRedEyeAndBlueLook() {
        var mob = new MobRenderState(1, MobType.SKELETON, 1e7, 64, 0, 1e7, 64, 0, 0, false, 0);
        var hitboxes = Hitboxes.of(List.of(mob), List.of(), 1);
        float[] vertices = HitboxRenderer.vertices(hitboxes, 1e7, 64, 0);
        assertEquals(HitboxRenderer.MAX_VERTICES_PER_HITBOX * HitboxRenderer.FLOATS_PER_VERTEX, vertices.length);
        assertEquals(-.3f, vertices[0], 1e-6);
        assertEquals(0, vertices[1], 1e-6);
        int stride = HitboxRenderer.FLOATS_PER_VERTEX;
        assertArrayEquals(new float[] {1, 1, 1}, slice(vertices, 3), 0);
        assertArrayEquals(new float[] {1, 0, 0}, slice(vertices, 24 * stride + 3), 0);
        assertArrayEquals(new float[] {0, 0, 1}, slice(vertices, 32 * stride + 3), 0);
        var item = new ItemRenderState(new ItemSnapshot(1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, true), 0, 0, 0, false);
        assertEquals(32 * stride, HitboxRenderer.vertices(Hitboxes.of(List.of(), List.of(item), 0), 0, 0, 0).length);
    }

    private static float[] slice(float[] values, int from) {
        return new float[] {values[from], values[from + 1], values[from + 2]};
    }
}
