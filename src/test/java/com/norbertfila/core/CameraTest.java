package com.norbertfila.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void frameTimeMouseLookChangesOnlyOrientationAndClampsPitch() {
        Camera camera = new Camera();
        camera.setPosition(-16.5f, 20.62f, 1);
        Vector3f before = new Vector3f(camera.position());

        camera.updateLook(10, -2000);

        assertEquals(before, camera.position());
        assertEquals(-88.9f, camera.yaw(), 1e-5);
        assertEquals(89, camera.pitch());
        camera.updateLook(0, 2000);
        assertEquals(-89, camera.pitch());
    }

    @Test
    void zoomChangesFovSmoothlyAndRestoresItAfterRelease() {
        Camera camera = new Camera();

        camera.updateZoom(true, 1.0 / 60);
        assertTrue(camera.fieldOfView() < 75 && camera.fieldOfView() > 20);
        for (int i = 0; i < 120; i++) camera.updateZoom(true, 1.0 / 60);
        assertEquals(20, camera.fieldOfView(), .01);
        for (int i = 0; i < 120; i++) camera.updateZoom(false, 1.0 / 60);
        assertEquals(75, camera.fieldOfView(), .01);
    }

    @Test
    void zoomTransitionIsIndependentOfFrameRate() {
        Camera at60Hz = new Camera();
        Camera at240Hz = new Camera();

        for (int i = 0; i < 60; i++) at60Hz.updateZoom(true, 1.0 / 60);
        for (int i = 0; i < 240; i++) at240Hz.updateZoom(true, 1.0 / 240);

        assertEquals(at60Hz.fieldOfView(), at240Hz.fieldOfView(), .02);
    }

    @Test
    void mouseSensitivityFollowsCurrentFov() {
        Camera camera = new Camera();
        float normal = camera.mouseSensitivity();

        camera.updateZoom(true, .2);

        assertTrue(camera.mouseSensitivity() < normal);
    }

    @Test
    void rejectsInvalidZoomDeltaWithoutChangingFov() {
        Camera camera = new Camera();
        float before = camera.fieldOfView();

        assertThrows(IllegalArgumentException.class, () -> camera.updateZoom(true, -1));
        assertEquals(before, camera.fieldOfView());
    }
}
