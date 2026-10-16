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
    public static final int SHORT_GRASS_TEXTURE = 19;
    public static final int FERN_TEXTURE = 20;
    public static final int DANDELION_YELLOW_TEXTURE = 21;
    public static final int DANDELION_WHITE_TEXTURE = 22;
    public static final int POPPY_TEXTURE = 23;
    public static final int MUD_TEXTURE = 24;
    public static final int PODZOL_TOP_TEXTURE = 25;
    public static final int PODZOL_SIDE_TEXTURE = 26;
    public static final int COARSE_DIRT_TEXTURE = 27;
    public static final int CLAY_TEXTURE = 28;
    public static final int TERRACOTTA_RED_TEXTURE = 29;
    public static final int TERRACOTTA_ORANGE_TEXTURE = 30;
    public static final int ICE_TEXTURE = 31;
    public static final int PACKED_ICE_TEXTURE = 32;
    public static final int DRY_GRASS_TOP_TEXTURE = 33;
    public static final int DRY_GRASS_SIDE_TEXTURE = 34;
    public static final int GRASS_SIDE_OVERLAY_TEXTURE = 49;
    public static final int CACTUS_TOP_TEXTURE = 35,
            CACTUS_SIDE_TEXTURE = 36,
            CACTUS_BOTTOM_TEXTURE = 37,
            DEAD_BUSH_TEXTURE = 38,
            REED_TEXTURE = 39,
            MUSHROOM_TEXTURE = 40,
            BERRY_BUSH_TEXTURE = 41,
            ALPINE_GRASS_TEXTURE = 42,
            PINE_LOG_TOP_TEXTURE = 43,
            PINE_LOG_SIDE_TEXTURE = 44,
            PINE_NEEDLES_TEXTURE = 45,
            SWAMP_LOG_TOP_TEXTURE = 46,
            SWAMP_LOG_SIDE_TEXTURE = 47,
            SWAMP_LEAVES_TEXTURE = 48;
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
        textures.put("refi_short_grass", SHORT_GRASS_TEXTURE);
        textures.put("refi_fern", FERN_TEXTURE);
        textures.put("refi_dandelion_yellow", DANDELION_YELLOW_TEXTURE);
        textures.put("refi_dandelion_white", DANDELION_WHITE_TEXTURE);
        textures.put("refi_poppy", POPPY_TEXTURE);
        textures.put("refi_mud", MUD_TEXTURE);
        textures.put("refi_podzol_top", PODZOL_TOP_TEXTURE);
        textures.put("refi_podzol_side", PODZOL_SIDE_TEXTURE);
        textures.put("refi_coarse_dirt", COARSE_DIRT_TEXTURE);
        textures.put("refi_clay", CLAY_TEXTURE);
        textures.put("refi_terracotta_red", TERRACOTTA_RED_TEXTURE);
        textures.put("refi_terracotta_orange", TERRACOTTA_ORANGE_TEXTURE);
        textures.put("refi_ice", ICE_TEXTURE);
        textures.put("refi_packed_ice", PACKED_ICE_TEXTURE);
        textures.put("refi_dry_grass_top", DRY_GRASS_TOP_TEXTURE);
        textures.put("refi_dry_grass_side", DRY_GRASS_SIDE_TEXTURE);
        textures.put("refi_cactus_top", CACTUS_TOP_TEXTURE);
        textures.put("refi_cactus_side", CACTUS_SIDE_TEXTURE);
        textures.put("refi_cactus_bottom", CACTUS_BOTTOM_TEXTURE);
        textures.put("refi_dead_bush", DEAD_BUSH_TEXTURE);
        textures.put("refi_reed", REED_TEXTURE);
        textures.put("refi_mushroom", MUSHROOM_TEXTURE);
        textures.put("refi_berry_bush", BERRY_BUSH_TEXTURE);
        textures.put("refi_alpine_grass", ALPINE_GRASS_TEXTURE);
        textures.put("refi_pine_log_top", PINE_LOG_TOP_TEXTURE);
        textures.put("refi_pine_log_side", PINE_LOG_SIDE_TEXTURE);
        textures.put("refi_pine_needles", PINE_NEEDLES_TEXTURE);
        textures.put("refi_swamp_log_top", SWAMP_LOG_TOP_TEXTURE);
        textures.put("refi_swamp_log_side", SWAMP_LOG_SIDE_TEXTURE);
        textures.put("refi_swamp_leaves", SWAMP_LEAVES_TEXTURE);
        textures.put("refi_grass_side_overlay", GRASS_SIDE_OVERLAY_TEXTURE);
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
                                RenderLayer.TRANSLUCENT,
                                false,
                                false),
                        plant(BlockId.SHORT_GRASS, "short_grass", SHORT_GRASS_TEXTURE),
                        plant(BlockId.FERN, "fern", FERN_TEXTURE),
                        plant(BlockId.DANDELION_YELLOW, "dandelion_yellow", DANDELION_YELLOW_TEXTURE),
                        plant(BlockId.DANDELION_WHITE, "dandelion_white", DANDELION_WHITE_TEXTURE),
                        plant(BlockId.POPPY, "poppy", POPPY_TEXTURE),
                        uniform(BlockId.MUD, "mud", MUD_TEXTURE, true),
                        new BlockDefinition(
                                BlockId.PODZOL,
                                "podzol",
                                PODZOL_TOP_TEXTURE,
                                DIRT_TEXTURE,
                                PODZOL_SIDE_TEXTURE,
                                true,
                                true,
                                RenderLayer.OPAQUE),
                        uniform(BlockId.COARSE_DIRT, "coarse_dirt", COARSE_DIRT_TEXTURE, true),
                        uniform(BlockId.CLAY, "clay", CLAY_TEXTURE, true),
                        uniform(BlockId.TERRACOTTA_RED, "terracotta_red", TERRACOTTA_RED_TEXTURE, true),
                        uniform(BlockId.TERRACOTTA_ORANGE, "terracotta_orange", TERRACOTTA_ORANGE_TEXTURE, true),
                        new BlockDefinition(
                                BlockId.ICE,
                                "ice",
                                ICE_TEXTURE,
                                ICE_TEXTURE,
                                ICE_TEXTURE,
                                true,
                                false,
                                RenderLayer.TRANSLUCENT),
                        uniform(BlockId.PACKED_ICE, "packed_ice", PACKED_ICE_TEXTURE, true),
                        new BlockDefinition(
                                BlockId.DRY_GRASS,
                                "dry_grass",
                                DRY_GRASS_TOP_TEXTURE,
                                DIRT_TEXTURE,
                                DRY_GRASS_SIDE_TEXTURE,
                                true,
                                true,
                                RenderLayer.OPAQUE),
                        new BlockDefinition(
                                BlockId.CACTUS,
                                "cactus",
                                CACTUS_TOP_TEXTURE,
                                CACTUS_BOTTOM_TEXTURE,
                                CACTUS_SIDE_TEXTURE,
                                true,
                                true,
                                RenderLayer.OPAQUE),
                        plant(BlockId.DEAD_BUSH, "dead_bush", DEAD_BUSH_TEXTURE),
                        plant(BlockId.REED, "reed", REED_TEXTURE),
                        plant(BlockId.MUSHROOM, "mushroom", MUSHROOM_TEXTURE),
                        plant(BlockId.BERRY_BUSH, "berry_bush", BERRY_BUSH_TEXTURE),
                        plant(BlockId.ALPINE_GRASS, "alpine_grass", ALPINE_GRASS_TEXTURE),
                        new BlockDefinition(
                                BlockId.PINE_LOG,
                                "pine_log",
                                PINE_LOG_TOP_TEXTURE,
                                PINE_LOG_TOP_TEXTURE,
                                PINE_LOG_SIDE_TEXTURE,
                                true,
                                true,
                                RenderLayer.OPAQUE),
                        uniform(BlockId.PINE_NEEDLES, "pine_needles", PINE_NEEDLES_TEXTURE, false),
                        new BlockDefinition(
                                BlockId.SWAMP_LOG,
                                "swamp_log",
                                SWAMP_LOG_TOP_TEXTURE,
                                SWAMP_LOG_TOP_TEXTURE,
                                SWAMP_LOG_SIDE_TEXTURE,
                                true,
                                true,
                                RenderLayer.OPAQUE),
                        uniform(BlockId.SWAMP_LEAVES, "swamp_leaves", SWAMP_LEAVES_TEXTURE, false),
                        new BlockDefinition(
                                BlockId.SNOW_LAYER,
                                "snow_layer",
                                SNOW_TEXTURE,
                                SNOW_TEXTURE,
                                SNOW_TEXTURE,
                                true,
                                false,
                                RenderLayer.OPAQUE,
                                true,
                                true,
                                BlockShape.SNOW_LAYER,
                                true)),
                textures);
    }

    private static BlockDefinition plant(byte id, String name, int texture) {
        return new BlockDefinition(
                Byte.toUnsignedInt(id),
                name,
                texture,
                texture,
                texture,
                false,
                false,
                RenderLayer.CUTOUT,
                true,
                true,
                BlockShape.CROSS,
                true,
                0);
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
