package com.example.demo.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.io.geojson.GeoJsonReader;

import java.util.*;

public class GeoJsonParser {

    private final JsonNode geoJson;
    private final int srid;

    @Getter
    private final List<Polygon> polygons = new ArrayList<>();

    @Getter
    private final List<Point> points = new ArrayList<>();

    private final GeoJsonReader reader = new GeoJsonReader();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GeoJsonParser(String geoJsonString, int srid) {
        this.srid = srid;
        try {
            this.geoJson = objectMapper.readTree(geoJsonString);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid GeoJSON string" + geoJsonString, e);
        }

        parseGeometries();
    }


    private void parseGeometries() {
        parseElement(geoJson);
    }

    private void parseElement(JsonNode element) {
        if (element == null || !element.has("type")) return;

        String type = element.get("type").asText();

        try {
            switch (type) {
                case "FeatureCollection":
                    JsonNode features = element.get("features");
                    if (features != null && features.isArray()) {
                        for (JsonNode feature : features) {
                            parseElement(feature);
                        }
                    }
                    break;

                case "Feature":
                    JsonNode geometry = element.get("geometry");
                    if (geometry != null) {
                        parseElement(geometry);
                    }
                    break;

                case "Polygon": {
                    String polygonJson = objectMapper.writeValueAsString(element);
                    Polygon polygon = (Polygon) reader.read(polygonJson);
                    polygons.add(polygon);
                    break;
                }

                case "Point": {
                    String pointJson = objectMapper.writeValueAsString(element);
                    Point point = (Point) reader.read(pointJson);
                    points.add(point);
                    break;
                }

                case "MultiPolygon": {
                    String multiJson = objectMapper.writeValueAsString(element);
                    MultiPolygon multi = (MultiPolygon) reader.read(multiJson);

                    for (int i = 0; i < multi.getNumGeometries(); i++)
                        polygons.add((Polygon) multi.getGeometryN(i));

                    break;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse geometry: " + type, e);
        }
    }
}