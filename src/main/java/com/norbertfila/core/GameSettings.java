package com.norbertfila.core;

import com.norbertfila.i18n.Language;
import com.norbertfila.i18n.Translations;
import com.norbertfila.player.visual.model.ModelVariant;
import com.norbertfila.render.CloudMode;
import com.norbertfila.ui.crosshair.CrosshairSettings;
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
    public static final int DEFAULT_RENDER_DISTANCE = 12;
    /** Block updates such as flowing water run only this many chunks from the player, at most the render distance. */
    public static final int MIN_SIMULATION_DISTANCE = 1;

    public static final int DEFAULT_SIMULATION_DISTANCE = 12;
    /** Far terrain radius in chunks; 0 or a value not above the render distance turns it off. */
    public static final int MAX_FAR_DISTANCE = 128;

    public static final int DEFAULT_FAR_DISTANCE = 0;
    public static final int MIN_FRAME_RATE = 30;
    public static final int MAX_FRAME_RATE = 240;
    public static final int UNLIMITED_FRAME_RATE = 0;
    public static final int DEFAULT_FRAME_RATE = 120;
    public static final int GUI_SCALE_AUTO = 0;
    public static final int MIN_GUI_SCALE = 1;
    public static final int MAX_GUI_SCALE = 4;
    public static final int MIN_BRIGHTNESS = 0;
    public static final int MAX_BRIGHTNESS = 100;
    public static final int DEFAULT_BRIGHTNESS = 50;
    public static final int MIN_CONTRAST = 50;
    public static final int MAX_CONTRAST = 150;
    public static final int DEFAULT_CONTRAST = 100;
    /** Display gamma in hundredths, so 100 leaves the image unchanged. */
    public static final int MIN_GAMMA = 50;

    public static final int MAX_GAMMA = 250;
    public static final int DEFAULT_GAMMA = 100;

    private int renderDistance = DEFAULT_RENDER_DISTANCE;
    private int simulationDistance = Math.min(DEFAULT_SIMULATION_DISTANCE, renderDistance);
    private int farDistance = DEFAULT_FAR_DISTANCE;
    private int frameRate = DEFAULT_FRAME_RATE;
    private float fieldOfView = DEFAULT_FIELD_OF_VIEW;
    private float zoomFieldOfView = DEFAULT_ZOOM_FIELD_OF_VIEW;
    private float mouseSensitivity = DEFAULT_MOUSE_SENSITIVITY;
    private float zoomSpeed = DEFAULT_ZOOM_SPEED;
    private int guiScale = GUI_SCALE_AUTO;
    private int brightness = DEFAULT_BRIGHTNESS;
    private int contrast = DEFAULT_CONTRAST;
    private int gamma = DEFAULT_GAMMA;
    private CloudMode clouds = CloudMode.FANCY;
    private CrosshairSettings crosshair = CrosshairSettings.DEFAULT;
    private ModelVariant modelVariant = ModelVariant.CLASSIC;

    public ModelVariant modelVariant() {
        return modelVariant;
    }

    public void setModelVariant(ModelVariant value) {
        modelVariant = Objects.requireNonNull(value);
    }

    public CrosshairSettings crosshair() {
        return crosshair;
    }

    public void setCrosshair(CrosshairSettings value) {
        crosshair = Objects.requireNonNull(value);
    }

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

    public int frameRate() {
        return frameRate;
    }

    public boolean setFrameRate(int value) {
        if (value != UNLIMITED_FRAME_RATE && (value < MIN_FRAME_RATE || value > MAX_FRAME_RATE))
            throw new IllegalArgumentException(
                    "Frame rate must be Unlimited or between " + MIN_FRAME_RATE + " and " + MAX_FRAME_RATE);
        if (frameRate == value) return false;
        frameRate = value;
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

    public int brightness() {
        return brightness;
    }

    public boolean setBrightness(int value) {
        checkRange("Brightness", value, MIN_BRIGHTNESS, MAX_BRIGHTNESS);
        if (brightness == value) return false;
        brightness = value;
        return true;
    }

    public int contrast() {
        return contrast;
    }

    public boolean setContrast(int value) {
        checkRange("Contrast", value, MIN_CONTRAST, MAX_CONTRAST);
        if (contrast == value) return false;
        contrast = value;
        return true;
    }

    public int gamma() {
        return gamma;
    }

    public boolean setGamma(int value) {
        checkRange("Gamma", value, MIN_GAMMA, MAX_GAMMA);
        if (gamma == value) return false;
        gamma = value;
        return true;
    }

    public void resetDisplay() {
        brightness = DEFAULT_BRIGHTNESS;
        contrast = DEFAULT_CONTRAST;
        gamma = DEFAULT_GAMMA;
    }

    private static void checkRange(String name, int value, int min, int max) {
        if (value < min || value > max)
            throw new IllegalArgumentException(name + " must be between " + min + " and " + max);
    }

    private static void validateFieldOfView(float value) {
        if (!Float.isFinite(value) || value < MIN_FIELD_OF_VIEW || value > MAX_FIELD_OF_VIEW)
            throw new IllegalArgumentException(
                    "FOV must be between " + MIN_FIELD_OF_VIEW + " and " + MAX_FIELD_OF_VIEW);
    }
}
