package com.example.demo.service;

import com.example.demo.dto.PolygonDataDto;
import com.example.demo.entity.PolygonEntity;
import com.example.demo.repository.PolygonRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.locationtech.jts.geom.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@Transactional
public class PolygonService {

    @Autowired
    private PolygonRepository repository;

    @Autowired
    private ObjectMapper objectMapper;

    private final GeometryFactory geometryFactory = new GeometryFactory();

    // Создание из JTS Polygon
    public Integer createPolygonFromGeometry(Polygon jtsPolygon, String geoJson) throws Exception {
        PolygonDataDto data = convertJtsToDto(jtsPolygon);
        data.recalculateBounds();

        PolygonEntity entity = new PolygonEntity();
        entity.setCoordsJson(geoJson);
        entity.setPolygonData(objectMapper.writeValueAsString(data));
        entity.setName("Полигон #" + (repository.count() + 1));
        entity.setMinX(data.getMinX());
        entity.setMaxX(data.getMaxX());
        entity.setMinY(data.getMinY());
        entity.setMaxY(data.getMaxY());

        return repository.save(entity).getId();
    }

    // Создание из GeoJSON строки
    public Integer createPolygon(String geoJsonString) throws Exception {
        PolygonDataDto data = objectMapper.readValue(geoJsonString, PolygonDataDto.class);
        data.recalculateBounds();

        PolygonEntity entity = new PolygonEntity();
        entity.setCoordsJson(geoJsonString);
        entity.setPolygonData(objectMapper.writeValueAsString(data));
        entity.setName(data.getProperties() != null ?
                data.getProperties().getOrDefault("name", "Без имени").toString() :
                "Полигон #" + (repository.count() + 1));
        entity.setMinX(data.getMinX());
        entity.setMaxX(data.getMaxX());
        entity.setMinY(data.getMinY());
        entity.setMaxY(data.getMaxY());

        return repository.save(entity).getId();
    }

    // Получить все полигоны
    public List<PolygonEntity> getAllPolygons() {
        return repository.findAllByOrderByUpdatedAtDesc();
    }

    // Перемещение вершины
    // Правило GeoJSON: первая и последняя вершина кольца — одна и та же точка.
    // Поэтому: менять последнюю вершину напрямую запрещено (она зеркало первой),
    // а смена первой автоматически обновляет последнюю.
    public void moveVertex(Integer polygonId, int ringIndex, int vertexIndex,
                           double newX, double newY) throws Exception {
        PolygonEntity entity = repository.findById(polygonId)
                .orElseThrow(() -> new RuntimeException("Полигон не найден"));

        PolygonDataDto data = getPolygonData(entity);
        List<double[]> ring = data.getCoordinates().get(ringIndex);
        int lastIndex = ring.size() - 1;

        // Последняя вершина — дубликат первой, менять её напрямую нельзя
        if (vertexIndex == lastIndex) {
            throw new IllegalArgumentException(
                    "Нельзя перемещать последнюю вершину напрямую: она всегда совпадает с первой. " +
                            "Переместите вершину с индексом 0.");
        }

        ring.get(vertexIndex)[0] = newX;
        ring.get(vertexIndex)[1] = newY;

        // Если меняем первую вершину — синхронизируем последнюю
        if (vertexIndex == 0) {
            ring.get(lastIndex)[0] = newX;
            ring.get(lastIndex)[1] = newY;
        }

        data.recalculateBounds();
        updateEntityFromDto(entity, data);
        repository.save(entity);
    }

    // Добавление вершины после указанного индекса.
    // afterIndex — индекс существующей вершины, после которой вставляем новую.
    // Диапазон допустимых значений: 0 .. size-2 (последняя вершина — замыкающий дубликат,
    // вставка после неё не имеет смысла).
    public void addVertex(Integer polygonId, int ringIndex, int afterIndex,
                          double x, double y) throws Exception {
        PolygonEntity entity = repository.findById(polygonId).orElseThrow();
        PolygonDataDto data = getPolygonData(entity);

        List<double[]> ring = data.getCoordinates().get(ringIndex);
        int lastIndex = ring.size() - 1; // замыкающий дубликат

        if (afterIndex < 0 || afterIndex >= lastIndex) {
            throw new IllegalArgumentException(
                    "afterIndex должен быть в диапазоне 0.." + (lastIndex - 1) +
                            ". Вставка после замыкающей вершины запрещена.");
        }

        ring.add(afterIndex + 1, new double[]{x, y});
        data.recalculateBounds();

        updateEntityFromDto(entity, data);
        repository.save(entity);
    }

    // Удаление вершины.
    // Нельзя удалять первую (индекс 0) или последнюю (замыкающий дубликат) вершины напрямую —
    // это нарушит замкнутость кольца.
    // Минимальное кольцо GeoJSON: 4 точки (3 уникальных + замыкающий дубликат).
    public void removeVertex(Integer polygonId, int ringIndex, int vertexIndex) throws Exception {
        PolygonEntity entity = repository.findById(polygonId).orElseThrow();
        PolygonDataDto data = getPolygonData(entity);

        List<double[]> ring = data.getCoordinates().get(ringIndex);
        int lastIndex = ring.size() - 1;

        if (vertexIndex == lastIndex) {
            throw new IllegalArgumentException(
                    "Нельзя удалять последнюю вершину напрямую: она является замыкающим дубликатом первой. " +
                            "Удалите вершину с индексом 0, если хотите убрать эту точку.");
        }

        if (ring.size() <= 4) {
            throw new IllegalArgumentException(
                    "Полигон должен иметь минимум 3 уникальных вершины (4 точки с учётом замыкания).");
        }

        ring.remove(vertexIndex);

        // Если удалили первую вершину — новая первая должна стать и последней
        if (vertexIndex == 0) {
            double[] newFirst = ring.get(0);
            ring.set(ring.size() - 1, new double[]{newFirst[0], newFirst[1]});
        }

        data.recalculateBounds();
        updateEntityFromDto(entity, data);
        repository.save(entity);
    }

    // Удаление полигона
    public void deletePolygon(Integer id) {
        repository.deleteById(id);
    }

    // Получить DTO из entity
    public PolygonDataDto getPolygonData(PolygonEntity entity) throws Exception {
        String jsonData = entity.getPolygonData() != null ?
                entity.getPolygonData() : entity.getCoordsJson();
        return objectMapper.readValue(jsonData, PolygonDataDto.class);
    }

    // Конвертация JTS Polygon -> DTO
    private PolygonDataDto convertJtsToDto(Polygon jtsPolygon) {
        PolygonDataDto dto = new PolygonDataDto();
        dto.setType("Polygon");

        List<List<double[]>> allRings = new ArrayList<>();

        // Внешнее кольцо
        List<double[]> outerRing = new ArrayList<>();
        for (Coordinate coord : jtsPolygon.getExteriorRing().getCoordinates()) {
            outerRing.add(new double[]{coord.getX(), coord.getY()});
        }
        allRings.add(outerRing);

        // Внутренние кольца (дыры)
        for (int i = 0; i < jtsPolygon.getNumInteriorRing(); i++) {
            List<double[]> innerRing = new ArrayList<>();
            for (Coordinate coord : jtsPolygon.getInteriorRingN(i).getCoordinates()) {
                innerRing.add(new double[]{coord.getX(), coord.getY()});
            }
            allRings.add(innerRing);
        }

        dto.setCoordinates(allRings);
        return dto;
    }

    private void updateEntityFromDto(PolygonEntity entity, PolygonDataDto data) throws Exception {
        String jsonString = objectMapper.writeValueAsString(data);
        entity.setCoordsJson(jsonString);
        entity.setPolygonData(jsonString);
        entity.setMinX(data.getMinX());
        entity.setMaxX(data.getMaxX());
        entity.setMinY(data.getMinY());
        entity.setMaxY(data.getMaxY());
    }
}