package com.example.demo.geometry;

import org.locationtech.jts.geom.*;
import org.locationtech.jts.io.geojson.GeoJsonWriter;
import org.locationtech.jts.operation.valid.IsValidOp;

import java.util.*;

/**
 * Сервис геометрических операций над полигонами.
 *
 * Политика обработки вырожденных случаев:
 * - Невалидные полигоны исправляются через buffer(0) перед операцией.
 * - Если результат операции — не Polygon/MultiPolygon (точка, линия, пустая геометрия),
 *   возвращается OperationResult с empty=true и пустым resultGeoJson.
 * - Буферизация с distance=0 используется только для починки геометрии,
 *   для пользовательской буферизации используется buffer(distance > 0).
 */
public class PolygonOperations {

    public enum Operation {
        INTERSECTION, UNION, DIFFERENCE, SYM_DIFFERENCE, BUFFER
    }

    public static class OperationResult {
        public final Operation operation;
        public final boolean intersects;   // только для INTERSECTION
        public final boolean empty;        // результат пуст или вырожден
        public final double area;
        public final String resultGeoJson; // null если empty=true
        public final Geometry resultGeom;  // null если empty=true
        public final String validationWarning; // если входные данные были починены

        private OperationResult(Builder b) {
            this.operation = b.operation;
            this.intersects = b.intersects;
            this.empty = b.empty;
            this.area = b.area;
            this.resultGeoJson = b.resultGeoJson;
            this.resultGeom = b.resultGeom;
            this.validationWarning = b.validationWarning;
        }

        public static class Builder {
            Operation operation;
            boolean intersects = false;
            boolean empty = false;
            double area = 0.0;
            String resultGeoJson = null;
            Geometry resultGeom = null;
            String validationWarning = null;

            Builder(Operation op) { this.operation = op; }
            Builder intersects(boolean v) { this.intersects = v; return this; }
            Builder empty(boolean v) { this.empty = v; return this; }
            Builder area(double v) { this.area = v; return this; }
            Builder resultGeoJson(String v) { this.resultGeoJson = v; return this; }
            Builder resultGeom(Geometry v) { this.resultGeom = v; return this; }
            Builder validationWarning(String v) { this.validationWarning = v; return this; }
            OperationResult build() { return new OperationResult(this); }
        }
    }

    private final GeoJsonWriter geoJsonWriter = new GeoJsonWriter();

    // ──────────────────────────────────────────────
    // ПУБЛИЧНЫЕ МЕТОДЫ
    // ──────────────────────────────────────────────

    public OperationResult intersection(Geometry a, Geometry b) {
        ValidationResult val = validateAndRepair(a, b);
        a = val.geomA;
        b = val.geomB;

        boolean bboxIntersects = a.getEnvelopeInternal().intersects(b.getEnvelopeInternal());
        if (!bboxIntersects) {
            // Быстрый выход: bbox не пересекаются → точно нет пересечения
            return new OperationResult.Builder(Operation.INTERSECTION)
                    .intersects(false)
                    .empty(true)
                    .validationWarning(val.warning)
                    .build();
        }

        boolean topologyIntersects = a.intersects(b);
        Geometry result = a.intersection(b);

        return buildResult(Operation.INTERSECTION, result, topologyIntersects, val.warning);
    }

    public OperationResult union(Geometry a, Geometry b) {
        ValidationResult val = validateAndRepair(a, b);
        Geometry result = val.geomA.union(val.geomB);
        return buildResult(Operation.UNION, result, true, val.warning);
    }

    public OperationResult difference(Geometry a, Geometry b) {
        ValidationResult val = validateAndRepair(a, b);
        Geometry result = val.geomA.difference(val.geomB);
        return buildResult(Operation.DIFFERENCE, result, null, val.warning);
    }

    public OperationResult symDifference(Geometry a, Geometry b) {
        ValidationResult val = validateAndRepair(a, b);
        Geometry result = val.geomA.symDifference(val.geomB);
        return buildResult(Operation.SYM_DIFFERENCE, result, null, val.warning);
    }

    /**
     * Буферизация: создаёт новую геометрию, расширяя/сужая полигон на distance единиц.
     * distance > 0 → расширение (offset наружу)
     * distance < 0 → сужение (offset внутрь), может вернуть пустой результат
     * distance = 0 → только починка геометрии
     *
     * @param segments количество сегментов на четверть окружности для округлённых углов (по умолч. 16)
     */
    public OperationResult buffer(Geometry geom, double distance, int segments) {
        String warning = null;
        if (!geom.isValid()) {
            warning = "Геометрия невалидна, применён buffer(0) для исправления";
            geom = geom.buffer(0);
        }

        Geometry result = geom.buffer(distance, segments);
        return buildResult(Operation.BUFFER, result, null, warning);
    }

    public OperationResult buffer(Geometry geom, double distance) {
        return buffer(geom, distance, 16);
    }

    // ──────────────────────────────────────────────
    // ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ
    // ──────────────────────────────────────────────

    /**
     * Проверяет валидность обоих полигонов и при необходимости чинит через buffer(0).
     * buffer(0) — стандартный JTS-трюк: перестраивает топологию без изменения формы.
     * Исправляет: самопересечения, дублирующиеся точки, неправильный обход колец.
     */
    private ValidationResult validateAndRepair(Geometry a, Geometry b) {
        StringBuilder warning = new StringBuilder();

        if (!a.isValid()) {
            IsValidOp op = new IsValidOp(a);
            warning.append("Полигон A невалиден: ")
                    .append(op.getValidationError().getMessage())
                    .append(". Применён buffer(0). ");
            a = a.buffer(0);
        }
        if (!b.isValid()) {
            IsValidOp op = new IsValidOp(b);
            warning.append("Полигон B невалиден: ")
                    .append(op.getValidationError().getMessage())
                    .append(". Применён buffer(0).");
            b = b.buffer(0);
        }

        return new ValidationResult(a, b, warning.length() > 0 ? warning.toString().trim() : null);
    }

    private OperationResult buildResult(Operation op, Geometry result,
                                        Boolean intersects, String warning) {
        OperationResult.Builder builder = new OperationResult.Builder(op)
                .validationWarning(warning);

        if (intersects != null) {
            builder.intersects(intersects);
        }

        if (result == null || result.isEmpty()) {
            return builder.empty(true).build();
        }

        boolean hasPolygon = hasPolygonGeometry(result);
        if (!hasPolygon) {
            return builder.empty(true).area(0.0).build();
        }

        String geoJson = geoJsonWriter.write(result);

        return builder
                .empty(false)
                .area(result.getArea())
                .resultGeoJson(geoJson)
                .resultGeom(result)
                .build();
    }

    private boolean hasPolygonGeometry(Geometry geom) {
        if (geom instanceof Polygon) return !geom.isEmpty();
        if (geom instanceof MultiPolygon) return !geom.isEmpty();
        if (geom instanceof GeometryCollection) {
            for (int i = 0; i < geom.getNumGeometries(); i++) {
                if (hasPolygonGeometry(geom.getGeometryN(i))) return true;
            }
        }
        return false;
    }

    private static class ValidationResult {
        final Geometry geomA;
        final Geometry geomB;
        final String warning;

        ValidationResult(Geometry a, Geometry b, String w) {
            this.geomA = a;
            this.geomB = b;
            this.warning = w;
        }
    }
}