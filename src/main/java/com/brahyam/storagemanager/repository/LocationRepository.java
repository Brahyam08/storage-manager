package com.brahyam.storagemanager.repository;

import com.brahyam.storagemanager.entity.Location;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface LocationRepository extends JpaRepository<Location, Long> {
    List<Location> findAllByHouseIdOrderByIdAsc(Long houseId);
    Optional<Location> findByIdAndHouseId(Long id, Long houseId);
    boolean existsByParentId(Long parentId);

    @Query("select l.house.id from Location l where l.id = :id")
    Optional<Long> findHouseIdById(@Param("id") Long id);
}
