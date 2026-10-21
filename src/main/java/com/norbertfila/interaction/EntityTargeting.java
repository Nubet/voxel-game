package com.norbertfila.interaction;

import com.norbertfila.combat.AttackTarget;
import com.norbertfila.player.PlayerController;
import com.norbertfila.world.Aabb;
import java.util.Objects;
import java.util.Optional;

/** Finds the attackable body nearest along the view ray, as Minecraft picks the entity under the crosshair. */
public final class EntityTargeting {
    public static final double SURVIVAL_REACH = 3;
    public static final double CREATIVE_REACH = 5;

    private EntityTargeting() {}

    public static double reach(PlayerController.Mode mode) {
        return switch (Objects.requireNonNull(mode)) {
            case SURVIVAL -> SURVIVAL_REACH;
            case CREATIVE -> CREATIVE_REACH;
            case SPECTATOR -> 0;
        };
    }

    /** The segment's bounds, padded so that an axis-aligned ray still has volume. */
    public static Aabb sweep(Ray ray, double reach) {
        double pad = .01;
        double endX = ray.originX() + ray.directionX() * reach,
                endY = ray.originY() + ray.directionY() * reach,
                endZ = ray.originZ() + ray.directionZ() * reach;
        return new Aabb(
                Math.min(ray.originX(), endX) - pad,
                Math.min(ray.originY(), endY) - pad,
                Math.min(ray.originZ(), endZ) - pad,
                Math.max(ray.originX(), endX) + pad,
                Math.max(ray.originY(), endY) + pad,
                Math.max(ray.originZ(), endZ) + pad);
    }

    public static <T extends AttackTarget> Optional<Hit<T>> find(Iterable<T> candidates, Ray ray, double reach) {
        Objects.requireNonNull(candidates);
        Objects.requireNonNull(ray);
        if (!Double.isFinite(reach) || reach < 0) throw new IllegalArgumentException("Invalid reach");
        Hit<T> nearest = null;
        for (T candidate : candidates) {
            if (!candidate.attackable()) continue;
            double distance = entry(candidate.body(), ray);
            if (distance <= reach && (nearest == null || distance < nearest.distance()))
                nearest = new Hit<>(candidate, distance);
        }
        return Optional.ofNullable(nearest);
    }

    /** Slab test; an origin inside the body counts as distance zero and a miss as infinity. */
    private static double entry(Aabb body, Ray ray) {
        double near = 0, far = Double.POSITIVE_INFINITY;
        double[] origin = {ray.originX(), ray.originY(), ray.originZ()};
        double[] direction = {ray.directionX(), ray.directionY(), ray.directionZ()};
        double[] min = {body.minX(), body.minY(), body.minZ()};
        double[] max = {body.maxX(), body.maxY(), body.maxZ()};
        for (int axis = 0; axis < 3; axis++) {
            if (direction[axis] == 0) {
                if (origin[axis] < min[axis] || origin[axis] > max[axis]) return Double.POSITIVE_INFINITY;
                continue;
            }
            double a = (min[axis] - origin[axis]) / direction[axis];
            double b = (max[axis] - origin[axis]) / direction[axis];
            near = Math.max(near, Math.min(a, b));
            far = Math.min(far, Math.max(a, b));
            if (near > far) return Double.POSITIVE_INFINITY;
        }
        return near;
    }

    /** A view ray with a unit direction. */
    public record Ray(
            double originX, double originY, double originZ, double directionX, double directionY, double directionZ) {
        public Ray {
            double length = Math.sqrt(directionX * directionX + directionY * directionY + directionZ * directionZ);
            if (!Double.isFinite(originX)
                    || !Double.isFinite(originY)
                    || !Double.isFinite(originZ)
                    || !Double.isFinite(length)
                    || Math.abs(length - 1) > 1e-6) throw new IllegalArgumentException("Invalid ray");
        }

        public static Ray look(double x, double y, double z, double yawDegrees, double pitchDegrees) {
            double yaw = Math.toRadians(yawDegrees), pitch = Math.toRadians(pitchDegrees);
            return new Ray(x, y, z, Math.cos(yaw) * Math.cos(pitch), Math.sin(pitch), Math.sin(yaw) * Math.cos(pitch));
        }
    }

    public record Hit<T extends AttackTarget>(T target, double distance) {
        public Hit {
            Objects.requireNonNull(target);
        }
    }
}
