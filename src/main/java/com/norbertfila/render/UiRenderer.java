package com.norbertfila.render;

import static org.lwjgl.opengl.GL46C.*;

import com.norbertfila.ui.BitmapFont;
import com.norbertfila.ui.UiCanvas;
import com.norbertfila.ui.UiViewport;
import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import org.lwjgl.system.MemoryUtil;

public final class UiRenderer implements AutoCloseable {
    private final int vao;
    private final int vbo;
    private final int program;
    private final int atlas;
    private final int viewportUniform;
    private FloatBuffer uploadBuffer;
    private boolean closed;

    public UiRenderer() {
        byte[] pixels = BitmapFont.DEFAULT.atlasPixels();
        int createdProgram = 0, createdVao = 0, createdVbo = 0, createdAtlas = 0;
        int viewportLocation;
        try {
            createdProgram = createProgram();
            viewportLocation = glGetUniformLocation(createdProgram, "uViewport");
            int atlasLocation = glGetUniformLocation(createdProgram, "uAtlas");
            if (viewportLocation < 0 || atlasLocation < 0)
                throw new IllegalStateException("Missing UI shader uniforms");
            glProgramUniform1i(createdProgram, atlasLocation, 0);
            createdVao = glGenVertexArrays();
            createdVbo = glGenBuffers();
            glBindVertexArray(createdVao);
            glBindBuffer(GL_ARRAY_BUFFER, createdVbo);
            glVertexAttribPointer(0, 2, GL_FLOAT, false, UiCanvas.FLOATS_PER_VERTEX * Float.BYTES, 0);
            glEnableVertexAttribArray(0);
            glVertexAttribPointer(1, 4, GL_FLOAT, false, UiCanvas.FLOATS_PER_VERTEX * Float.BYTES, 2L * Float.BYTES);
            glEnableVertexAttribArray(1);
            glVertexAttribPointer(2, 2, GL_FLOAT, false, UiCanvas.FLOATS_PER_VERTEX * Float.BYTES, 6L * Float.BYTES);
            glEnableVertexAttribArray(2);
            glBindVertexArray(0);
            createdAtlas = glGenTextures();
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, createdAtlas);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAX_LEVEL, 0);
            var upload = MemoryUtil.memAlloc(pixels.length);
            try {
                upload.put(pixels).flip();
                glTexImage2D(
                        GL_TEXTURE_2D,
                        0,
                        GL_RGBA8,
                        BitmapFont.ATLAS_WIDTH,
                        BitmapFont.ATLAS_HEIGHT,
                        0,
                        GL_RGBA,
                        GL_UNSIGNED_BYTE,
                        upload);
            } finally {
                MemoryUtil.memFree(upload);
            }
        } catch (RuntimeException | Error failure) {
            glDeleteTextures(createdAtlas);
            glDeleteBuffers(createdVbo);
            glDeleteVertexArrays(createdVao);
            glDeleteProgram(createdProgram);
            throw failure;
        }
        program = createdProgram;
        viewportUniform = viewportLocation;
        vao = createdVao;
        vbo = createdVbo;
        atlas = createdAtlas;
    }

    public void render(UiCanvas canvas, UiViewport viewport) {
        if (closed) throw new IllegalStateException("UI renderer is closed");
        if (canvas.vertexCount() == 0) return;
        if (uploadBuffer == null) uploadBuffer = MemoryUtil.memAllocFloat(canvas.floatCount());
        else if (uploadBuffer.capacity() < canvas.floatCount())
            uploadBuffer = MemoryUtil.memRealloc(
                    uploadBuffer, Math.max(canvas.floatCount(), Math.multiplyExact(uploadBuffer.capacity(), 2)));
        uploadBuffer.clear();
        canvas.writeTo(uploadBuffer);
        uploadBuffer.flip();

        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glUseProgram(program);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, atlas);
        glUniform2f(viewportUniform, (float) viewport.width(), (float) viewport.height());
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, uploadBuffer, GL_DYNAMIC_DRAW);
        glDrawArrays(GL_TRIANGLES, 0, canvas.vertexCount());
        glBindVertexArray(0);
        glDisable(GL_BLEND);
        glEnable(GL_CULL_FACE);
        glEnable(GL_DEPTH_TEST);
    }

    private static int compile(int type, String resource) {
        String source = loadSource(resource);
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            String log = glGetShaderInfoLog(shader);
            glDeleteShader(shader);
            throw new IllegalStateException("UI shader compilation failed for " + resource + ": " + log);
        }
        return shader;
    }

    private static int createProgram() {
        int vertex = 0, fragment = 0, program = 0;
        try {
            vertex = compile(GL_VERTEX_SHADER, "/shaders/ui.vert");
            fragment = compile(GL_FRAGMENT_SHADER, "/shaders/ui.frag");
            program = glCreateProgram();
            glAttachShader(program, vertex);
            glAttachShader(program, fragment);
            glLinkProgram(program);
            if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE)
                throw new IllegalStateException("UI shader link failed: " + glGetProgramInfoLog(program));
            return program;
        } catch (RuntimeException | Error failure) {
            glDeleteProgram(program);
            throw failure;
        } finally {
            if (vertex != 0) glDeleteShader(vertex);
            if (fragment != 0) glDeleteShader(fragment);
        }
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
        MemoryUtil.memFree(uploadBuffer);
        uploadBuffer = null;
        glDeleteTextures(atlas);
        glDeleteBuffers(vbo);
        glDeleteVertexArrays(vao);
        glDeleteProgram(program);
    }
}
