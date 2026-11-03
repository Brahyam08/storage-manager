package com.brahyam.storagemanager.repository;

import com.brahyam.storagemanager.entity.ItemConsumption;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ItemConsumptionRepository extends JpaRepository<ItemConsumption, Long> {
    List<ItemConsumption> findByItemIdOrderByConsumedAtDescIdDesc(Long itemId);
    boolean existsByItemId(Long itemId);
    boolean existsByLocationId(Long locationId);
}
