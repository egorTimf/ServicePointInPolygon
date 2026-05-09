package com.example.demo.geometry;

import org.locationtech.jts.geom.*;
import org.locationtech.jts.operation.valid.IsValidOp;
import org.locationtech.jts.operation.valid.TopologyValidationError;

import java.util.ArrayList;
import java.util.List;

public class PolygonValidator {

    public static class ValidationResult {
        public final boolean valid;
        public final List<String> errors;

        private ValidationResult(boolean valid, List<String> errors) {
            this.valid = valid;
            this.errors = errors;
        }

        public static ValidationResult ok() {
            return new ValidationResult(true, List.of());
        }

        public static ValidationResult fail(List<String> errors) {
            return new ValidationResult(false, errors);
        }

        @Override
        public String toString() {
            return valid ? "VALID" : "INVALID: " + String.join("; ", errors);
        }
    }

    private final GeometryFactory gf = new GeometryFactory();

    public ValidationResult validate(Polygon polygon) {
        List<String> errors = new ArrayList<>();
        IsValidOp isValidOp = new IsValidOp(polygon);
        if (!isValidOp.isValid()) {
            TopologyValidationError error = isValidOp.getValidationError();
            errors.add("JTS: " + error.getMessage() +
                    " в точке (" + error.getCoordinate().x +
                    ", " + error.getCoordinate().y + ")");
        }

        int numHoles = polygon.getNumInteriorRing();
        if (numHoles > 0) {
            Polygon outerOnly = gf.createPolygon(
                    (LinearRing) polygon.getExteriorRing()
            );

            for (int i = 0; i < numHoles; i++) {
                LinearRing holeRing = (LinearRing) polygon.getInteriorRingN(i);
                Polygon holePoly = gf.createPolygon(holeRing);
                if (!outerOnly.covers(holePoly)) {
                    List<String> outsidePoints = new ArrayList<>();
                    for (Coordinate coord : holeRing.getCoordinates()) {
                        Point pt = gf.createPoint(coord);
                        if (!outerOnly.covers(pt)) {
                            outsidePoints.add(
                                    "(" + coord.x + ", " + coord.y + ")"
                            );
                            if (outsidePoints.size() >= 3) {
                                outsidePoints.add("...");
                                break;
                            }
                        }
                    }
                    errors.add("Дыра #" + i + " выходит за границы внешнего контура. " +
                            "Точки вне контура: " + String.join(", ", outsidePoints));
                }
            }
            for (int i = 0; i < numHoles; i++) {
                for (int j = i + 1; j < numHoles; j++) {
                    Polygon holeI = gf.createPolygon(
                            (LinearRing) polygon.getInteriorRingN(i));
                    Polygon holeJ = gf.createPolygon(
                            (LinearRing) polygon.getInteriorRingN(j));

                    if (holeI.intersects(holeJ) && !holeI.touches(holeJ)) {
                        errors.add("Дыры #" + i + " и #" + j +
                                " пересекаются между собой");
                    }
                }
            }
        }

        return errors.isEmpty() ? ValidationResult.ok() : ValidationResult.fail(errors);
    }

    public ValidationResult validateHolesOnly(Polygon polygon) {
        List<String> errors = new ArrayList<>();
        int numHoles = polygon.getNumInteriorRing();

        if (numHoles == 0) return ValidationResult.ok();

        Polygon outerOnly = gf.createPolygon(
                (LinearRing) polygon.getExteriorRing()
        );

        for (int i = 0; i < numHoles; i++) {
            LinearRing holeRing = (LinearRing) polygon.getInteriorRingN(i);
            Polygon holePoly = gf.createPolygon(holeRing);

            if (!outerOnly.covers(holePoly)) {
                errors.add("Дыра #" + i + " выходит за границы внешнего контура");
            }
        }

        return errors.isEmpty() ? ValidationResult.ok() : ValidationResult.fail(errors);
    }
}