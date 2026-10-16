package com.norbertfila.core;

import com.norbertfila.i18n.Language;
import com.norbertfila.i18n.Translations;
import com.norbertfila.render.CloudMode;
import java.util.Objects;

public final class GameSettings {
    private final Translations translations;

    public GameSettings() {
        this(new Translations(Language.ENGLISH));
    }

    public GameSettings(Translations translations) {
        this.translations = Objects.requireNonNull(translations);
    }

    public Translations translations() {
        return translations;
    }

    public static final float MIN_FIELD_OF_VIEW = 30f;
    public static final float MAX_FIELD_OF_VIEW = 110f;
    public static final float MIN_ZOOM_FIELD_OF_VIEW = 5f;
    public static final float DEFAULT_FIELD_OF_VIEW = 75f;
    public static final float DEFAULT_ZOOM_FIELD_OF_VIEW = 20f;
    public static final float DEFAULT_MOUSE_SENSITIVITY = .11f;
    public static final float DEFAULT_ZOOM_SPEED = 12f;
    public static final int MIN_RENDER_DISTANCE = 2;
    public static final int MAX_RENDER_DISTANCE = 32;
    /** Block updates such as flowing water run only this many chunks from the player, at most the render distance. */
    public static final int MIN_SIMULATION_DISTANCE = 1;

    public static final int DEFAULT_SIMULATION_DISTANCE = 6;
    /** Far terrain radius in chunks; 0 or a value not above the render distance turns it off. */
    public static final int MAX_FAR_DISTANCE = 128;

    public static final int DEFAULT_FAR_DISTANCE = 64;
    public static final int GUI_SCALE_AUTO = 0;
    public static final int MIN_GUI_SCALE = 1;
    public static final int MAX_GUI_SCALE = 4;

    private int renderDistance = 4;
    private int simulationDistance = Math.min(DEFAULT_SIMULATION_DISTANCE, renderDistance);
    private int farDistance = DEFAULT_FAR_DISTANCE;
    private float fieldOfView = DEFAULT_FIELD_OF_VIEW;
    private float zoomFieldOfView = DEFAULT_ZOOM_FIELD_OF_VIEW;
    private float mouseSensitivity = DEFAULT_MOUSE_SENSITIVITY;
    private float zoomSpeed = DEFAULT_ZOOM_SPEED;
    private int guiScale = GUI_SCALE_AUTO;
    private CloudMode clouds = CloudMode.FAST;

    public CloudMode clouds() {
        return clouds;
    }

    public void setClouds(CloudMode mode) {
        clouds = Objects.requireNonNull(mode);
    }

    public int renderDistance() {
        return renderDistance;
    }

    public boolean setRenderDistance(int value) {
        if (value < MIN_RENDER_DISTANCE || value > MAX_RENDER_DISTANCE) {
            throw new IllegalArgumentException(
                    "Render distance must be between " + MIN_RENDER_DISTANCE + " and " + MAX_RENDER_DISTANCE);
        }
        if (renderDistance == value) return false;
        renderDistance = value;
        if (simulationDistance > renderDistance) simulationDistance = renderDistance;
        return true;
    }

    public int simulationDistance() {
        return simulationDistance;
    }

    public boolean setSimulationDistance(int value) {
        if (value < MIN_SIMULATION_DISTANCE || value > renderDistance)
            throw new IllegalArgumentException(
                    "Simulation distance must be between " + MIN_SIMULATION_DISTANCE + " and the render distance");
        if (simulationDistance == value) return false;
        simulationDistance = value;
        return true;
    }

    public int farDistance() {
        return farDistance;
    }

    public boolean setFarDistance(int value) {
        if (value < 0 || value > MAX_FAR_DISTANCE)
            throw new IllegalArgumentException("Far distance must be between 0 and " + MAX_FAR_DISTANCE);
        if (farDistance == value) return false;
        farDistance = value;
        return true;
    }

    public float fieldOfView() {
        return fieldOfView;
    }

    public boolean setFieldOfView(float value) {
        validateFieldOfView(value);
        if (fieldOfView == value) return false;
        fieldOfView = value;
        if (zoomFieldOfView > fieldOfView) zoomFieldOfView = fieldOfView;
        return true;
    }

    public float zoomFieldOfView() {
        return zoomFieldOfView;
    }

    public boolean setZoomFieldOfView(float value) {
        if (!Float.isFinite(value) || value < MIN_ZOOM_FIELD_OF_VIEW || value > MAX_FIELD_OF_VIEW)
            throw new IllegalArgumentException(
                    "Zoom FOV must be between " + MIN_ZOOM_FIELD_OF_VIEW + " and " + MAX_FIELD_OF_VIEW);
        if (value > fieldOfView) throw new IllegalArgumentException("Zoom FOV cannot exceed base FOV");
        if (zoomFieldOfView == value) return false;
        zoomFieldOfView = value;
        return true;
    }

    public float mouseSensitivity() {
        return mouseSensitivity;
    }

    public boolean setMouseSensitivity(float value) {
        if (!Float.isFinite(value) || value <= 0)
            throw new IllegalArgumentException("Mouse sensitivity must be positive");
        if (mouseSensitivity == value) return false;
        mouseSensitivity = value;
        return true;
    }

    public float zoomSpeed() {
        return zoomSpeed;
    }

    public boolean setZoomSpeed(float value) {
        if (!Float.isFinite(value) || value <= 0) throw new IllegalArgumentException("Zoom speed must be positive");
        if (zoomSpeed == value) return false;
        zoomSpeed = value;
        return true;
    }

    public int guiScale() {
        return guiScale;
    }

    public boolean setGuiScale(int value) {
        if (value != GUI_SCALE_AUTO && (value < MIN_GUI_SCALE || value > MAX_GUI_SCALE))
            throw new IllegalArgumentException("GUI scale must be Auto or between 1 and 4");
        if (guiScale == value) return false;
        guiScale = value;
        return true;
    }

    private static void validateFieldOfView(float value) {
        if (!Float.isFinite(value) || value < MIN_FIELD_OF_VIEW || value > MAX_FIELD_OF_VIEW)
            throw new IllegalArgumentException(
                    "FOV must be between " + MIN_FIELD_OF_VIEW + " and " + MAX_FIELD_OF_VIEW);
    }
}
