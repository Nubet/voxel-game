package com.norbertfila.render;

import static org.lwjgl.opengl.GL46C.*;

import com.norbertfila.items.ItemRenderState;
import com.norbertfila.world.BlockRegistry;
import com.norbertfila.world.BlockShape;
import java.util.List;
import java.util.Objects;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

public final class ItemEntityRenderer implements AutoCloseable {
    private static final double RENDER_DISTANCE = 64;
    private final BlockTextureArray textures;
    private final Frustum frustum = new Frustum();
    private final int program, vao, vp, offset, angle, layers, cross, height;
    private final int daylight;
    private boolean closed;

    public ItemEntityRenderer(BlockTextureArray textures) {
        this.textures = Objects.requireNonNull(textures);
        int createdProgram = 0, createdVao = 0;
        try {
            createdProgram = ShaderPrograms.create("/shaders/item_entity.vert", "/shaders/item_entity.frag");
            vp = ShaderPrograms.uniform(createdProgram, "uVP");
            offset = ShaderPrograms.uniform(createdProgram, "uOffset");
            angle = ShaderPrograms.uniform(createdProgram, "uAngle");
            layers = ShaderPrograms.uniform(createdProgram, "uLayers");
            cross = ShaderPrograms.uniform(createdProgram, "uCross");
            height = ShaderPrograms.uniform(createdProgram, "uHeight");
            daylight = ShaderPrograms.uniform(createdProgram, "uDaylight");
            glProgramUniform1i(createdProgram, ShaderPrograms.uniform(createdProgram, "uBlockTextures"), 0);
            createdVao = glCreateVertexArrays();
        } catch (RuntimeException | Error failure) {
            glDeleteProgram(createdProgram);
            glDeleteVertexArrays(createdVao);
            throw failure;
        }
        program = createdProgram;
        vao = createdVao;
    }

    public int render(
            Matrix4f viewProjection,
            List<ItemRenderState> items,
            double alpha,
            double cameraX,
            double cameraY,
            double cameraZ) {
        if (closed) throw new IllegalStateException("Item renderer is closed");
        if (!Double.isFinite(alpha)
                || alpha < 0
                || alpha > 1
                || !Double.isFinite(cameraX)
                || !Double.isFinite(cameraY)
                || !Double.isFinite(cameraZ)) throw new IllegalArgumentException("Invalid item render position/time");
        if (items.isEmpty()) return 0;
        frustum.set(viewProjection);
        boolean depth = glIsEnabled(GL_DEPTH_TEST), mask = glGetBoolean(GL_DEPTH_WRITEMASK);
        boolean blend = glIsEnabled(GL_BLEND), cull = glIsEnabled(GL_CULL_FACE);
        int depthFunction = glGetInteger(GL_DEPTH_FUNC);
        int previousProgram = glGetInteger(GL_CURRENT_PROGRAM), previousVao = glGetInteger(GL_VERTEX_ARRAY_BINDING);
        int srcRGB = glGetInteger(GL_BLEND_SRC_RGB), dstRGB = glGetInteger(GL_BLEND_DST_RGB);
        int srcAlpha = glGetInteger(GL_BLEND_SRC_ALPHA), dstAlpha = glGetInteger(GL_BLEND_DST_ALPHA);
        int activeTexture = glGetInteger(GL_ACTIVE_TEXTURE);
        glActiveTexture(GL_TEXTURE0);
        int previousTexture = glGetInteger(GL_TEXTURE_BINDING_2D_ARRAY);
        int drawn = 0;
        try (var stack = MemoryStack.stackPush()) {
            glEnable(GL_DEPTH_TEST);
            glDepthFunc(GL_LEQUAL);
            glDepthMask(true);
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            glDisable(GL_CULL_FACE);
            glUseProgram(program);
            glBindVertexArray(vao);
            textures.bind(0);
            glUniformMatrix4fv(vp, false, viewProjection.get(stack.mallocFloat(16)));
            for (var state : items) {
                var item = state.item();
                double x = state.previousX() + (item.x() - state.previousX()) * alpha - cameraX;
                double y = state.previousY() + (item.y() - state.previousY()) * alpha - cameraY;
                double z = state.previousZ() + (item.z() - state.previousZ()) * alpha - cameraZ;
                if (x * x + y * y + z * z > RENDER_DISTANCE * RENDER_DISTANCE) continue;
                double time = (item.ageTicks() - (state.moving() ? 1 - alpha : 0)) / 60d;
                double phase = item.id() % 1024 * .37;
                y += .12 + Math.sin(time * 3 + phase) * .035;
                if (!frustum.intersects(
                        (float) x - .25f,
                        (float) y - .25f,
                        (float) z - .25f,
                        (float) x + .25f,
                        (float) y + .25f,
                        (float) z + .25f)) continue;
                var block = BlockRegistry.DEFAULT.definition((byte) item.itemId());
                boolean isCross = block.model().shape() == BlockShape.CROSS
                        || block.model().shape().geometryIndex() > 0
                                && block.model().shape().geometryIndex() < 5;
                glUniform3f(offset, (float) x, (float) y, (float) z);
                glUniform1f(angle, (float) (time * 1.5 + phase));
                glUniform3i(layers, block.sideTexture(), block.topTexture(), block.bottomTexture());
                glUniform1i(cross, isCross ? 1 : 0);
                glUniform1f(height, block.model().maxY());
                glDrawArrays(GL_TRIANGLES, 0, isCross ? 12 : 36);
                drawn++;
            }
        } finally {
            glBindTexture(GL_TEXTURE_2D_ARRAY, previousTexture);
            glActiveTexture(activeTexture);
            glUseProgram(previousProgram);
            glBindVertexArray(previousVao);
            glBlendFuncSeparate(srcRGB, dstRGB, srcAlpha, dstAlpha);
            if (blend) glEnable(GL_BLEND);
            else glDisable(GL_BLEND);
            if (cull) glEnable(GL_CULL_FACE);
            else glDisable(GL_CULL_FACE);
            glDepthMask(mask);
            glDepthFunc(depthFunction);
            if (depth) glEnable(GL_DEPTH_TEST);
            else glDisable(GL_DEPTH_TEST);
        }
        return drawn;
    }

    public void environment(SkyState sky) {
        glProgramUniform1f(program, daylight, sky.light());
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        glDeleteProgram(program);
        glDeleteVertexArrays(vao);
    }
}
