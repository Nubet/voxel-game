package com.norbertfila.render;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.assets.CloudPattern;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class CloudMeshTest {
    @Test
    void emptyPatternHasNoGeometryAndIsolatedCellHasSixFaces() throws Exception {
        assertEquals(0, CloudMesh.build(pattern(4), 16, 4).length);
        float[] mesh = CloudMesh.build(pattern(4, 1, 1), 16, 4);
        assertEquals(6 * 6 * 4, mesh.length);
        for (int i = 0; i < mesh.length; i += 4) {
            assertTrue(mesh[i] >= 16 && mesh[i] <= 32);
            assertTrue(mesh[i + 1] == 0 || mesh[i + 1] == 4);
            assertTrue(mesh[i + 2] >= 16 && mesh[i + 2] <= 32);
            assertTrue(mesh[i + 3] >= .8f && mesh[i + 3] <= 1);
        }
    }

    @Test
    void fullPeriodicTileHasOnlyMergedTopAndBottomStrips() throws Exception {
        assertEquals(4 * 6 * 4, CloudMesh.build(pattern(2, 0, 0, 1, 0, 0, 1, 1, 1), 16, 4).length);
    }

    @Test
    void adjacentCellsAcrossTileSeamDoNotHaveInternalWalls() throws Exception {
        int separated = CloudMesh.build(pattern(4, 0, 1, 2, 1), 16, 4).length;
        int wrapped = CloudMesh.build(pattern(4, 0, 1, 3, 1), 16, 4).length;
        assertEquals(12 * 6 * 4, separated);
        assertEquals(10 * 6 * 4, wrapped);
    }

    @Test
    void rejectsInvalidDimensionsAndMissingAssets() throws Exception {
        CloudPattern pattern = pattern(2);
        assertThrows(IllegalArgumentException.class, () -> CloudMesh.build(pattern, 0, 4));
        assertThrows(IllegalArgumentException.class, () -> CloudMesh.build(pattern, 16, Float.NaN));
        assertThrows(java.io.IOException.class, () -> CloudPattern.load(null));
    }

    private static CloudPattern pattern(int size, int... cells) throws Exception {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i < cells.length; i += 2) image.setRGB(cells[i], cells[i + 1], 0xffffffff);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        return CloudPattern.load(new ByteArrayInputStream(bytes.toByteArray()));
    }
}
