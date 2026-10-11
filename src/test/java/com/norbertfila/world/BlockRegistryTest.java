package com.norbertfila.world;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BlockRegistryTest {
    @Test
    void defaultRegistryMapsFacesAndTexturesToStableLayers() {
        BlockRegistry registry = BlockRegistry.DEFAULT;
        BlockDefinition grass = registry.definition(BlockId.GRASS);
        assertEquals("grass", grass.name());
        assertEquals(BlockRegistry.GRASS_TOP_TEXTURE, grass.textureLayer(2));
        assertEquals(BlockRegistry.DIRT_TEXTURE, grass.textureLayer(3));
        for (int face : new int[] {0, 1, 4, 5})
            assertEquals(BlockRegistry.GRASS_SIDE_TEXTURE, grass.textureLayer(face));
        assertEquals(
                BlockRegistry.DIRT_TEXTURE, registry.definition(BlockId.DIRT).textureLayer(2));
        assertEquals(
                BlockRegistry.STONE_TEXTURE, registry.definition(BlockId.STONE).textureLayer(5));
        assertEquals(
                BlockRegistry.FALLBACK_TEXTURE, registry.definition(BlockId.AIR).textureLayer(0));
    }

    @Test
    void definitionsExposeSolidAndOpaquePropertiesWithoutChangingCurrentBlocks() {
        BlockRegistry registry = BlockRegistry.DEFAULT;
        assertFalse(registry.isSolid(BlockId.AIR));
        assertFalse(registry.isOpaque(BlockId.AIR));
        for (byte id : new byte[] {BlockId.GRASS, BlockId.DIRT, BlockId.STONE}) {
            assertTrue(registry.isSolid(id));
            assertTrue(registry.isOpaque(id));
        }
    }

    @Test
    void textureLayerMappingIsImmutableAndDoesNotDependOnMapIterationOrder() {
        Map<String, Integer> input = new LinkedHashMap<>();
        input.put("missing", 0);
        input.put("stone", 4);
        input.put("grass_side", 3);
        input.put("grass_top", 1);
        input.put("dirt", 2);
        BlockRegistry registry = new BlockRegistry(
                List.of(
                        new BlockDefinition(0, "air", 0, 0, 0, false, false),
                        new BlockDefinition(3, "stone", 4, 4, 4, true, true)),
                input);
        input.put("new", 5);
        assertEquals(4, registry.textureLayer("stone"));
        assertEquals(
                List.of("missing", "stone", "grass_side", "grass_top", "dirt"),
                List.copyOf(registry.textureLayers().keySet()));
        assertThrows(
                UnsupportedOperationException.class,
                () -> registry.textureLayers().put("x", 9));
    }

    @Test
    void rejectsUnknownIdsAndInvalidFaceIndexes() {
        assertThrows(IllegalArgumentException.class, () -> BlockRegistry.DEFAULT.definition((byte) 99));
        assertThrows(IllegalArgumentException.class, () -> BlockRegistry.DEFAULT.textureLayer("missing-file"));
        assertThrows(
                IllegalArgumentException.class,
                () -> BlockRegistry.DEFAULT.definition(BlockId.GRASS).textureLayer(6));
    }

    @Test
    void rejectsInvalidDefinitionsAndRegistryCollisions() {
        assertThrows(IllegalArgumentException.class, () -> new BlockDefinition(256, "bad", 0, 0, 0, true, true));
        assertThrows(IllegalArgumentException.class, () -> new BlockDefinition(1, " ", 0, 0, 0, true, true));
        assertThrows(IllegalArgumentException.class, () -> new BlockDefinition(1, "glass", 0, 0, 0, false, true));
        Map<String, Integer> textures = BlockRegistry.DEFAULT.textureLayers();
        assertThrows(
                IllegalArgumentException.class,
                () -> new BlockRegistry(
                        List.of(
                                BlockRegistry.DEFAULT.definition(BlockId.AIR),
                                BlockRegistry.DEFAULT.definition(BlockId.AIR)),
                        textures));
        assertThrows(
                IllegalArgumentException.class,
                () -> new BlockRegistry(List.of(BlockRegistry.DEFAULT.definition(BlockId.GRASS)), textures));
        assertThrows(
                IllegalArgumentException.class,
                () -> new BlockRegistry(
                        List.of(
                                BlockRegistry.DEFAULT.definition(BlockId.AIR),
                                new BlockDefinition(1, "invalid-layer", 9, 9, 9, true, true)),
                        textures));
    }

    @Test
    void worldSolidityUsesRegistryProperties() {
        World world = new World();
        Chunk chunk = new Chunk();
        chunk.set(1, 2, 3, BlockId.GRASS);
        world.putChunk(new ChunkPos(0, 0, 0), chunk);
        assertTrue(world.isSolid(new BlockPos(1, 2, 3)));
        assertFalse(world.isSolid(new BlockPos(2, 2, 3)));
    }
}
