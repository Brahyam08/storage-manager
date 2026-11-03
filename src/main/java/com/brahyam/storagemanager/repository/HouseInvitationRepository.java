package com.brahyam.storagemanager.repository;

import com.brahyam.storagemanager.entity.HouseInvitation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import java.util.Optional;

public interface HouseInvitationRepository extends JpaRepository<HouseInvitation, Long> {
    Optional<HouseInvitation> findByHouseId(Long houseId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<HouseInvitation> findByCodeHash(String codeHash);
}
