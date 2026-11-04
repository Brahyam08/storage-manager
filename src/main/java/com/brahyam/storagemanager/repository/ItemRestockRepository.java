package com.brahyam.storagemanager.repository;

import com.brahyam.storagemanager.entity.ItemRestock;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ItemRestockRepository extends JpaRepository<ItemRestock, Long> {
    void deleteByItemId(Long itemId);
    List<ItemRestock> findByItemIdOrderByRestockedAtDescIdDesc(Long itemId);
    boolean existsByItemId(Long itemId);
    boolean existsByLocationId(Long locationId);
}
