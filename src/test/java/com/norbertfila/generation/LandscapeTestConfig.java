package com.norbertfila.generation;

final class LandscapeTestConfig {
    private LandscapeTestConfig() {}

    static LandscapeConfig uniform() {
        var field = new LandscapeConfig.Field(32, 0, .5);
        var range = new LandscapeConfig.Range(1, 1);
        var style = new LandscapeConfig.Style(range, range, range, range, 1, 1, 1);
        return new LandscapeConfig(field, field, field, field, field, style, style);
    }

    static LandscapeConfig region(double classicness) {
        var config = LandscapeConfig.defaults();
        return new LandscapeConfig(
                new LandscapeConfig.Field(1536, 0, classicness),
                config.woodland(),
                config.vegetation(),
                config.flowers(),
                config.relief(),
                config.classic(),
                config.modern());
    }
}
