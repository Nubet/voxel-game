package com.norbertfila.platform;

import static org.lwjgl.glfw.GLFW.*;

import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;

public final class Window implements AutoCloseable {
    private final GLFWErrorCallback errorCallback;
    private long handle;
    private boolean glfwInitialized;
    private boolean glCapabilitiesCreated;
    private boolean closed;

    public Window(int width, int height, String title) {
        GLFWErrorCallback callback = GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) {
            callback.free();
            throw new IllegalStateException("GLFW init failed");
        }
        errorCallback = callback;
        glfwInitialized = true;
        try {
            glfwDefaultWindowHints();
            glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 6);
            glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
            glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
            handle = glfwCreateWindow(width, height, title, 0, 0);
            if (handle == 0) {
                throw new IllegalStateException("Window creation failed (requires OpenGL 4.6)");
            }
            glfwMakeContextCurrent(handle);
            GL.createCapabilities();
            glCapabilitiesCreated = true;
            glfwSwapInterval(0);
            glfwShowWindow(handle);
            glfwSetInputMode(handle, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
        } catch (RuntimeException | Error failure) {
            close();
            throw failure;
        }
    }

    public long handle() {
        return handle;
    }

    public boolean shouldClose() {
        return glfwWindowShouldClose(handle);
    }

    public void requestClose() {
        glfwSetWindowShouldClose(handle, true);
    }

    public void pollEvents() {
        glfwPollEvents();
    }

    public double time() {
        return glfwGetTime();
    }

    public void framebufferSize(int[] width, int[] height) {
        glfwGetFramebufferSize(handle, width, height);
    }

    public void swapBuffers() {
        glfwSwapBuffers(handle);
    }

    public void setCursorCaptured(boolean captured) {
        glfwSetInputMode(handle, GLFW_CURSOR, captured ? GLFW_CURSOR_DISABLED : GLFW_CURSOR_NORMAL);
    }

    public void setTitle(String title) {
        glfwSetWindowTitle(handle, title);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (glCapabilitiesCreated) {
            GL.destroy();
            glCapabilitiesCreated = false;
        }
        if (handle != 0) {
            glfwMakeContextCurrent(0);
            glfwDestroyWindow(handle);
            handle = 0;
        }
        if (glfwInitialized) {
            glfwTerminate();
            glfwInitialized = false;
        }
        errorCallback.free();
    }
}
