package com.norbertfila.render;

import com.norbertfila.items.ItemRenderState;
import com.norbertfila.mob.MobRenderState;
import com.norbertfila.player.PlayerState;
import com.norbertfila.world.Aabb;
import java.util.ArrayList;
import java.util.List;
import org.joml.Vector3fc;

/** Builds the F3+B debug boxes from published render state. */
public final class Hitboxes {
    /** Minecraft places an entity's eyes at 85% of its height unless the entity says otherwise. */
    static final double DEFAULT_EYE_FRACTION = .85;

    private Hitboxes() {}

    public static List<Hitbox> of(List<MobRenderState> mobs, List<ItemRenderState> items, double alpha) {
        var boxes = new ArrayList<Hitbox>(mobs.size() + items.size() + 1);
        for (var mob : mobs) boxes.add(mob(mob, alpha));
        for (var item : items) boxes.add(item(item, alpha));
        return boxes;
    }

    static Hitbox mob(MobRenderState mob, double alpha) {
        double y = mob.interpolatedY(alpha);
        double yaw = Math.toRadians(mob.yaw());
        return new Hitbox(
                mob.type().body(mob.interpolatedX(alpha), y, mob.interpolatedZ(alpha)),
                y + mob.type().height() * DEFAULT_EYE_FRACTION,
                Math.sin(yaw),
                0,
                -Math.cos(yaw));
    }

    static Hitbox item(ItemRenderState state, double alpha) {
        var item = state.item();
        double x = state.previousX() + (item.x() - state.previousX()) * alpha;
        double y = state.previousY() + (item.y() - state.previousY()) * alpha;
        double z = state.previousZ() + (item.z() - state.previousZ()) * alpha;
        var box = new Aabb(
                x - ItemRenderState.HALF_SIZE,
                y - ItemRenderState.HALF_SIZE,
                z - ItemRenderState.HALF_SIZE,
                x + ItemRenderState.HALF_SIZE,
                y + ItemRenderState.HALF_SIZE,
                z + ItemRenderState.HALF_SIZE);
        return new Hitbox(box, box.minY() + 2 * ItemRenderState.HALF_SIZE * DEFAULT_EYE_FRACTION, 0, 0, 0);
    }

    /** The local player's box placed under the interpolated eye, looking where the camera looks. */
    public static Hitbox player(PlayerState state, PlayerState.Position eye, Vector3fc look) {
        var body = state.body();
        double halfWidth = (body.maxX() - body.minX()) * .5, halfDepth = (body.maxZ() - body.minZ()) * .5;
        double feet = eye.y() - state.eyeHeight();
        return new Hitbox(
                new Aabb(
                        eye.x() - halfWidth,
                        feet,
                        eye.z() - halfDepth,
                        eye.x() + halfWidth,
                        feet + body.maxY() - body.minY(),
                        eye.z() + halfDepth),
                eye.y(),
                look.x(),
                look.y(),
                look.z());
    }
}
