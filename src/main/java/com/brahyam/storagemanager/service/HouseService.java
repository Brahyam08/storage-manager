package com.brahyam.storagemanager.service;

import com.brahyam.storagemanager.entity.House;
import com.brahyam.storagemanager.entity.User;
import com.brahyam.storagemanager.repository.HouseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HouseService {
    private final HouseRepository houseRepository;

    @Transactional
    public House create(String name, User owner) {
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El nombre es obligatorio");
        }
        House house = new House();
        house.setName(name.strip());
        house.setOwner(owner);
        return houseRepository.save(house);
    }

    public House findById(Long id, User user) {
        return houseRepository.findByIdAndOwnerId(id, user.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Casa no encontrada"));
    }

    public List<House> findAll(User user) {
        return houseRepository.findAllByOwnerIdOrderByIdAsc(user.getId());
    }
}
