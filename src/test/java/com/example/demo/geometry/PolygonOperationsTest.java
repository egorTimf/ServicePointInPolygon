package com.example.demo.geometry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты геометрических операций.
 *
 * Покрываемые случаи:
 * - Нормальное пересечение двух перекрывающихся квадратов
 * - Непересекающиеся полигоны (быстрый выход через bbox)
 * - Касание по ребру → вырожденный результат (empty=true)
 * - Касание в одной точке → вырожденный результат
 * - Один полигон внутри другого
 * - Вырожденный (самопересекающийся) полигон → автоисправление + предупреждение
 * - Буферизация: расширение, сужение, нулевой буфер
 * - Разность: A\B, симметричная разность
 */
class PolygonOperationsTest {

    private GeometryFactory gf;
    private PolygonOperations ops;

    @BeforeEach
    void setUp() {
        gf = new GeometryFactory();
        ops = new PolygonOperations();
    }

    // ── вспомогательные фабрики ──────────────────────────

    /** Прямоугольник (x0,y0)→(x1,y1) */
    private Polygon rect(double x0, double y0, double x1, double y1) {
        return gf.createPolygon(new Coordinate[]{
                new Coordinate(x0, y0), new Coordinate(x1, y0),
                new Coordinate(x1, y1), new Coordinate(x0, y1),
                new Coordinate(x0, y0)
        });
    }

    /** Квадрат [0,10]×[0,10] */
    private Polygon square10() { return rect(0, 0, 10, 10); }

    // ════════════════════════════════════════════════════
    // INTERSECTION
    // ════════════════════════════════════════════════════

    @Test
    void intersection_normalOverlap() {
        // [0,10]×[0,10] ∩ [5,15]×[0,10] = [5,10]×[0,10], площадь 50
        Polygon a = square10();
        Polygon b = rect(5, 0, 15, 10);

        PolygonOperations.OperationResult result = ops.intersection(a, b);

        assertFalse(result.empty, "Результат не должен быть пустым");
        assertTrue(result.intersects, "Полигоны должны пересекаться");
        assertEquals(50.0, result.area, 1e-9, "Площадь пересечения = 50");
        assertNotNull(result.resultGeoJson);
    }

    @Test
    void intersection_noOverlap_bboxFastExit() {
        // Полигоны не пересекаются даже по bbox
        Polygon a = square10();
        Polygon b = rect(20, 20, 30, 30);

        PolygonOperations.OperationResult result = ops.intersection(a, b);

        assertTrue(result.empty);
        assertFalse(result.intersects);
        assertEquals(0.0, result.area, 1e-9);
        assertNull(result.resultGeoJson);
    }

    @Test
    void intersection_touchByEdge_degenerate() {
        // Квадраты касаются по ребру x=10 → результат линия, не полигон
        Polygon a = square10();
        Polygon b = rect(10, 0, 20, 10);

        PolygonOperations.OperationResult result = ops.intersection(a, b);

        // Осознанная политика: касание по ребру = вырожденный результат, площадь 0
        assertTrue(result.empty, "Касание по ребру — вырожденный результат (area=0)");
        assertEquals(0.0, result.area, 1e-9);
    }

    @Test
    void intersection_touchByPoint_degenerate() {
        // Квадраты касаются только в точке (10,10)
        Polygon a = square10();
        Polygon b = rect(10, 10, 20, 20);

        PolygonOperations.OperationResult result = ops.intersection(a, b);

        assertTrue(result.empty, "Касание в точке — вырожденный результат");
        assertEquals(0.0, result.area, 1e-9);
    }

    @Test
    void intersection_containedPolygon() {
        // b полностью внутри a → intersection = b
        Polygon a = square10();
        Polygon b = rect(2, 2, 8, 8);

        PolygonOperations.OperationResult result = ops.intersection(a, b);

        assertFalse(result.empty);
        assertEquals(36.0, result.area, 1e-9, "Площадь = 6×6 = 36");
    }

    @Test
    void intersection_identicalPolygons() {
        Polygon a = square10();
        Polygon b = square10();

        PolygonOperations.OperationResult result = ops.intersection(a, b);

        assertFalse(result.empty);
        assertEquals(100.0, result.area, 1e-9, "Пересечение одинаковых = площадь = 100");
    }

    @Test
    void intersection_invalidPolygon_autoRepaired() {
        // Самопересекающийся полигон (галстук-бабочка)
        Polygon bowtie = gf.createPolygon(new Coordinate[]{
                new Coordinate(0, 0), new Coordinate(10, 10),
                new Coordinate(10, 0), new Coordinate(0, 10),
                new Coordinate(0, 0)
        });
        assertFalse(bowtie.isValid(), "Полигон должен быть невалидным");

        Polygon b = rect(2, 2, 8, 8);
        // Не должно выбросить исключение; предупреждение должно быть выставлено
        PolygonOperations.OperationResult result = ops.intersection(bowtie, b);

        assertNotNull(result.validationWarning, "Должно быть предупреждение о починке");
        // После repair результат может быть валидным или пустым — главное, нет NPE/исключения
    }

    // ════════════════════════════════════════════════════
    // UNION
    // ════════════════════════════════════════════════════

    @Test
    void union_overlapping() {
        // [0,10]×[0,10] ∪ [5,15]×[0,10] = [0,15]×[0,10], площадь 150
        Polygon a = square10();
        Polygon b = rect(5, 0, 15, 10);

        PolygonOperations.OperationResult result = ops.union(a, b);

        assertFalse(result.empty);
        assertEquals(150.0, result.area, 1e-9);
    }

    @Test
    void union_disjoint_resultIsMultiPolygon() {
        // Два не пересекающихся квадрата → MultiPolygon
        Polygon a = square10();
        Polygon b = rect(20, 0, 30, 10);

        PolygonOperations.OperationResult result = ops.union(a, b);

        assertFalse(result.empty);
        assertEquals(200.0, result.area, 1e-9, "Сумма площадей = 100 + 100");
        assertNotNull(result.resultGeoJson);
    }

    @Test
    void union_contained() {
        // b внутри a → union = a
        Polygon a = square10();
        Polygon b = rect(2, 2, 8, 8);

        PolygonOperations.OperationResult result = ops.union(a, b);

        assertFalse(result.empty);
        assertEquals(100.0, result.area, 1e-9, "Объединение = площадь внешнего");
    }

    // ════════════════════════════════════════════════════
    // DIFFERENCE
    // ════════════════════════════════════════════════════

    @Test
    void difference_partialOverlap() {
        // [0,10]×[0,10] \ [5,15]×[0,10] = [0,5]×[0,10], площадь 50
        Polygon a = square10();
        Polygon b = rect(5, 0, 15, 10);

        PolygonOperations.OperationResult result = ops.difference(a, b);

        assertFalse(result.empty);
        assertEquals(50.0, result.area, 1e-9);
    }

    @Test
    void difference_noOverlap() {
        // a \ b где b не пересекает a → result = a
        Polygon a = square10();
        Polygon b = rect(20, 0, 30, 10);

        PolygonOperations.OperationResult result = ops.difference(a, b);

        assertFalse(result.empty);
        assertEquals(100.0, result.area, 1e-9, "Разность с непересекающимся = исходный");
    }

    @Test
    void difference_bContainsA_emptyResult() {
        // b полностью содержит a → a \ b = пусто
        Polygon a = rect(2, 2, 8, 8);
        Polygon b = square10();

        PolygonOperations.OperationResult result = ops.difference(a, b);

        assertTrue(result.empty, "A полностью внутри B → разность пуста");
        assertEquals(0.0, result.area, 1e-9);
    }

    @Test
    void difference_identical_emptyResult() {
        Polygon a = square10();
        Polygon b = square10();

        PolygonOperations.OperationResult result = ops.difference(a, b);

        assertTrue(result.empty, "Разность одинаковых полигонов пуста");
    }

    @Test
    void difference_holeCreated() {
        // Вырезаем квадрат из середины — получаем полигон с дыркой
        Polygon outer = square10();
        Polygon inner = rect(3, 3, 7, 7);

        PolygonOperations.OperationResult result = ops.difference(outer, inner);

        assertFalse(result.empty);
        // Площадь = 100 - 16 = 84
        assertEquals(84.0, result.area, 1e-9);
    }

    // ════════════════════════════════════════════════════
    // SYM DIFFERENCE
    // ════════════════════════════════════════════════════

    @Test
    void symDifference_partialOverlap() {
        // (A\B) ∪ (B\A) для двух полуперекрытых квадратов
        // [0,10]×[0,10] △ [5,15]×[0,10]
        // левая половина [0,5] + правая [10,15], площадь = 50 + 50 = 100
        Polygon a = square10();
        Polygon b = rect(5, 0, 15, 10);

        PolygonOperations.OperationResult result = ops.symDifference(a, b);

        assertFalse(result.empty);
        assertEquals(100.0, result.area, 1e-9);
    }

    @Test
    void symDifference_identical_empty() {
        Polygon a = square10();
        Polygon b = square10();

        PolygonOperations.OperationResult result = ops.symDifference(a, b);

        assertTrue(result.empty, "Симм. разность одинаковых = пусто");
    }

    @Test
    void symDifference_disjoint_equalsUnion() {
        Polygon a = square10();
        Polygon b = rect(20, 0, 30, 10);

        PolygonOperations.OperationResult symDiff = ops.symDifference(a, b);
        PolygonOperations.OperationResult union = ops.union(a, b);

        // Для непересекающихся A△B = A∪B
        assertEquals(union.area, symDiff.area, 1e-9);
    }

    // ════════════════════════════════════════════════════
    // BUFFER
    // ════════════════════════════════════════════════════

    @Test
    void buffer_expand_areaIncreases() {
        Polygon a = square10();
        PolygonOperations.OperationResult result = ops.buffer(a, 5.0);

        assertFalse(result.empty);
        assertTrue(result.area > 100.0, "Буфер расширяет полигон");
    }

    @Test
    void buffer_shrink_areaDecreases() {
        Polygon a = square10();
        PolygonOperations.OperationResult result = ops.buffer(a, -2.0);

        assertFalse(result.empty);
        assertTrue(result.area < 100.0, "Отрицательный буфер сужает полигон");
        // Ожидаемая площадь: примерно 6×6 = 36 (без учёта скруглений)
        assertTrue(result.area > 30.0 && result.area < 50.0);
    }

    @Test
    void buffer_shrinkToEmpty() {
        // Маленький полигон + большой отрицательный буфер → пусто
        Polygon a = rect(0, 0, 1, 1);
        PolygonOperations.OperationResult result = ops.buffer(a, -10.0);

        assertTrue(result.empty, "Полигон полностью «сжат» до пустоты");
    }

    @Test
    void buffer_zero_repairsGeometry() {
        // buffer(0) не меняет площадь валидного полигона
        Polygon a = square10();
        PolygonOperations.OperationResult result = ops.buffer(a, 0.0);

        assertFalse(result.empty);
        assertEquals(100.0, result.area, 1e-6);
    }

    @Test
    void buffer_invalidPolygon_repairedAndBuffered() {
        Polygon bowtie = gf.createPolygon(new Coordinate[]{
                new Coordinate(0, 0), new Coordinate(10, 10),
                new Coordinate(10, 0), new Coordinate(0, 10),
                new Coordinate(0, 0)
        });
        assertFalse(bowtie.isValid());

        // Не должно падать; предупреждение должно быть
        PolygonOperations.OperationResult result = ops.buffer(bowtie, 1.0);
        assertNotNull(result.validationWarning);
    }

    @Test
    void buffer_expandPreservesCenter() {
        // Центр полигона не должен смещаться при буферизации
        Polygon a = square10(); // центр (5,5)
        PolygonOperations.OperationResult result = ops.buffer(a, 2.0);

        assertFalse(result.empty);
        Coordinate centroid = result.resultGeom.getCentroid().getCoordinate();
        assertEquals(5.0, centroid.x, 1e-6, "Центроид X не должен смещаться");
        assertEquals(5.0, centroid.y, 1e-6, "Центроид Y не должен смещаться");
    }

    // ════════════════════════════════════════════════════
    // ВАЛИДАЦИЯ И ПРЕДУПРЕЖДЕНИЯ
    // ════════════════════════════════════════════════════

    @Test
    void validPolygons_noWarning() {
        Polygon a = square10();
        Polygon b = rect(5, 0, 15, 10);

        PolygonOperations.OperationResult result = ops.intersection(a, b);

        assertNull(result.validationWarning, "Для валидных полигонов предупреждений нет");
    }

    @Test
    void invalidPolygonA_warningContainsPolygonA() {
        Polygon bowtie = gf.createPolygon(new Coordinate[]{
                new Coordinate(0, 0), new Coordinate(10, 10),
                new Coordinate(10, 0), new Coordinate(0, 10),
                new Coordinate(0, 0)
        });
        Polygon b = square10();

        PolygonOperations.OperationResult result = ops.union(bowtie, b);

        assertNotNull(result.validationWarning);
        assertTrue(result.validationWarning.contains("A"), "Предупреждение должно упоминать полигон A");
    }
}