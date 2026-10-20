package com.norbertfila.render;

import com.norbertfila.render.SkyState.Color;
import com.norbertfila.simulation.WorldTime;
import com.norbertfila.weather.WeatherSample;

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
                1 - smooth(-.22f, .02f, y),
                0,
                1);
    }

    public static SkyState weather(SkyState sky, WeatherSample weather) {
        float cover = smooth(.2f, 1, weather.clouds());
        float visibility = 1 - cover;
        float daylight = sky.light();
        var grey = new Color(.38f * daylight, .41f * daylight, .46f * daylight);
        var storm = new Color(.14f * daylight, .16f * daylight, .20f * daylight);
        var flash = new Color(.8f, .84f, .9f);
        return new SkyState(
                sky.zenith()
                        .mix(grey, cover * .8f)
                        .mix(storm, weather.storm() * .7f)
                        .mix(flash, weather.flash() * .8f),
                sky.horizon()
                        .mix(grey, cover * .7f)
                        .mix(storm, weather.storm() * .65f)
                        .mix(flash, weather.flash() * .65f),
                sky.clouds()
                        .mix(grey, cover * .8f)
                        .mix(storm, weather.storm() * .65f)
                        .mix(flash, weather.flash() * .8f),
                sky.sunX(),
                sky.sunY(),
                daylight * (1 - cover * .2f - weather.storm() * .3f),
                sky.twilight() * visibility,
                sky.stars() * visibility,
                cover,
                visibility);
    }

    private static float smooth(float start, float end, float value) {
        float t = Math.clamp((value - start) / (end - start), 0, 1);
        return t * t * (3 - 2 * t);
    }
}
