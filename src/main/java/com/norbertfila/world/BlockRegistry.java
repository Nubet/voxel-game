package com.norbertfila.world;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class BlockRegistry {
    public static final int FALLBACK_TEXTURE = 0;
    public static final int GRASS_TOP_TEXTURE = 1;
    public static final int DIRT_TEXTURE = 2;
    public static final int GRASS_SIDE_TEXTURE = 3;
    public static final int STONE_TEXTURE = 4;
    public static final int SAND_TEXTURE = 5;
    public static final int GRAVEL_TEXTURE = 6;
    public static final int BEDROCK_TEXTURE = 7;
    public static final int SNOW_TEXTURE = 8;
    public static final int LOG_SIDE_TEXTURE = 9;
    public static final int LOG_TOP_TEXTURE = 10;
    public static final int LEAVES_TEXTURE = 11;
    public static final int PLANKS_TEXTURE = 12;
    public static final int COBBLE_TEXTURE = 13;
    public static final int BRICKS_TEXTURE = 14;
    public static final int GLASS_TEXTURE = 15;
    public static final int SANDSTONE_TEXTURE = 16;
    public static final int STONE_BRICKS_TEXTURE = 17;
    public static final int WATER_TEXTURE = 18;
    public static final BlockRegistry DEFAULT = defaults();

    private final BlockDefinition[] definitions;
    private final Map<String, Integer> textureLayers;

    public BlockRegistry(Iterable<BlockDefinition> blocks, Map<String, Integer> textureLayers) {
        Objects.requireNonNull(blocks);
        Objects.requireNonNull(textureLayers);
        this.textureLayers = Collections.unmodifiableMap(new LinkedHashMap<>(textureLayers));
        if (this.textureLayers.getOrDefault("missing", -1) != FALLBACK_TEXTURE)
            throw new IllegalArgumentException("Texture layer 0 must be reserved for the missing texture");
        if (this.textureLayers.values().stream().distinct().count() != this.textureLayers.size())
            throw new IllegalArgumentException("Texture layer indices must be unique");
        for (int layer : this.textureLayers.values())
            if (layer < 0) throw new IllegalArgumentException("Texture layers must be non-negative");
        definitions = new BlockDefinition[256];
        for (BlockDefinition block : blocks) {
            Objects.requireNonNull(block);
            if (definitions[block.id()] != null)
                throw new IllegalArgumentException("Duplicate block id: " + block.id());
            requireLayer(block.topTexture());
            requireLayer(block.bottomTexture());
            requireLayer(block.sideTexture());
            definitions[block.id()] = block;
        }
        if (definitions[Byte.toUnsignedInt(BlockId.AIR)] == null)
            throw new IllegalArgumentException("AIR must be registered");
    }

    public BlockDefinition definition(byte id) {
        BlockDefinition definition = definitions[Byte.toUnsignedInt(id)];
        if (definition == null) throw new IllegalArgumentException("Unknown block id: " + Byte.toUnsignedInt(id));
        return definition;
    }

    public boolean isCollidable(byte id) {
        return definition(id).collidable();
    }

    public boolean occludesFullFace(byte id) {
        return definition(id).occludesFullFace();
    }

    public int textureLayer(String name) {
        Integer layer = textureLayers.get(Objects.requireNonNull(name));
        if (layer == null) throw new IllegalArgumentException("Unknown texture: " + name);
        return layer;
    }

    public Map<String, Integer> textureLayers() {
        return textureLayers;
    }

    private void requireLayer(int layer) {
        if (!textureLayers.containsValue(layer)) throw new IllegalArgumentException("Unmapped texture layer: " + layer);
    }

    private static BlockRegistry defaults() {
        Map<String, Integer> textures = new LinkedHashMap<>();
        textures.put("missing", FALLBACK_TEXTURE);
        textures.put("refi_grass_top", GRASS_TOP_TEXTURE);
        textures.put("refi_dirt", DIRT_TEXTURE);
        textures.put("refi_grass_side", GRASS_SIDE_TEXTURE);
        textures.put("refi_stone", STONE_TEXTURE);
        textures.put("refi_sand", SAND_TEXTURE);
        textures.put("refi_gravel", GRAVEL_TEXTURE);
        textures.put("refi_bedrock", BEDROCK_TEXTURE);
        textures.put("refi_snow", SNOW_TEXTURE);
        textures.put("refi_log_side", LOG_SIDE_TEXTURE);
        textures.put("refi_log_top", LOG_TOP_TEXTURE);
        textures.put("refi_leaves", LEAVES_TEXTURE);
        textures.put("refi_planks", PLANKS_TEXTURE);
        textures.put("refi_cobble", COBBLE_TEXTURE);
        textures.put("refi_bricks", BRICKS_TEXTURE);
        textures.put("refi_glass", GLASS_TEXTURE);
        textures.put("refi_sandstone", SANDSTONE_TEXTURE);
        textures.put("refi_stone_bricks", STONE_BRICKS_TEXTURE);
        textures.put("refi_water", WATER_TEXTURE);
        return new BlockRegistry(
                java.util.List.of(
                        new BlockDefinition(
                                0,
                                "air",
                                FALLBACK_TEXTURE,
                                FALLBACK_TEXTURE,
                                FALLBACK_TEXTURE,
                                false,
                                false,
                                RenderLayer.OPAQUE),
                        new BlockDefinition(
                                1,
                                "grass",
                                GRASS_TOP_TEXTURE,
                                DIRT_TEXTURE,
                                GRASS_SIDE_TEXTURE,
                                true,
                                true,
                                RenderLayer.OPAQUE),
                        new BlockDefinition(
                                2, "dirt", DIRT_TEXTURE, DIRT_TEXTURE, DIRT_TEXTURE, true, true, RenderLayer.OPAQUE),
                        new BlockDefinition(
                                3,
                                "stone",
                                STONE_TEXTURE,
                                STONE_TEXTURE,
                                STONE_TEXTURE,
                                true,
                                true,
                                RenderLayer.OPAQUE),
                        uniform(BlockId.SAND, "sand", SAND_TEXTURE, true),
                        uniform(BlockId.GRAVEL, "gravel", GRAVEL_TEXTURE, true),
                        uniform(BlockId.BEDROCK, "bedrock", BEDROCK_TEXTURE, true),
                        uniform(BlockId.SNOW, "snow", SNOW_TEXTURE, true),
                        new BlockDefinition(
                                Byte.toUnsignedInt(BlockId.LOG),
                                "log",
                                LOG_TOP_TEXTURE,
                                LOG_TOP_TEXTURE,
                                LOG_SIDE_TEXTURE,
                                true,
                                true,
                                RenderLayer.OPAQUE),
                        uniform(BlockId.LEAVES, "leaves", LEAVES_TEXTURE, false),
                        uniform(BlockId.PLANKS, "planks", PLANKS_TEXTURE, true),
                        uniform(BlockId.COBBLE, "cobble", COBBLE_TEXTURE, true),
                        uniform(BlockId.BRICKS, "bricks", BRICKS_TEXTURE, true),
                        uniform(BlockId.GLASS, "glass", GLASS_TEXTURE, false),
                        uniform(BlockId.SANDSTONE, "sandstone", SANDSTONE_TEXTURE, true),
                        uniform(BlockId.STONE_BRICKS, "stone_bricks", STONE_BRICKS_TEXTURE, true),
                        new BlockDefinition(
                                Byte.toUnsignedInt(BlockId.WATER),
                                "water",
                                WATER_TEXTURE,
                                WATER_TEXTURE,
                                WATER_TEXTURE,
                                false,
                                false,
                                RenderLayer.TRANSLUCENT)),
                textures);
    }

    private static BlockDefinition uniform(byte id, String name, int texture, boolean occludesFullFace) {
        return new BlockDefinition(
                Byte.toUnsignedInt(id),
                name,
                texture,
                texture,
                texture,
                true,
                occludesFullFace,
                occludesFullFace ? RenderLayer.OPAQUE : RenderLayer.CUTOUT);
    }
}
