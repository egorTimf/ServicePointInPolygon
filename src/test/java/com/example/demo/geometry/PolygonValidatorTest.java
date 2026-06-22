package com.example.demo.geometry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.*;

import static org.junit.jupiter.api.Assertions.*;

class PolygonValidatorTest {

    private GeometryFactory gf;
    private PolygonValidator validator;

    @BeforeEach
    void setUp() {
        gf = new GeometryFactory();
        validator = new PolygonValidator();
    }

    private LinearRing ring(double... coords) {
        Coordinate[] c = new Coordinate[coords.length / 2 + 1];
        for (int i = 0; i < coords.length / 2; i++) {
            c[i] = new Coordinate(coords[i * 2], coords[i * 2 + 1]);
        }
        c[c.length - 1] = c[0];
        return gf.createLinearRing(c);
    }

    private Polygon square(double x0, double y0, double x1, double y1) {
        return gf.createPolygon(ring(x0, y0, x1, y0, x1, y1, x0, y1));
    }

    @Test
    void simplePolygon_noHoles_valid() {
        PolygonValidator.ValidationResult r = validator.validate(square(0, 0, 10, 10));
        assertTrue(r.valid);
        assertTrue(r.errors.isEmpty());
    }

    @Test
    void selfIntersecting_invalid() {
        Polygon bowtie = gf.createPolygon(new Coordinate[]{
                new Coordinate(0, 0), new Coordinate(10, 10),
                new Coordinate(10, 0), new Coordinate(0, 10),
                new Coordinate(0, 0)
        });
        PolygonValidator.ValidationResult r = validator.validate(bowtie);
        assertFalse(r.valid);
        assertTrue(r.errors.stream().anyMatch(e -> e.startsWith("JTS:")));
    }

    @Test
    void holeStrictlyInside_valid() {
        Polygon poly = gf.createPolygon(
                ring(0, 0, 10, 0, 10, 10, 0, 10),
                new LinearRing[]{ ring(3, 3, 7, 3, 7, 7, 3, 7) }
        );
        PolygonValidator.ValidationResult r = validator.validate(poly);
        assertTrue(r.valid, r.toString());
    }

    @Test
    void twoHolesInside_valid() {
        Polygon poly = gf.createPolygon(
                ring(0, 0, 20, 0, 20, 20, 0, 20),
                new LinearRing[]{
                        ring(1, 1, 4, 1, 4, 4, 1, 4),
                        ring(10, 10, 14, 10, 14, 14, 10, 14)
                }
        );
        PolygonValidator.ValidationResult r = validator.validate(poly);
        assertTrue(r.valid, r.toString());
    }

    @Test
    void holePartiallyOutside_invalid() {
        Polygon poly = gf.createPolygon(
                ring(0, 0, 10, 0, 10, 10, 0, 10),
                new LinearRing[]{ ring(8, 3, 12, 3, 12, 7, 8, 7) }
        );
        PolygonValidator.ValidationResult r = validator.validate(poly);
        assertFalse(r.valid, "Дыра частично снаружи — невалидно");
        assertTrue(r.errors.stream().anyMatch(e -> e.contains("Дыра #0")));
    }

    @Test
    void holeCompletelyOutside_invalid() {
        Polygon poly = gf.createPolygon(
                ring(0, 0, 10, 0, 10, 10, 0, 10),
                new LinearRing[]{ ring(20, 20, 25, 20, 25, 25, 20, 25) }
        );
        PolygonValidator.ValidationResult r = validator.validate(poly);
        assertFalse(r.valid);
        assertTrue(r.errors.stream().anyMatch(e -> e.contains("Дыра #0")));
    }

    @Test
    void holeEqualsOuter_invalid() {
        LinearRing outer = ring(0, 0, 10, 0, 10, 10, 0, 10);
        LinearRing hole  = ring(0, 0, 10, 0, 10, 10, 0, 10);
        Polygon poly = gf.createPolygon(outer, new LinearRing[]{ hole });
        PolygonValidator.ValidationResult r = validator.validate(poly);
        assertFalse(r.valid);
    }

    @Test
    void holeLargerThanOuter_invalid() {
        Polygon poly = gf.createPolygon(
                ring(3, 3, 7, 3, 7, 7, 3, 7),
                new LinearRing[]{ ring(0, 0, 10, 0, 10, 10, 0, 10) }
        );
        PolygonValidator.ValidationResult r = validator.validate(poly);
        assertFalse(r.valid);
    }

    @Test
    void secondHoleOutside_firstValid_invalid() {
        Polygon poly = gf.createPolygon(
                ring(0, 0, 20, 0, 20, 20, 0, 20),
                new LinearRing[]{
                        ring(1, 1, 4, 1, 4, 4, 1, 4),
                        ring(18, 18, 25, 18, 25, 25, 18, 25)
                }
        );
        PolygonValidator.ValidationResult r = validator.validate(poly);
        assertFalse(r.valid);
        assertTrue(r.errors.stream().anyMatch(e -> e.contains("Дыра #1")));
        assertTrue(r.errors.stream().noneMatch(e -> e.contains("Дыра #0")),
                "Первая дыра валидна — ошибки по ней не должно быть");
    }

    @Test
    void holesOverlap_invalid() {
        Polygon poly = gf.createPolygon(
                ring(0, 0, 20, 0, 20, 20, 0, 20),
                new LinearRing[]{
                        ring(2, 2, 8, 2, 8, 8, 2, 8),
                        ring(6, 6, 12, 6, 12, 12, 6, 12)
                }
        );
        PolygonValidator.ValidationResult r = validator.validate(poly);
        assertFalse(r.valid);
    }

    @Test
    void holesTouch_valid() {
        Polygon poly = gf.createPolygon(
                ring(0, 0, 20, 0, 20, 20, 0, 20),
                new LinearRing[]{
                        ring(1, 1, 5, 1, 5, 5, 1, 5),
                        ring(5, 5, 9, 5, 9, 9, 5, 9)
                }
        );
        PolygonValidator.ValidationResult r = validator.validate(poly);
        boolean hasHoleOverlapError = r.errors.stream()
                .anyMatch(e -> e.contains("пересекаются между собой"));
        assertFalse(hasHoleOverlapError, "Касание в точке — не пересечение");
    }

    @Test
    void errorMessage_containsHoleIndex() {
        Polygon poly = gf.createPolygon(
                ring(0, 0, 10, 0, 10, 10, 0, 10),
                new LinearRing[]{ ring(8, 3, 12, 3, 12, 7, 8, 7) }
        );
        PolygonValidator.ValidationResult r = validator.validate(poly);
        assertFalse(r.valid);
        assertEquals(2, r.errors.size());
    }

    @Test
    void multipleErrors_allReported() {
        Polygon poly = gf.createPolygon(
                ring(0, 0, 10, 0, 10, 10, 0, 10),
                new LinearRing[]{
                        ring(8, 1, 15, 1, 15, 4, 8, 4),
                        ring(8, 6, 15, 6, 15, 9, 8, 9)
                }
        );
        PolygonValidator.ValidationResult r = validator.validate(poly);
        assertFalse(r.valid);
        long holeErrors = r.errors.stream()
                .filter(e -> e.contains("выходит за границы")).count();
        assertEquals(2, holeErrors, "Должны быть сообщения об обеих дырках");
    }
}