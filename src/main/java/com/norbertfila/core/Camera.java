package com.norbertfila.core;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public final class Camera {
    private static final float MOUSE_SENSITIVITY = 0.11f;
    private static final float MAX_PITCH = 89f;
    private final Vector3f position = new Vector3f(0, 22, 34);
    private final Vector3f forward = new Vector3f();
    private float yaw = -90f, pitch = -25f;

    public void updateLook(float deltaX, float deltaY) {
        if (!Float.isFinite(deltaX) || !Float.isFinite(deltaY)) {
            throw new IllegalArgumentException("Mouse movement must be finite");
        }
        yaw += deltaX * MOUSE_SENSITIVITY;
        pitch = Math.max(-MAX_PITCH, Math.min(MAX_PITCH, pitch - deltaY * MOUSE_SENSITIVITY));
    }

    public void setPosition(float x, float y, float z) {
        position.set(x, y, z);
    }

    public float yaw() {
        return yaw;
    }

    public float pitch() {
        return pitch;
    }

    public Vector3fc position() {
        return position;
    }

    public Vector3fc direction() {
        getForward();
        return forward;
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
