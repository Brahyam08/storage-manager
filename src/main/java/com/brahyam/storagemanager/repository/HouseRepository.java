package com.brahyam.storagemanager.repository;

import com.brahyam.storagemanager.entity.House;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

@Repository
public interface HouseRepository extends JpaRepository<House, Long> {
    Optional<House> findByIdAndOwnerId(Long id, Long ownerId);
    List<House> findAllByOwnerIdOrderByIdAsc(Long ownerId);
    @Query("select h from House h where h.id = :id and (h.owner.id = :userId or exists (select m.id from HouseMember m where m.house = h and m.user.id = :userId))")
    Optional<House> findAccessibleById(@Param("id") Long id, @Param("userId") Long userId);

    @Query("select h from House h where h.owner.id = :userId or exists (select m.id from HouseMember m where m.house = h and m.user.id = :userId) order by h.id")
    List<House> findAllAccessible(@Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from House h where h.id = :id and h.owner.id = :userId")
    Optional<House> findOwnedForUpdate(@Param("id") Long id, @Param("userId") Long userId);
}
