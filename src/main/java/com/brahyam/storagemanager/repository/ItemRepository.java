package com.brahyam.storagemanager.repository;

import com.brahyam.storagemanager.entity.Item;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ItemRepository extends JpaRepository<Item, Long> {

    @Query("SELECT i FROM Item i WHERE " +
            "(:search IS NULL OR LOWER(i.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))) AND " +
            "(:category IS NULL OR i.category = :category) AND " +
            "(:locationId IS NULL OR i.location.id = :locationId)")
    List<Item> findWithFilters(@Param("search") String search,
                               @Param("category") String category,
                               @Param("locationId") Long locationId);

}