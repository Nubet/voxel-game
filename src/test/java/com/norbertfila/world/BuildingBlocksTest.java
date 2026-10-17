package com.norbertfila.world;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.assets.AssetLoader;
import com.norbertfila.generation.biome.BiomeTint;
import com.norbertfila.i18n.Language;
import com.norbertfila.i18n.Translations;
import com.norbertfila.interaction.BlockInteraction;
import com.norbertfila.items.ItemSystem;
import com.norbertfila.meshing.BlockNeighborhood;
import com.norbertfila.meshing.WaterMesher;
import com.norbertfila.player.Inventory;
import com.norbertfila.player.PlayerController.Mode;
import com.norbertfila.ui.screens.InventoryScreen;
import java.util.HashSet;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class BuildingBlocksTest {
    @Test
    void buildingBlocksCanBeMinedCollectedAndPlacedIncludingUnsignedIds() {
        for (byte block :
                new byte[] {BlockId.PINE_PLANKS, BlockId.MYCELIUM, BlockId.WOOL_CYAN, BlockId.TERRACOTTA_BLACK}) {
            var world = new World();
            world.putChunk(new ChunkPos(0, 0, 0), new Chunk());
            var position = new BlockPos(3, 2, 3);
            world.setBlock(position, block, 8);
            var items = new ItemSystem();
            var interaction = new BlockInteraction(world, 8);
            assertTrue(interaction
                    .remove(Mode.SURVIVAL, 1.5, 2.5, 3.5, 1, 0, 0, items)
                    .orElseThrow()
                    .changed());
            var inventory = new Inventory();
            for (int tick = 0; tick < 12; tick++)
                items.tick(world, new ChunkPos(0, 0, 0), 1, 1 / 60d, new Aabb(0, 0, 0, 16, 16, 16), inventory);
            assertEquals(block, inventory.snapshot().block(0));
            assertEquals(1, inventory.snapshot().count(0));
            world.setBlock(new BlockPos(4, 2, 3), BlockId.STONE, 8);
            assertTrue(interaction
                    .place(1.5, 2.5, 3.5, 1, 0, 0, block, Mode.SURVIVAL, new Aabb(0, 2, 0, 1, 3, 1))
                    .orElseThrow()
                    .changed());
            assertEquals(Byte.toUnsignedInt(block), world.getBlock(position).orElseThrow());
        }
    }

    @Test
    void newMaterialsHaveTexturesNamesCreativeStacksAndCorrectMeshFaces() throws Exception {
        var registry = BlockRegistry.DEFAULT;
        var textures = new AssetLoader(registry).loadBlockTextures();
        var names = new HashSet<String>();
        for (var field : BlockId.class.getFields()) {
            byte id = field.getByte(null);
            assertTrue(names.add(registry.definition(id).name()), field.getName());
            assertEquals(
                    field.getName().toLowerCase(java.util.Locale.ROOT),
                    registry.definition(id).name());
        }
        for (int id = 47; id <= 149; id++) {
            byte block = (byte) id;
            var definition = registry.definition(block);
            assertTrue(InventoryScreen.catalogBlocks().contains(block));
            assertEquals(BlockShape.CUBE, definition.shape());
            assertTrue(definition.collidable() && definition.breakable() && definition.targetable());
            for (var language : Language.values())
                assertFalse(new Translations(language)
                        .text("block." + definition.name())
                        .isBlank());
            var inventory = new Inventory();
            inventory.creativePick(8, block);
            assertEquals(block, inventory.snapshot().block(8));
            assertEquals(64, inventory.snapshot().count(8));
            var chunk = new Chunk();
            chunk.set(3, 4, 5, block);
            var snapshot = chunk.snapshot();
            var mesh = new WaterMesher()
                    .mesh(
                            snapshot,
                            BlockNeighborhood.centerOnly(snapshot),
                            0,
                            0,
                            0,
                            com.norbertfila.meshing.ChunkMesh.SOLID)
                    .solid();
            assertEquals(6, mesh.quadCount(), definition.name());
            for (int i = 0; i < mesh.records().length; i += 2) {
                int face = mesh.records()[i] >>> 15 & 7;
                int material = mesh.records()[i + 1];
                assertEquals(id, material >>> 16 & 255);
                assertEquals(definition.textureLayer(face), material & 255);
                if (definition.renderLayer() == RenderLayer.OPAQUE)
                    for (int y = 0; y < 16; y++)
                        for (int x = 0; x < 16; x++)
                            assertTrue(textures.layer(material & 255).rgbaAt(x, y, 3) > 0, definition.name());
            }
        }
        assertTrue(registry.textureLayers().values().stream().allMatch(layer -> layer < 256));
        for (var entry : registry.textureLayers().entrySet()) {
            if (entry.getValue() < 56) continue;
            try (var input = getClass().getResourceAsStream("/assets/textures/blocks/" + entry.getKey() + ".png")) {
                assertNotNull(input, entry.getKey());
                var image = ImageIO.read(input);
                assertEquals(16, image.getWidth());
                assertEquals(16, image.getHeight());
                var texture = textures.layer(entry.getValue());
                for (int y = 0; y < 16; y++)
                    for (int x = 0; x < 16; x++)
                        for (int c = 0; c < 4; c++)
                            assertEquals(
                                    image.getRGB(x, y) >>> (c == 3 ? 24 : 16 - c * 8) & 255, texture.rgbaAt(x, y, c));
            }
        }
    }

    @Test
    void naturalMaterialsSupportPlantsAndNewLeavesSupportSnow() {
        for (byte ground : new byte[] {BlockId.MOSS, BlockId.ROOTED_DIRT, BlockId.MYCELIUM}) {
            assertTrue(PlantRules.supports(BlockId.FERN, ground, false));
            assertTrue(PlantRules.supports(BlockId.MUSHROOM, ground, false));
        }
        assertTrue(PlantRules.supports(BlockId.CACTUS, BlockId.RED_SAND, false));
        assertTrue(PlantRules.supports(BlockId.DEAD_BUSH, BlockId.TERRACOTTA_BLACK, false));
        for (byte leaves : new byte[] {
            BlockId.BIRCH_LEAVES,
            BlockId.DARK_OAK_LEAVES,
            BlockId.ACACIA_LEAVES,
            BlockId.JUNGLE_LEAVES,
            BlockId.AZALEA_LEAVES,
            BlockId.FLOWERING_AZALEA_LEAVES
        }) {
            assertTrue(PlantRules.supports(BlockId.SNOW_LAYER, leaves, false));
            assertEquals(
                    RenderLayer.CUTOUT, BlockRegistry.DEFAULT.definition(leaves).renderLayer());
        }
        for (int layer = 56; layer <= 59; layer++) assertTrue(BiomeTint.foliageTexture(layer));
        assertFalse(BiomeTint.foliageTexture(BlockRegistry.DEFAULT.textureLayer("refi_flowering_azalea_leaves")));
    }
}
