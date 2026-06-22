Here's the code with all comments removed:

```java
package com.example.demo.indexing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.*;

import static org.junit.jupiter.api.Assertions.*;

class GridIndexTest {

    private GridIndex index;
    private GeometryFactory gf;

    @BeforeEach
    void setUp() {
        index = new GridIndex(50);
        gf = new GeometryFactory();
    }

    private Polygon rect(double x0, double y0, double x1, double y1) {
        return gf.createPolygon(new Coordinate[]{
                new Coordinate(x0, y0), new Coordinate(x1, y0),
                new Coordinate(x1, y1), new Coordinate(x0, y1),
                new Coordinate(x0, y0)
        });
    }

    private Point pt(double x, double y) {
        return gf.createPoint(new Coordinate(x, y));
    }

    @Test
    void insert_andContains_pointInside() {
        Polygon poly = rect(10, 10, 40, 40);
        index.insert(poly, 1);

        assertTrue(index.contains(pt(25, 25), 1));
    }

    @Test
    void insert_andContains_pointOutside() {
        Polygon poly = rect(10, 10, 40, 40);
        index.insert(poly, 1);

        assertFalse(index.contains(pt(5, 5), 1));
    }

    @Test
    void contains_unknownId_returnsFalse() {
        assertFalse(index.contains(pt(25, 25), 99));
    }

    @Test
    void contains_pointInCellButOutsidePolygon() {
        Polygon poly = rect(10, 10, 20, 20);
        index.insert(poly, 1);

        assertFalse(index.contains(pt(45, 45), 1));
    }
    @Test
    void multiplePolygons_independentChecks() {
        Polygon p1 = rect(0, 0, 20, 20);
        Polygon p2 = rect(60, 60, 80, 80);
        index.insert(p1, 1);
        index.insert(p2, 2);

        assertTrue(index.contains(pt(10, 10), 1));
        assertFalse(index.contains(pt(10, 10), 2));
        assertTrue(index.contains(pt(70, 70), 2));
        assertFalse(index.contains(pt(70, 70), 1));
    }

    @Test
    void multiplePolygons_overlapping() {
        Polygon p1 = rect(0, 0, 30, 30);
        Polygon p2 = rect(20, 20, 50, 50);
        index.insert(p1, 1);
        index.insert(p2, 2);

        assertTrue(index.contains(pt(25, 25), 1));
        assertTrue(index.contains(pt(25, 25), 2));
    }


    @Test
    void remove_polygonNoLongerFound() {
        Polygon poly = rect(10, 10, 40, 40);
        index.insert(poly, 1);
        assertTrue(index.contains(pt(25, 25), 1));

        index.remove(1);
        assertFalse(index.contains(pt(25, 25), 1));
    }

    @Test
    void remove_nonExistentId_noException() {
        assertDoesNotThrow(() -> index.remove(999));
    }

    @Test
    void update_movedPolygon() {
        Polygon original = rect(0, 0, 20, 20);
        index.insert(original, 1);
        assertTrue(index.contains(pt(10, 10), 1));

        Polygon moved = rect(60, 60, 80, 80);
        index.update(1, moved);

        assertFalse(index.contains(pt(10, 10), 1));
        assertTrue(index.contains(pt(70, 70), 1));
    }

    @Test
    void update_enlargedPolygon() {
        Polygon small = rect(0, 0, 10, 10);
        index.insert(small, 1);
        assertFalse(index.contains(pt(80, 80), 1));

        Polygon large = rect(0, 0, 100, 100);
        index.update(1, large);

        assertTrue(index.contains(pt(80, 80), 1));
    }

    @Test
    void contains_pointOnEdge_returnsTrue() {
        Polygon poly = rect(0, 0, 20, 20);
        index.insert(poly, 1);

        assertTrue(index.contains(pt(0, 10), 1));
        assertTrue(index.contains(pt(10, 0), 1));
        assertTrue(index.contains(pt(20, 10), 1));
    }

    @Test
    void contains_pointOnVertex_returnsTrue() {
        Polygon poly = rect(0, 0, 20, 20);
        index.insert(poly, 1);

        assertTrue(index.contains(pt(0, 0), 1));
        assertTrue(index.contains(pt(20, 20), 1));
    }

    @Test
    void contains_polygonWithHole_pointInHole_returnsFalse() {
        LinearRing outer = gf.createLinearRing(new Coordinate[]{
                new Coordinate(0, 0), new Coordinate(20, 0),
                new Coordinate(20, 20), new Coordinate(0, 20),
                new Coordinate(0, 0)
        });
        LinearRing hole = gf.createLinearRing(new Coordinate[]{
                new Coordinate(8, 8), new Coordinate(12, 8),
                new Coordinate(12, 12), new Coordinate(8, 12),
                new Coordinate(8, 8)
        });
        Polygon poly = gf.createPolygon(outer, new LinearRing[]{hole});
        index.insert(poly, 1);

        assertFalse(index.contains(pt(10, 10), 1));
        assertTrue(index.contains(pt(2, 2), 1));
    }

    @Test
    void contains_polygonWithHole_pointOnHoleBoundary_returnsTrue() {
        LinearRing outer = gf.createLinearRing(new Coordinate[]{
                new Coordinate(0, 0), new Coordinate(20, 0),
                new Coordinate(20, 20), new Coordinate(0, 20),
                new Coordinate(0, 0)
        });
        LinearRing hole = gf.createLinearRing(new Coordinate[]{
                new Coordinate(8, 8), new Coordinate(12, 8),
                new Coordinate(12, 12), new Coordinate(8, 12),
                new Coordinate(8, 8)
        });
        Polygon poly = gf.createPolygon(outer, new LinearRing[]{hole});
        index.insert(poly, 1);

        assertTrue(index.contains(pt(8, 10), 1));
    }

    @Test
    void largePolygon_spansMultipleCells() {
        Polygon large = rect(0, 0, 200, 200);
        index.insert(large, 1);

        assertTrue(index.contains(pt(25, 25), 1));
        assertTrue(index.contains(pt(75, 75), 1));
        assertTrue(index.contains(pt(175, 175), 1));
        assertFalse(index.contains(pt(250, 250), 1));
    }

    @Test
    void negativeCoordinates_handledCorrectly() {
        Polygon poly = rect(-30, -30, -10, -10);
        index.insert(poly, 1);

        assertTrue(index.contains(pt(-20, -20), 1));
        assertFalse(index.contains(pt(0, 0), 1));
    }

    @Test
    void size_reflectsInsertAndRemove() {
        assertEquals(0, index.size());

        index.insert(rect(0, 0, 10, 10), 1);
        assertEquals(1, index.size());

        index.insert(rect(20, 20, 30, 30), 2);
        assertEquals(2, index.size());

        index.remove(1);
        assertEquals(1, index.size());
    }

    @Test
    void clear_emptiesIndex() {
        index.insert(rect(0, 0, 10, 10), 1);
        index.insert(rect(20, 20, 30, 30), 2);
        assertEquals(2, index.size());

        index.clear();
        assertEquals(0, index.size());
        assertFalse(index.contains(pt(5, 5), 1));
    }
}
```
