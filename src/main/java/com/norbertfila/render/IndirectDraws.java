package com.norbertfila.render;

import static org.lwjgl.opengl.GL46C.*;

import java.nio.IntBuffer;
import org.lwjgl.system.MemoryUtil;

/**
 * GPU side of {@link DrawCommands}: the indirect command buffer, the per-draw origin buffer and their submission,
 * either as one multi-draw call per run or, for A/B checks, one direct draw per command.
 */
final class IndirectDraws implements AutoCloseable {
    private final boolean multiDraw;
    private final int indirectBufferId = glGenBuffers();
    private final int chunkBufferId = glGenBuffers();
    private IntBuffer commandStaging = MemoryUtil.memAllocInt(1024);
    private IntBuffer chunkStaging = MemoryUtil.memAllocInt(1024);

    IndirectDraws(boolean multiDraw) {
        this.multiDraw = multiDraw;
    }

    /** Uploads the commands when they changed since the last frame and binds both buffers. */
    void bind(DrawCommands commands) {
        if (commands.changedSinceLastUpload()) {
            int ints = commands.draws() * DrawCommands.COMMAND_INTS;
            commandStaging =
                    upload(GL_DRAW_INDIRECT_BUFFER, indirectBufferId, commandStaging, commands.commands(), ints);
            chunkStaging = upload(GL_SHADER_STORAGE_BUFFER, chunkBufferId, chunkStaging, commands.chunks(), ints);
        }
        glBindBuffer(GL_DRAW_INDIRECT_BUFFER, indirectBufferId);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 1, chunkBufferId);
    }

    /** Copies the first {@code ints} values of {@code data}; orphaning the buffer avoids waiting on draws in flight. */
    private static IntBuffer upload(int target, int glBuffer, IntBuffer staging, int[] data, int ints) {
        IntBuffer buffer = staging.capacity() >= ints ? staging.clear() : MemoryUtil.memRealloc(staging, ints * 2);
        buffer.put(data, 0, ints).flip();
        glBindBuffer(target, glBuffer);
        glBufferData(target, buffer, GL_STREAM_DRAW);
        return buffer;
    }

    /** Issues every run of {@code pass}; returns the number of GL draw calls. */
    int submit(DrawCommands commands, int pass) {
        int calls = 0;
        for (int run = 0; run < commands.runs(); run++) {
            if (commands.runPass(run) != pass) continue;
            calls += submitRun(commands, run);
        }
        return calls;
    }

    int submitRun(DrawCommands commands, int run) {
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, commands.runBuffer(run));
        int start = commands.runStart(run), count = commands.runCount(run);
        if (multiDraw) {
            glMultiDrawArraysIndirect(GL_TRIANGLES, (long) start * DrawCommands.COMMAND_INTS * Integer.BYTES, count, 0);
            return 1;
        }
        int[] data = commands.commands();
        for (int draw = start; draw < start + count; draw++) {
            int command = draw * DrawCommands.COMMAND_INTS;
            glDrawArraysInstancedBaseInstance(GL_TRIANGLES, data[command + 2], data[command], 1, data[command + 3]);
        }
        return count;
    }

    @Override
    public void close() {
        glDeleteBuffers(indirectBufferId);
        glDeleteBuffers(chunkBufferId);
        MemoryUtil.memFree(commandStaging);
        MemoryUtil.memFree(chunkStaging);
    }
}
