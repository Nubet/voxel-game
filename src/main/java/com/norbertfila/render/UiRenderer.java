package com.norbertfila.render;

import static org.lwjgl.opengl.GL46C.*;

import com.norbertfila.core.Hotbar;
import com.norbertfila.core.SettingsPanel;
import com.norbertfila.world.BlockId;
import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import org.lwjgl.system.MemoryStack;

public final class UiRenderer implements AutoCloseable {
    private static final int[] RECTANGLE_CORNERS = {0, 1, 2, 2, 3, 0};
    private static final int[] DIGITS = {0x3f, 0x06, 0x5b, 0x4f, 0x66, 0x6d, 0x7d, 0x07, 0x7f, 0x6f};
    private static final int[][] SEGMENTS = {
        {0, 0, 6, 1}, {5, 0, 1, 6}, {5, 5, 1, 6}, {0, 10, 6, 1}, {0, 5, 1, 6}, {0, 0, 1, 6}, {0, 5, 6, 1}
    };
    private final float[] vertices = new float[36 * (Hotbar.SLOTS * 11 + 4)];
    private static final float[][] BLOCK_COLORS = {
        {0, 0, 0},
        {.28f, .68f, .21f},
        {.53f, .34f, .19f},
        {.53f, .55f, .57f},
        {.82f, .72f, .48f},
        {.45f, .43f, .4f},
        {.17f, .16f, .15f},
        {.92f, .94f, .98f},
        {.44f, .27f, .13f},
        {.24f, .52f, .2f},
        {.61f, .4f, .24f},
        {.46f, .48f, .5f},
        {.62f, .28f, .18f},
        {.42f, .73f, .82f},
        {.79f, .68f, .46f},
        {.4f, .42f, .44f},
        {.25f, .4f, .8f}
    };
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

    private static int panelVertices(float[] vertices, SettingsPanel panel) {
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
                panel.canDecrease() ? .15f : .07f,
                panel.canDecrease() ? .18f : .08f,
                panel.canDecrease() ? .23f : .1f,
                1f);
        offset = rectangle(
                vertices,
                offset,
                SettingsPanel.PLUS_X,
                SettingsPanel.BUTTON_Y,
                SettingsPanel.BUTTON_SIZE,
                SettingsPanel.BUTTON_SIZE,
                panel.canIncrease() ? .15f : .07f,
                panel.canIncrease() ? .18f : .08f,
                panel.canIncrease() ? .23f : .1f,
                1f);
        offset = rectangle(
                vertices,
                offset,
                SettingsPanel.MINUS_X + 5,
                SettingsPanel.BUTTON_Y + 9,
                10,
                3,
                panel.canDecrease() ? .85f : .3f,
                panel.canDecrease() ? .94f : .3f,
                panel.canDecrease() ? .96f : .3f,
                1f);
        offset = rectangle(
                vertices,
                offset,
                SettingsPanel.PLUS_X + 5,
                SettingsPanel.BUTTON_Y + 9,
                10,
                3,
                panel.canIncrease() ? .85f : .3f,
                panel.canIncrease() ? .94f : .3f,
                panel.canIncrease() ? .96f : .3f,
                1f);
        offset = rectangle(
                vertices,
                offset,
                SettingsPanel.PLUS_X + 9,
                SettingsPanel.BUTTON_Y + 5,
                3,
                11,
                panel.canIncrease() ? .85f : .3f,
                panel.canIncrease() ? .94f : .3f,
                panel.canIncrease() ? .96f : .3f,
                1f);
        return offset;
    }

    static int hudVertices(float[] vertices, Hotbar hotbar, int width, int height) {
        int offset = 0;
        int centerX = width / 2;
        int centerY = height / 2;
        offset = rectangle(vertices, offset, centerX - 8, centerY - 2, 16, 4, 0, 0, 0, .8f);
        offset = rectangle(vertices, offset, centerX - 2, centerY - 8, 4, 16, 0, 0, 0, .8f);
        offset = rectangle(vertices, offset, centerX - 7, centerY - 1, 14, 2, 1, 1, 1, 1);
        offset = rectangle(vertices, offset, centerX - 1, centerY - 7, 2, 14, 1, 1, 1, 1);
        int gap = Math.min(4, width / (Hotbar.SLOTS * 16));
        int size = Math.max(1, Math.min(48, (width - 2 * gap) / Hotbar.SLOTS - gap));
        int startX = (width - Hotbar.SLOTS * size - (Hotbar.SLOTS - 1) * gap) / 2;
        int y = Math.max(0, height - size - 16);
        for (int slot = 0; slot < Hotbar.SLOTS; slot++) {
            int x = startX + slot * (size + gap);
            boolean selected = slot == hotbar.selectedSlot();
            offset = rectangle(
                    vertices,
                    offset,
                    x,
                    y,
                    size,
                    size,
                    selected ? .96f : .25f,
                    selected ? .8f : .29f,
                    selected ? .3f : .35f,
                    1);
            if (size < 8) continue;
            offset = rectangle(vertices, offset, x + 2, y + 2, size - 4, size - 4, .06f, .08f, .11f, .92f);
            byte block = hotbar.block(slot);
            if (block != BlockId.AIR) {
                float[] color = BLOCK_COLORS[Byte.toUnsignedInt(block)];
                offset = rectangle(
                        vertices,
                        offset,
                        x + size / 4,
                        y + 5,
                        size / 2,
                        Math.max(1, size - 23),
                        color[0],
                        color[1],
                        color[2],
                        1);
            }
            if (size < 18) continue;
            int label = slot + 1;
            int digits = label < 10 ? 1 : 2;
            for (int digit = 0; digit < digits; digit++) {
                int value = digits == 1 ? label : digit == 0 ? label / 10 : label % 10;
                int digitX = x + (digits == 1 ? 5 : 3 + digit * 7);
                for (int segment = 0; segment < SEGMENTS.length; segment++) {
                    if ((DIGITS[value] & (1 << segment)) == 0) continue;
                    int[] shape = SEGMENTS[segment];
                    offset = rectangle(
                            vertices,
                            offset,
                            digitX + shape[0],
                            y + size - 15 + shape[1],
                            shape[2],
                            shape[3],
                            .94f,
                            .94f,
                            .94f,
                            1);
                }
            }
        }
        return offset;
    }

    public void render(SettingsPanel panel, Hotbar hotbar, int width, int height) {
        int count = panel.open() ? panelVertices(vertices, panel) : hudVertices(vertices, hotbar, width, height);

        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glUseProgram(program);
        glUniform2f(viewportUniform, width, height);
        glBindVertexArray(vao);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer buffer = stack.mallocFloat(count);
            buffer.put(vertices, 0, count).flip();
            glBindBuffer(GL_ARRAY_BUFFER, vbo);
            glBufferData(GL_ARRAY_BUFFER, buffer, GL_DYNAMIC_DRAW);
            glDrawArrays(GL_TRIANGLES, 0, count / 6);
        }
        glBindVertexArray(0);
        glDisable(GL_BLEND);
        glEnable(GL_CULL_FACE);
        glEnable(GL_DEPTH_TEST);
    }

    private static int rectangle(
            float[] target, int offset, int x, int y, int width, int height, float r, float g, float b, float a) {
        int cursor = offset;
        for (int index : RECTANGLE_CORNERS) {
            target[cursor++] = index == 1 || index == 2 ? x + width : x;
            target[cursor++] = index >= 2 ? y + height : y;
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
