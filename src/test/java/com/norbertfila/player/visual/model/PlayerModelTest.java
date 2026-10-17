package com.norbertfila.player.visual.model;

import static com.norbertfila.player.visual.model.PlayerModel.Part.*;
import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.player.PlayerController;
import com.norbertfila.player.WaterContact;
import com.norbertfila.player.visual.PlayerAppearance;
import com.norbertfila.player.visual.PlayerRenderStateExtractor;
import java.util.EnumMap;
import java.util.EnumSet;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class PlayerModelTest {
    @Test
    void outerCuboidsInflateGeometryWithoutStretchingTheUvNetAndInheritThePartPose() {
        int[][] origins = {{16, 32}, {32, 0}, {40, 32}, {48, 48}, {0, 32}, {0, 48}};
        var pose = PlayerPose.NEUTRAL.with(BODY, new PartTransform(1, 2, 3, .3f, .4f, .5f));
        for (var variant : ModelVariant.values()) {
            for (var layer : PlayerModel.OUTER_LAYERS) {
                var part = PlayerModel.definition(variant, layer.part());
                var box = part.cuboid();
                assertEquals(
                        new UvOrigin(
                                origins[layer.part().ordinal()][0],
                                origins[layer.part().ordinal()][1]),
                        layer.uvOrigin());
                float inflation = layer.part() == HEAD ? .5f : .25f;
                assertEquals(inflation, layer.inflation());
                var base = CuboidMesh.vertices(box, layer.uvOrigin(), 64, 64);
                var outer = CuboidMesh.vertices(box, layer.uvOrigin(), 64, 64, layer.inflation());
                var transform = PlayerModel.partMatrix(
                        variant,
                        layer.part(),
                        pose.with(layer.part(), new PartTransform(0, 0, 0, .6f, -.2f, .1f)),
                        new Matrix4f());
                var inherited = new Matrix4f().scaling(PlayerModel.PIXEL_SCALE);
                if (part.parent() != null) {
                    var parent = PlayerModel.definition(variant, part.parent());
                    parent.pivot().apply(inherited);
                    pose.transform(part.parent()).apply(inherited);
                }
                part.pivot().apply(inherited);
                new PartTransform(0, 0, 0, .6f, -.2f, .1f).apply(inherited);
                assertEquals(inherited, transform);
                for (int i = 0; i < outer.length; i += CuboidMesh.FLOATS_PER_VERTEX) {
                    for (int axis = 0; axis < 3; axis++)
                        assertEquals(inflation, Math.abs(outer[i + axis] - base[i + axis]));
                    assertEquals(base[i + 3], outer[i + 3]);
                    assertEquals(base[i + 4], outer[i + 4]);
                    var basePoint = transform.transformPosition(new Vector3f(base[i], base[i + 1], base[i + 2]));
                    var outerPoint = transform.transformPosition(new Vector3f(outer[i], outer[i + 1], outer[i + 2]));
                    assertEquals(Math.sqrt(3) * inflation / 16, basePoint.distance(outerPoint), .000001);
                }
            }
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> CuboidMesh.vertices(
                        PlayerModel.definition(HEAD).cuboid(), new UvOrigin(0, 0), 64, 64, Float.NaN));
    }

    @Test
    void slimChangesOnlyArmsWithCanonicalShoulderPivotsAndSymmetricBounds() {
        for (var part : PlayerModel.Part.values()) {
            var classic = PlayerModel.definition(ModelVariant.CLASSIC, part);
            var slim = PlayerModel.definition(ModelVariant.SLIM, part);
            if (part != RIGHT_ARM && part != LEFT_ARM) {
                assertSame(classic, slim);
                continue;
            }
            assertEquals(new ModelCuboid(part == RIGHT_ARM ? -1 : -2, -10, -2, 3, 12, 4), slim.cuboid());
            assertEquals(new PartTransform(part == RIGHT_ARM ? 5 : -5, 9.5f, 0, 0, 0, 0), slim.pivot());
            assertEquals(BODY, slim.parent());
            assertEquals(classic.uvOrigin(), slim.uvOrigin());
            var neutral = PlayerModel.partMatrix(ModelVariant.SLIM, part, PlayerPose.NEUTRAL, new Matrix4f());
            vector(
                    new Vector3f((part == RIGHT_ARM ? 5 : -5) / 16f, 21.5f / 16, 0),
                    neutral.getTranslation(new Vector3f()));
            var box = slim.cuboid();
            vector(
                    new Vector3f((part == RIGHT_ARM ? 4 : -7) / 16f, 11.5f / 16, -2f / 16),
                    neutral.transformPosition(new Vector3f(box.x(), box.y(), box.z())));
            vector(
                    new Vector3f((part == RIGHT_ARM ? 7 : -4) / 16f, 23.5f / 16, 2f / 16),
                    neutral.transformPosition(new Vector3f(box.x() + 3, box.y() + 12, box.z() + 4)));
            var rotated = PlayerModel.partMatrix(
                    ModelVariant.SLIM,
                    part,
                    PlayerPose.NEUTRAL.with(part, new PartTransform(0, 0, 0, .8f, .2f, -.3f)),
                    new Matrix4f());
            vector(neutral.getTranslation(new Vector3f()), rotated.getTranslation(new Vector3f()));
        }
        assertThrows(UnsupportedOperationException.class, () -> PlayerModel.SLIM_PARTS.clear());
        assertThrows(NullPointerException.class, () -> PlayerModel.parts(null));
        assertThrows(NullPointerException.class, () -> new PlayerAppearance(null));
        var slimAppearance = new PlayerAppearance(ModelVariant.SLIM).withOuterLayer(RIGHT_ARM, false);
        assertEquals(ModelVariant.SLIM, slimAppearance.model());
        assertFalse(slimAppearance.outerLayers().contains(RIGHT_ARM));
    }

    @Test
    void appearanceMaskIsImmutableIndependentAndPublishedInSnapshots() {
        var source = EnumSet.allOf(PlayerModel.Part.class);
        var appearance = new PlayerAppearance(ModelVariant.CLASSIC, source);
        source.clear();
        assertEquals(6, appearance.outerLayers().size());
        assertThrows(
                UnsupportedOperationException.class,
                () -> appearance.outerLayers().clear());
        for (var part : PlayerModel.Part.values()) {
            var changed = appearance.withOuterLayer(part, false);
            assertEquals(5, changed.outerLayers().size());
            assertFalse(changed.outerLayers().contains(part));
            assertEquals(appearance, changed.withOuterLayer(part, true));
            assertEquals(6, PlayerAppearance.DEFAULT.outerLayers().size());
            var state = new PlayerRenderStateExtractor()
                    .extract(new PlayerController(), 0, 0, new WaterContact.Result(0, false, true), false, changed);
            assertEquals(changed, state.appearance());
        }
        assertThrows(NullPointerException.class, () -> appearance.withOuterLayer(null, true));
    }

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
