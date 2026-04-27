package com.example.demo.dto;

import lombok.Data;
import java.util.List;

@Data
public class CheckMultipleRequest {
    private List<Integer> polygonIds;
    private double x;
    private double y;
}