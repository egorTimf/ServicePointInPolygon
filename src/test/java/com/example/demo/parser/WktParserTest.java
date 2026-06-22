package com.example.demo.parser;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Polygon;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WktParserTest {

    @Test
    void polygon_simple() {
        WktParser p = new WktParser("POLYGON ((0 0, 10 0, 10 10, 0 10, 0 0))");
        assertEquals(1, p.getPolygons().size());
        assertEquals(0, p.getPoints().size());
    }

    @Test
    void polygon_withHole() {
        WktParser p = new WktParser(
                "POLYGON ((0 0, 10 0, 10 10, 0 10, 0 0), (3 3, 7 3, 7 7, 3 7, 3 3))");
        List<Polygon> polys = p.getPolygons();
        assertEquals(1, polys.size());
        assertEquals(1, polys.get(0).getNumInteriorRing(), "Должна быть одна дырка");
    }

    @Test
    void multipolygon() {
        WktParser p = new WktParser(
                "MULTIPOLYGON (((0 0, 5 0, 5 5, 0 5, 0 0)), ((10 10, 15 10, 15 15, 10 15, 10 10)))");
        assertEquals(2, p.getPolygons().size());
    }

    @Test
    void geometryCollection_polygonsExtracted() {
        WktParser p = new WktParser(
                "GEOMETRYCOLLECTION (POLYGON ((0 0, 5 0, 5 5, 0 5, 0 0)), POINT (1 1))");
        assertEquals(1, p.getPolygons().size());
        assertEquals(1, p.getPoints().size());
    }

    @Test
    void area_isCorrect() {
        WktParser p = new WktParser("POLYGON ((0 0, 10 0, 10 10, 0 10, 0 0))");
        assertEquals(100.0, p.getPolygons().get(0).getArea(), 1e-9);
    }

    @Test
    void invalidWkt_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> new WktParser("NOT A WKT STRING"));
    }

    @Test
    void emptyString_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> new WktParser(""));
    }

    @Test
    void nullString_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> new WktParser(null));
    }

    @Test
    void linestring_returnsEmptyPolygons() {
        WktParser p = new WktParser("LINESTRING (0 0, 1 1, 2 2)");
        assertTrue(p.getPolygons().isEmpty());
    }

    @Test
    void realWorldCoordinates() {
        WktParser p = new WktParser(
                "POLYGON ((37.3 55.5, 37.9 55.5, 37.9 55.9, 37.3 55.9, 37.3 55.5))");
        assertEquals(1, p.getPolygons().size());
        assertTrue(p.getPolygons().get(0).isValid());
    }
}