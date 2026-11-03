package com.brahyam.storagemanager.repository;

import com.brahyam.storagemanager.entity.House;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface HouseRepository extends JpaRepository<House, Long> {
    Optional<House> findByIdAndOwnerId(Long id, Long ownerId);
    List<House> findAllByOwnerIdOrderByIdAsc(Long ownerId);
}
