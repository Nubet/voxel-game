package com.norbertfila.player.visual.model;

import static com.norbertfila.player.visual.model.PlayerModel.Part.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.EnumMap;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class PlayerModelTest {
    @Test
    void classicDimensionsAndNeutralBounds() {
        assertEquals(6, PlayerModel.PARTS.size());
        var min = new Vector3f(Float.POSITIVE_INFINITY);
        var max = new Vector3f(Float.NEGATIVE_INFINITY);
        for (var part : PlayerModel.PARTS) {
            var box = part.cuboid();
            assertEquals(part.part() == HEAD || part.part() == BODY ? 8 : 4, box.width());
            assertEquals(part.part() == HEAD ? 8 : 12, box.height());
            assertEquals(part.part() == HEAD ? 8 : 4, box.depth());
            for (int corner = 0; corner < 8; corner++) {
                var point = new Vector3f(
                        box.x() + ((corner & 1) == 0 ? 0 : box.width()),
                        box.y() + ((corner & 2) == 0 ? 0 : box.height()),
                        box.z() + ((corner & 4) == 0 ? 0 : box.depth()));
                matrix(part.part(), PlayerPose.NEUTRAL).transformPosition(point);
                min.min(point);
                max.max(point);
            }
        }
        vector(new Vector3f(-.5f, 0, -.25f), min);
        vector(new Vector3f(.5f, 2, .25f), max);
        vector(
                new Vector3f(5f / 16, 22f / 16, 0),
                matrix(RIGHT_ARM, PlayerPose.NEUTRAL).getTranslation(new Vector3f()));
        vector(new Vector3f(0, 1.5f, 0), matrix(HEAD, PlayerPose.NEUTRAL).getTranslation(new Vector3f()));
    }

    @Test
    void eachPartRotatesAtItsOwnPivotAndLeavesSiblingsAlone() {
        for (var part : PlayerModel.Part.values()) {
            var pose = PlayerPose.NEUTRAL.with(part, new PartTransform(0, 0, 0, (float) Math.PI / 2, 0, 0));
            var neutral = matrix(part, PlayerPose.NEUTRAL);
            var rotated = matrix(part, pose);
            vector(neutral.getTranslation(new Vector3f()), rotated.getTranslation(new Vector3f()));
            var tip = rotated.transformPosition(new Vector3f(0, -12, 0));
            vector(new Vector3f(0, 0, -.75f).add(neutral.getTranslation(new Vector3f())), tip);
            for (var other : PlayerModel.Part.values()) {
                if (other != part
                        && !(part == BODY && PlayerModel.definition(other).parent() == BODY))
                    assertEquals(matrix(other, PlayerPose.NEUTRAL), matrix(other, pose));
            }
        }
    }

    @Test
    void torsoCarriesHeadAndShouldersButNotHips() {
        var pose = PlayerPose.NEUTRAL.with(BODY, new PartTransform(0, 0, 0, 0, 0, (float) Math.PI / 2));
        vector(new Vector3f(-.75f, .75f, 0), matrix(HEAD, pose).getTranslation(new Vector3f()));
        vector(new Vector3f(-10f / 16, 17f / 16, 0), matrix(RIGHT_ARM, pose).getTranslation(new Vector3f()));
        assertEquals(matrix(RIGHT_LEG, PlayerPose.NEUTRAL), matrix(RIGHT_LEG, pose));
        var headTurn = PlayerPose.NEUTRAL.with(HEAD, new PartTransform(0, 0, 0, 0, (float) Math.PI / 2, 0));
        vector(new Vector3f(-.25f, 1.5f, 0), matrix(HEAD, headTurn).transformPosition(new Vector3f(0, 0, -4)));
    }

    @Test
    void posesAreImmutableAndRejectInvalidInput() {
        var source = new EnumMap<PlayerModel.Part, PartTransform>(PlayerModel.Part.class);
        source.put(HEAD, PartTransform.IDENTITY);
        var pose = new PlayerPose(source);
        source.clear();
        assertEquals(1, pose.parts().size());
        assertThrows(UnsupportedOperationException.class, () -> pose.parts().clear());
        var changed = pose.with(HEAD, new PartTransform(0, 1, 0, 0, 0, 0));
        assertEquals(0, pose.transform(HEAD).y());
        assertEquals(1, changed.transform(HEAD).y());
        assertThrows(IllegalArgumentException.class, () -> new PartTransform(0, 0, 0, Float.NaN, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new ModelCuboid(0, 0, 0, 0, 12, 4));
        assertThrows(IllegalArgumentException.class, () -> new UvOrigin(-1, 0));
    }

    private static Matrix4f matrix(PlayerModel.Part part, PlayerPose pose) {
        return PlayerModel.partMatrix(part, pose, new Matrix4f());
    }

    private static void vector(Vector3f expected, Vector3f actual) {
        assertTrue(expected.equals(actual, .00001f), () -> "Expected " + expected + " but got " + actual);
    }
}
