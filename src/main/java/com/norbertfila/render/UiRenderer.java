package com.norbertfila.render;

import static org.lwjgl.opengl.GL46C.*;

import com.norbertfila.core.SettingsPanel;
import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import org.lwjgl.system.MemoryStack;

public final class UiRenderer implements AutoCloseable {
    private final int vao;
    private final int vbo;
    private final int program;
    private final int viewportUniform;
    private boolean closed;

    public UiRenderer() {
        int vertexShader = compile(GL_VERTEX_SHADER, "/shaders/ui.vert");
        int fragmentShader = compile(GL_FRAGMENT_SHADER, "/shaders/ui.frag");
        int linkedProgram = glCreateProgram();
        glAttachShader(linkedProgram, vertexShader);
        glAttachShader(linkedProgram, fragmentShader);
        glLinkProgram(linkedProgram);
        glDeleteShader(vertexShader);
        glDeleteShader(fragmentShader);
        if (glGetProgrami(linkedProgram, GL_LINK_STATUS) == GL_FALSE) {
            glDeleteProgram(linkedProgram);
            throw new IllegalStateException("UI shader link failed");
        }
        program = linkedProgram;
        viewportUniform = glGetUniformLocation(program, "uViewport");
        vao = glGenVertexArrays();
        vbo = glGenBuffers();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 6 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 4, GL_FLOAT, false, 6 * Float.BYTES, 2L * Float.BYTES);
        glEnableVertexAttribArray(1);
        glBindVertexArray(0);
    }

    public void render(SettingsPanel panel, int width, int height) {
        if (!panel.open()) return;
        float[] vertices = new float[6 * 6 * 9];
        int offset = 0;
        offset = rectangle(
                vertices,
                offset,
                SettingsPanel.PANEL_X,
                SettingsPanel.PANEL_Y,
                SettingsPanel.PANEL_WIDTH,
                SettingsPanel.PANEL_HEIGHT,
                .06f,
                .08f,
                .11f,
                .94f);
        offset = rectangle(
                vertices,
                offset,
                SettingsPanel.SLIDER_X,
                SettingsPanel.SLIDER_Y,
                SettingsPanel.SLIDER_WIDTH,
                SettingsPanel.SLIDER_HEIGHT,
                .18f,
                .21f,
                .27f,
                1f);
        offset = rectangle(
                vertices,
                offset,
                SettingsPanel.SLIDER_X,
                SettingsPanel.SLIDER_Y,
                (int) (SettingsPanel.SLIDER_WIDTH * panel.sliderFraction()),
                SettingsPanel.SLIDER_HEIGHT,
                .22f,
                .67f,
                .78f,
                1f);
        int knobX = SettingsPanel.SLIDER_X + (int) (SettingsPanel.SLIDER_WIDTH * panel.sliderFraction());
        rectangle(
                vertices,
                offset,
                knobX - 6,
                SettingsPanel.SLIDER_Y - 6,
                12,
                SettingsPanel.SLIDER_HEIGHT + 12,
                .85f,
                .94f,
                .96f,
                1f);
        offset += 36;
        offset = rectangle(
                vertices,
                offset,
                SettingsPanel.MINUS_X,
                SettingsPanel.BUTTON_Y,
                SettingsPanel.BUTTON_SIZE,
                SettingsPanel.BUTTON_SIZE,
                .15f,
                .18f,
                .23f,
                1f);
        offset = rectangle(
                vertices,
                offset,
                SettingsPanel.PLUS_X,
                SettingsPanel.BUTTON_Y,
                SettingsPanel.BUTTON_SIZE,
                SettingsPanel.BUTTON_SIZE,
                .15f,
                .18f,
                .23f,
                1f);
        offset = rectangle(
                vertices, offset, SettingsPanel.MINUS_X + 5, SettingsPanel.BUTTON_Y + 9, 10, 3, .85f, .94f, .96f, 1f);
        offset = rectangle(
                vertices, offset, SettingsPanel.PLUS_X + 5, SettingsPanel.BUTTON_Y + 9, 10, 3, .85f, .94f, .96f, 1f);
        rectangle(vertices, offset, SettingsPanel.PLUS_X + 9, SettingsPanel.BUTTON_Y + 5, 3, 11, .85f, .94f, .96f, 1f);

        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glUseProgram(program);
        glUniform2f(viewportUniform, width, height);
        glBindVertexArray(vao);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer buffer = stack.mallocFloat(vertices.length);
            buffer.put(vertices).flip();
            glBindBuffer(GL_ARRAY_BUFFER, vbo);
            glBufferData(GL_ARRAY_BUFFER, buffer, GL_DYNAMIC_DRAW);
            glDrawArrays(GL_TRIANGLES, 0, vertices.length / 6);
        }
        glBindVertexArray(0);
        glDisable(GL_BLEND);
        glEnable(GL_CULL_FACE);
        glEnable(GL_DEPTH_TEST);
    }

    private static int rectangle(
            float[] target, int offset, int x, int y, int width, int height, float r, float g, float b, float a) {
        int[] indices = {0, 1, 2, 2, 3, 0};
        float[] corners = {x, y, x + width, y, x + width, y + height, x, y + height};
        int cursor = offset;
        for (int index : indices) {
            target[cursor++] = corners[index * 2];
            target[cursor++] = corners[index * 2 + 1];
            target[cursor++] = r;
            target[cursor++] = g;
            target[cursor++] = b;
            target[cursor++] = a;
        }
        return cursor;
    }

    private static int compile(int type, String resource) {
        int shader = glCreateShader(type);
        glShaderSource(shader, loadSource(resource));
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            String log = glGetShaderInfoLog(shader);
            glDeleteShader(shader);
            throw new IllegalStateException("UI shader compilation failed for " + resource + ": " + log);
        }
        return shader;
    }

    private static String loadSource(String resource) {
        try (InputStream stream = UiRenderer.class.getResourceAsStream(resource)) {
            if (stream == null) throw new IllegalStateException("UI shader resource not found: " + resource);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot read UI shader resource: " + resource, failure);
        }
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        glDeleteBuffers(vbo);
        glDeleteVertexArrays(vao);
        glDeleteProgram(program);
    }
}
