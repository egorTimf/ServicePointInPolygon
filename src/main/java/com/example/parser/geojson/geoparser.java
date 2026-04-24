package com.example.parser.geojson;

import com.fasterxml.jackson.databind.JsonNode;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.io.geojson.GeoJsonReader;

import java.util.ArrayList;
import java.util.List;

public class GeoJsonParser {

    private final JsonNode geoJson;
    private final int srid;

    private final List<Polygon> polygons = new ArrayList<>();
    private final List<Point> points = new ArrayList<>();
    private final List<List<Polygon>> multiPolygons = new ArrayList<>();

    public GeoJsonParser(JsonNode geoJson) {
        this(geoJson, 0);
    }

    public GeoJsonParser(JsonNode geoJson, int srid) {
        this.geoJson = geoJson;
        this.srid = srid;
        parseGeometries();
    }

    public List<Polygon> getPolygons() {
        return polygons;
    }

    public List<Point> getPoints() {
        return points;
    }

    public List<List<Polygon>> getMultiPolygons() {
        return multiPolygons;
    }

    private void parseGeometries() {
        GeoJsonReader reader = new GeoJsonReader();
        parseElement(geoJson, reader);
    }

    private void parseElement(JsonNode element, GeoJsonReader reader) {
        if (!element.has("type")) return;

        String type = element.get("type").asText();

        switch (type) {

            case "FeatureCollection":
                JsonNode features = element.get("features");
                if (features != null && features.isArray()) {
                    for (JsonNode feature : features) {
                        parseElement(feature, reader);
                    }
                }
                break;

            case "Feature":
                JsonNode geometry = element.get("geometry");
                if (geometry != null) {
                    parseElement(geometry, reader);
                }
                break;

            case "Polygon":
                try {
                    Geometry geom = reader.read(element.toString());
                    if (geom instanceof Polygon polygon) {
                        applySrid(polygon);
                        polygons.add(polygon);
                    }
                } catch (Exception e) {
                    throw new RuntimeException("Error parsing Polygon", e);
                }
                break;

            case "Point":
                try {
                    Geometry geom = reader.read(element.toString());
                    if (geom instanceof Point point) {
                        applySrid(point);
                        points.add(point);
                    }
                } catch (Exception e) {
                    throw new RuntimeException("Error parsing Point", e);
                }
                break;

            case "MultiPolygon":
                try {
                    Geometry geom = reader.read(element.toString());
                    if (geom instanceof MultiPolygon multiPolygon) {

                        List<Polygon> list = new ArrayList<>();

                        for (int i = 0; i < multiPolygon.getNumGeometries(); i++) {
                            Polygon polygon = (Polygon) multiPolygon.getGeometryN(i);
                            applySrid(polygon);
                            list.add(polygon);
                        }

                        multiPolygons.add(list);
                    }
                } catch (Exception e) {
                    throw new RuntimeException("Error parsing MultiPolygon", e);
                }
                break;
        }
    }

    private void applySrid(Geometry geometry) {
        if (srid != 0) {
            geometry.setSRID(srid);
        }
    }
}