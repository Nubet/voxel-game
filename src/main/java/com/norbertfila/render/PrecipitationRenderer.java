package com.norbertfila.render;

import static org.lwjgl.opengl.GL46C.*;

import com.norbertfila.weather.PrecipitationColumn;
import com.norbertfila.weather.PrecipitationField;
import com.norbertfila.weather.WeatherSample;
import java.util.List;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

public final class PrecipitationRenderer implements AutoCloseable {
    private static final int MAX_COLUMNS = (PrecipitationField.RADIUS * 2 + 1) * (PrecipitationField.RADIUS * 2 + 1);
    private static final int INSTANCES_PER_COLUMN = 16;
    private final int program, vao, buffer, rain, snow;
    private final int vp, offset, seconds, base, intensity, visibility, light, radius;
    private List<PrecipitationColumn> uploaded;
    private int originX, originZ;
    private boolean closed;

    public PrecipitationRenderer() {
        int p = 0, v = 0, b = 0, r = 0, s = 0;
        try {
            p = ShaderPrograms.create("/shaders/precipitation.vert", "/shaders/precipitation.frag");
            vp = ShaderPrograms.uniform(p, "uVP");
            offset = ShaderPrograms.uniform(p, "uOffset");
            seconds = ShaderPrograms.uniform(p, "uSeconds");
            base = ShaderPrograms.uniform(p, "uBase");
            intensity = ShaderPrograms.uniform(p, "uIntensity");
            visibility = ShaderPrograms.uniform(p, "uVisibility");
            light = ShaderPrograms.uniform(p, "uLight");
            radius = ShaderPrograms.uniform(p, "uRadius");
            glProgramUniform1i(p, ShaderPrograms.uniform(p, "uRain"), 0);
            glProgramUniform1i(p, ShaderPrograms.uniform(p, "uSnow"), 1);
            v = glGenVertexArrays();
            b = glGenBuffers();
            glBindBuffer(GL_SHADER_STORAGE_BUFFER, b);
            glBufferData(GL_SHADER_STORAGE_BUFFER, (long) MAX_COLUMNS * 8 * Float.BYTES, GL_DYNAMIC_DRAW);
            r = EnvironmentTexture.load("rain");
            s = EnvironmentTexture.load("snow");
        } catch (RuntimeException | Error failure) {
            glDeleteTextures(r);
            glDeleteTextures(s);
            glDeleteBuffers(b);
            glDeleteVertexArrays(v);
            glDeleteProgram(p);
            throw failure;
        } finally {
            glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
        }
        program = p;
        vao = v;
        buffer = b;
        rain = r;
        snow = s;
    }

    public int render(
            Matrix4f viewProjection,
            List<PrecipitationColumn> columns,
            double cameraX,
            double cameraY,
            double cameraZ,
            double time,
            WeatherSample weather,
            float underwater,
            SkyState sky) {
        if (closed) throw new IllegalStateException("Precipitation renderer is closed");
        if (!Double.isFinite(cameraX)
                || !Double.isFinite(cameraY)
                || !Double.isFinite(cameraZ)
                || !Double.isFinite(time)
                || time < 0
                || !Float.isFinite(underwater)
                || underwater < 0
                || underwater > 1
                || columns.size() > MAX_COLUMNS) throw new IllegalArgumentException("Invalid precipitation view");
        if (columns.isEmpty() || weather.precipitation() == 0 || underwater == 1) return 0;
        if (uploaded != columns) upload(columns);
        glEnable(GL_DEPTH_TEST);
        glDepthMask(false);
        glDisable(GL_CULL_FACE);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        try (var stack = MemoryStack.stackPush()) {
            glUseProgram(program);
            glUniformMatrix4fv(vp, false, viewProjection.get(stack.mallocFloat(16)));
            glUniform3f(offset, (float) (originX - cameraX), (float) -cameraY, (float) (originZ - cameraZ));
            // Both fall speeds and snow drift repeat after 256 seconds, preserving motion in old worlds.
            glUniform1f(seconds, (float) (time % 256));
            glUniform1f(base, (float) (Math.floor(cameraY / 16) * 16 - 16));
            glUniform1f(intensity, weather.precipitation());
            glUniform1f(visibility, 1 - underwater);
            glUniform1f(light, sky.light());
            glUniform1f(radius, PrecipitationField.RADIUS);
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, rain);
            glActiveTexture(GL_TEXTURE1);
            glBindTexture(GL_TEXTURE_2D, snow);
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 4, buffer);
            glBindVertexArray(vao);
            glDrawArraysInstanced(GL_TRIANGLES, 0, 6, columns.size() * INSTANCES_PER_COLUMN);
            return columns.size() * INSTANCES_PER_COLUMN;
        } finally {
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 4, 0);
            glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
            glBindVertexArray(0);
            glBindTexture(GL_TEXTURE_2D, 0);
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, 0);
            glUseProgram(0);
            TerrainRenderer.restoreTerrainState();
        }
    }

    private void upload(List<PrecipitationColumn> columns) {
        originX = columns.getFirst().x();
        originZ = columns.getFirst().z();
        var data = MemoryUtil.memAllocFloat(columns.size() * 8);
        try {
            for (var column : columns) {
                int hash = column.x() * 73428767 ^ column.z() * 912931;
                float seed = (hash & 0xffff) / 65536f;
                data.put((float) ((long) column.x() - originX))
                        .put(column.surface())
                        .put((float) ((long) column.z() - originZ))
                        .put(seed);
                data.put(column.weather().rain())
                        .put(column.weather().snow())
                        .put(0)
                        .put(0);
            }
            data.flip();
            glBindBuffer(GL_SHADER_STORAGE_BUFFER, buffer);
            glBufferSubData(GL_SHADER_STORAGE_BUFFER, 0, data);
            uploaded = columns;
        } finally {
            glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
            MemoryUtil.memFree(data);
        }
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        uploaded = null;
        glDeleteTextures(rain);
        glDeleteTextures(snow);
        glDeleteBuffers(buffer);
        glDeleteVertexArrays(vao);
        glDeleteProgram(program);
    }
}
