package com.norbertfila.mob.spawn;

import com.norbertfila.mob.PlayerPosition;
import com.norbertfila.mob.SpawnZone;
import com.norbertfila.world.BlockId;
import com.norbertfila.world.BlockPos;
import com.norbertfila.world.BlockRegistry;
import com.norbertfila.world.WorldBounds;

public final class SpawnConditions {
    private SpawnConditions() {}

    /** Outside the no-spawn sphere around every player, yet close enough that the mob would not despawn at once. */
    public static SpawnCondition playerDistance() {
        return attempt -> {
            var settings = attempt.context().settings();
            double nearest = PlayerPosition.nearest(attempt.context().players(), attempt.x(), attempt.y(), attempt.z());
            return nearest >= settings.minPlayerDistance() && nearest <= settings.despawnDistance();
        };
    }

    public static SpawnCondition solidGround() {
        return attempt -> {
            var position = attempt.position();
            if (!WorldBounds.containsY(position.y() - 1)) return false;
            var ground =
                    attempt.context().environment().block(new BlockPos(position.x(), position.y() - 1, position.z()));
            if (ground.isEmpty()) return false;
            var definition = BlockRegistry.DEFAULT.definition((byte) ground.getAsInt());
            return definition.collidable()
                    && definition.occludesFullFace()
                    && ground.getAsInt() != Byte.toUnsignedInt(BlockId.BEDROCK);
        };
    }

    public static SpawnCondition noFluid() {
        return attempt -> {
            var body = attempt.body();
            var position = attempt.position();
            for (int y = position.y(); y < Math.ceil(body.maxY()); y++) {
                var block = attempt.context().environment().block(new BlockPos(position.x(), y, position.z()));
                if (block.isEmpty() || BlockId.isFluid(block.getAsInt())) return false;
            }
            return true;
        };
    }

    public static SpawnCondition freeSpace() {
        return attempt -> attempt.context().environment().spaceFree(attempt.body());
    }

    public static SpawnCondition noMobOverlap() {
        return attempt -> attempt.context().population().near(attempt.body()).isEmpty();
    }

    public static SpawnCondition allowedZone() {
        return attempt ->
                attempt.zone().filter(attempt.entry().zones()::contains).isPresent();
    }

    public static SpawnCondition zoneHasRoom() {
        return attempt ->
                attempt.populationKey().filter(attempt.context()::hasRoom).isPresent();
    }

    public static SpawnCondition nightOnSurface() {
        return attempt -> attempt.zone().orElseThrow() != SpawnZone.SURFACE
                || attempt.context()
                        .settings()
                        .isNight(attempt.context().environment().time());
    }

    /**
     * Minecraft's monster rule: no block light at all, and the brightest of block light and darkened sky light at
     * or below a random level in [0, maxSpawnLight].
     */
    public static SpawnCondition darkness() {
        return attempt -> {
            var context = attempt.context();
            var blockLight = attempt.blockLight();
            var skyLight = attempt.skyLight();
            if (blockLight.isEmpty() || skyLight.isEmpty()) return false;
            if (blockLight.getAsInt() > context.settings().maxBlockLight()) return false;
            int sky = Math.max(0, skyLight.getAsInt() - context.environment().skyDarkening());
            int brightness = Math.max(blockLight.getAsInt(), sky);
            return brightness <= context.random().nextInt(context.settings().maxSpawnLight() + 1);
        };
    }
}
