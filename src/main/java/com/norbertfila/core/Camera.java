package com.norbertfila.core;

import java.util.Objects;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public final class Camera {
    private static final float MAX_PITCH = 89f;
    private final Vector3f position = new Vector3f(0, 22, 34);
    private final Vector3f forward = new Vector3f();
    private final GameSettings settings;
    private float yaw = -90f, pitch = -25f;
    private float currentFov;

    public Camera() {
        this(new GameSettings());
    }

    public Camera(GameSettings settings) {
        this.settings = Objects.requireNonNull(settings);
        currentFov = settings.fieldOfView();
    }

    public void updateLook(float deltaX, float deltaY) {
        if (!Float.isFinite(deltaX) || !Float.isFinite(deltaY)) {
            throw new IllegalArgumentException("Mouse movement must be finite");
        }
        float sensitivity = mouseSensitivity();
        yaw += deltaX * sensitivity;
        pitch = Math.max(-MAX_PITCH, Math.min(MAX_PITCH, pitch - deltaY * sensitivity));
    }

    public void updateZoom(boolean held, double deltaSeconds) {
        if (!Double.isFinite(deltaSeconds) || deltaSeconds < 0)
            throw new IllegalArgumentException("Zoom delta must be finite and nonnegative");
        float target = held ? settings.zoomFieldOfView() : settings.fieldOfView();
        float blend = (float) -Math.expm1(-settings.zoomSpeed() * deltaSeconds);
        currentFov += (target - currentFov) * blend;
        if (Math.abs(target - currentFov) < .01f) currentFov = target;
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

    public float fieldOfView() {
        return currentFov;
    }

    public float mouseSensitivity() {
        double current = Math.toRadians(currentFov * .5);
        double base = Math.toRadians(settings.fieldOfView() * .5);
        return settings.mouseSensitivity() * (float) (Math.tan(current) / Math.tan(base));
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
                .perspective((float) Math.toRadians(currentFov), (float) width / Math.max(1, height), 0.1f, 800f)
                .lookAt(position, new Vector3f(position).add(forward), new Vector3f(0, 1, 0));
    }
}
