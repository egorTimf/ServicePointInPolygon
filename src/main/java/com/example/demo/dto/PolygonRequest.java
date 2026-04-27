package com.example.demo.dto;

import lombok.Data;
import java.util.List;

@Data
public class PolygonRequest {
    private List<List<Double>> coordinates;
}