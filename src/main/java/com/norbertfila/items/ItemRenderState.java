package com.norbertfila.items;

public record ItemRenderState(
        ItemSnapshot item, double previousX, double previousY, double previousZ, boolean moving) {}
