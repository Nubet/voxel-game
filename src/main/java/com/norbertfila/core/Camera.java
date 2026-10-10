package com.norbertfila.core;

import static org.lwjgl.glfw.GLFW.*;

import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class Camera {
    private final Vector3f position = new Vector3f(0, 22, 34);
    private final Vector3f forward = new Vector3f();
    private final Vector3f right = new Vector3f();
    private float yaw = -90f, pitch = -25f;
    private double lastX, lastY;
    private boolean mouseInitialized;

    public void update(long window, double dt) {
        double[] x = new double[1], y = new double[1];
        glfwGetCursorPos(window, x, y);
        if (!mouseInitialized) {
            lastX = x[0];
            lastY = y[0];
            mouseInitialized = true;
        }
        float dx = (float) (x[0] - lastX), dy = (float) (y[0] - lastY);
        lastX = x[0];
        lastY = y[0];
        yaw += dx * 0.11f;
        pitch = Math.max(-89f, Math.min(89f, pitch - dy * 0.11f));
        getForward();
        forward.cross(0, 1, 0, right).normalize();
        float speed = (glfwGetKey(window, GLFW_KEY_LEFT_CONTROL) == GLFW_PRESS ? 45f : 12f) * (float) Math.min(dt, 0.1);
        if (glfwGetKey(window, GLFW_KEY_W) == GLFW_PRESS) position.fma(speed, forward);
        if (glfwGetKey(window, GLFW_KEY_S) == GLFW_PRESS) position.fma(-speed, forward);
        if (glfwGetKey(window, GLFW_KEY_D) == GLFW_PRESS) position.fma(speed, right);
        if (glfwGetKey(window, GLFW_KEY_A) == GLFW_PRESS) position.fma(-speed, right);
        if (glfwGetKey(window, GLFW_KEY_SPACE) == GLFW_PRESS) position.y += speed;
        if (glfwGetKey(window, GLFW_KEY_LEFT_SHIFT) == GLFW_PRESS) position.y -= speed;
    }

    private void getForward() {
        float yr = (float) Math.toRadians(yaw), pr = (float) Math.toRadians(pitch);
        forward.set((float) (Math.cos(yr) * Math.cos(pr)), (float) Math.sin(pr), (float) (Math.sin(yr) * Math.cos(pr)))
                .normalize();
    }

    public Matrix4f viewProjection(int width, int height) {
        getForward();
        return new Matrix4f()
                .perspective((float) Math.toRadians(75), (float) width / Math.max(1, height), 0.1f, 800f)
                .lookAt(position, new Vector3f(position).add(forward), new Vector3f(0, 1, 0));
    }
}
