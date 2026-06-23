package com.example.demo.benchmark;

import com.example.demo.geometry.PointInPolygon;
import com.example.demo.indexing.GridIndex;
import org.locationtech.jts.geom.*;

import java.util.*;

/**
 * Микро-бенчмарк главного сценария «точка против N полигонов».
 *
 * Сравнивает два подхода:
 *   1) LINEAR — линейный перебор всех полигонов через PointInPolygon.contains.
 *   2) GRID   — НАСТОЯЩИЙ класс GridIndex: вызываем gridIndex.queryContaining(point),
 *               который сам отбирает кандидатов из ячейки точки и проверяет их.
 *
 * Чтобы цифры были честными:
 *   - оба метода ищут ВСЕ содержащие точку полигоны (одинаковая работа);
 *   - перед замером сверяем, что оба метода дают одинаковый ответ (CHECK);
 *   - прогрев JIT отдельным прогоном;
 *   - каждый замер повторяется REPEATS раз, берём МЕДИАНУ (устойчива к выбросам:
 *     GC, фоновые процессы, троттлинг). Печатаем также min/max для оценки разброса.
 *
 * Это НЕ JUnit-тест — он печатает время, а не проверяет assert'ы,
 * поэтому запускается как обычный main-класс.
 *
 * Запуск:
 *   - из IDE: правый клик по классу -> Run 'PipBenchmark.main()';
 *   - либо через Gradle-задачу runBenchmark (см. инструкцию).
 */
public class PipBenchmark {

    private static final GeometryFactory GF = new GeometryFactory();

    private static final int[]  POLYGON_COUNTS = {100, 1_000, 10_000};
    private static final int    QUERIES        = 20_000; // точек-запросов на один замер
    private static final int    REPEATS        = 7;      // повторов замера (для медианы)
    private static final int    WARMUP_RUNS    = 3;      // прогревочных прогонов (JIT)
    private static final double WORLD          = 1_000;
    private static final double CELL_SIZE      = 50;     // как в PolygonController: new GridIndex(50)
    private static final long   SEED           = 42;

    public static void main(String[] args) {
        System.out.println("=== PIP Benchmark: линейный перебор vs GridIndex ===");
        System.out.printf(Locale.US,
                "Мир %.0fx%.0f, ячейка %.0f, запросов на замер: %d, повторов: %d%n",
                WORLD, WORLD, CELL_SIZE, QUERIES, REPEATS);

        System.out.printf("%-10s | %-22s | %-22s | %-9s%n",
                "Полигонов", "Линейно, мс", "Индекс, мс", "Ускорение");
        System.out.println("-".repeat(72));

        for (int count : POLYGON_COUNTS) {
            Random rnd = new Random(SEED);

            // 1. Генерируем полигоны и параллельно заполняем настоящий GridIndex
            List<Polygon> polygons = new ArrayList<>(count);
            GridIndex index = new GridIndex(CELL_SIZE);
            for (int i = 0; i < count; i++) {
                Polygon poly = randomSquare(rnd);
                polygons.add(poly);
                index.insert(poly, i); // тот же insert, что использует контроллер
            }

            // 2. Запросы-точки (одни и те же для обоих методов)
            double[][] points = new double[QUERIES][2];
            for (int q = 0; q < QUERIES; q++) {
                points[q][0] = rnd.nextDouble() * WORLD;
                points[q][1] = rnd.nextDouble() * WORLD;
            }

            // 3. СВЕРКА: оба метода должны давать одинаковый результат на каждой точке.
            //    Если расходятся — бенчмарк бессмыслен, останавливаемся с понятной ошибкой.
            verifySameResults(polygons, index, points);

            // 4. Прогрев JIT (результаты не засекаем)
            for (int w = 0; w < WARMUP_RUNS; w++) {
                runLinear(polygons, points);
                runGrid(index, points);
            }

            // 5. Замеры: REPEATS повторов, берём медиану
            long[] linear = new long[REPEATS];
            long[] grid   = new long[REPEATS];
            for (int r = 0; r < REPEATS; r++) {
                linear[r] = time(() -> runLinear(polygons, points));
                grid[r]   = time(() -> runGrid(index, points));
            }

            double linMed = median(linear) / 1e6;
            double linMin = min(linear)    / 1e6;
            double linMax = max(linear)    / 1e6;
            double grdMed = median(grid)   / 1e6;
            double grdMin = min(grid)      / 1e6;
            double grdMax = max(grid)      / 1e6;

            double speedup = grdMed == 0 ? 0 : linMed / grdMed;

            System.out.printf(Locale.US,
                    "%-10d | %7.4f | %7.4f | %.1fx%n",
                    count,
                    linMed,
                    grdMed,
                    speedup);
        }

        System.out.println();
    }

    // ── Линейный перебор: считаем ВСЕ полигоны, содержащие точку ────────────
    private static int runLinear(List<Polygon> polygons, double[][] points) {
        int total = 0;
        for (double[] pt : points) {
            Point p = GF.createPoint(new Coordinate(pt[0], pt[1]));
            for (Polygon poly : polygons) {
                if (PointInPolygon.contains(poly, p)) total++;
            }
        }
        return total;
    }

    // ── Через GridIndex: queryContaining тоже возвращает ВСЕ содержащие ──────
    private static int runGrid(GridIndex index, double[][] points) {
        int total = 0;
        for (double[] pt : points) {
            Point p = GF.createPoint(new Coordinate(pt[0], pt[1]));
            total += index.queryContaining(p).size();
        }
        return total;
    }

    // ── Сверка: на каждой точке множество найденных полигонов должно совпасть ─
    private static void verifySameResults(List<Polygon> polygons,
                                          GridIndex index,
                                          double[][] points) {
        for (double[] pt : points) {
            Point p = GF.createPoint(new Coordinate(pt[0], pt[1]));

            // эталон — линейным перебором
            Set<Integer> expected = new HashSet<>();
            for (int i = 0; i < polygons.size(); i++) {
                if (PointInPolygon.contains(polygons.get(i), p)) expected.add(i);
            }

            Set<Integer> actual = new HashSet<>(index.queryContaining(p));

            if (!expected.equals(actual)) {
                throw new IllegalStateException(String.format(Locale.US,
                        "Расхождение методов в точке (%.3f, %.3f): линейно=%s, индекс=%s",
                        pt[0], pt[1], expected, actual));
            }
        }
    }

    // ── Генерация одного случайного квадрата ────────────────────────────────
    private static Polygon randomSquare(Random rnd) {
        double s = 5 + rnd.nextDouble() * 10;
        double ox = rnd.nextDouble() * (WORLD - s);
        double oy = rnd.nextDouble() * (WORLD - s);
        Coordinate[] ring = {
                new Coordinate(ox, oy),
                new Coordinate(ox + s, oy),
                new Coordinate(ox + s, oy + s),
                new Coordinate(ox, oy + s),
                new Coordinate(ox, oy)
        };
        return GF.createPolygon(ring);
    }

    // ── Утилиты времени и статистики ────────────────────────────────────────
    private static long time(Runnable r) {
        long start = System.nanoTime();
        r.run();
        return System.nanoTime() - start;
    }

    private static double median(long[] a) {
        long[] s = a.clone();
        Arrays.sort(s);
        int n = s.length;
        return (n % 2 == 1) ? s[n / 2] : (s[n / 2 - 1] + s[n / 2]) / 2.0;
    }

    private static long min(long[] a) {
        long m = Long.MAX_VALUE;
        for (long v : a) m = Math.min(m, v);
        return m;
    }

    private static long max(long[] a) {
        long m = Long.MIN_VALUE;
        for (long v : a) m = Math.max(m, v);
        return m;
    }
}