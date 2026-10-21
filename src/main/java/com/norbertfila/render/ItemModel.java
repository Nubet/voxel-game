package com.norbertfila.render;

import com.norbertfila.items.Tool;
import com.norbertfila.world.BlockRegistry;
import com.norbertfila.world.BlockShape;

/** How item_entity.vert draws an item; layers index the block or the item texture array. */
record ItemModel(Shape shape, boolean itemTexture, int side, int top, int bottom, float height) {
    enum Shape {
        CUBE(36),
        CROSS(12),
        FLAT(6),
        EXTRUDED(16 * 16 * 36);

        final int vertices;

        Shape(int vertices) {
            this.vertices = vertices;
        }
    }

    static ItemModel dropped(byte item) {
        var tool = Tool.of(item);
        if (tool.isPresent())
            return new ItemModel(Shape.EXTRUDED, true, tool.get().ordinal(), 0, 0, 1);
        var block = BlockRegistry.DEFAULT.definition(item);
        var shape = flat(block.model().shape()) ? Shape.CROSS : Shape.CUBE;
        return new ItemModel(
                shape,
                false,
                block.sideTexture(),
                block.topTexture(),
                block.bottomTexture(),
                block.model().maxY());
    }

    /** Plants and tools are held as extruded sprites, like Minecraft's generated item models. */
    static ItemModel held(byte item) {
        var model = dropped(item);
        return model.shape == Shape.CUBE
                ? model
                : new ItemModel(Shape.EXTRUDED, model.itemTexture, model.side, 0, 0, 1);
    }

    private static boolean flat(BlockShape shape) {
        return shape == BlockShape.CROSS || shape.geometryIndex() > 0 && shape.geometryIndex() < 5;
    }
}
