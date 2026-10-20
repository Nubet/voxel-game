package com.norbertfila.render;

import static org.lwjgl.opengl.GL46C.*;

import com.norbertfila.world.Aabb;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/** Minecraft's F3+B view: a white collision box, a red eye-height outline and a blue look line per entity. */
public final class HitboxRenderer implements AutoCloseable {
    static final int FLOATS_PER_VERTEX = 6;
    static final int MAX_VERTICES_PER_HITBOX = 24 + 8 + 2;
    private static final float[] WHITE = {1, 1, 1}, RED = {1, 0, 0}, BLUE = {0, 0, 1};
    private static final double LOOK_LENGTH = 2;
    private final int program, vao, vbo, viewProjection;
    private int capacity;
    private boolean closed;

    public HitboxRenderer() {
        int createdProgram = 0, createdVao = 0, createdVbo = 0;
        try {
            createdProgram = ShaderPrograms.create("/shaders/hitbox.vert", "/shaders/hitbox.frag");
            viewProjection = ShaderPrograms.uniform(createdProgram, "uVP");
            createdVao = glCreateVertexArrays();
            createdVbo = glCreateBuffers();
            glVertexArrayVertexBuffer(createdVao, 0, createdVbo, 0, FLOATS_PER_VERTEX * Float.BYTES);
            glVertexArrayAttribFormat(createdVao, 0, 3, GL_FLOAT, false, 0);
            glVertexArrayAttribBinding(createdVao, 0, 0);
            glEnableVertexArrayAttrib(createdVao, 0);
            glVertexArrayAttribFormat(createdVao, 1, 3, GL_FLOAT, false, 3 * Float.BYTES);
            glVertexArrayAttribBinding(createdVao, 1, 0);
            glEnableVertexArrayAttrib(createdVao, 1);
        } catch (RuntimeException | Error failure) {
            glDeleteBuffers(createdVbo);
            glDeleteVertexArrays(createdVao);
            glDeleteProgram(createdProgram);
            throw failure;
        }
        program = createdProgram;
        vao = createdVao;
        vbo = createdVbo;
    }

    public void render(
            Matrix4f relativeViewProjection, List<Hitbox> hitboxes, double cameraX, double cameraY, double cameraZ) {
        if (closed) throw new IllegalStateException("Hitbox renderer is closed");
        Objects.requireNonNull(relativeViewProjection);
        if (hitboxes.isEmpty()) return;
        float[] vertices = vertices(hitboxes, cameraX, cameraY, cameraZ);
        int bytes = vertices.length * Float.BYTES;
        if (bytes > capacity) {
            capacity = Integer.highestOneBit(bytes - 1) << 1;
            glNamedBufferData(vbo, capacity, GL_STREAM_DRAW);
        }
        var upload = MemoryUtil.memAllocFloat(vertices.length);
        try {
            glNamedBufferSubData(vbo, 0, upload.put(vertices).flip());
        } finally {
            MemoryUtil.memFree(upload);
        }
        boolean depthTest = glIsEnabled(GL_DEPTH_TEST), depthMask = glGetBoolean(GL_DEPTH_WRITEMASK);
        boolean blend = glIsEnabled(GL_BLEND);
        int depthFunction = glGetInteger(GL_DEPTH_FUNC);
        int previousProgram = glGetInteger(GL_CURRENT_PROGRAM), previousVao = glGetInteger(GL_VERTEX_ARRAY_BINDING);
        float lineWidth = glGetFloat(GL_LINE_WIDTH);
        try (var stack = MemoryStack.stackPush()) {
            glEnable(GL_DEPTH_TEST);
            glDepthFunc(GL_LEQUAL);
            glDepthMask(false);
            glDisable(GL_BLEND);
            glLineWidth(1);
            glUseProgram(program);
            glUniformMatrix4fv(viewProjection, false, relativeViewProjection.get(stack.mallocFloat(16)));
            glBindVertexArray(vao);
            glDrawArrays(GL_LINES, 0, vertices.length / FLOATS_PER_VERTEX);
        } finally {
            glBindVertexArray(previousVao);
            glUseProgram(previousProgram);
            glLineWidth(lineWidth);
            if (blend) glEnable(GL_BLEND);
            else glDisable(GL_BLEND);
            glDepthMask(depthMask);
            glDepthFunc(depthFunction);
            if (depthTest) glEnable(GL_DEPTH_TEST);
            else glDisable(GL_DEPTH_TEST);
        }
    }

    static float[] vertices(List<Hitbox> hitboxes, double cameraX, double cameraY, double cameraZ) {
        var lines = new Lines(hitboxes.size(), cameraX, cameraY, cameraZ);
        for (var hitbox : hitboxes) {
            var b = hitbox.box();
            double eye = hitbox.eyeY();
            for (double y : new double[] {b.minY(), b.maxY()}) lines.rectangle(b, y, WHITE);
            for (double x : new double[] {b.minX(), b.maxX()})
                for (double z : new double[] {b.minZ(), b.maxZ()}) lines.add(x, b.minY(), z, x, b.maxY(), z, WHITE);
            lines.rectangle(b, eye, RED);
            if (hitbox.hasLook()) {
                double x = (b.minX() + b.maxX()) * .5, z = (b.minZ() + b.maxZ()) * .5;
                lines.add(
                        x,
                        eye,
                        z,
                        x + hitbox.lookX() * LOOK_LENGTH,
                        eye + hitbox.lookY() * LOOK_LENGTH,
                        z + hitbox.lookZ() * LOOK_LENGTH,
                        BLUE);
            }
        }
        return lines.toArray();
    }

    /** Subtracts the camera in double precision before narrowing, so distant entities keep sharp lines. */
    private static final class Lines {
        private final float[] data;
        private final double cameraX, cameraY, cameraZ;
        private int size;

        Lines(int hitboxes, double cameraX, double cameraY, double cameraZ) {
            data = new float[hitboxes * MAX_VERTICES_PER_HITBOX * FLOATS_PER_VERTEX];
            this.cameraX = cameraX;
            this.cameraY = cameraY;
            this.cameraZ = cameraZ;
        }

        void rectangle(Aabb box, double y, float[] color) {
            add(box.minX(), y, box.minZ(), box.maxX(), y, box.minZ(), color);
            add(box.maxX(), y, box.minZ(), box.maxX(), y, box.maxZ(), color);
            add(box.maxX(), y, box.maxZ(), box.minX(), y, box.maxZ(), color);
            add(box.minX(), y, box.maxZ(), box.minX(), y, box.minZ(), color);
        }

        void add(double x0, double y0, double z0, double x1, double y1, double z1, float[] color) {
            vertex(x0, y0, z0, color);
            vertex(x1, y1, z1, color);
        }

        private void vertex(double x, double y, double z, float[] color) {
            data[size++] = (float) (x - cameraX);
            data[size++] = (float) (y - cameraY);
            data[size++] = (float) (z - cameraZ);
            data[size++] = color[0];
            data[size++] = color[1];
            data[size++] = color[2];
        }

        float[] toArray() {
            return Arrays.copyOf(data, size);
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
