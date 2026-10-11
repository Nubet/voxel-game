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

    public boolean isSolid(byte id) {
        return definition(id).solid();
    }

    public boolean isOpaque(byte id) {
        return definition(id).opaque();
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
        textures.put("grass_top", GRASS_TOP_TEXTURE);
        textures.put("dirt", DIRT_TEXTURE);
        textures.put("grass_side", GRASS_SIDE_TEXTURE);
        textures.put("stone", STONE_TEXTURE);
        return new BlockRegistry(
                java.util.List.of(
                        new BlockDefinition(
                                0, "air", FALLBACK_TEXTURE, FALLBACK_TEXTURE, FALLBACK_TEXTURE, false, false),
                        new BlockDefinition(
                                1, "grass", GRASS_TOP_TEXTURE, DIRT_TEXTURE, GRASS_SIDE_TEXTURE, true, true),
                        new BlockDefinition(2, "dirt", DIRT_TEXTURE, DIRT_TEXTURE, DIRT_TEXTURE, true, true),
                        new BlockDefinition(3, "stone", STONE_TEXTURE, STONE_TEXTURE, STONE_TEXTURE, true, true)),
                textures);
    }
}
