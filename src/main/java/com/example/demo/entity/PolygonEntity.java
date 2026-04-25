package com.example.demo.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "polygon_entity")
public class PolygonEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "name")
    private String name;

    @Column(name = "coords_json", columnDefinition = "TEXT")
    private String coordsJson = "";

    @Column(name = "polygon_data", columnDefinition = "JSON")
    private String polygonData;

    @Column(name = "min_x")
    private Double minX;

    @Column(name = "max_x")
    private Double maxX;

    @Column(name = "min_y")
    private Double minY;

    @Column(name = "max_y")
    private Double maxY;

    @Version
    @Column(name = "version")
    private Integer version = 1;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ===== ГЕТТЕРЫ =====
    public Integer getId() { return id; }
    public String getName() { return name; }
    public String getCoordsJson() { return coordsJson; }
    public String getPolygonData() { return polygonData; }
    public Double getMinX() { return minX; }
    public Double getMaxX() { return maxX; }
    public Double getMinY() { return minY; }
    public Double getMaxY() { return maxY; }
    public Integer getVersion() { return version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    // ===== СЕТТЕРЫ =====
    public void setId(Integer id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setCoordsJson(String coordsJson) { this.coordsJson = coordsJson; }
    public void setPolygonData(String polygonData) { this.polygonData = polygonData; }
    public void setMinX(Double minX) { this.minX = minX; }
    public void setMaxX(Double maxX) { this.maxX = maxX; }
    public void setMinY(Double minY) { this.minY = minY; }
    public void setMaxY(Double maxY) { this.maxY = maxY; }
    public void setVersion(Integer version) { this.version = version; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}