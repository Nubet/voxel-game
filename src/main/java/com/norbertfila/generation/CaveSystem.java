package com.norbertfila.generation;

import java.util.List;

record CaveSystem(CaveProfile character, List<Point> junctions, List<Route> routes, List<Flood> floods) {
    CaveSystem {
        junctions = List.copyOf(junctions);
        routes = List.copyOf(routes);
        floods = List.copyOf(floods);
    }

    record Point(double x, double y, double z) {}

    record Route(int from, int to, CaveProfile profile, List<Point> points) {
        Route {
            points = List.copyOf(points);
        }
    }

    record Flood(Point seed, int waterLevel) {}
}
