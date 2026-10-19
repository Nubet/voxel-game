package com.norbertfila.generation;

import java.util.List;

record CaveSystem(
        CaveProfile character,
        List<Point> junctions,
        List<Route> routes,
        List<Flood> floods,
        List<Sinkhole> sinkholes) {
    CaveSystem {
        junctions = List.copyOf(junctions);
        routes = List.copyOf(routes);
        floods = List.copyOf(floods);
        sinkholes = List.copyOf(sinkholes);
    }

    CaveSystem(CaveProfile character, List<Point> junctions, List<Route> routes, List<Flood> floods) {
        this(character, junctions, routes, floods, List.of());
    }

    record Point(double x, double y, double z) {}

    record Route(int from, int to, CaveProfile profile, List<Point> points) {
        Route {
            points = List.copyOf(points);
        }
    }

    record Flood(Point seed, int waterLevel) {}

    record Sinkhole(Point underground, Point surface) {}
}
