package com.norbertfila.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class CameraTest {
    @Test
    void interactionRayMatchesTheRenderedScreenCenter() {
        Camera camera = new Camera();
        camera.setPosition(-12, 9, 16);
        Vector3f target = new Vector3f(camera.position()).fma(4, camera.direction());
        Vector3f projected = camera.viewProjection(1280, 720).transformProject(target);

        assertEquals(0, projected.x(), 1e-5);
        assertEquals(0, projected.y(), 1e-5);
        assertEquals(1, camera.direction().length(), 1e-6);
    }
}
