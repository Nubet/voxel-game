package com.norbertfila.generation;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.world.BlockId;
import com.norbertfila.world.ChunkPos;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LandscapeDiagnosticsTest {
    @TempDir
    Path output;

    @Test
    void comparesMatchingWindowsAndRejectsDifferentSamplingCoordinates() throws Exception {
        String header = "seed,version,window,origin_x,origin_z,size,step,min,fingerprint\n";
        var baseline = output.resolve("baseline.csv");
        var current = output.resolve("current.csv");
        var comparison = output.resolve("comparison.csv");
        java.nio.file.Files.writeString(baseline, header + "42,15,detail,-32,-32,16,4,20,abc\n");
        java.nio.file.Files.writeString(current, header + "42,22,detail,-32,-32,16,4,30,def\n");
        LandscapeDiagnostics.compare(baseline, current, comparison);
        assertTrue(java.nio.file.Files.readString(comparison).contains("42,detail,min,20,30,10.000000"));
        java.nio.file.Files.writeString(current, header + "42,22,detail,-64,-32,16,4,30,def\n");
        assertThrows(java.io.IOException.class, () -> LandscapeDiagnostics.compare(baseline, current, comparison));
    }

    @Test
    void measuresKnownRampAndWaterBoundaryWithoutDependingOnMapSpacing() throws Exception {
        TerrainSource ramp = new TerrainSource() {
            public long seed() {
                return 0;
            }

            public int generatorVersion() {
                return 1;
            }

            public ChunkData generate(ChunkPos position) {
                throw new UnsupportedOperationException();
            }

            public int surfaceHeight(int x, int z) {
                return x;
            }

            public TerrainSummary sample(int x, int z) {
                return new TerrainSummary(x, BlockId.GRASS, BlockId.DIRT, BlockId.STONE, TerrainSummary.NO_BEDROCK, 0);
            }
        };
        var window = new LandscapeDiagnostics.Window("ramp", -1, -2, 3, 1);
        var survey = LandscapeDiagnostics.survey(ramp, window);
        assertEquals(3, survey.water());
        assertEquals(6, survey.shores());
        assertEquals("-1,-1,0,1,1", survey.heightDistribution());
        for (double slope : survey.slopes()) assertEquals(1, slope);
        for (int relief : survey.relief()) assertEquals(64, relief);
        assertEquals(9, survey.distribution().get("material,grass"));
        assertEquals(1, survey.distribution().get("sampled_water_component,OCEAN:3"));
        assertEquals(1, survey.distribution().get("sampled_biome_component,PLAINS:9"));
        assertEquals(
                survey.summary(0, 1), LandscapeDiagnostics.survey(ramp, window).summary(0, 1));
        LandscapeDiagnostics.writeMaps(output.resolve("ramp.png"), survey);
        var image = ImageIO.read(output.resolve("ramp.png").toFile());
        assertEquals(9, image.getWidth());
        assertEquals(46, image.getHeight());
        assertNotEquals(image.getRGB(6, 43), image.getRGB(8, 43));
    }

    @Test
    void rejectsInvalidOrOverflowingWindows() {
        assertThrows(IllegalArgumentException.class, () -> new LandscapeDiagnostics.Window("bad", 0, 0, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new LandscapeDiagnostics.Window("bad", 0, 0, 1, 0));
        assertThrows(
                ArithmeticException.class, () -> new LandscapeDiagnostics.Window("bad", Integer.MAX_VALUE, 0, 2, 1));
    }
}
