package com.norbertfila.core;

import com.norbertfila.platform.Input;
import com.norbertfila.world.BlockPos;
import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.Coordinates;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public final class Camera {
    private static final float MOUSE_SENSITIVITY = 0.11f;
    private static final float WALK_SPEED = 12f;
    private static final float SPRINT_SPEED = 45f;
    private static final float MAX_PITCH = 89f;
    public static final float PLAYER_EYE_HEIGHT = 1.62f;
    private final Vector3f position = new Vector3f(0, 22, 34);
    private final Vector3f forward = new Vector3f();
    private final Vector3f right = new Vector3f();
    private float yaw = -90f, pitch = -25f;

    public void update(Input input, double dt) {
        updateLook(input);
        move(input, dt);
    }

    public void updateLook(Input input) {
        yaw += input.mouseDeltaX() * MOUSE_SENSITIVITY;
        pitch = Math.max(-MAX_PITCH, Math.min(MAX_PITCH, pitch - input.mouseDeltaY() * MOUSE_SENSITIVITY));
    }

    private void move(Input input, double dt) {
        getForward();
        forward.cross(0, 1, 0, right).normalize();
        float speed = (input.sprint() ? SPRINT_SPEED : WALK_SPEED) * (float) dt;
        if (input.moveForward()) position.fma(speed, forward);
        if (input.moveBackward()) position.fma(-speed, forward);
        if (input.moveRight()) position.fma(speed, right);
        if (input.moveLeft()) position.fma(-speed, right);
        if (input.moveUp()) position.y += speed;
        if (input.moveDown()) position.y -= speed;
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

    public ChunkPos chunkPosition() {
        return Coordinates.worldToChunk(
                new BlockPos((int) Math.floor(position.x), (int) Math.floor(position.y), (int) Math.floor(position.z)));
    }
}
