package com.norbertfila.render;

import static org.lwjgl.opengl.GL46C.*;

import com.norbertfila.assets.PlayerSkin;
import com.norbertfila.player.PlayerController;
import com.norbertfila.player.visual.PlayerAppearance;
import com.norbertfila.player.visual.PlayerRenderState;
import com.norbertfila.player.visual.model.CuboidMesh;
import com.norbertfila.player.visual.model.ModelVariant;
import com.norbertfila.player.visual.model.PlayerModel;
import com.norbertfila.player.visual.model.PlayerPose;
import java.util.Objects;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

public final class PlayerRenderer implements AutoCloseable {
    private static final int VERTICES_PER_MODEL =
            (PlayerModel.PARTS.size() + PlayerModel.OUTER_LAYERS.size()) * CuboidMesh.VERTEX_COUNT;
    private final int program, vao, vbo, vp, model, outerLayer, worldShading, fogRange, underwater, opacity;
    private final SkinManager skins;
    private boolean closed;

    public PlayerRenderer(SkinManager skins) {
        this.skins = Objects.requireNonNull(skins);
        int createdProgram = 0, createdVao = 0, createdVbo = 0;
        try {
            createdProgram = ShaderPrograms.create("/shaders/player_debug.vert", "/shaders/player_debug.frag");
            vp = ShaderPrograms.uniform(createdProgram, "uVP");
            model = ShaderPrograms.uniform(createdProgram, "uModel");
            outerLayer = ShaderPrograms.uniform(createdProgram, "uOuterLayer");
            worldShading = ShaderPrograms.uniform(createdProgram, "uWorldShading");
            fogRange = ShaderPrograms.uniform(createdProgram, "uDistanceFogRange");
            underwater = ShaderPrograms.uniform(createdProgram, "uUnderwater");
            opacity = ShaderPrograms.uniform(createdProgram, "uOpacity");
            glProgramUniform3f(
                    createdProgram,
                    ShaderPrograms.uniform(createdProgram, "uSkyColor"),
                    UnderwaterVisibility.SKY_RED,
                    UnderwaterVisibility.SKY_GREEN,
                    UnderwaterVisibility.SKY_BLUE);
            glProgramUniform3f(
                    createdProgram,
                    ShaderPrograms.uniform(createdProgram, "uWaterColor"),
                    UnderwaterVisibility.FOG_RED,
                    UnderwaterVisibility.FOG_GREEN,
                    UnderwaterVisibility.FOG_BLUE);
            glProgramUniform2f(
                    createdProgram,
                    ShaderPrograms.uniform(createdProgram, "uWaterFogRange"),
                    UnderwaterVisibility.CLEAR_DISTANCE,
                    UnderwaterVisibility.FOG_DENSITY);
            glProgramUniform1i(createdProgram, ShaderPrograms.uniform(createdProgram, "uSkin"), 0);
            createdVao = glCreateVertexArrays();
            createdVbo = glCreateBuffers();
            glNamedBufferStorage(createdVbo, vertices(), 0);
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

    public boolean render(
            Matrix4f viewProjection,
            PlayerRenderState state,
            PlayerPose pose,
            double alpha,
            double cameraX,
            double cameraY,
            double cameraZ) {
        return render(viewProjection, state, pose, alpha, cameraX, cameraY, cameraZ, null, 0);
    }

    public boolean render(
            Matrix4f viewProjection,
            PlayerRenderState state,
            PlayerPose pose,
            double alpha,
            double cameraX,
            double cameraY,
            double cameraZ,
            DistanceFog fog,
            float water) {
        if (closed) throw new IllegalStateException("Player renderer is closed");
        if (skins.texture() == 0) return false;
        var sample = state.interpolate(alpha);
        var feet = sample.feet();
        float x = (float) (feet.x() - cameraX), y = (float) (feet.y() - cameraY), z = (float) (feet.z() - cameraZ);
        // Encloses fully rotated limbs, swimming, crouching and inflated outer layers.
        if (!new FrustumIntersection(viewProjection).testAab(x - 2, y - 2, z - 2, x + 2, y + 3, z + 2)) return false;
        var transform = new Matrix4f().translation(x, y, z).rotateY((float) Math.toRadians(-sample.yaw() - 90));
        render(
                viewProjection,
                transform,
                pose,
                state.appearance(),
                fog,
                water,
                state.mode() == PlayerController.Mode.SPECTATOR);
        return true;
    }

    public void render(Matrix4f viewProjection, Matrix4f modelToWorld, PlayerPose pose) {
        render(viewProjection, modelToWorld, pose, PlayerAppearance.DEFAULT);
    }

    public void render(Matrix4f viewProjection, Matrix4f modelToWorld, PlayerPose pose, PlayerAppearance appearance) {
        render(viewProjection, modelToWorld, pose, appearance, null, 0);
    }

    public void render(
            Matrix4f viewProjection,
            Matrix4f modelToWorld,
            PlayerPose pose,
            PlayerAppearance appearance,
            DistanceFog fog,
            float water) {
        render(viewProjection, modelToWorld, pose, appearance, fog, water, false);
    }

    private void render(
            Matrix4f viewProjection,
            Matrix4f modelToWorld,
            PlayerPose pose,
            PlayerAppearance appearance,
            DistanceFog fog,
            float water,
            boolean spectator) {
        if (closed) throw new IllegalStateException("Player renderer is closed");
        Objects.requireNonNull(viewProjection);
        Objects.requireNonNull(modelToWorld);
        Objects.requireNonNull(pose);
        Objects.requireNonNull(appearance);
        int texture = skins.texture();
        if (texture == 0) return;
        boolean depth = glIsEnabled(GL_DEPTH_TEST), mask = glGetBoolean(GL_DEPTH_WRITEMASK);
        boolean blend = glIsEnabled(GL_BLEND), cull = glIsEnabled(GL_CULL_FACE);
        int depthFunction = glGetInteger(GL_DEPTH_FUNC);
        int cullMode = glGetInteger(GL_CULL_FACE_MODE);
        int blendSourceRgb = glGetInteger(GL_BLEND_SRC_RGB), blendDestinationRgb = glGetInteger(GL_BLEND_DST_RGB);
        int blendSourceAlpha = glGetInteger(GL_BLEND_SRC_ALPHA),
                blendDestinationAlpha = glGetInteger(GL_BLEND_DST_ALPHA);
        int blendEquationRgb = glGetInteger(GL_BLEND_EQUATION_RGB),
                blendEquationAlpha = glGetInteger(GL_BLEND_EQUATION_ALPHA);
        int previousProgram = glGetInteger(GL_CURRENT_PROGRAM), previousVao = glGetInteger(GL_VERTEX_ARRAY_BINDING);
        int previousTexture = glGetIntegeri(GL_TEXTURE_BINDING_2D, 0);
        int previousSampler = glGetIntegeri(GL_SAMPLER_BINDING, 0);
        int previousActiveTexture = glGetInteger(GL_ACTIVE_TEXTURE);
        try (var stack = MemoryStack.stackPush()) {
            glEnable(GL_DEPTH_TEST);
            glDepthFunc(GL_LEQUAL);
            glDepthMask(!spectator);
            if (spectator) {
                glEnable(GL_BLEND);
                glBlendEquation(GL_FUNC_ADD);
                glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
                glEnable(GL_CULL_FACE);
            } else {
                glDisable(GL_BLEND);
                glDisable(GL_CULL_FACE);
            }
            glUseProgram(program);
            glUniform1f(opacity, spectator ? .35f : 1);
            glUniform1i(worldShading, fog == null ? 0 : 1);
            glUniform2f(fogRange, fog == null ? 0 : fog.start(), fog == null ? 0 : fog.end());
            glUniform1f(underwater, water);
            glBindVertexArray(vao);
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, texture);
            glBindSampler(0, 0);
            var buffer = stack.mallocFloat(16);
            glUniformMatrix4fv(vp, false, viewProjection.get(buffer));
            var partMatrix = new Matrix4f();
            var worldMatrix = new Matrix4f();
            int firstVertex = appearance.model().ordinal() * VERTICES_PER_MODEL;
            for (var part : PlayerModel.parts(appearance.model())) {
                if (spectator && part.part() != PlayerModel.Part.HEAD) continue;
                PlayerModel.partMatrix(appearance.model(), part.part(), pose, partMatrix);
                modelToWorld.mul(partMatrix, worldMatrix);
                glUniformMatrix4fv(model, false, worldMatrix.get(buffer));
                boolean overlay = appearance.outerLayers().contains(part.part());
                for (int pass = 0; pass < (spectator ? 2 : 1); pass++) {
                    boolean backFaces = spectator && pass == 0;
                    if (spectator) glCullFace(backFaces ? GL_FRONT : GL_BACK);
                    if (overlay && backFaces) drawPart(firstVertex, part.part(), true);
                    drawPart(firstVertex, part.part(), false);
                    if (overlay && !backFaces) drawPart(firstVertex, part.part(), true);
                }
            }
        } finally {
            glBindTexture(GL_TEXTURE_2D, previousTexture);
            glActiveTexture(previousActiveTexture);
            glBindSampler(0, previousSampler);
            glBindVertexArray(previousVao);
            glUseProgram(previousProgram);
            glCullFace(cullMode);
            glBlendFuncSeparate(blendSourceRgb, blendDestinationRgb, blendSourceAlpha, blendDestinationAlpha);
            glBlendEquationSeparate(blendEquationRgb, blendEquationAlpha);
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

    private void drawPart(int firstVertex, PlayerModel.Part part, boolean overlay) {
        glUniform1i(outerLayer, overlay ? 1 : 0);
        int index = part.ordinal() + (overlay ? PlayerModel.PARTS.size() : 0);
        glDrawArrays(GL_TRIANGLES, firstVertex + index * CuboidMesh.VERTEX_COUNT, CuboidMesh.VERTEX_COUNT);
    }

    private static float[] vertices() {
        int partSize = CuboidMesh.VERTEX_COUNT * CuboidMesh.FLOATS_PER_VERTEX;
        var vertices = new float[ModelVariant.values().length * VERTICES_PER_MODEL * CuboidMesh.FLOATS_PER_VERTEX];
        for (var variant : ModelVariant.values()) {
            int offset = variant.ordinal() * VERTICES_PER_MODEL * CuboidMesh.FLOATS_PER_VERTEX;
            for (var part : PlayerModel.parts(variant)) {
                var mesh = CuboidMesh.vertices(part.cuboid(), part.uvOrigin(), PlayerSkin.SIZE, PlayerSkin.SIZE);
                System.arraycopy(mesh, 0, vertices, offset + part.part().ordinal() * partSize, partSize);
            }
            for (var layer : PlayerModel.OUTER_LAYERS) {
                var mesh = CuboidMesh.vertices(
                        PlayerModel.definition(variant, layer.part()).cuboid(),
                        layer.uvOrigin(),
                        PlayerSkin.SIZE,
                        PlayerSkin.SIZE,
                        layer.inflation());
                System.arraycopy(
                        mesh,
                        0,
                        vertices,
                        offset + (PlayerModel.PARTS.size() + layer.part().ordinal()) * partSize,
                        partSize);
            }
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
