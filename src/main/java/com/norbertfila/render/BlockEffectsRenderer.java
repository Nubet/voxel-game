package com.norbertfila.render;

import static org.lwjgl.opengl.GL46C.*;

import com.norbertfila.interaction.BlockBreakEvent;
import com.norbertfila.interaction.BlockBreaking;
import java.nio.FloatBuffer;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

public final class BlockEffectsRenderer implements AutoCloseable {
    private final BlockTextureArray textures;
    private final int cracks, debris, vao, vbo;
    private final int crackVP, crackOffset, crackProgress, crackHeight, crackWidth, debrisVP;
    private final int daylight;
    private FloatBuffer particles;
    private boolean closed;

    public BlockEffectsRenderer(BlockTextureArray textures) {
        this.textures = Objects.requireNonNull(textures);
        int createdCracks = 0, createdDebris = 0, createdVao = 0, createdVbo = 0;
        try {
            createdCracks = ShaderPrograms.create("/shaders/block_cracks.vert", "/shaders/block_cracks.frag");
            createdDebris = ShaderPrograms.create("/shaders/block_debris.vert", "/shaders/block_debris.frag");
            crackVP = ShaderPrograms.uniform(createdCracks, "uVP");
            crackOffset = ShaderPrograms.uniform(createdCracks, "uOffset");
            crackProgress = ShaderPrograms.uniform(createdCracks, "uProgress");
            crackHeight = ShaderPrograms.uniform(createdCracks, "uHeight");
            crackWidth = ShaderPrograms.uniform(createdCracks, "uWidth");
            debrisVP = ShaderPrograms.uniform(createdDebris, "uVP");
            daylight = ShaderPrograms.uniform(createdDebris, "uDaylight");
            glProgramUniform1i(createdDebris, ShaderPrograms.uniform(createdDebris, "uBlockTextures"), 0);
            createdVao = glCreateVertexArrays();
            createdVbo = glCreateBuffers();
            glVertexArrayVertexBuffer(createdVao, 0, createdVbo, 0, BlockDebris.FLOATS_PER_PARTICLE * Float.BYTES);
            int[] sizes = {3, 2, 1, 1, 1};
            int offset = 0;
            for (int attribute = 0; attribute < sizes.length; attribute++) {
                glVertexArrayAttribFormat(
                        createdVao, attribute, sizes[attribute], GL_FLOAT, false, offset * Float.BYTES);
                glVertexArrayAttribBinding(createdVao, attribute, 0);
                glEnableVertexArrayAttrib(createdVao, attribute);
                offset += sizes[attribute];
            }
            glVertexArrayBindingDivisor(createdVao, 0, 1);
        } catch (RuntimeException | Error failure) {
            glDeleteBuffers(createdVbo);
            glDeleteVertexArrays(createdVao);
            glDeleteProgram(createdCracks);
            glDeleteProgram(createdDebris);
            throw failure;
        }
        cracks = createdCracks;
        debris = createdDebris;
        vao = createdVao;
        vbo = createdVbo;
    }

    public void render(
            Matrix4f viewProjection,
            Optional<BlockBreaking.Progress> progress,
            List<BlockBreakEvent> events,
            double seconds,
            double cameraX,
            double cameraY,
            double cameraZ) {
        if (closed) throw new IllegalStateException("Block effects renderer is closed");
        Objects.requireNonNull(viewProjection);
        Objects.requireNonNull(progress);
        Objects.requireNonNull(events);
        if (!Double.isFinite(seconds)
                || seconds < 0
                || !Double.isFinite(cameraX)
                || !Double.isFinite(cameraY)
                || !Double.isFinite(cameraZ))
            throw new IllegalArgumentException("Effect time and camera position must be finite");
        int particleCount = 0;
        if (!events.isEmpty()) {
            int capacity = Math.multiplyExact(
                    events.size(), BlockDebris.PARTICLES_PER_BLOCK * BlockDebris.FLOATS_PER_PARTICLE);
            if (particles == null) particles = MemoryUtil.memAllocFloat(capacity);
            else if (particles.capacity() < capacity) particles = MemoryUtil.memRealloc(particles, capacity);
            particles.clear();
            particleCount = BlockDebris.write(particles, events, seconds, cameraX, cameraY, cameraZ);
            particles.flip();
        }
        if (progress.isEmpty() && particleCount == 0) return;
        boolean depth = glIsEnabled(GL_DEPTH_TEST), depthMask = glGetBoolean(GL_DEPTH_WRITEMASK);
        boolean blend = glIsEnabled(GL_BLEND), cull = glIsEnabled(GL_CULL_FACE);
        int depthFunction = glGetInteger(GL_DEPTH_FUNC);
        int previousProgram = glGetInteger(GL_CURRENT_PROGRAM), previousVao = glGetInteger(GL_VERTEX_ARRAY_BINDING);
        int srcRGB = glGetInteger(GL_BLEND_SRC_RGB), dstRGB = glGetInteger(GL_BLEND_DST_RGB);
        int srcAlpha = glGetInteger(GL_BLEND_SRC_ALPHA), dstAlpha = glGetInteger(GL_BLEND_DST_ALPHA);
        int activeTexture = glGetInteger(GL_ACTIVE_TEXTURE);
        glActiveTexture(GL_TEXTURE0);
        int previousTexture = glGetInteger(GL_TEXTURE_BINDING_2D_ARRAY);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            glEnable(GL_DEPTH_TEST);
            glDepthFunc(GL_LEQUAL);
            glDepthMask(false);
            glDisable(GL_CULL_FACE);
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            glBindVertexArray(vao);
            var matrix = viewProjection.get(stack.mallocFloat(16));
            if (progress.isPresent()) {
                var mining = progress.orElseThrow();
                var block = mining.block();
                glUseProgram(cracks);
                var model = com.norbertfila.world.BlockRegistry.DEFAULT
                        .definition((byte) mining.blockId())
                        .model();
                glUniform1f(crackHeight, model.maxY() - model.minY());
                glUniform2f(crackWidth, model.maxX() - model.minX(), model.maxZ() - model.minZ());
                glUniformMatrix4fv(crackVP, false, matrix);
                glUniform3f(
                        crackOffset,
                        (float) (block.x() - cameraX + model.minX()),
                        (float) (block.y() - cameraY + model.minY()),
                        (float) (block.z() - cameraZ + model.minZ()));
                glUniform1f(crackProgress, Math.min(10, 1 + (int) (mining.fraction() * 10)) / 10f);
                glDrawArrays(GL_TRIANGLES, 0, 36);
            }
            if (particleCount > 0) {
                glUseProgram(debris);
                glUniformMatrix4fv(debrisVP, false, matrix);
                textures.bind(0);
                glNamedBufferData(vbo, particles, GL_STREAM_DRAW);
                glDrawArraysInstanced(GL_TRIANGLES, 0, 6, particleCount);
            }
        } finally {
            glBindTexture(GL_TEXTURE_2D_ARRAY, previousTexture);
            glActiveTexture(activeTexture);
            glBindVertexArray(previousVao);
            glUseProgram(previousProgram);
            glBlendFuncSeparate(srcRGB, dstRGB, srcAlpha, dstAlpha);
            if (blend) glEnable(GL_BLEND);
            else glDisable(GL_BLEND);
            if (cull) glEnable(GL_CULL_FACE);
            else glDisable(GL_CULL_FACE);
            glDepthMask(depthMask);
            glDepthFunc(depthFunction);
            if (depth) glEnable(GL_DEPTH_TEST);
            else glDisable(GL_DEPTH_TEST);
        }
    }

    public void environment(SkyState sky) {
        glProgramUniform1f(debris, daylight, sky.light());
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        MemoryUtil.memFree(particles);
        glDeleteBuffers(vbo);
        glDeleteVertexArrays(vao);
        glDeleteProgram(cracks);
        glDeleteProgram(debris);
    }
}
