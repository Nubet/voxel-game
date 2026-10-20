package com.norbertfila.render;

import com.norbertfila.render.SkyState.Color;
import com.norbertfila.simulation.WorldTime;

public final class SkyModel {
    private static final Color DAY_ZENITH = new Color(.22f, .49f, .88f);
    private static final Color DAY_HORIZON = new Color(.55f, .76f, .96f);
    private static final Color NIGHT_ZENITH = new Color(.008f, .012f, .035f);
    private static final Color NIGHT_HORIZON = new Color(.025f, .035f, .075f);
    private static final Color WARM_HORIZON = new Color(.92f, .43f, .22f);
    private static final Color DAY_CLOUD = new Color(1, 1, 1);
    private static final Color NIGHT_CLOUD = new Color(.12f, .16f, .25f);
    private static final Color WARM_CLOUD = new Color(.95f, .62f, .44f);
    public static final SkyState DAY = at(WorldTime.TICKS_PER_DAY / 4d);

    private SkyModel() {}

    public static SkyState interpolate(WorldTime previous, WorldTime current, double alpha) {
        if (!Double.isFinite(alpha) || alpha < 0 || alpha > 1)
            throw new IllegalArgumentException("Sky interpolation must be in [0, 1]");
        int elapsed = Math.floorMod(current.tickOfDay() - previous.tickOfDay(), WorldTime.TICKS_PER_DAY);
        return at(previous.tickOfDay() + elapsed * alpha);
    }

    public static SkyState at(double tickOfDay) {
        if (!Double.isFinite(tickOfDay)) throw new IllegalArgumentException("Sky time must be finite");
        double angle = (tickOfDay % WorldTime.TICKS_PER_DAY) / WorldTime.TICKS_PER_DAY * Math.TAU;
        float x = (float) Math.cos(angle), y = (float) Math.sin(angle);
        float daylight = smooth(-.16f, .22f, y);
        float twilight = (1 - smooth(.02f, .4f, Math.abs(y))) * smooth(-.25f, -.02f, y);
        return new SkyState(
                NIGHT_ZENITH.mix(DAY_ZENITH, daylight),
                NIGHT_HORIZON.mix(DAY_HORIZON, daylight).mix(WARM_HORIZON, twilight * .65f),
                NIGHT_CLOUD.mix(DAY_CLOUD, daylight).mix(WARM_CLOUD, twilight * .65f),
                x,
                y,
                .16f + .84f * daylight,
                twilight,
                1 - smooth(-.22f, .02f, y));
    }

    private static float smooth(float start, float end, float value) {
        float t = Math.clamp((value - start) / (end - start), 0, 1);
        return t * t * (3 - 2 * t);
    }
}
