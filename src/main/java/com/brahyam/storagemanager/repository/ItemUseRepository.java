package com.brahyam.storagemanager.repository;

import com.brahyam.storagemanager.entity.ItemUse;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface ItemUseRepository extends JpaRepository<ItemUse, Long> {
    @Query("select coalesce(sum(u.quantity), 0) from ItemUse u where u.item.id = :itemId and u.returnedAt is null")
    long activeQuantity(@Param("itemId") Long itemId);
    Optional<ItemUse> findByIdAndItemId(Long id, Long itemId);
    List<ItemUse> findByItemIdOrderByTakenAtDescIdDesc(Long itemId);
    boolean existsByItemId(Long itemId);
    boolean existsByLocationId(Long locationId);
}
