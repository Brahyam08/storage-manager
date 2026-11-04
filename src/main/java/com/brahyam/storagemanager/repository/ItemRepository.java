package com.brahyam.storagemanager.repository;

import com.brahyam.storagemanager.entity.Item;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface ItemRepository extends JpaRepository<Item, Long> {
    String ACCESS = "i.owner is not null and i.visibility is not null and "
            + "(i.owner.id = :userId or i.visibility = com.brahyam.storagemanager.entity.ItemVisibility.HOUSE_SHARED) and "
            + "(i.house.owner.id = :userId or exists "
            + "(select m.id from HouseMember m where m.house = i.house and m.user.id = :userId))";

    @Query("select i from Item i where i.id = :id and " + ACCESS)
    Optional<Item> findAccessibleById(@Param("id") Long id, @Param("userId") Long userId);

    @Query("select i.house.id from Item i where i.id = :id and " + ACCESS)
    Optional<Long> findAccessibleHouseId(@Param("id") Long id, @Param("userId") Long userId);

    @Query("select i from Item i where " + ACCESS + " and "
            + "(:houseId is null or i.house.id = :houseId) and "
            + "(:search is null or lower(i.name) like lower(concat('%', cast(:search as string), '%'))) and "
            + "(:category is null or i.category = :category) and "
            + "(:locationId is null or i.location.id = :locationId) order by i.id")
    List<Item> findAccessibleWithFilters(@Param("userId") Long userId, @Param("houseId") Long houseId,
            @Param("search") String search, @Param("category") String category, @Param("locationId") Long locationId);

    List<Item> findAllByLocationId(Long locationId);
    boolean existsByLocationId(Long locationId);
    long countByLocationIdAndArchivedFalse(Long locationId);
    boolean existsByLocationIdAndArchivedFalse(Long locationId);
}
