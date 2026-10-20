package com.norbertfila.render;

import static org.lwjgl.opengl.GL46C.*;

import com.norbertfila.platform.Window;
import com.norbertfila.weather.*;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;

public final class WeatherSmoke {
    private static final int SIZE = 512;
    private static final Path OUTPUT = Path.of("build/weather-smoke");
    private static final WeatherSample RAIN = new WeatherSample(.95f, 1, 0, 0);
    private static final WeatherSample STORM = new WeatherSample(1, 1, 1, 0);

    public static void main(String[] args) throws Exception {
        Files.createDirectories(OUTPUT);
        try (var window = new Window(SIZE, SIZE, "Weather regression");
                var sky = new SkyRenderer();
                var precipitation = new PrecipitationRenderer()) {
            glViewport(0, 0, SIZE, SIZE);
            var view = new Matrix4f()
                    .perspective((float) Math.toRadians(70), 1, .1f, 128)
                    .lookAt(0, 0, 0, 0, 0, -1, 0, 1, 0);
            var columns = columns(new LocalWeather(1, 0), 0);
            clear();
            check(
                    precipitation.render(view, columns, 0, 8, 0, 1, WeatherSample.CLEAR, 0, SkyModel.DAY) == 0,
                    "Clear weather drew precipitation");
            check(total(capture()) == 0, "Clear weather changed pixels");
            precipitation.render(view, columns, 0, 8, 0, 1, RAIN, 0, SkyModel.DAY);
            var rain = capture();
            check(total(rain) > 1000, "Rain is not visible");
            write(rain, "rain");
            check(
                    glGetBoolean(GL_DEPTH_WRITEMASK)
                            && glIsEnabled(GL_DEPTH_TEST)
                            && glIsEnabled(GL_CULL_FACE)
                            && !glIsEnabled(GL_BLEND),
                    "Precipitation leaked terrain state");
            clear();
            precipitation.render(view, columns, 0, 8, 0, 1.1, RAIN, 0, SkyModel.DAY);
            check(!rain.equals(capture()), "Rain did not animate");
            clear();
            precipitation.render(view, columns, 0, 8, 0, 257, RAIN, 0, SkyModel.DAY);
            check(rain.equals(capture()), "Time wrapping jumped precipitation");
            clear();
            precipitation.render(view, columns(new LocalWeather(0, 1), 0), 0, 8, 0, 1, RAIN, 0, SkyModel.DAY);
            var snow = capture();
            check(total(snow) > 1000 && !snow.equals(rain), "Snow is not distinct and visible");
            write(snow, "snow");
            clear();
            precipitation.render(view, columns(new LocalWeather(1, 0), 40), 0, 8, 0, 1, RAIN, 0, SkyModel.DAY);
            check(total(capture()) == 0, "Rain leaked under a roof");
            clear();
            precipitation.render(view, columns, 0, 8, 0, 1, RAIN, 1, SkyModel.DAY);
            check(total(capture()) == 0, "Rain visible underwater");
            clear();
            precipitation.render(view, columns(new LocalWeather(0, 0), 0), 0, 8, 0, 1, RAIN, 0, SkyModel.DAY);
            check(total(capture()) == 0, "Desert drew precipitation");
            var overhead = new Matrix4f()
                    .perspective((float) Math.toRadians(70), 1, .1f, 128)
                    .lookAt(0, 0, 0, 0, 1, -.001f, 0, 1, 0);
            long clearSky = 0, stormSky = 0;
            for (var weather : List.of(
                    WeatherSample.CLEAR, new WeatherSample(.8f, 0, 0, 0), RAIN, STORM, new WeatherSample(1, 1, 1, 1))) {
                clear();
                sky.render(overhead, SkyModel.weather(SkyModel.DAY, weather), 0);
                var pixels = capture();
                write(pixels, "sky-" + weather.clouds() + "-" + weather.storm() + "-" + weather.flash());
                if (weather == WeatherSample.CLEAR) clearSky = total(pixels);
                if (weather == STORM) stormSky = total(pixels);
                if (weather.flash() > 0) check(total(pixels) > stormSky, "Storm flash missing");
            }
            check(clearSky > stormSky * 2, "Storm did not darken the sky");
            check(glGetError() == GL_NO_ERROR, "Weather OpenGL error");
            System.out.println(
                    "PASS rain/snow, roofs, desert, underwater, time wrap, atmosphere, flashes and GL state; GPU="
                            + glGetString(GL_RENDERER));
        }
    }

    private static List<PrecipitationColumn> columns(LocalWeather weather, float surface) {
        var columns = new ArrayList<PrecipitationColumn>();
        for (int z = -20; z <= 4; z++)
            for (int x = -16; x <= 16; x++) columns.add(new PrecipitationColumn(x, z, surface, weather));
        return List.copyOf(columns);
    }

    private static void clear() {
        glClearColor(0, 0, 0, 1);
        glDepthMask(true);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
    }

    private static ByteBuffer capture() {
        var pixels = BufferUtils.createByteBuffer(SIZE * SIZE * 4);
        glReadPixels(0, 0, SIZE, SIZE, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        return pixels;
    }

    private static long total(ByteBuffer pixels) {
        long result = 0;
        for (int i = 0; i < pixels.capacity(); i += 4)
            result += Byte.toUnsignedInt(pixels.get(i))
                    + Byte.toUnsignedInt(pixels.get(i + 1))
                    + Byte.toUnsignedInt(pixels.get(i + 2));
        return result;
    }

    private static void write(ByteBuffer pixels, String name) throws Exception {
        var image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < SIZE; y++)
            for (int x = 0; x < SIZE; x++) {
                int offset = ((SIZE - 1 - y) * SIZE + x) * 4;
                image.setRGB(
                        x,
                        y,
                        Byte.toUnsignedInt(pixels.get(offset)) << 16
                                | Byte.toUnsignedInt(pixels.get(offset + 1)) << 8
                                | Byte.toUnsignedInt(pixels.get(offset + 2)));
            }
        ImageIO.write(image, "png", OUTPUT.resolve(name + ".png").toFile());
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
