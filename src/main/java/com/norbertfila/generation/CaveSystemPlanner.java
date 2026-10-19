package com.norbertfila.generation;

import com.norbertfila.generation.CaveSystem.Point;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.function.IntBinaryOperator;

final class CaveSystemPlanner {
    private final SplittableRandom random;
    private final CaveConfig config;
    private final int bedrockY;
    private final CaveRegionSampler.Sample regional;
    private final IntBinaryOperator surfaceHeight;
    private final List<Point> nodes = new ArrayList<>();
    private final List<CaveSystem.Route> routes = new ArrayList<>();
    private final List<CaveSystem.Flood> floods = new ArrayList<>();
    private final List<CavePlan.Segment> segments = new ArrayList<>();

    CaveSystemPlanner(
            SplittableRandom random,
            CaveConfig config,
            int bedrockY,
            CaveRegionSampler.Sample regional,
            IntBinaryOperator surfaceHeight) {
        this.random = random;
        this.config = config;
        this.bedrockY = bedrockY;
        this.regional = regional;
        this.surfaceHeight = surfaceHeight;
    }

    Result plan(double x, double y, double z, CaveProfile character) {
        double yaw = random.nextDouble(Math.PI * 2);
        node(new Point(x, y, z));
        double length = random.nextInt(config.minLength(), config.maxLength() + 1);
        for (int i = 0; i < 3; i++) {
            yaw += random.nextDouble(-.65, .65);
            int end = node(offset(nodes.get(i), yaw, length / 3, random.nextDouble(-2.5, 2.5)));
            route(i, end, character, 0);
        }
        double side = yaw + (random.nextBoolean() ? 1 : -1) * Math.PI / 2;
        if (character == CaveProfile.SPAGHETTI || character == CaveProfile.INTERCONNECTED) {
            int a = node(offset(nodes.get(1), side, random.nextDouble(11, 17), -2));
            int b = node(offset(nodes.get(2), side, random.nextDouble(11, 17), 1));
            route(1, a, character, 0);
            route(a, b, character, 0);
            route(b, 2, character, 0);
        } else if (random.nextDouble() < .7) {
            int end = node(offset(nodes.get(1), side, random.nextDouble(10, 22), random.nextDouble(-2, 2)));
            route(1, end, CaveProfile.CLASSIC, 0);
        }
        if (character == CaveProfile.INTERCONNECTED) {
            int a = node(offset(nodes.get(1), side + Math.PI, 18, -9));
            int b = node(offset(nodes.get(2), side + Math.PI, 18, -9));
            route(1, a, CaveProfile.VERTICAL, 0);
            route(a, b, CaveProfile.INTERCONNECTED, 0);
            route(b, 2, CaveProfile.VERTICAL, 0);
            entrance(0, yaw + Math.PI);
            entrance(3, yaw);
        } else if (random.nextDouble() < .07 && y > bedrockY + 22) {
            int lower = node(offset(nodes.get(2), side, 22, -10));
            int end = node(offset(nodes.get(3), side, 22, -10));
            route(2, lower, CaveProfile.VERTICAL, 0);
            route(lower, end, CaveProfile.CLASSIC, 0);
        }
        if (random.nextDouble() < .55) chamber(nodes.get(1 + random.nextInt(2)), yaw);
        if (random.nextDouble() < .28 && y > bedrockY + 14) {
            int bank = node(offset(nodes.get(1), side + Math.PI, 9, 0));
            int farBank = node(offset(nodes.get(bank), side + Math.PI, 11, 0));
            route(1, bank, CaveProfile.CLASSIC, 0);
            var wet = route(bank, farBank, CaveProfile.FLOODED, 2.8);
            floods.add(new CaveSystem.Flood(
                    wet.get(wet.size() / 2), (int) Math.floor(nodes.get(bank).y() - 2.2)));
            int end = node(offset(nodes.get(farBank), yaw, 8, 1));
            route(farBank, end, CaveProfile.CLASSIC, 0);
        }
        return new Result(new CaveSystem(character, nodes, routes, floods), List.copyOf(segments));
    }

    private int node(Point point) {
        nodes.add(new Point(point.x(), Math.max(bedrockY + 7, point.y()), point.z()));
        return nodes.size() - 1;
    }

    private void entrance(int junction, double yaw) {
        var position = offset(nodes.get(junction), yaw, 18, 0);
        if (position.x() < Integer.MIN_VALUE
                || position.x() > Integer.MAX_VALUE
                || position.z() < Integer.MIN_VALUE
                || position.z() > Integer.MAX_VALUE) return;
        int height = surfaceHeight.applyAsInt((int) Math.floor(position.x()), (int) Math.floor(position.z()));
        if (height < position.y() || height - position.y() > 12) return;
        int end = node(new Point(position.x(), height + .5, position.z()));
        route(junction, end, CaveProfile.CLASSIC, 0);
    }

    private List<Point> route(int from, int to, CaveProfile profile, double depression) {
        Point a = nodes.get(from), b = nodes.get(to);
        double dx = b.x() - a.x(), dz = b.z() - a.z();
        double distance = Math.hypot(dx, dz);
        int steps = Math.max(2, (int) Math.ceil(Math.hypot(distance, b.y() - a.y()) * 1.5));
        double bend = random.nextDouble(-1, 1) * Math.min(distance * .22, 5) * regional.winding();
        double phase = random.nextDouble(Math.PI * 2);
        double width = Math.clamp(
                config.minRadius()
                        + regional.width() * (config.maxRadius() - config.minRadius())
                        + random.nextDouble(-.3, .3),
                config.minRadius(),
                config.maxRadius());
        var points = new ArrayList<Point>();
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps, envelope = Math.sin(Math.PI * t);
            double twist = profile == CaveProfile.SPAGHETTI ? 2.2 : .65;
            double lateral = envelope * (bend + Math.sin(t * Math.PI * 2 + phase) * twist);
            double y = a.y()
                    + (b.y() - a.y()) * t
                    + envelope * (profile == CaveProfile.VERTICAL ? 0 : Math.sin(t * Math.PI * 2) * .65)
                    - depression * envelope * envelope;
            var point =
                    new Point(a.x() + dx * t - dz / distance * lateral, y, a.z() + dz * t + dx / distance * lateral);
            points.add(point);
            double radius = Math.clamp(
                    width + Math.sin(t * Math.PI * 3 + phase) * .35,
                    config.minRadius(),
                    profile == CaveProfile.SPAGHETTI
                            ? Math.max(config.minRadius(), Math.min(2, config.maxRadius()))
                            : config.maxRadius());
            double rz = Math.clamp(
                    radius * (.9 + .15 * Math.sin(t * Math.PI * 2 + phase)), config.minRadius(), config.maxRadius());
            segments.add(new CavePlan.Segment(
                    point.x(),
                    point.y(),
                    point.z(),
                    radius,
                    Math.max(1.85, radius * .9),
                    rz,
                    profile,
                    profile == CaveProfile.SPAGHETTI ? .35 : .25));
        }
        routes.add(new CaveSystem.Route(from, to, profile, points));
        return points;
    }

    private void chamber(Point point, double yaw) {
        double radius = random.nextDouble(3.2, 4.9);
        for (int lobe = 0; lobe < 3; lobe++) {
            double offset = lobe - 1;
            segments.add(new CavePlan.Segment(
                    point.x() + Math.cos(yaw) * offset * 1.2,
                    point.y() + random.nextDouble(-.4, .6),
                    point.z() + Math.sin(yaw) * offset * 1.2,
                    radius * random.nextDouble(.8, 1),
                    random.nextDouble(2.3, 3.15),
                    radius * random.nextDouble(.65, 1.05),
                    CaveProfile.CHAMBER,
                    .6));
        }
    }

    private static Point offset(Point origin, double yaw, double length, double dy) {
        return new Point(origin.x() + Math.cos(yaw) * length, origin.y() + dy, origin.z() + Math.sin(yaw) * length);
    }

    record Result(CaveSystem system, List<CavePlan.Segment> segments) {}
}
