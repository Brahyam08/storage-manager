package com.brahyam.storagemanager.service;

import com.brahyam.storagemanager.entity.House;
import com.brahyam.storagemanager.entity.User;
import com.brahyam.storagemanager.repository.HouseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class HouseServiceTest {

    @Mock
    private HouseRepository houseRepository;

    @InjectMocks
    private HouseService houseService;

    @Test
    void shouldCreateHouseWithNameAndOwner()
    {
        User owner = new User();
        owner.setId(1L);
        String houseName = "Casa Valencia";
        House saved = new House(10L, houseName, owner);
        when(houseRepository.save(any(House.class))).thenReturn(saved);
        House result = houseService.create(houseName, owner);
        ArgumentCaptor<House> captor = ArgumentCaptor.forClass(House.class);
        verify(houseRepository).save(captor.capture());
        assertNull(captor.getValue().getId());
        assertEquals(houseName, captor.getValue().getName());
        assertSame(owner, captor.getValue().getOwner());
        assertSame(saved, result);
    }

    @Test
    void shouldNotSaveHouseWithoutName() {
        User owner = new User();
        for (String name : new String[]{null, "", "   "}) {
            ResponseStatusException error = assertThrows(ResponseStatusException.class,
                    () -> houseService.create(name, owner));
            assertEquals(400, error.getStatusCode().value());
        }
        verifyNoInteractions(houseRepository);
    }
}
