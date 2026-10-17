package com.norbertfila.render;

import static org.lwjgl.opengl.GL46C.*;

import com.norbertfila.assets.PlayerSkin;
import com.norbertfila.player.visual.model.CuboidMesh;
import com.norbertfila.player.visual.model.PlayerModel;
import com.norbertfila.player.visual.model.PlayerPose;
import java.util.Objects;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

public final class PlayerDebugRenderer implements AutoCloseable {
    private final int program, vao, vbo, vp, model;
    private final SkinManager skins;
    private boolean closed;

    public PlayerDebugRenderer(SkinManager skins) {
        this.skins = Objects.requireNonNull(skins);
        int createdProgram = 0, createdVao = 0, createdVbo = 0;
        try {
            createdProgram = ShaderPrograms.create("/shaders/player_debug.vert", "/shaders/player_debug.frag");
            vp = ShaderPrograms.uniform(createdProgram, "uVP");
            model = ShaderPrograms.uniform(createdProgram, "uModel");
            glProgramUniform1i(createdProgram, ShaderPrograms.uniform(createdProgram, "uSkin"), 0);
            createdVao = glCreateVertexArrays();
            createdVbo = glCreateBuffers();
            glNamedBufferData(createdVbo, vertices(), GL_STATIC_DRAW);
            glVertexArrayVertexBuffer(createdVao, 0, createdVbo, 0, CuboidMesh.FLOATS_PER_VERTEX * Float.BYTES);
            glVertexArrayAttribFormat(createdVao, 0, 3, GL_FLOAT, false, 0);
            glVertexArrayAttribBinding(createdVao, 0, 0);
            glEnableVertexArrayAttrib(createdVao, 0);
            glVertexArrayAttribFormat(createdVao, 1, 2, GL_FLOAT, false, 3 * Float.BYTES);
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

    public void render(Matrix4f viewProjection, Matrix4f modelToWorld, PlayerPose pose) {
        if (closed) throw new IllegalStateException("Player debug renderer is closed");
        Objects.requireNonNull(viewProjection);
        Objects.requireNonNull(modelToWorld);
        Objects.requireNonNull(pose);
        int texture = skins.texture();
        if (texture == 0) return;
        boolean depth = glIsEnabled(GL_DEPTH_TEST), mask = glGetBoolean(GL_DEPTH_WRITEMASK);
        boolean blend = glIsEnabled(GL_BLEND), cull = glIsEnabled(GL_CULL_FACE);
        int depthFunction = glGetInteger(GL_DEPTH_FUNC);
        int previousProgram = glGetInteger(GL_CURRENT_PROGRAM), previousVao = glGetInteger(GL_VERTEX_ARRAY_BINDING);
        int previousTexture = glGetIntegeri(GL_TEXTURE_BINDING_2D, 0);
        int previousSampler = glGetIntegeri(GL_SAMPLER_BINDING, 0);
        int previousActiveTexture = glGetInteger(GL_ACTIVE_TEXTURE);
        try (var stack = MemoryStack.stackPush()) {
            glEnable(GL_DEPTH_TEST);
            glDepthFunc(GL_LEQUAL);
            glDepthMask(true);
            glDisable(GL_BLEND);
            glDisable(GL_CULL_FACE);
            glUseProgram(program);
            glBindVertexArray(vao);
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, texture);
            glBindSampler(0, 0);
            var buffer = stack.mallocFloat(16);
            glUniformMatrix4fv(vp, false, viewProjection.get(buffer));
            var partMatrix = new Matrix4f();
            var worldMatrix = new Matrix4f();
            for (var part : PlayerModel.PARTS) {
                PlayerModel.partMatrix(part.part(), pose, partMatrix);
                modelToWorld.mul(partMatrix, worldMatrix);
                glUniformMatrix4fv(model, false, worldMatrix.get(buffer));
                glDrawArrays(GL_TRIANGLES, part.part().ordinal() * CuboidMesh.VERTEX_COUNT, CuboidMesh.VERTEX_COUNT);
            }
        } finally {
            glBindTexture(GL_TEXTURE_2D, previousTexture);
            glActiveTexture(previousActiveTexture);
            glBindSampler(0, previousSampler);
            glBindVertexArray(previousVao);
            glUseProgram(previousProgram);
            if (cull) glEnable(GL_CULL_FACE);
            else glDisable(GL_CULL_FACE);
            if (blend) glEnable(GL_BLEND);
            else glDisable(GL_BLEND);
            glDepthMask(mask);
            glDepthFunc(depthFunction);
            if (depth) glEnable(GL_DEPTH_TEST);
            else glDisable(GL_DEPTH_TEST);
        }
    }

    private static float[] vertices() {
        int partSize = CuboidMesh.VERTEX_COUNT * CuboidMesh.FLOATS_PER_VERTEX;
        var vertices = new float[PlayerModel.PARTS.size() * partSize];
        for (var part : PlayerModel.PARTS) {
            var mesh = CuboidMesh.vertices(part.cuboid(), part.uvOrigin(), PlayerSkin.SIZE, PlayerSkin.SIZE);
            System.arraycopy(mesh, 0, vertices, part.part().ordinal() * partSize, partSize);
        }
        return vertices;
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
