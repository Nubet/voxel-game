package com.norbertfila.world;

import java.util.ArrayList;
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
    public static final int SNOWY_GRASS_SIDE_TEXTURE = 55;
    public static final int BIRCH_LEAVES_TEXTURE = 56,
            DARK_OAK_LEAVES_TEXTURE = 57,
            ACACIA_LEAVES_TEXTURE = 58,
            JUNGLE_LEAVES_TEXTURE = 59;
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
    public static final int COAL_ORE_TEXTURE = 50,
            IRON_ORE_TEXTURE = 51,
            GOLD_ORE_TEXTURE = 52,
            DIAMOND_ORE_TEXTURE = 53,
            LAPIS_ORE_TEXTURE = 54;
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
        textures.put("refi_coal_ore", COAL_ORE_TEXTURE);
        textures.put("refi_iron_ore", IRON_ORE_TEXTURE);
        textures.put("refi_gold_ore", GOLD_ORE_TEXTURE);
        textures.put("refi_diamond_ore", DIAMOND_ORE_TEXTURE);
        textures.put("refi_lapis_ore", LAPIS_ORE_TEXTURE);
        textures.put("refi_sand", SAND_TEXTURE);
        textures.put("refi_gravel", GRAVEL_TEXTURE);
        textures.put("refi_bedrock", BEDROCK_TEXTURE);
        textures.put("refi_snow", SNOW_TEXTURE);
        textures.put("refi_grass_side_snowed", SNOWY_GRASS_SIDE_TEXTURE);
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
        textures.put("refi_birch_leaves", BIRCH_LEAVES_TEXTURE);
        textures.put("refi_dark_oak_leaves", DARK_OAK_LEAVES_TEXTURE);
        textures.put("refi_acacia_leaves", ACACIA_LEAVES_TEXTURE);
        textures.put("refi_jungle_leaves", JUNGLE_LEAVES_TEXTURE);
        var blocks = new ArrayList<>(java.util.List.of(
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
                        3, "stone", STONE_TEXTURE, STONE_TEXTURE, STONE_TEXTURE, true, true, RenderLayer.OPAQUE),
                uniform(BlockId.COAL_ORE, "coal_ore", COAL_ORE_TEXTURE, true),
                uniform(BlockId.IRON_ORE, "iron_ore", IRON_ORE_TEXTURE, true),
                uniform(BlockId.GOLD_ORE, "gold_ore", GOLD_ORE_TEXTURE, true),
                uniform(BlockId.DIAMOND_ORE, "diamond_ore", DIAMOND_ORE_TEXTURE, true),
                uniform(BlockId.LAPIS_ORE, "lapis_ore", LAPIS_ORE_TEXTURE, true),
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
                uniform(BlockId.LEAVES, "leaves", LEAVES_TEXTURE, false, .3),
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
                        false,
                        RenderLayer.CUTOUT),
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
                uniform(BlockId.PINE_NEEDLES, "pine_needles", PINE_NEEDLES_TEXTURE, false, .3),
                new BlockDefinition(
                        BlockId.SWAMP_LOG,
                        "swamp_log",
                        SWAMP_LOG_TOP_TEXTURE,
                        SWAMP_LOG_TOP_TEXTURE,
                        SWAMP_LOG_SIDE_TEXTURE,
                        true,
                        true,
                        RenderLayer.OPAQUE),
                uniform(BlockId.SWAMP_LEAVES, "swamp_leaves", SWAMP_LEAVES_TEXTURE, false, .3),
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
                        true)));
        blocks.addAll(java.util.List.of(
                material(textures, BlockId.GRANITE, "granite"),
                material(textures, BlockId.POLISHED_GRANITE, "polished_granite"),
                material(textures, BlockId.DIORITE, "diorite"),
                material(textures, BlockId.POLISHED_DIORITE, "polished_diorite"),
                material(textures, BlockId.ANDESITE, "andesite"),
                material(textures, BlockId.POLISHED_ANDESITE, "polished_andesite"),
                material(textures, BlockId.MOSSY_COBBLE, "mossy_cobble"),
                material(textures, BlockId.MOSSY_STONE_BRICKS, "mossy_stone_bricks"),
                material(textures, BlockId.CRACKED_STONE_BRICKS, "cracked_stone_bricks"),
                material(textures, BlockId.CHISELED_STONE_BRICKS, "chiseled_stone_bricks"),
                material(textures, BlockId.PINE_PLANKS, "pine_planks"),
                log(textures, BlockId.BIRCH_LOG, "birch_log"),
                material(textures, BlockId.BIRCH_PLANKS, "birch_planks"),
                leaves(textures, BlockId.BIRCH_LEAVES, "birch_leaves"),
                log(textures, BlockId.DARK_OAK_LOG, "dark_oak_log"),
                material(textures, BlockId.DARK_OAK_PLANKS, "dark_oak_planks"),
                leaves(textures, BlockId.DARK_OAK_LEAVES, "dark_oak_leaves"),
                log(textures, BlockId.ACACIA_LOG, "acacia_log"),
                material(textures, BlockId.ACACIA_PLANKS, "acacia_planks"),
                leaves(textures, BlockId.ACACIA_LEAVES, "acacia_leaves"),
                log(textures, BlockId.JUNGLE_LOG, "jungle_log"),
                material(textures, BlockId.JUNGLE_PLANKS, "jungle_planks"),
                leaves(textures, BlockId.JUNGLE_LEAVES, "jungle_leaves"),
                log(textures, BlockId.STRIPPED_OAK_LOG, "stripped_oak_log"),
                log(textures, BlockId.STRIPPED_PINE_LOG, "stripped_pine_log"),
                log(textures, BlockId.STRIPPED_BIRCH_LOG, "stripped_birch_log"),
                log(textures, BlockId.STRIPPED_DARK_OAK_LOG, "stripped_dark_oak_log"),
                log(textures, BlockId.STRIPPED_ACACIA_LOG, "stripped_acacia_log"),
                log(textures, BlockId.STRIPPED_JUNGLE_LOG, "stripped_jungle_log"),
                material(textures, BlockId.MOSS, "moss"),
                material(textures, BlockId.ROOTED_DIRT, "rooted_dirt"),
                material(textures, BlockId.PACKED_MUD, "packed_mud"),
                material(textures, BlockId.MUD_BRICKS, "mud_bricks"),
                material(textures, BlockId.MYCELIUM, "mycelium", "mycelium_top", "dirt", "mycelium_side"),
                material(textures, BlockId.RED_SAND, "red_sand"),
                material(textures, BlockId.CALCITE, "calcite"),
                material(textures, BlockId.TUFF, "tuff"),
                material(textures, BlockId.POLISHED_TUFF, "polished_tuff"),
                material(textures, BlockId.TUFF_BRICKS, "tuff_bricks"),
                material(
                        textures,
                        BlockId.CHISELED_TUFF,
                        "chiseled_tuff",
                        "chiseled_tuff_top",
                        "chiseled_tuff_top",
                        "chiseled_tuff"),
                material(
                        textures,
                        BlockId.CHISELED_TUFF_BRICKS,
                        "chiseled_tuff_bricks",
                        "chiseled_tuff_bricks_top",
                        "chiseled_tuff_bricks_top",
                        "chiseled_tuff_bricks"),
                material(textures, BlockId.BLUE_ICE, "blue_ice"),
                leaves(textures, BlockId.AZALEA_LEAVES, "azalea_leaves"),
                leaves(textures, BlockId.FLOWERING_AZALEA_LEAVES, "flowering_azalea_leaves"),
                material(
                        textures,
                        BlockId.CHISELED_SANDSTONE,
                        "chiseled_sandstone",
                        "sandstone_top",
                        "sandstone_bottom",
                        "chiseled_sandstone"),
                material(textures, BlockId.SMOOTH_SANDSTONE, "smooth_sandstone"),
                material(
                        textures,
                        BlockId.RED_SANDSTONE,
                        "red_sandstone",
                        "red_sandstone_top",
                        "red_sandstone_bottom",
                        "red_sandstone"),
                material(
                        textures,
                        BlockId.CHISELED_RED_SANDSTONE,
                        "chiseled_red_sandstone",
                        "red_sandstone_top",
                        "red_sandstone_bottom",
                        "chiseled_red_sandstone"),
                material(textures, BlockId.SMOOTH_RED_SANDSTONE, "smooth_red_sandstone"),
                material(textures, BlockId.IRON_BLOCK, "iron_block"),
                material(textures, BlockId.GOLD_BLOCK, "gold_block"),
                material(textures, BlockId.COAL_BLOCK, "coal_block"),
                material(textures, BlockId.DIAMOND_BLOCK, "diamond_block"),
                material(textures, BlockId.EMERALD_BLOCK, "emerald_block"),
                material(textures, BlockId.LAPIS_BLOCK, "lapis_block"),
                material(textures, BlockId.TERRACOTTA, "terracotta")));
        String[] colors = {
            "white",
            "orange",
            "magenta",
            "light_blue",
            "yellow",
            "lime",
            "pink",
            "gray",
            "light_gray",
            "cyan",
            "purple",
            "blue",
            "brown",
            "green",
            "red",
            "black"
        };
        int terracotta = Byte.toUnsignedInt(BlockId.TERRACOTTA_WHITE);
        for (int i = 0; i < colors.length; i++) {
            String color = colors[i];
            blocks.add(material(textures, (byte) (BlockId.CONCRETE_WHITE + i), "concrete_" + color));
            blocks.add(material(textures, (byte) (BlockId.WOOL_WHITE + i), "wool_" + color));
            if (!color.equals("orange") && !color.equals("red"))
                blocks.add(material(textures, (byte) terracotta++, "terracotta_" + color));
        }
        for (int i = 0; i < 4; i++) {
            var vine = BlockShape.values()[BlockShape.VINE_EAST.ordinal() + i];
            var cocoa = BlockShape.values()[BlockShape.COCOA_EAST.ordinal() + i];
            blocks.add(decoration(
                    (byte) (Byte.toUnsignedInt(BlockId.VINE_EAST) + i),
                    vine.name().toLowerCase(java.util.Locale.ROOT),
                    texture(textures, "vine"),
                    vine));
            blocks.add(decoration(
                    (byte) (Byte.toUnsignedInt(BlockId.COCOA_EAST) + i),
                    cocoa.name().toLowerCase(java.util.Locale.ROOT),
                    texture(textures, "cocoa"),
                    cocoa));
        }
        blocks.add(plant(BlockId.TALL_GRASS, "tall_grass", texture(textures, "tall_grass_bottom")));
        blocks.add(plant(BlockId.TALL_GRASS_TOP, "tall_grass_top", texture(textures, "tall_grass_top")));
        blocks.add(plant(BlockId.LARGE_FERN, "large_fern", texture(textures, "large_fern_bottom")));
        blocks.add(plant(BlockId.LARGE_FERN_TOP, "large_fern_top", texture(textures, "large_fern_top")));
        blocks.add(material(textures, BlockId.MELON, "melon", "melon_top", "melon_top", "melon_side"));
        blocks.add(log(textures, BlockId.MAHOGANY_LOG, "mahogany_log"));
        blocks.add(leaves(textures, BlockId.MAHOGANY_LEAVES, "mahogany_leaves"));
        blocks.add(material(textures, BlockId.MAHOGANY_PLANKS, "mahogany_planks"));
        blocks.add(material(textures, BlockId.PUMPKIN, "pumpkin", "pumpkin_top", "pumpkin_top", "pumpkin_side"));
        int lava = texture(textures, "lava");
        blocks.add(new BlockDefinition(
                Byte.toUnsignedInt(BlockId.LAVA),
                "lava",
                lava,
                lava,
                lava,
                false,
                false,
                RenderLayer.OPAQUE,
                false,
                false));
        return new BlockRegistry(blocks, textures);
    }

    private static BlockDefinition decoration(byte id, String name, int texture, BlockShape shape) {
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
                shape,
                true,
                0);
    }

    private static int texture(Map<String, Integer> textures, String name) {
        return textures.computeIfAbsent("refi_" + name, key -> textures.size());
    }

    private static BlockDefinition material(Map<String, Integer> textures, byte id, String name) {
        return uniform(id, name, texture(textures, name), true);
    }

    private static BlockDefinition material(
            Map<String, Integer> textures, byte id, String name, String top, String bottom, String side) {
        return new BlockDefinition(
                Byte.toUnsignedInt(id),
                name,
                texture(textures, top),
                texture(textures, bottom),
                texture(textures, side),
                true,
                true,
                RenderLayer.OPAQUE);
    }

    private static BlockDefinition log(Map<String, Integer> textures, byte id, String name) {
        return material(textures, id, name, name + "_top", name + "_top", name + "_side");
    }

    private static BlockDefinition leaves(Map<String, Integer> textures, byte id, String name) {
        return uniform(id, name, texture(textures, name), false, .3);
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
        return uniform(id, name, texture, occludesFullFace, 1);
    }

    private static BlockDefinition uniform(
            byte id, String name, int texture, boolean occludesFullFace, double breakSeconds) {
        return new BlockDefinition(
                Byte.toUnsignedInt(id),
                name,
                texture,
                texture,
                texture,
                true,
                occludesFullFace,
                occludesFullFace ? RenderLayer.OPAQUE : RenderLayer.CUTOUT,
                true,
                true,
                BlockShape.CUBE,
                false,
                breakSeconds);
    }
}
