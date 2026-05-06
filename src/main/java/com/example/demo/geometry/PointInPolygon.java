package com.example.demo.geometry;

import org.locationtech.jts.geom.*;

import java.util.List;

public class PointInPolygon {
    private static final double EPSILON = 1e-9;

    public static boolean contains(Polygon polygon, Point point) {
        Coordinate coord = point.getCoordinate();

        if (!polygon.getEnvelopeInternal().contains(coord)) {
            return false;
        }

        for (int i = 0; i < polygon.getNumInteriorRing(); i++) {
            if (winding(polygon.getInteriorRingN(i).getCoordinates(), coord, true)) {
                return false;
            }
        }

        return winding(polygon.getExteriorRing().getCoordinates(), coord, false);
    }

    private static boolean winding(Coordinate[] ring, Coordinate p, boolean isHole) {
        int wn = 0;
        int n = ring.length;

        for (int i = 0; i < n; i++) {
            Coordinate a = ring[i];
            Coordinate b = ring[(i + 1) % n];

            if (isSamePoint(a, p) || isSamePoint(b, p) || onSegment(a, b, p)) {
                return !isHole;
            }

            if (Math.abs(a.getY() - b.getY()) < EPSILON) {
                continue;
            }

            if (a.getY() <= p.getY()) {
                if (b.getY() > p.getY() && isLeft(a, b, p)) {
                    wn++;
                }
            } else {
                if (b.getY() <= p.getY() && !isLeft(a, b, p)) {
                    wn--;
                }
            }
        }

        return wn != 0;
    }

    private static boolean isLeft(Coordinate a, Coordinate b, Coordinate p) {
        double abX = b.getX() - a.getX();
        double abY = b.getY() - a.getY();
        double apX = p.getX() - a.getX();
        double apY = p.getY() - a.getY();

        return cross(abX, abY, apX, apY) > 0;
    }

    private static boolean isSamePoint(Coordinate a, Coordinate b) {
        return Math.abs(a.getX() - b.getX()) < EPSILON &&
                Math.abs(a.getY() - b.getY()) < EPSILON;
    }

    private static boolean onSegment(Coordinate a, Coordinate b, Coordinate p) {
        double abX = b.getX() - a.getX();
        double abY = b.getY() - a.getY();
        double apX = p.getX() - a.getX();
        double apY = p.getY() - a.getY();

        if (Math.abs(cross(abX, abY, apX, apY)) > EPSILON) {
            return false;
        }

        return p.getX() >= Math.min(a.getX(), b.getX()) - EPSILON &&
                p.getX() <= Math.max(a.getX(), b.getX()) + EPSILON &&
                p.getY() >= Math.min(a.getY(), b.getY()) - EPSILON &&
                p.getY() <= Math.max(a.getY(), b.getY()) + EPSILON;
    }

    private static double cross(double ax, double ay, double bx, double by) {
        return ax * by - ay * bx;
    }
}