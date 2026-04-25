package com.example.demo.repository;

import com.example.demo.entity.PolygonEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PolygonRepository extends JpaRepository<PolygonEntity, Integer> {

    boolean existsByCoordsJson(String coordsJson);

    @Query(value = """
        SELECT * FROM polygon_entity 
        WHERE min_x <= :maxX AND max_x >= :minX 
          AND min_y <= :maxY AND max_y >= :minY
        """, nativeQuery = true)
    List<PolygonEntity> findInBoundingBox(
            @Param("minX") double minX,
            @Param("maxX") double maxX,
            @Param("minY") double minY,
            @Param("maxY") double maxY
    );

    List<PolygonEntity> findByNameContaining(String name);

    List<PolygonEntity> findAllByOrderByUpdatedAtDesc();
}