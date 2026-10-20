package com.norbertfila.render;

public record SkyState(
        Color zenith, Color horizon, Color clouds, float sunX, float sunY, float light, float twilight, float stars) {
    public record Color(float red, float green, float blue) {
        Color mix(Color other, float amount) {
            return new Color(
                    red + (other.red - red) * amount,
                    green + (other.green - green) * amount,
                    blue + (other.blue - blue) * amount);
        }
    }
}
