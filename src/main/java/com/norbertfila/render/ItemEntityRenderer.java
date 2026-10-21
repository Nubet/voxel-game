package com.norbertfila.render;

import static org.lwjgl.opengl.GL46C.*;

import com.norbertfila.assets.AssetLoader;
import com.norbertfila.items.ItemRenderState;
import com.norbertfila.world.BlockRegistry;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

public final class ItemEntityRenderer implements AutoCloseable {
    private static final double RENDER_DISTANCE = 64;
    private final BlockTextureArray textures;
    private final BlockTextureArray tools;
    private final Frustum frustum = new Frustum();
    private final int program, vao, vp, offset, angle, layers, shape, itemTexture, height, held, model;
    private final int daylight, blockLight;
    private boolean closed;

    public ItemEntityRenderer(BlockTextureArray textures) {
        this.textures = Objects.requireNonNull(textures);
        int createdProgram = 0, createdVao = 0;
        BlockTextureArray createdTools = null;
        try {
            createdTools = new BlockTextureArray(new AssetLoader(BlockRegistry.DEFAULT).loadToolTextures());
            createdProgram = ShaderPrograms.create("/shaders/item_entity.vert", "/shaders/item_entity.frag");
            vp = ShaderPrograms.uniform(createdProgram, "uVP");
            offset = ShaderPrograms.uniform(createdProgram, "uOffset");
            angle = ShaderPrograms.uniform(createdProgram, "uAngle");
            layers = ShaderPrograms.uniform(createdProgram, "uLayers");
            shape = ShaderPrograms.uniform(createdProgram, "uShape");
            itemTexture = ShaderPrograms.uniform(createdProgram, "uItemTexture");
            height = ShaderPrograms.uniform(createdProgram, "uHeight");
            held = ShaderPrograms.uniform(createdProgram, "uHeld");
            model = ShaderPrograms.uniform(createdProgram, "uModel");
            daylight = ShaderPrograms.uniform(createdProgram, "uDaylight");
            blockLight = ShaderPrograms.uniform(createdProgram, "uBlockLight");
            glProgramUniform1i(createdProgram, ShaderPrograms.uniform(createdProgram, "uBlockTextures"), 0);
            glProgramUniform1i(createdProgram, ShaderPrograms.uniform(createdProgram, "uItemTextures"), 1);
            createdVao = glCreateVertexArrays();
        } catch (RuntimeException | Error failure) {
            if (createdTools != null) createdTools.close();
            glDeleteProgram(createdProgram);
            glDeleteVertexArrays(createdVao);
            throw failure;
        }
        tools = createdTools;
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
        int[] drawn = {0};
        draw(viewProjection, false, 0, stack -> {
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
                glUniform3f(offset, (float) x, (float) y, (float) z);
                glUniform1f(angle, (float) (time * 1.5 + phase));
                drawModel(ItemModel.dropped((byte) item.itemId()));
                drawn[0]++;
            }
        });
        return drawn[0];
    }

    /** Draws an item placed over unit space by {@code itemToView}, lit like the player who holds it. */
    public void renderHeld(Matrix4f projection, Matrix4f itemToView, byte item, int blockLightLevel) {
        if (closed) throw new IllegalStateException("Item renderer is closed");
        Objects.requireNonNull(itemToView);
        draw(projection, true, blockLightLevel, stack -> {
            glUniformMatrix4fv(model, false, itemToView.get(stack.mallocFloat(16)));
            drawModel(ItemModel.held(item));
        });
    }

    private void drawModel(ItemModel item) {
        glUniform1i(shape, item.shape().ordinal());
        glUniform1i(itemTexture, item.itemTexture() ? 1 : 0);
        glUniform3i(layers, item.side(), item.top(), item.bottom());
        glUniform1f(height, item.height());
        glDrawArrays(GL_TRIANGLES, 0, item.shape().vertices);
    }

    private void draw(Matrix4f viewProjection, boolean heldItem, int blockLightLevel, Consumer<MemoryStack> body) {
        boolean depth = glIsEnabled(GL_DEPTH_TEST), mask = glGetBoolean(GL_DEPTH_WRITEMASK);
        boolean blend = glIsEnabled(GL_BLEND), cull = glIsEnabled(GL_CULL_FACE);
        int depthFunction = glGetInteger(GL_DEPTH_FUNC);
        int previousProgram = glGetInteger(GL_CURRENT_PROGRAM), previousVao = glGetInteger(GL_VERTEX_ARRAY_BINDING);
        int srcRGB = glGetInteger(GL_BLEND_SRC_RGB), dstRGB = glGetInteger(GL_BLEND_DST_RGB);
        int srcAlpha = glGetInteger(GL_BLEND_SRC_ALPHA), dstAlpha = glGetInteger(GL_BLEND_DST_ALPHA);
        int activeTexture = glGetInteger(GL_ACTIVE_TEXTURE);
        glActiveTexture(GL_TEXTURE1);
        int previousItemTexture = glGetInteger(GL_TEXTURE_BINDING_2D_ARRAY);
        glActiveTexture(GL_TEXTURE0);
        int previousTexture = glGetInteger(GL_TEXTURE_BINDING_2D_ARRAY);
        try (var stack = MemoryStack.stackPush()) {
            glEnable(GL_DEPTH_TEST);
            glDepthFunc(GL_LEQUAL);
            glDepthMask(true);
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            glDisable(GL_CULL_FACE);
            glUseProgram(program);
            glBindVertexArray(vao);
            tools.bind(1);
            textures.bind(0);
            glUniformMatrix4fv(vp, false, viewProjection.get(stack.mallocFloat(16)));
            glUniform1i(held, heldItem ? 1 : 0);
            glUniform1f(blockLight, blockLightLevel);
            body.accept(stack);
        } finally {
            glBindTexture(GL_TEXTURE_2D_ARRAY, previousTexture);
            glActiveTexture(GL_TEXTURE1);
            glBindTexture(GL_TEXTURE_2D_ARRAY, previousItemTexture);
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
    }

    public void environment(SkyState sky) {
        glProgramUniform1f(program, daylight, sky.light());
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        tools.close();
        glDeleteProgram(program);
        glDeleteVertexArrays(vao);
    }
}
