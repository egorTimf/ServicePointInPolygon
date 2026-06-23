package com.example.demo;

import com.example.demo.indexing.GridIndex;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class PolygonApplication {
    public static void main(String[] args) {
        SpringApplication.run(PolygonApplication.class, args);
    }
    @Bean
    public GridIndex gridIndex() {
        return new GridIndex(50);
    }
}