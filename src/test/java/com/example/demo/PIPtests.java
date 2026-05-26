package com.example.demo;

import com.example.demo.geometry.PointInPolygon;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.*;
import static org.junit.jupiter.api.Assertions.*;

class PointInPolygonTests {

    // проверка пограничных случаев на простом квадрате
    GeometryFactory geometryFactory = new GeometryFactory();

    // Создаём координаты
    Coordinate[] coords = new Coordinate[] {
            new Coordinate(0, 0),
            new Coordinate(10, 0),
            new Coordinate(10, 10),
            new Coordinate(0, 10),
            new Coordinate(0, 0)  // закрываем полигон
    };

    // Создаём полигон
    LinearRing shell = geometryFactory.createLinearRing(coords);
    Polygon square = geometryFactory.createPolygon(shell);

    @Test
    public void simpleOutSquare() {
        Point point = new GeometryFactory().createPoint(new Coordinate(-5, 5));
        assertFalse(PointInPolygon.contains(square, point));
    }

    @Test
    public void simpleInsideSquare() {
        Point point = new GeometryFactory().createPoint(new Coordinate(5, 5));
        assertTrue(PointInPolygon.contains(square, point));
    }

    @Test
    public void nearEdgeInsideSquare() {
        Point point = new GeometryFactory().createPoint(new Coordinate(0.000000002, 5));
        assertTrue(PointInPolygon.contains(square, point));
    }

    @Test
    public void nearVertexInsideSquare() {
        Point point = new GeometryFactory().createPoint(new Coordinate(2e-9, 2e-9));
        assertTrue(PointInPolygon.contains(square, point));
    }

    @Test
    public void nearVertexOutsideSquare() {
        Point point = new GeometryFactory().createPoint(new Coordinate(-2e-9, -2e-9));
        assertFalse(PointInPolygon.contains(square, point));
    }

    @Test
    public void onVertexSquare() {
        Point point = new GeometryFactory().createPoint(new Coordinate(0, 0));
        assertTrue(PointInPolygon.contains(square, point));
    }

    @Test
    public void onEdgeSquare() {
        Point point = new GeometryFactory().createPoint(new Coordinate(10, 5));
        assertTrue(PointInPolygon.contains(square, point));
    }

    private final Polygon selfIntersectingSquare = new GeometryFactory().createPolygon(new Coordinate[]{
            new Coordinate(0, 0),
            new Coordinate(10, 0),
            new Coordinate(10, 10),
            new Coordinate(0, 10),
            new Coordinate(0, 0),
            new Coordinate(10, 10),
            new Coordinate(2, 5),
            new Coordinate(0, 0)
    });

    @Test
    public void simpleOutSelfIntersectedSquare() {
        Point point = new GeometryFactory().createPoint(new Coordinate(-5, 5));
        assertFalse(PointInPolygon.contains(selfIntersectingSquare, point));
    }

    @Test
    public void simpleInSelfIntersectedSquare() {
        Point point = new GeometryFactory().createPoint(new Coordinate(5, 5));
        assertTrue(PointInPolygon.contains(selfIntersectingSquare, point));
    }

    @Test
    public void simpleInSelfIntersectedSquare2() {
        Point point = new GeometryFactory().createPoint(new Coordinate(1, 5));
        assertTrue(PointInPolygon.contains(selfIntersectingSquare, point));
    }

    @Test
    public void simpleInSelfIntersectedSquare3() {
        Point point = new GeometryFactory().createPoint(new Coordinate(3.5, 5));
        assertTrue(PointInPolygon.contains(selfIntersectingSquare, point));
    }

    private final Polygon clockPolygon = new GeometryFactory().createPolygon(new Coordinate[]{
            new Coordinate(0, 0),
            new Coordinate(10, 10),
            new Coordinate(0, 10),
            new Coordinate(10, 0),
            new Coordinate(0, 0)
    });

    @Test
    public void pointInClock() {
        Point point = new GeometryFactory().createPoint(new Coordinate(1, 1));
        assertTrue(PointInPolygon.contains(clockPolygon, point));
    }

    @Test
    public void pointOutClock() {
        Point point = new GeometryFactory().createPoint(new Coordinate(0, 5));
        assertFalse(PointInPolygon.contains(clockPolygon, point));
    }

    @Test
    public void pointOnIntersectClock() {
        Point point = new GeometryFactory().createPoint(new Coordinate(5.0000000001, 5.00000000001));
        assertTrue(PointInPolygon.contains(clockPolygon, point));
    }

    @Test
    public void pointOutIntersectClock() {
        Point point = new GeometryFactory().createPoint(new Coordinate(2.893434321233, 3));
        assertFalse(PointInPolygon.contains(clockPolygon, point));
    }

    // ========================= // POLYGON WITH HOLE // =========================
    private final Polygon polygonWithHole = new GeometryFactory().createPolygon(
            new GeometryFactory().createLinearRing(new Coordinate[]{
                    new Coordinate(0, 0),
                    new Coordinate(10, 0),
                    new Coordinate(10, 10),
                    new Coordinate(0, 10),
                    new Coordinate(0, 0)
            }),
            new LinearRing[]{
                    new GeometryFactory().createLinearRing(new Coordinate[]{
                            new Coordinate(3, 3),
                            new Coordinate(7, 3),
                            new Coordinate(7, 7),
                            new Coordinate(3, 7),
                            new Coordinate(3, 3)
                    })
            }
    );
    @Test
    public void hole_InsideOuter() {
        assertTrue(PointInPolygon.contains(polygonWithHole, new GeometryFactory().createPoint(new Coordinate(1, 1))));
    }

    @Test
    public void hole_InsideHole() {
        assertFalse(PointInPolygon.contains(polygonWithHole, new GeometryFactory().createPoint(new Coordinate(5, 5))));
    }

    @Test
    public void hole_OnHoleEdge() {
        assertTrue(PointInPolygon.contains(polygonWithHole, new GeometryFactory().createPoint(new Coordinate(3, 5))));
    }

    @Test
    public void hole_OnOuterEdge() {
        assertTrue(PointInPolygon.contains(polygonWithHole, new GeometryFactory().createPoint(new Coordinate(0, 5))));
    }

    @Test
    public void hole_BetweenHoleAndOuter() {
        assertTrue(PointInPolygon.contains(polygonWithHole, new GeometryFactory().createPoint(new Coordinate(2.9, 5))));
    }

    @Test
    public void hole_JustInsideHoleBoundary() {
        assertFalse(PointInPolygon.contains(polygonWithHole, new GeometryFactory().createPoint(new Coordinate(3 + 1.5e-9, 5))));
    }

    private final Polygon octagonWithHoles = new GeometryFactory().createPolygon(
            new GeometryFactory().createLinearRing(new Coordinate[]{
                    new Coordinate(2, 0),
                    new Coordinate(8, 0),
                    new Coordinate(10, 2),
                    new Coordinate(10, 8),
                    new Coordinate(8, 10),
                    new Coordinate(2, 10),
                    new Coordinate(0, 8),
                    new Coordinate(0, 2),
                    new Coordinate(2, 0)
            }),
            new LinearRing[]{
                    new GeometryFactory().createLinearRing(new Coordinate[]{
                            new Coordinate(3, 3),
                            new Coordinate(4, 3),
                            new Coordinate(4, 4),
                            new Coordinate(3, 4),
                            new Coordinate(3, 3)
                    }),
                    new GeometryFactory().createLinearRing(new Coordinate[]{
                            new Coordinate(6, 6),
                            new Coordinate(7, 6),
                            new Coordinate(7, 7),
                            new Coordinate(6, 7),
                            new Coordinate(6, 6)
                    })
            }
    );

    @Test
    public void octagon_Inside() {
        assertTrue(PointInPolygon.contains(octagonWithHoles, new GeometryFactory().createPoint(new Coordinate(5, 2))));
    }

    @Test
    public void octagon_InsideSecondArea() {
        assertTrue(PointInPolygon.contains(octagonWithHoles, new GeometryFactory().createPoint(new Coordinate(2, 5))));
    }

    @Test
    public void octagon_InsideHole1() {
        assertFalse(PointInPolygon.contains(octagonWithHoles, new GeometryFactory().createPoint(new Coordinate(3.5, 3.5))));
    }

    @Test
    public void octagon_InsideHole2() {
        assertFalse(PointInPolygon.contains(octagonWithHoles, new GeometryFactory().createPoint(new Coordinate(6.5, 6.5))));
    }

    @Test
    public void octagon_BetweenHoles() {
        assertTrue(PointInPolygon.contains(octagonWithHoles, new GeometryFactory().createPoint(new Coordinate(5, 5))));
    }

    @Test
    public void octagon_Outside() {
        assertFalse(PointInPolygon.contains(octagonWithHoles, new GeometryFactory().createPoint(new Coordinate(-1, 5))));
    }

    @Test
    public void octagon_OnEdge() {
        assertTrue(PointInPolygon.contains(octagonWithHoles, new GeometryFactory().createPoint(new Coordinate(2, 0))));
    }

    @Test
    public void octagon_NearHoleBoundary() {
        assertFalse(PointInPolygon.contains(octagonWithHoles, new GeometryFactory().createPoint(new Coordinate(4 - 2e-9, 3.5))));
    }
}