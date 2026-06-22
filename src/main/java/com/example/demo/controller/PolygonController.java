package com.example.demo.controller;

import com.example.demo.dto.CheckMultipleRequest;
import com.example.demo.entity.PolygonEntity;
import com.example.demo.geometry.PolygonOperations;
import com.example.demo.geometry.PolygonValidator;
import com.example.demo.indexing.GridIndex;
import com.example.demo.parser.GeoJsonParser;
import com.example.demo.parser.WktParser;
import com.example.demo.repository.PolygonRepository;
import com.example.demo.geometry.PointInPolygon;
import com.example.demo.service.PolygonService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.locationtech.jts.geom.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/polygons")
@CrossOrigin(origins = "*")
public class PolygonController {

    @Autowired
    private PolygonRepository polygonRepository;

    @Autowired
    private PolygonService polygonService;

    private final PolygonValidator polygonValidator = new PolygonValidator();
    private final PolygonOperations polygonOperations = new PolygonOperations();
    private final GridIndex gridIndex = new GridIndex(50);
    private final GeometryFactory geometryFactory = new GeometryFactory();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @GetMapping("/")
    public ResponseEntity<List<PolygonEntity>> getAllPolygons() {
        return ResponseEntity.ok(polygonRepository.findAll());
    }

    @PostMapping("/")
    public ResponseEntity<?> createPolygon(@RequestBody JsonNode rawData) {
        try {
            String geoJson = rawData.toString();
            GeoJsonParser geoJsonParser = new GeoJsonParser(geoJson, 0);
            List<Polygon> ntsPolygons = geoJsonParser.getPolygons();

            if (ntsPolygons.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Формат не распознан."));
            }

            List<Integer> addedIds = new ArrayList<>();
            List<Map<String, Object>> validationErrors = new ArrayList<>();

            for (Polygon poly : ntsPolygons) {
                PolygonValidator.ValidationResult validation =
                        polygonValidator.validate(poly);
                if (!validation.valid) {
                    validationErrors.add(Map.of(
                            "polygon", polygonToGeoJSON(poly),
                            "errors", validation.errors
                    ));
                    continue;
                }

                String polyGeoJSON = polygonToGeoJSON(poly);
                if (!polygonRepository.existsByCoordsJson(polyGeoJSON)) {
                    Integer id = polygonService.createPolygonFromGeometry(poly, polyGeoJSON);
                    addedIds.add(id);
                    gridIndex.insert(poly, id);
                }
            }

            if (addedIds.isEmpty() && !validationErrors.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Все полигоны невалидны",
                        "details", validationErrors
                ));
            }

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("ids", addedIds);
            if (!validationErrors.isEmpty()) {
                response.put("skipped", validationErrors.size());
                response.put("validationErrors", validationErrors);
            }

            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/wkt")
    public ResponseEntity<?> createPolygonFromWkt(@RequestBody Map<String, String> body) {
        try {
            String wkt = body.get("wkt");
            if (wkt == null || wkt.isBlank()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Поле 'wkt' обязательно"));
            }

            WktParser parser = new WktParser(wkt);
            List<Polygon> parsedPolygons = parser.getPolygons();

            if (parsedPolygons.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error",
                                "WKT не содержит полигонов. Поддерживаются: POLYGON, MULTIPOLYGON, GEOMETRYCOLLECTION"));
            }

            List<Integer> addedIds = new ArrayList<>();
            List<Map<String, Object>> validationErrors = new ArrayList<>();

            for (Polygon poly : parsedPolygons) {
                PolygonValidator.ValidationResult validation =
                        polygonValidator.validate(poly);
                if (!validation.valid) {
                    validationErrors.add(Map.of(
                            "errors", validation.errors
                    ));
                    continue;
                }

                String polyGeoJson = polygonToGeoJSON(poly);
                if (!polygonRepository.existsByCoordsJson(polyGeoJson)) {
                    Integer id = polygonService.createPolygonFromGeometry(poly, polyGeoJson);
                    addedIds.add(id);
                    gridIndex.insert(poly, id);
                }
            }

            if (addedIds.isEmpty() && !validationErrors.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Все полигоны невалидны",
                        "details", validationErrors
                ));
            }

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("ids", addedIds);
            if (!validationErrors.isEmpty()) {
                response.put("skipped", validationErrors.size());
                response.put("validationErrors", validationErrors);
            }

            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Ошибка обработки WKT: " + e.getMessage()));
        }
    }

    @GetMapping("/{id}/wkt")
    public ResponseEntity<?> getPolygonAsWkt(@PathVariable int id) {
        try {
            Optional<PolygonEntity> entityOpt = polygonRepository.findById(id);
            if (entityOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            GeoJsonParser parser = new GeoJsonParser(entityOpt.get().getCoordsJson(), 0);
            if (parser.getPolygons().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Не удалось прочитать геометрию полигона"));
            }

            org.locationtech.jts.io.WKTWriter wktWriter = new org.locationtech.jts.io.WKTWriter();
            String wkt = wktWriter.write(parser.getPolygons().get(0));

            return ResponseEntity.ok(Map.of("id", id, "wkt", wkt));

        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/check-multiple")
    public ResponseEntity<?> checkMultiple(@RequestBody CheckMultipleRequest request) {
        List<PolygonEntity> entities = polygonRepository.findAllById(request.getPolygonIds());

        if (entities.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Point point = geometryFactory.createPoint(
                new Coordinate(request.getX(), request.getY()));

        List<Map<String, Object>> results = new ArrayList<>();

        for (PolygonEntity entity : entities) {
            try {
                GeoJsonParser geoJson = new GeoJsonParser(entity.getCoordsJson(), 0);
                List<Polygon> polygons = geoJson.getPolygons();

                if (!polygons.isEmpty()) {
                    Polygon polygon = polygons.get(0);
                    boolean isInside = PointInPolygon.contains(polygon, point);
                    results.add(Map.of("polygonId", entity.getId(), "inside", isInside));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return ResponseEntity.ok(results);
    }

    @PostMapping("/check")
    public ResponseEntity<?> checkPoint(
            @RequestParam int id,
            @RequestParam double x,
            @RequestParam double y) {

        Optional<PolygonEntity> entityOpt = polygonRepository.findById(id);
        if (entityOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Point point = geometryFactory.createPoint(new Coordinate(x, y));
        boolean isInside = gridIndex.contains(point, id);

        return ResponseEntity.ok(Map.of("polygonId", id, "inside", isInside));
    }

    @PutMapping("/{id}/vertex/move")
    public ResponseEntity<?> moveVertex(
            @PathVariable Integer id,
            @RequestBody Map<String, Object> request) {
        try {
            int ringIndex = ((Number) request.get("ringIndex")).intValue();
            int vertexIndex = ((Number) request.get("vertexIndex")).intValue();
            double newX = ((Number) request.get("x")).doubleValue();
            double newY = ((Number) request.get("y")).doubleValue();

            polygonService.moveVertex(id, ringIndex, vertexIndex, newX, newY);
            updateGridIndex(id);

            return ResponseEntity.ok(Map.of("message", "Вершина перемещена"));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }
    @PostMapping("/{id}/vertex/add")
    public ResponseEntity<?> addVertex(
            @PathVariable Integer id,
            @RequestBody Map<String, Object> request) {
        try {
            int ringIndex = ((Number) request.get("ringIndex")).intValue();
            int afterIndex = ((Number) request.get("afterIndex")).intValue();
            double x = ((Number) request.get("x")).doubleValue();
            double y = ((Number) request.get("y")).doubleValue();

            polygonService.addVertex(id, ringIndex, afterIndex, x, y);
            updateGridIndex(id);

            return ResponseEntity.ok(Map.of("message", "Вершина добавлена"));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}/vertex/{ringIndex}/{vertexIndex}")
    public ResponseEntity<?> removeVertex(
            @PathVariable Integer id,
            @PathVariable int ringIndex,
            @PathVariable int vertexIndex) {
        try {
            polygonService.removeVertex(id, ringIndex, vertexIndex);
            updateGridIndex(id);

            return ResponseEntity.ok(Map.of("message", "Вершина удалена"));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletePolygon(@PathVariable int id) {
        if (!polygonRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        polygonRepository.deleteById(id);
        gridIndex.remove(id);
        return ResponseEntity.noContent().build();
    }


    private void updateGridIndex(Integer polygonId) {
        try {
            PolygonEntity entity = polygonRepository.findById(polygonId).orElse(null);
            if (entity != null) {
                GeoJsonParser parser = new GeoJsonParser(entity.getCoordsJson(), 0);
                List<Polygon> polygons = parser.getPolygons();
                if (!polygons.isEmpty()) {
                    gridIndex.update(polygonId, polygons.get(0));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String polygonToGeoJSON(Polygon polygon) {
        StringBuilder result = new StringBuilder("{\"type\":\"Polygon\",\"coordinates\":[");

        result.append(coordinatesToJson(polygon.getExteriorRing().getCoordinates()));

        for (int i = 0; i < polygon.getNumInteriorRing(); i++) {
            result.append(",");
            LineString hole = polygon.getInteriorRingN(i);
            result.append(coordinatesToJson(hole.getCoordinates()));
        }

        result.append("]}");
        return result.toString();
    }

    private StringBuilder coordinatesToJson(Coordinate[] coordinates) {
        StringBuilder result = new StringBuilder("[");

        for (Coordinate coord : coordinates) {
            result.append(String.format(Locale.US, "[%f,%f],", coord.getX(), coord.getY()));
        }

        if (result.length() > 1) {
            result.deleteCharAt(result.length() - 1);
        }
        result.append("]");

        return result;
    }

    @PostMapping("/operation")
    public ResponseEntity<?> polygonOperation(@RequestBody Map<String, Object> request) {
        try {
            String operation = (String) request.get("operation");
            if ("buffer".equals(operation)) {
                Integer idA = ((Number) request.get("polygonIdA")).intValue();
                double distance = ((Number) request.get("distance")).doubleValue();
                int segments = request.containsKey("segments")
                        ? ((Number) request.get("segments")).intValue() : 16;

                PolygonEntity entityA = polygonRepository.findById(idA)
                        .orElseThrow(() -> new RuntimeException("Полигон не найден: " + idA));

                GeoJsonParser parserA = new GeoJsonParser(entityA.getCoordsJson(), 0);
                if (parserA.getPolygons().isEmpty())
                    return ResponseEntity.badRequest().body(Map.of("error", "Не удалось распарсить полигон"));

                PolygonOperations.OperationResult result =
                        polygonOperations.buffer(parserA.getPolygons().get(0), distance, segments);

                return buildOperationResponse(operation, idA, null, result);
            }

            Integer idA = ((Number) request.get("polygonIdA")).intValue();
            Integer idB = ((Number) request.get("polygonIdB")).intValue();

            PolygonEntity entityA = polygonRepository.findById(idA)
                    .orElseThrow(() -> new RuntimeException("Полигон A не найден: " + idA));
            PolygonEntity entityB = polygonRepository.findById(idB)
                    .orElseThrow(() -> new RuntimeException("Полигон B не найден: " + idB));

            GeoJsonParser parserA = new GeoJsonParser(entityA.getCoordsJson(), 0);
            GeoJsonParser parserB = new GeoJsonParser(entityB.getCoordsJson(), 0);

            if (parserA.getPolygons().isEmpty() || parserB.getPolygons().isEmpty())
                return ResponseEntity.badRequest().body(Map.of("error", "Не удалось распарсить один из полигонов"));

            Geometry geomA = parserA.getPolygons().get(0);
            Geometry geomB = parserB.getPolygons().get(0);

            PolygonOperations.OperationResult result = switch (operation) {
                case "intersection"   -> polygonOperations.intersection(geomA, geomB);
                case "union"          -> polygonOperations.union(geomA, geomB);
                case "difference"     -> polygonOperations.difference(geomA, geomB);
                case "symDifference"  -> polygonOperations.symDifference(geomA, geomB);
                default -> throw new IllegalArgumentException(
                        "Неизвестная операция: " + operation +
                                ". Допустимые: intersection, union, difference, symDifference, buffer");
            };

            return buildOperationResponse(operation, idA, idB, result);

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private ResponseEntity<?> buildOperationResponse(String operation,
                                                     Integer idA, Integer idB,
                                                     PolygonOperations.OperationResult result) throws Exception {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("operation", operation);
        response.put("polygonIdA", idA);
        if (idB != null) response.put("polygonIdB", idB);
        response.put("intersects", result.intersects);
        response.put("empty", result.empty);
        response.put("area", result.area);
        response.put("result", result.resultGeoJson != null
                ? objectMapper.readTree(result.resultGeoJson) : null);

        if (result.validationWarning != null)
            response.put("validationWarning", result.validationWarning);

        return ResponseEntity.ok(response);
    }
}
