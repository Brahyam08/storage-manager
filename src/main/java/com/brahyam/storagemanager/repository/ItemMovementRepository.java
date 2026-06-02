package com.brahyam.storagemanager.repository;

import com.brahyam.storagemanager.entity.ItemMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ItemMovementRepository extends JpaRepository<ItemMovement, Long> {

    List<ItemMovement> findByItemId(Long itemId);

}