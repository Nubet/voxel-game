package com.norbertfila.items;

import java.util.HashSet;
import java.util.List;

public record ItemState(long nextId, List<ItemSnapshot> entities) {
    public static final int MAX_ENTITIES = 4096;
    public static final ItemState EMPTY = new ItemState(1, List.of());

    public ItemState {
        entities = List.copyOf(entities);
        if (nextId < 1 || entities.size() > MAX_ENTITIES) throw new IllegalArgumentException("Invalid item state");
        var ids = new HashSet<Long>();
        for (var item : entities)
            if (item.id() >= nextId || !ids.add(item.id()))
                throw new IllegalArgumentException("Duplicate or unallocated item id");
    }
}
