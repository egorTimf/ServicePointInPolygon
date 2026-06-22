package com.example.demo.indexing;

import com.example.demo.geometry.PointInPolygon;
import org.locationtech.jts.geom.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class GridIndex {

    private final double cellSize;
    private final Map<String, Set<Integer>> grid = new ConcurrentHashMap<>();
    private final Map<Integer, Polygon> idPolygon = new ConcurrentHashMap<>();
    private final GeometryFactory geometryFactory = new GeometryFactory();

    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock.ReadLock  readLock  = rwLock.readLock();
    private final ReentrantReadWriteLock.WriteLock writeLock = rwLock.writeLock();

    public GridIndex(double cellSize) {
        this.cellSize = cellSize;
    }

    private String getCell(double x, double y) {
        int cellX = (int) Math.floor(x / cellSize);
        int cellY = (int) Math.floor(y / cellSize);
        return cellX + ":" + cellY;
    }

    private void insertUnsafe(Polygon polygon, int id) {
        Envelope box = polygon.getEnvelopeInternal();
        int minX = (int) Math.floor(box.getMinX() / cellSize);
        int maxX = (int) Math.floor(box.getMaxX() / cellSize);
        int minY = (int) Math.floor(box.getMinY() / cellSize);
        int maxY = (int) Math.floor(box.getMaxY() / cellSize);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                grid.computeIfAbsent(x + ":" + y, k -> ConcurrentHashMap.newKeySet()).add(id);
            }
        }
        idPolygon.put(id, polygon);
    }

    private void removeUnsafe(int id) {
        Polygon polygon = idPolygon.remove(id);
        if (polygon == null) return;

        Envelope box = polygon.getEnvelopeInternal();
        int minX = (int) Math.floor(box.getMinX() / cellSize);
        int maxX = (int) Math.floor(box.getMaxX() / cellSize);
        int minY = (int) Math.floor(box.getMinY() / cellSize);
        int maxY = (int) Math.floor(box.getMaxY() / cellSize);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                String key = x + ":" + y;
                Set<Integer> ids = grid.get(key);
                if (ids != null) {
                    ids.remove(id);
                    if (ids.isEmpty()) grid.remove(key);
                }
            }
        }
    }

    public void insert(Polygon polygon, int id) {
        writeLock.lock();
        try {
            insertUnsafe(polygon, id);
        } finally {
            writeLock.unlock();
        }
    }

    public void remove(int id) {
        writeLock.lock();
        try {
            removeUnsafe(id);
        } finally {
            writeLock.unlock();
        }
    }


    public void update(int id, Polygon newPolygon) {
        writeLock.lock();
        try {
            removeUnsafe(id);
            insertUnsafe(newPolygon, id);
        } finally {
            writeLock.unlock();
        }
    }

    public boolean contains(Point point, int id) {
        readLock.lock();
        try {
            Polygon polygon = idPolygon.get(id);
            if (polygon == null) return false;

            String key = getCell(point.getX(), point.getY());
            Set<Integer> cellIds = grid.get(key);
            if (cellIds == null || !cellIds.contains(id)) return false;

            return PointInPolygon.contains(polygon, point);
        } finally {
            readLock.unlock();
        }
    }

    public void clear() {
        writeLock.lock();
        try {
            grid.clear();
            idPolygon.clear();
        } finally {
            writeLock.unlock();
        }
    }

    public int size() {
        readLock.lock();
        try {
            return idPolygon.size();
        } finally {
            readLock.unlock();
        }
    }
}