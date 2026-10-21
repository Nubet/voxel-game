package com.norbertfila.interaction;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.items.ItemSystem;
import com.norbertfila.items.Tool;
import com.norbertfila.items.ToolContext;
import com.norbertfila.player.Inventory;
import com.norbertfila.player.PlayerController.Mode;
import com.norbertfila.world.*;
import java.util.Optional;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OreLifecycleTest {
    @ParameterizedTest
    @ValueSource(ints = {42, 43, 44, 45, 46})
    void survivalMiningDropsAnOreThatCanBeCollectedAndPlaced(int id) {
        var world = new World();
        world.putChunk(new ChunkPos(0, -1, 0), new Chunk());
        var position = new BlockPos(3, -2, 3);
        world.setBlock(position, (byte) id, 8);
        var hit = Optional.of(new VoxelRaycaster.Hit(position, new VoxelRaycaster.Normal(-1, 0, 0), 1, id));
        var breaking = new BlockBreaking(world);
        var pickaxe = new ToolContext(Tool.DIAMOND_PICKAXE.id());
        assertFalse(breaking.tick(Mode.SURVIVAL, hit, true, true, pickaxe, .124));
        assertTrue(breaking.tick(Mode.SURVIVAL, hit, true, false, pickaxe, .001));
        var items = new ItemSystem();
        var interaction = new BlockInteraction(world, 8);
        assertTrue(interaction
                .remove(Mode.SURVIVAL, 1.5, -1.5, 3.5, 1, 0, 0, items, pickaxe)
                .orElseThrow()
                .changed());
        assertEquals(BlockId.AIR, world.getBlock(position).orElseThrow());
        var inventory = new Inventory();
        for (int tick = 0; tick < 12; tick++)
            items.tick(world, new ChunkPos(0, -1, 0), 1, 1 / 60d, new Aabb(0, -10, 0, 16, 0, 16), inventory);
        assertEquals(id, inventory.snapshot().block(0));
        assertEquals(1, inventory.snapshot().count(0));
        world.setBlock(new BlockPos(4, -2, 3), BlockId.STONE, 8);
        assertTrue(interaction
                .place(1.5, -1.5, 3.5, 1, 0, 0, (byte) id, Mode.SURVIVAL, new Aabb(0, -2, 0, 1, 0, 1))
                .orElseThrow()
                .changed());
        assertEquals(id, world.getBlock(position).orElseThrow());
    }
}
