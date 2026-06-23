package com.example.demo.indexing;

import com.example.demo.entity.PolygonEntity;
import com.example.demo.parser.GeoJsonParser;
import com.example.demo.repository.PolygonRepository;
import org.locationtech.jts.geom.Polygon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Прогрев grid-индекса при старте приложения.
 *
 * Индекс живёт в оперативной памяти и при рестарте пуст. Этот компонент
 * после полного запуска приложения (ApplicationReadyEvent) читает все
 * полигоны из БД и заносит их в индекс — чтобы запросы «точка в полигоне»
 * сразу работали через индекс, а не падали в медленный fallback.
 *
 * Закрывает ограничение, заявленное на слайде «Инженерия»:
 * «индекс не переживает рестарт → нужен прогрев из БД при старте».
 */
@Component
public class GridIndexWarmup {

    private static final Logger log = LoggerFactory.getLogger(GridIndexWarmup.class);

    private final PolygonRepository polygonRepository;
    private final GridIndex gridIndex;

    public GridIndexWarmup(PolygonRepository polygonRepository, GridIndex gridIndex) {
        this.polygonRepository = polygonRepository;
        this.gridIndex = gridIndex;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        long start = System.currentTimeMillis();
        List<PolygonEntity> entities = polygonRepository.findAll();

        int loaded = 0;
        int failed = 0;
        for (PolygonEntity entity : entities) {
            try {
                GeoJsonParser parser = new GeoJsonParser(entity.getCoordsJson(), 0);
                List<Polygon> polygons = parser.getPolygons();
                if (!polygons.isEmpty()) {
                    gridIndex.insert(polygons.get(0), entity.getId());
                    loaded++;
                } else {
                    failed++;
                }
            } catch (Exception e) {
                failed++;
                log.warn("Прогрев: не удалось загрузить полигон id={}: {}",
                        entity.getId(), e.getMessage());
            }
        }

        long ms = System.currentTimeMillis() - start;
        log.info("Прогрев grid-индекса завершён: загружено {}, пропущено {}, за {} мс",
                loaded, failed, ms);
    }
}