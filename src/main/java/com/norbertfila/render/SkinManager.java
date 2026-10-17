package com.norbertfila.render;

import static org.lwjgl.opengl.GL46C.*;

import com.norbertfila.assets.PlayerSkin;
import com.norbertfila.assets.SkinLoader;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.lwjgl.system.MemoryStack;

public final class SkinManager implements AutoCloseable {
    public enum State {
        LOADING,
        EXTERNAL,
        FALLBACK,
        ERROR
    }

    public record Status(State state, String source, String diagnostic) {}

    private static final Logger LOG = Logger.getLogger(SkinManager.class.getName());
    private final Thread owner = Thread.currentThread();
    private final ExecutorService worker;
    private final Supplier<SkinLoader.Loaded> loader;
    private CompletableFuture<SkinLoader.Loaded> pending;
    private Status status = new Status(State.LOADING, SkinLoader.EXTERNAL_PATH.toString(), "");
    private byte[] cachedPixels;
    private int texture;
    private boolean closed;

    public SkinManager() {
        this(
                Executors.newSingleThreadExecutor(
                        Thread.ofPlatform().daemon().name("skin-loader").factory()),
                new SkinLoader()::load);
    }

    SkinManager(ExecutorService worker, Supplier<SkinLoader.Loaded> loader) {
        this.worker = Objects.requireNonNull(worker);
        this.loader = Objects.requireNonNull(loader);
        reload();
    }

    public void reload() {
        checkOpen();
        if (pending != null) pending.cancel(false);
        // Only the latest future is polled; an older decode cannot publish over a newer reload.
        pending = CompletableFuture.supplyAsync(loader, worker);
        status = new Status(State.LOADING, status.source(), "");
    }

    public void update() {
        checkOpen();
        if (pending == null || !pending.isDone()) return;
        var completed = pending;
        pending = null;
        try {
            var loaded = completed.join();
            byte[] pixels = loaded.skin().rgba();
            if (!Arrays.equals(cachedPixels, pixels)) {
                int replacement = upload(pixels);
                int previous = texture;
                texture = replacement;
                cachedPixels = pixels;
                glDeleteTextures(previous);
            }
            status = new Status(
                    loaded.diagnostic().isEmpty() ? State.EXTERNAL : State.FALLBACK,
                    loaded.source(),
                    loaded.diagnostic());
            if (!loaded.diagnostic().isEmpty()) LOG.warning("Using bundled skin: " + loaded.diagnostic());
        } catch (CompletionException | IllegalStateException failure) {
            status = new Status(State.ERROR, status.source(), failure.toString());
            LOG.warning("Skin reload failed; keeping previous texture: " + failure);
        }
    }

    public int texture() {
        checkOpen();
        return texture;
    }

    public Status status() {
        return status;
    }

    private static int upload(byte[] pixels) {
        int texture = glCreateTextures(GL_TEXTURE_2D);
        try {
            glTextureStorage2D(texture, 1, GL_RGBA8, PlayerSkin.SIZE, PlayerSkin.SIZE);
            glTextureParameteri(texture, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTextureParameteri(texture, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTextureParameteri(texture, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTextureParameteri(texture, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            int unpackBuffer = glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING);
            int alignment = glGetInteger(GL_UNPACK_ALIGNMENT), rowLength = glGetInteger(GL_UNPACK_ROW_LENGTH);
            int skipRows = glGetInteger(GL_UNPACK_SKIP_ROWS), skipPixels = glGetInteger(GL_UNPACK_SKIP_PIXELS);
            try (var stack = MemoryStack.stackPush()) {
                glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
                glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
                glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);
                glPixelStorei(GL_UNPACK_SKIP_ROWS, 0);
                glPixelStorei(GL_UNPACK_SKIP_PIXELS, 0);
                glTextureSubImage2D(
                        texture,
                        0,
                        0,
                        0,
                        PlayerSkin.SIZE,
                        PlayerSkin.SIZE,
                        GL_RGBA,
                        GL_UNSIGNED_BYTE,
                        stack.bytes(pixels));
                int error = glGetError();
                if (error != GL_NO_ERROR) throw new IllegalStateException("Skin upload GL error: " + error);
            } finally {
                glBindBuffer(GL_PIXEL_UNPACK_BUFFER, unpackBuffer);
                glPixelStorei(GL_UNPACK_ALIGNMENT, alignment);
                glPixelStorei(GL_UNPACK_ROW_LENGTH, rowLength);
                glPixelStorei(GL_UNPACK_SKIP_ROWS, skipRows);
                glPixelStorei(GL_UNPACK_SKIP_PIXELS, skipPixels);
            }
            return texture;
        } catch (RuntimeException | Error failure) {
            glDeleteTextures(texture);
            throw failure;
        }
    }

    private void checkOpen() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("SkinManager requires its GL thread");
        if (closed) throw new IllegalStateException("SkinManager is closed");
    }

    @Override
    public void close() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("SkinManager requires its GL thread");
        if (closed) return;
        closed = true;
        if (pending != null) pending.cancel(false);
        pending = null;
        worker.shutdownNow();
        glDeleteTextures(texture);
        texture = 0;
        cachedPixels = null;
    }
}
