package com.norbertfila.meshing;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.world.BlockId;
import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.World;
import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class WaterFaceVisibilityTest {
    private static Stream<Arguments> pairs() {
        return Stream.of(
                Arguments.of(BlockId.WATER, BlockId.WATER, false),
                Arguments.of(BlockId.WATER, BlockId.AIR, true),
                Arguments.of(BlockId.AIR, BlockId.WATER, false),
                Arguments.of(BlockId.WATER, BlockId.STONE, false),
                Arguments.of(BlockId.STONE, BlockId.WATER, true),
                Arguments.of(BlockId.WATER, BlockId.DIRT, false),
                Arguments.of(BlockId.DIRT, BlockId.WATER, true),
                Arguments.of(BlockId.WATER, BlockId.LEAVES, true),
                Arguments.of(BlockId.LEAVES, BlockId.WATER, true),
                Arguments.of(BlockId.WATER, BlockId.GLASS, true),
                Arguments.of(BlockId.GLASS, BlockId.WATER, true),
                Arguments.of(BlockId.LEAVES, BlockId.LEAVES, true),
                Arguments.of(BlockId.GLASS, BlockId.GLASS, true));
    }

    @ParameterizedTest
    @MethodSource("pairs")
    void interiorFacesFollowTheMaterialPairInEveryDirection(byte block, byte neighbor, boolean visible) {
        assertEquals(visible, FaceVisibility.isVisible(block, neighbor));
        for (int face = 0; face < 6; face++) {
            Chunk chunk = new Chunk();
            chunk.set(7, 7, 7, block);
            int[] normal = VisibleFaceMesher.NORMALS[face];
            chunk.set(7 + normal[0], 7 + normal[1], 7 + normal[2], neighbor);
            var snapshot = chunk.snapshot();
            var neighborhood = BlockNeighborhood.centerOnly(snapshot);
            float[] vertices = new VisibleFaceMesher()
                    .mesh(snapshot, neighborhood, 0, 0, 0)
                    .vertices();
            int stride = MeshData.VERTEX_STRIDE;
            int ownFaces = 0;
            boolean sharedFace = false;
            for (int i = 0; i < vertices.length; i += stride * MeshData.VERTICES_PER_FACE) {
                int faceIndex = faceOf(vertices, i, stride);
                int[] corner = VisibleFaceMesher.FACES[faceIndex][0];
                if (vertices[i] - corner[0] == 7
                        && vertices[i + 1] - corner[1] == 7
                        && vertices[i + 2] - corner[2] == 7) {
                    ownFaces++;
                    sharedFace |= faceIndex == face;
                }
            }
            assertEquals(block == BlockId.AIR ? 0 : visible ? 6 : 5, ownFaces);
            assertEquals(visible, sharedFace);
            assertMeshAgreement(neighborhood, new ChunkPos(0, 0, 0));
        }
    }

    @ParameterizedTest
    @MethodSource("pairs")
    void crossChunkFacesFollowTheSameRulesAtNegativeCoordinates(byte block, byte neighbor, boolean visible) {
        for (int face = 0; face < 6; face++) {
            World world = new World();
            ChunkPos position = new ChunkPos(-2, -1, -3);
            int[] cell = boundaryCell(face);
            Chunk center = new Chunk();
            center.set(cell[0], cell[1], cell[2], block);
            world.putChunk(position, center);
            int[] normal = VisibleFaceMesher.NORMALS[face];
            Chunk other = new Chunk();
            other.set(
                    Math.floorMod(cell[0] + normal[0], Chunk.SIZE),
                    Math.floorMod(cell[1] + normal[1], Chunk.SIZE),
                    Math.floorMod(cell[2] + normal[2], Chunk.SIZE),
                    neighbor);
            world.putChunk(
                    new ChunkPos(position.x() + normal[0], position.y() + normal[1], position.z() + normal[2]), other);
            int[] expected = new int[6];
            Arrays.fill(expected, block == BlockId.AIR ? 0 : 1);
            expected[face] = visible ? 1 : 0;
            assertArrayEquals(expected, assertMeshAgreement(world.blockNeighborhood(position), position));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5})
    void unknownNeighborRetainsTemporaryFaceAndNewSnapshotsReflectArrivalEditsAndEviction(int face) {
        World world = new World();
        ChunkPos position = new ChunkPos(-1, -1, -1);
        int[] cell = boundaryCell(face);
        Chunk center = new Chunk();
        center.set(cell[0], cell[1], cell[2], BlockId.WATER);
        world.putChunk(position, center);
        var unknown = world.blockNeighborhood(position);
        assertTrue(unknown.neighbor(BlockNeighborhood.Side.values()[face]).isEmpty());
        int[] expected = {1, 1, 1, 1, 1, 1};
        assertArrayEquals(expected, assertMeshAgreement(unknown, position));
        int[] normal = VisibleFaceMesher.NORMALS[face];
        ChunkPos otherPosition =
                new ChunkPos(position.x() + normal[0], position.y() + normal[1], position.z() + normal[2]);
        Chunk other = new Chunk();
        int x = Math.floorMod(cell[0] + normal[0], Chunk.SIZE);
        int y = Math.floorMod(cell[1] + normal[1], Chunk.SIZE);
        int z = Math.floorMod(cell[2] + normal[2], Chunk.SIZE);
        other.set(x, y, z, BlockId.WATER);
        world.putChunk(otherPosition, other);
        expected[face] = 0;
        assertArrayEquals(expected, assertMeshAgreement(world.blockNeighborhood(position), position));
        other.set(x, y, z, BlockId.STONE);
        assertArrayEquals(expected, assertMeshAgreement(world.blockNeighborhood(position), position));
        other.set(x, y, z, BlockId.AIR);
        expected[face] = 1;
        assertArrayEquals(expected, assertMeshAgreement(world.blockNeighborhood(position), position));
        assertTrue(world.blockNeighborhood(position)
                .neighbor(BlockNeighborhood.Side.values()[face])
                .isPresent());
        world.removeChunk(otherPosition);
        assertArrayEquals(expected, assertMeshAgreement(world.blockNeighborhood(position), position));
        assertArrayEquals(expected, assertMeshAgreement(unknown, position));
    }

    private static int[] boundaryCell(int face) {
        int[] cell = {7, 7, 7};
        cell[face / 2] = face % 2 == 0 ? Chunk.SIZE - 1 : 0;
        return cell;
    }

    private static int[] assertMeshAgreement(BlockNeighborhood neighborhood, ChunkPos position) {
        var snapshot = neighborhood.center();
        MeshData baseline =
                new VisibleFaceMesher().mesh(snapshot, neighborhood, position.x(), position.y(), position.z());
        GreedyMesher greedy = new GreedyMesher();
        int[] expected = directionalAreas(baseline);
        assertArrayEquals(
                expected,
                directionalAreas(greedy.mesh(snapshot, neighborhood, position.x(), position.y(), position.z())));
        assertArrayEquals(
                expected,
                directionalAreas(
                        greedy.meshTextured(snapshot, neighborhood, position.x(), position.y(), position.z())));
        return expected;
    }

    /** Face index of a triangle pair from its outward normal; meshes wind counter-clockwise seen from outside. */
    private static int faceOf(float[] vertices, int offset, int stride) {
        float ax = vertices[offset + stride] - vertices[offset];
        float ay = vertices[offset + stride + 1] - vertices[offset + 1];
        float az = vertices[offset + stride + 2] - vertices[offset + 2];
        float bx = vertices[offset + stride * 2] - vertices[offset];
        float by = vertices[offset + stride * 2 + 1] - vertices[offset + 1];
        float bz = vertices[offset + stride * 2 + 2] - vertices[offset + 2];
        float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
        for (int face = 0; face < 6; face++) {
            int[] normal = VisibleFaceMesher.NORMALS[face];
            if (Math.signum(nx) == normal[0] && Math.signum(ny) == normal[1] && Math.signum(nz) == normal[2])
                return face;
        }
        throw new AssertionError("Degenerate face");
    }

    private static int[] directionalAreas(MeshData mesh) {
        float[] vertices = mesh.vertices();
        int stride = mesh.format().stride();
        int[] doubledAreas = new int[6];
        for (int offset = 0; offset < vertices.length; offset += stride * 3) {
            float ax = vertices[offset + stride] - vertices[offset];
            float ay = vertices[offset + stride + 1] - vertices[offset + 1];
            float az = vertices[offset + stride + 2] - vertices[offset + 2];
            float bx = vertices[offset + stride * 2] - vertices[offset];
            float by = vertices[offset + stride * 2 + 1] - vertices[offset + 1];
            float bz = vertices[offset + stride * 2 + 2] - vertices[offset + 2];
            float[] normal = {ay * bz - az * by, az * bx - ax * bz, ax * by - ay * bx};
            int axis = normal[0] != 0 ? 0 : normal[1] != 0 ? 1 : 2;
            doubledAreas[axis * 2 + (normal[axis] > 0 ? 0 : 1)] += Math.round(Math.abs(normal[axis]));
        }
        for (int face = 0; face < 6; face++) doubledAreas[face] /= 2;
        return doubledAreas;
    }
}
