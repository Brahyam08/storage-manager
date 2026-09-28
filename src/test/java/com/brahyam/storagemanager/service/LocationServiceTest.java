package com.brahyam.storagemanager.service;

import com.brahyam.storagemanager.entity.Location;
import com.brahyam.storagemanager.repository.LocationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LocationServiceTest {

    @Mock
    private LocationRepository locationRepository;

    @InjectMocks
    private LocationService locationService;

    @Test
    void shouldReturnLocationWhenItExists() {
        // Arrange: preparamos la ubicación que devolverá el repositorio simulado.
        Location expectedLocation = new Location(1L, "Cajón principal", "Material de uso frecuente");
        when(locationRepository.findById(1L)).thenReturn(Optional.of(expectedLocation));

        // Act: ejecutamos el método que queremos comprobar.
        Location result = locationService.findById(1L);

        // Assert: verificamos que el servicio devuelve la ubicación esperada.
        assertEquals(expectedLocation, result);
    }

    @Test
    void shouldThrowExceptionWhenLocationDoesNotExist() {
        // Arrange: indicamos que el repositorio no encuentra la ubicación.
        when(locationRepository.findById(99L)).thenReturn(Optional.empty());

        // Act y Assert: ejecutamos el método y verificamos el error resultante.
        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> locationService.findById(99L)
        );

        assertEquals("Ubicación no encontrada", exception.getMessage());
    }

    @Test
    void shouldSaveLocation() {
        //arrange
        Location locationToSave = new Location(
                null,
                "cajon de herramientas",
                "Herramientas pequeñas"
        );

        Location savedLocation = new Location(
                1L,
                "Cajón de herramientas",
                "Herramientas pequeñas"
        );

        when(locationRepository.save(locationToSave)).thenReturn(savedLocation);
        //act
        Location result = locationService.save(locationToSave);
        //assert
        assertEquals(savedLocation, result);
    }

    @Test
    void shouldDeleteLocationWhenItExists() {
        // Arrange
        Location existingLocation = new Location(
                1L,
                "Cajón principal",
                "Material de uso frecuente"
        );

        when(locationRepository.findById(1L)).thenReturn(Optional.of(existingLocation));
        // Act
        locationService.deleteById(1L);
        // Assert
        verify(locationRepository).deleteById(1L);
    }

    @Test
    void shouldDeleteLocationWhenItDoesNotExist() {
        //Arrange
        when(locationRepository.findById(99L)).thenReturn(Optional.empty());
        //Act
        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> locationService.deleteById(99L)
        );

        assertEquals(
                "Ubicación no encontrada",
                exception.getMessage()
        );

        verify(locationRepository, never()).deleteById(99L);
    }
}
