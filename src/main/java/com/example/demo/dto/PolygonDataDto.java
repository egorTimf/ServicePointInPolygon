package com.example.demo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PolygonDataDto {
    private String type = "Polygon";

    @JsonProperty("coordinates")
    private List<List<double[]>> coordinates;

    @JsonProperty("min_x")
    private Double minX;

    @JsonProperty("max_x")
    private Double maxX;

    @JsonProperty("min_y")
    private Double minY;

    @JsonProperty("max_y")
    private Double maxY;

    private Map<String, Object> properties;

    public void recalculateBounds() {
        if (coordinates == null || coordinates.isEmpty()) return;

        List<double[]> outerRing = coordinates.get(0);
        minX = outerRing.stream().mapToDouble(p -> p[0]).min().orElse(0);
        maxX = outerRing.stream().mapToDouble(p -> p[0]).max().orElse(0);
        minY = outerRing.stream().mapToDouble(p -> p[1]).min().orElse(0);
        maxY = outerRing.stream().mapToDouble(p -> p[1]).max().orElse(0);
    }
}