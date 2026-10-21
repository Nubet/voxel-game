package com.norbertfila.mob.visual;

import com.norbertfila.mob.MobType;
import com.norbertfila.mob.visual.MobModel.Limb;
import com.norbertfila.player.visual.model.ModelCuboid;
import com.norbertfila.player.visual.model.PartTransform;
import com.norbertfila.player.visual.model.UvOrigin;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Models in the classic 64x32 mob texture layout, where left limbs reuse the right limb's texture. */
public final class MobModels {
    private static final float ARMS_FORWARD = (float) (Math.PI / 2);

    private static final Map<MobType, MobModel> MODELS = new EnumMap<>(MobType.class);

    static {
        for (var type : MobType.values()) MODELS.put(type, create(type));
    }

    private MobModels() {}

    public static MobModel of(MobType type) {
        return MODELS.get(type);
    }

    private static MobModel create(MobType type) {
        return switch (type) {
            case ZOMBIE -> new MobModel("refi_zombie", 64, 64, biped(4, ARMS_FORWARD));
            case SKELETON ->
                new MobModel(
                        "refi_skeleton",
                        64,
                        32,
                        biped(2, 0),
                        Optional.of(new MobModel.HeldItem(
                                Limb.RIGHT_ARM,
                                List.of(
                                        "refi_bow",
                                        "refi_bow_pulling_0",
                                        "refi_bow_pulling_1",
                                        "refi_bow_pulling_2"))));
            case CREEPER -> new MobModel("refi_creeper", 64, 32, creeper());
        };
    }

    private static List<MobModel.Part> biped(int limb, float armPitch) {
        float half = limb / 2f;
        return List.of(
                part(Limb.HEAD, 0, 24, 0, 0, new ModelCuboid(-4, 0, -4, 8, 8, 8), 0, 0),
                part(Limb.BODY, 0, 24, 0, 0, new ModelCuboid(-4, -12, -2, 8, 12, 4), 16, 16),
                part(Limb.RIGHT_ARM, 5, 22, 0, armPitch, new ModelCuboid(-1, -10, -half, limb, 12, limb), 40, 16),
                part(Limb.LEFT_ARM, -5, 22, 0, armPitch, new ModelCuboid(1 - limb, -10, -half, limb, 12, limb), 40, 16),
                part(Limb.RIGHT_LEG, 2, 12, 0, 0, new ModelCuboid(-half, -12, -half, limb, 12, limb), 0, 16),
                part(Limb.LEFT_LEG, -2, 12, 0, 0, new ModelCuboid(-half, -12, -half, limb, 12, limb), 0, 16));
    }

    private static List<MobModel.Part> creeper() {
        var leg = new ModelCuboid(-2, -6, -2, 4, 6, 4);
        return List.of(
                part(Limb.HEAD, 0, 18, 0, 0, new ModelCuboid(-4, 0, -4, 8, 8, 8), 0, 0),
                part(Limb.BODY, 0, 18, 0, 0, new ModelCuboid(-4, -12, -2, 8, 12, 4), 16, 16),
                part(Limb.FRONT_RIGHT_LEG, 2, 6, -4, 0, leg, 0, 16),
                part(Limb.FRONT_LEFT_LEG, -2, 6, -4, 0, leg, 0, 16),
                part(Limb.BACK_RIGHT_LEG, 2, 6, 4, 0, leg, 0, 16),
                part(Limb.BACK_LEFT_LEG, -2, 6, 4, 0, leg, 0, 16));
    }

    private static MobModel.Part part(
            Limb limb, float x, float y, float z, float pitch, ModelCuboid cuboid, int u, int v) {
        return new MobModel.Part(limb, new PartTransform(x, y, z, pitch, 0, 0), cuboid, new UvOrigin(u, v));
    }
}
