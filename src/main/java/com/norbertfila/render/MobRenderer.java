package com.norbertfila.render;

import static org.lwjgl.opengl.GL46C.*;

import com.norbertfila.mob.MobRenderState;
import com.norbertfila.mob.MobType;
import com.norbertfila.mob.visual.MobModel;
import com.norbertfila.mob.visual.MobModels;
import com.norbertfila.player.visual.model.CuboidMesh;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

public final class MobRenderer implements AutoCloseable {
    private final int program, vao, vbo, vp, model, fogRange, underwater, skyColor, daylight;
    private final Map<MobType, Integer> textures = new EnumMap<>(MobType.class);
    private final Map<MobType, Integer> firstVertex = new EnumMap<>(MobType.class);
    private boolean closed;

    public MobRenderer() {
        int createdProgram = 0, createdVao = 0, createdVbo = 0;
        try {
            createdProgram = ShaderPrograms.create("/shaders/player_debug.vert", "/shaders/player_debug.frag");
            vp = ShaderPrograms.uniform(createdProgram, "uVP");
            model = ShaderPrograms.uniform(createdProgram, "uModel");
            fogRange = ShaderPrograms.uniform(createdProgram, "uDistanceFogRange");
            underwater = ShaderPrograms.uniform(createdProgram, "uUnderwater");
            skyColor = ShaderPrograms.uniform(createdProgram, "uSkyColor");
            daylight = ShaderPrograms.uniform(createdProgram, "uDaylight");
            glProgramUniform1i(createdProgram, ShaderPrograms.uniform(createdProgram, "uOuterLayer"), 0);
            glProgramUniform1i(createdProgram, ShaderPrograms.uniform(createdProgram, "uWorldShading"), 1);
            glProgramUniform1f(createdProgram, ShaderPrograms.uniform(createdProgram, "uOpacity"), 1);
            glProgramUniform1i(createdProgram, ShaderPrograms.uniform(createdProgram, "uSkin"), 0);
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
            glProgramUniform3f(
                    createdProgram,
                    skyColor,
                    UnderwaterVisibility.SKY_RED,
                    UnderwaterVisibility.SKY_GREEN,
                    UnderwaterVisibility.SKY_BLUE);
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
            for (var type : MobType.values())
                textures.put(
                        type, EnvironmentTexture.loadResource(MobModels.of(type).textureResource()));
        } catch (RuntimeException | Error failure) {
            textures.values().forEach(texture -> glDeleteTextures(texture));
            glDeleteBuffers(createdVbo);
            glDeleteVertexArrays(createdVao);
            glDeleteProgram(createdProgram);
            throw failure;
        }
        program = createdProgram;
        vao = createdVao;
        vbo = createdVbo;
    }

    public void environment(SkyState sky) {
        var color = sky.horizon();
        glProgramUniform3f(program, skyColor, color.red(), color.green(), color.blue());
        glProgramUniform1f(program, daylight, sky.light());
    }

    public void render(
            Matrix4f viewProjection,
            List<MobRenderState> mobs,
            double alpha,
            double cameraX,
            double cameraY,
            double cameraZ,
            DistanceFog fog,
            float water) {
        if (closed) throw new IllegalStateException("Mob renderer is closed");
        Objects.requireNonNull(viewProjection);
        Objects.requireNonNull(fog);
        if (mobs.isEmpty()) return;
        boolean depth = glIsEnabled(GL_DEPTH_TEST), mask = glGetBoolean(GL_DEPTH_WRITEMASK);
        boolean blend = glIsEnabled(GL_BLEND), cull = glIsEnabled(GL_CULL_FACE);
        int depthFunction = glGetInteger(GL_DEPTH_FUNC);
        int previousProgram = glGetInteger(GL_CURRENT_PROGRAM), previousVao = glGetInteger(GL_VERTEX_ARRAY_BINDING);
        int previousActiveTexture = glGetInteger(GL_ACTIVE_TEXTURE);
        glActiveTexture(GL_TEXTURE0);
        int previousTexture = glGetInteger(GL_TEXTURE_BINDING_2D);
        int previousSampler = glGetIntegeri(GL_SAMPLER_BINDING, 0);
        try (var stack = MemoryStack.stackPush()) {
            glEnable(GL_DEPTH_TEST);
            glDepthFunc(GL_LEQUAL);
            glDepthMask(true);
            glDisable(GL_BLEND);
            glDisable(GL_CULL_FACE);
            glUseProgram(program);
            glUniform2f(fogRange, fog.start(), fog.end());
            glUniform1f(underwater, water);
            glBindVertexArray(vao);
            glBindSampler(0, 0);
            var buffer = stack.mallocFloat(16);
            glUniformMatrix4fv(vp, false, viewProjection.get(buffer));
            var frustum = new FrustumIntersection(viewProjection);
            var base = new Matrix4f();
            var part = new Matrix4f();
            for (var mob : mobs) {
                float x = (float) (mob.interpolatedX(alpha) - cameraX);
                float y = (float) (mob.interpolatedY(alpha) - cameraY);
                float z = (float) (mob.interpolatedZ(alpha) - cameraZ);
                // A yawed model can reach past its collision box, so test a box as wide as the mob is tall.
                float reach = (float) mob.type().height();
                if (!frustum.testAab(x - reach, y, z - reach, x + reach, y + reach, z + reach)) continue;
                glBindTexture(GL_TEXTURE_2D, textures.get(mob.type()));
                base.translation(x, y, z)
                        .rotateY((float) Math.toRadians(-mob.yaw()))
                        .scale(1f / 16);
                var mobModel = MobModels.of(mob.type());
                int first = firstVertex.get(mob.type());
                for (int i = 0; i < mobModel.parts().size(); i++) {
                    mobModel.parts().get(i).pivot().apply(base.get(part));
                    glUniformMatrix4fv(model, false, part.get(buffer));
                    glDrawArrays(GL_TRIANGLES, first + i * CuboidMesh.VERTEX_COUNT, CuboidMesh.VERTEX_COUNT);
                }
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

    private float[] vertices() {
        int partFloats = CuboidMesh.VERTEX_COUNT * CuboidMesh.FLOATS_PER_VERTEX;
        int total = 0;
        for (var type : MobType.values()) total += MobModels.of(type).parts().size();
        var vertices = new float[total * partFloats];
        int part = 0;
        for (var type : MobType.values()) {
            MobModel mobModel = MobModels.of(type);
            firstVertex.put(type, part * CuboidMesh.VERTEX_COUNT);
            for (var definition : mobModel.parts()) {
                var mesh = CuboidMesh.vertices(
                        definition.cuboid(), definition.uv(), mobModel.textureWidth(), mobModel.textureHeight());
                System.arraycopy(mesh, 0, vertices, part++ * partFloats, partFloats);
            }
        }
        return vertices;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        textures.values().forEach(texture -> glDeleteTextures(texture));
        glDeleteBuffers(vbo);
        glDeleteVertexArrays(vao);
        glDeleteProgram(program);
    }
}
