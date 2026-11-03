package com.brahyam.storagemanager.repository;

import com.brahyam.storagemanager.entity.HouseMember;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HouseMemberRepository extends JpaRepository<HouseMember, Long> {
    boolean existsByHouseIdAndUserId(Long houseId, Long userId);
}
