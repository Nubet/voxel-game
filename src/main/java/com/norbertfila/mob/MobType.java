package com.norbertfila.mob;

import com.norbertfila.world.Aabb;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

public enum MobType {
    CREEPER("creeper", MobCategory.HOSTILE, .6, 1.7, new MobAttributes(20, 2.5, 16, false)),
    ZOMBIE("zombie", MobCategory.HOSTILE, .6, 1.95, new MobAttributes(20, 2.3, 35, true)),
    SKELETON("skeleton", MobCategory.HOSTILE, .6, 1.99, new MobAttributes(20, 2.5, 16, true));

    private final String id;
    private final MobCategory category;
    private final double width;
    private final double height;
    private final MobAttributes attributes;

    MobType(String id, MobCategory category, double width, double height, MobAttributes attributes) {
        this.id = id;
        this.category = category;
        this.width = width;
        this.height = height;
        this.attributes = attributes;
    }

    public MobAttributes attributes() {
        return attributes;
    }

    /** Minecraft places an entity's eyes at 85% of its height. */
    public double eyeHeight() {
        return height * .85;
    }

    public String id() {
        return id;
    }

    public MobCategory category() {
        return category;
    }

    public double width() {
        return width;
    }

    public double height() {
        return height;
    }

    /** Body with its feet centered on the given point. */
    public Aabb body(double x, double y, double z) {
        double half = width * .5;
        return new Aabb(x - half, y, z - half, x + half, y + height, z + half);
    }

    public static Optional<MobType> byId(String id) {
        Objects.requireNonNull(id);
        return Arrays.stream(values()).filter(type -> type.id.equals(id)).findFirst();
    }
}
