package com.norbertfila.render;

import static org.lwjgl.opengl.GL46C.*;

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
    private final int viewportUniform;
    private FloatBuffer uploadBuffer;
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
        glVertexAttribPointer(0, 2, GL_FLOAT, false, UiCanvas.FLOATS_PER_VERTEX * Float.BYTES, 0);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 4, GL_FLOAT, false, UiCanvas.FLOATS_PER_VERTEX * Float.BYTES, 2L * Float.BYTES);
        glEnableVertexAttribArray(1);
        glBindVertexArray(0);
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
        MemoryUtil.memFree(uploadBuffer);
        uploadBuffer = null;
        glDeleteBuffers(vbo);
        glDeleteVertexArrays(vao);
        glDeleteProgram(program);
    }
}
