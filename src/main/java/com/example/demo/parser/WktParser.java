package com.example.demo.parser;

import lombok.Getter;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;

import java.util.ArrayList;
import java.util.List;

public class WktParser {

    @Getter
    private final List<Polygon> polygons = new ArrayList<>();

    @Getter
    private final List<Point> points = new ArrayList<>();

    private final WKTReader reader = new WKTReader();

    public WktParser(String wkt) {
        if (wkt == null || wkt.isBlank()) {
            throw new IllegalArgumentException("WKT строка не может быть пустой");
        }
        parse(wkt.trim());
    }

    private void parse(String wkt) {
        try {
            Geometry geom = reader.read(wkt);
            extractGeometries(geom);
        } catch (ParseException e) {
            throw new IllegalArgumentException("Невалидная WKT строка: " + e.getMessage(), e);
        }
    }

    private void extractGeometries(Geometry geom) {
        if (geom == null || geom.isEmpty()) return;

        if (geom instanceof Polygon) {
            polygons.add((Polygon) geom);
        } else if (geom instanceof MultiPolygon) {
            for (int i = 0; i < geom.getNumGeometries(); i++) {
                polygons.add((Polygon) geom.getGeometryN(i));
            }
        } else if (geom instanceof Point) {
            points.add((Point) geom);
        } else if (geom instanceof GeometryCollection) {
            for (int i = 0; i < geom.getNumGeometries(); i++) {
                extractGeometries(geom.getGeometryN(i));
            }
        }
    }
}