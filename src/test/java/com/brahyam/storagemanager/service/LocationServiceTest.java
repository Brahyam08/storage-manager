package com.brahyam.storagemanager.service;

import com.brahyam.storagemanager.entity.Location;
import com.brahyam.storagemanager.entity.House;
import com.brahyam.storagemanager.entity.User;
import com.brahyam.storagemanager.dto.LocationRequest;
import com.brahyam.storagemanager.dto.LocationResponse;
import com.brahyam.storagemanager.repository.ItemRepository;
import org.springframework.web.server.ResponseStatusException;
import com.brahyam.storagemanager.repository.LocationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LocationServiceTest {

    @Mock
    private LocationRepository locationRepository;

    @Mock
    private HouseService houseService;
    @Mock
    private ItemRepository items;
    private final User user = new User();
    private final House house = new House(5L, "Casa", user);

    private void stubLocation(Long id, Location location) {
        location.setHouse(house);
        when(locationRepository.findHouseIdById(id)).thenReturn(Optional.of(house.getId()));
        when(locationRepository.findByIdAndHouseId(id, house.getId())).thenReturn(Optional.of(location));
    }

    @InjectMocks
    private LocationService locationService;

    @Test
    void shouldRetainDeletedLocationOnlyAsHistoricalReference() {
        Location location = new Location(1L, "Cajón", null);
        stubLocation(1L, location);
        locationService.deleteById(1L, user);
        assertTrue(location.isDeleted());
        assertEquals("Cajón", location.getDeletedPath());
        verify(locationRepository, never()).delete(any(Location.class));
        assertThrows(ResponseStatusException.class, () -> locationService.findById(1L, user));
    }

    @Test
    void shouldReturnLocationWhenItExists() {
        // Arrange: preparamos la ubicación que devolverá el repositorio simulado.
        Location expectedLocation = new Location(1L, "Cajón principal", "Material de uso frecuente");
        stubLocation(1L, expectedLocation);

        // Act: ejecutamos el método que queremos comprobar.
        Location result = locationService.findById(1L, user);

        // Assert: verificamos que el servicio devuelve la ubicación esperada.
        assertEquals(expectedLocation, result);
    }

    @Test
    void shouldThrowExceptionWhenLocationDoesNotExist() {
        // Arrange: indicamos que el repositorio no encuentra la ubicación.
        when(locationRepository.findHouseIdById(99L)).thenReturn(Optional.empty());

        // Act y Assert: ejecutamos el método y verificamos el error resultante.
        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> locationService.findById(99L, user)
        );

        assertEquals("Ubicación no encontrada", ((ResponseStatusException) exception).getReason());
    }

    @Test
    void shouldSaveLocation() {
        LocationRequest request = new LocationRequest();
        request.setName("  Cajón de herramientas  ");
        request.setDescription("Herramientas pequeñas");
        request.setHouseId(house.getId());
        when(houseService.lockForUpdate(house.getId(), user)).thenReturn(house);
        when(locationRepository.save(any(Location.class))).thenAnswer(invocation -> {
            Location location = invocation.getArgument(0);
            location.setId(1L);
            return location;
        });

        LocationResponse result = locationService.create(request, user);

        assertEquals("Cajón de herramientas", result.name());
        assertEquals(house.getId(), result.houseId());
        assertNull(result.parentId());
        assertEquals(result.name(), result.path());
    }

    @Test
    void shouldDeleteLocationWhenItExists() {
        // Arrange
        Location existingLocation = new Location(
                1L,
                "Cajón principal",
                "Material de uso frecuente"
        );

        stubLocation(1L, existingLocation);
        // Act
        locationService.deleteById(1L, user);
        // Assert
        assertTrue(existingLocation.isDeleted());
        verify(locationRepository).flush();
    }

    @Test
    void shouldDeleteLocationWhenItDoesNotExist() {
        //Arrange
        when(locationRepository.findHouseIdById(99L)).thenReturn(Optional.empty());
        //Act
        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> locationService.deleteById(99L, user)
        );

        assertEquals(
                "Ubicación no encontrada",
                ((ResponseStatusException) exception).getReason()
        );

        verify(locationRepository, never()).delete(any(Location.class));
    }
}
