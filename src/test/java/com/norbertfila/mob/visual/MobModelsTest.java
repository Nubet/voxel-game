package com.norbertfila.mob.visual;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.mob.MobType;
import com.norbertfila.player.visual.model.CuboidMesh;
import javax.imageio.ImageIO;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class MobModelsTest {
    @Test
    void everyMobHasATextureMatchingItsModel() throws Exception {
        for (var type : MobType.values()) {
            var model = MobModels.of(type);
            try (var stream = MobModelsTest.class.getResourceAsStream(model.textureResource())) {
                assertNotNull(stream, model.textureResource());
                var image = ImageIO.read(stream);
                assertEquals(model.textureWidth(), image.getWidth(), type.id());
                assertEquals(model.textureHeight(), image.getHeight(), type.id());
            }
            for (var part : model.parts())
                assertEquals(
                        CuboidMesh.VERTEX_COUNT * CuboidMesh.FLOATS_PER_VERTEX,
                        CuboidMesh.vertices(part.cuboid(), part.uv(), model.textureWidth(), model.textureHeight())
                                .length);
        }
    }

    @Test
    void modelsStandOnTheGroundAndMatchTheirCollisionHeight() {
        for (var type : MobType.values()) {
            float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxHorizontal = 0;
            for (var part : MobModels.of(type).parts()) {
                var matrix = part.pivot().apply(new Matrix4f());
                var box = part.cuboid();
                for (int corner = 0; corner < 8; corner++) {
                    var point = matrix.transformPosition(new Vector3f(
                            box.x() + ((corner & 1) == 0 ? 0 : box.width()),
                            box.y() + ((corner & 2) == 0 ? 0 : box.height()),
                            box.z() + ((corner & 4) == 0 ? 0 : box.depth())));
                    minY = Math.min(minY, point.y);
                    maxY = Math.max(maxY, point.y);
                    maxHorizontal = Math.max(maxHorizontal, Math.max(Math.abs(point.x), Math.abs(point.z)));
                }
            }
            assertEquals(0, minY, 1e-4, type.id());
            assertEquals(type.height() * 16, maxY, 2.5, type.id());
            assertTrue(maxHorizontal <= 12, type.id());
        }
    }
}
