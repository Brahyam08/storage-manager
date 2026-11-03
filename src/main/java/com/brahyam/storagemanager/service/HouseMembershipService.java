package com.brahyam.storagemanager.service;

import com.brahyam.storagemanager.entity.*;
import com.brahyam.storagemanager.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
@Transactional
public class HouseMembershipService {
    private final HouseRepository houses;
    private final HouseInvitationRepository invitations;
    private final HouseMemberRepository members;
    private final SecureRandom random = new SecureRandom();

    public String renewInvitation(Long houseId, User user) {
        House house = houses.findOwnedForUpdate(houseId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Casa no encontrada"));
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        String code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        HouseInvitation invitation = invitations.findByHouseId(houseId).orElseGet(HouseInvitation::new);
        invitation.setHouse(house);
        invitation.setCodeHash(hash(code));
        invitations.save(invitation);
        return code;
    }

    public House join(String code, User user) {
        HouseInvitation invitation = invitations.findByCodeHash(hash(code.strip()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invitación no válida"));
        House house = invitation.getHouse();
        if (!house.getOwner().getId().equals(user.getId())
                && !members.existsByHouseIdAndUserId(house.getId(), user.getId())) {
            members.save(new HouseMember(null, house, user));
        }
        return house;
    }

    private String hash(String code) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(code.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no disponible", ex);
        }
    }
}
