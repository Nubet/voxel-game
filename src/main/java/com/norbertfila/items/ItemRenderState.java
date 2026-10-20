package com.norbertfila.items;

public record ItemRenderState(ItemSnapshot item, double previousX, double previousY, double previousZ, boolean moving) {
    public static final double HALF_SIZE = ItemEntity.HALF_SIZE;
}
