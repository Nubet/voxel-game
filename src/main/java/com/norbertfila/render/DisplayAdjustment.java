package com.norbertfila.render;

import static com.norbertfila.render.ShaderPrograms.uniform;
import static org.lwjgl.opengl.GL46C.*;

import com.norbertfila.core.GameSettings;

/** Applies brightness, contrast and gamma to the rendered world before the UI is drawn over it. */
public final class DisplayAdjustment implements AutoCloseable {
    private final int program, vao, frame, adjust;
    private int width, height;
    private boolean closed;

    public DisplayAdjustment() {
        int createdProgram = 0, createdVao = 0, createdFrame = 0;
        try {
            createdProgram = ShaderPrograms.create("/shaders/display.vert", "/shaders/display.frag");
            adjust = uniform(createdProgram, "uAdjust");
            glProgramUniform1i(createdProgram, uniform(createdProgram, "uFrame"), 0);
            createdVao = glCreateVertexArrays();
            createdFrame = glGenTextures();
        } catch (RuntimeException | Error failure) {
            glDeleteTextures(createdFrame);
            glDeleteVertexArrays(createdVao);
            glDeleteProgram(createdProgram);
            throw failure;
        }
        program = createdProgram;
        vao = createdVao;
        frame = createdFrame;
    }

    public static boolean isNeutral(GameSettings settings) {
        return settings.brightness() == GameSettings.DEFAULT_BRIGHTNESS
                && settings.contrast() == GameSettings.DEFAULT_CONTRAST
                && settings.gamma() == GameSettings.DEFAULT_GAMMA;
    }

    public void apply(int framebufferWidth, int framebufferHeight, GameSettings settings) {
        if (isNeutral(settings) || framebufferWidth <= 0 || framebufferHeight <= 0) return;
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, frame);
        if (width != framebufferWidth || height != framebufferHeight) {
            width = framebufferWidth;
            height = framebufferHeight;
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, 0L);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAX_LEVEL, 0);
        }
        glCopyTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, 0, 0, width, height);
        glProgramUniform3f(
                program,
                adjust,
                (settings.brightness() - GameSettings.DEFAULT_BRIGHTNESS) / 200f,
                settings.contrast() / 100f,
                settings.gamma() / 100f);
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);
        glDisable(GL_BLEND);
        glUseProgram(program);
        glBindVertexArray(vao);
        glDrawArrays(GL_TRIANGLES, 0, 3);
        glBindVertexArray(0);
        glEnable(GL_CULL_FACE);
        glEnable(GL_DEPTH_TEST);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        glDeleteTextures(frame);
        glDeleteVertexArrays(vao);
        glDeleteProgram(program);
    }
}
