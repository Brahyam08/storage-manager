package com.brahyam.storagemanager.service;

import com.brahyam.storagemanager.config.JwtService;
import com.brahyam.storagemanager.entity.*;
import com.brahyam.storagemanager.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:item-movement-transactions;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ItemMovementTransactionTest {
    @Autowired private MockMvc mvc;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private UserRepository users;
    @Autowired private HouseRepository houses;
    @Autowired private LocationRepository locations;
    @MockitoSpyBean private ItemRepository items;
    @Autowired private ItemMovementRepository movements;
    @Autowired private JwtService jwt;
    private Long itemId;
    private Long sourceId;
    private Long firstTargetId;
    private Long secondTargetId;
    private String token;

    @BeforeEach
    void setUp() {
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            User owner = users.save(new User(null, "owner", "owner@transaction.test", "unused", Role.VIEWER));
            House house = houses.save(new House(null, "Casa", owner));
            Location source = location("Origen", house);
            Location first = location("Primero", house);
            Location second = location("Segundo", house);
            Item item = new Item(null, "Ratón", null, null, 3, source);
            item.setOwner(owner);
            itemId = items.saveAndFlush(item).getId();
            sourceId = source.getId();
            firstTargetId = first.getId();
            secondTargetId = second.getId();
            token = "Bearer " + jwt.generateToken(owner.getEmail());
        });
    }

    private Location location(String name, House house) {
        Location location = new Location(null, name, null);
        location.setHouse(house);
        return locations.save(location);
    }

    @AfterEach
    void cleanUp() {
        reset(items);
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            movements.deleteAll();
            items.deleteAll();
            locations.deleteAll();
            houses.deleteAll();
            users.deleteAll();
        });
    }

    @Test
    void shouldRollBackLocationAndHistoryWhenSavingItemFails() throws Exception {
        doAnswer(invocation -> {
            // The count flushes the new history and dirty item inside the transaction.
            assertEquals(1, movements.count());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Fallo simulado");
        }).when(items).save(any(Item.class));
        mvc.perform(patch("/api/items/{id}/move", itemId).header("Authorization", token)
                        .param("toLocationId", firstTargetId.toString()).param("quantity", "3"))
                .andExpect(status().isServiceUnavailable());
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            assertEquals(sourceId, items.findById(itemId).orElseThrow().getLocation().getId());
            assertEquals(3, items.findById(itemId).orElseThrow().getQuantity());
            assertEquals(0, movements.count());
        });
    }

    @Test
    void shouldSerializeConcurrentMovesAndKeepAConsistentHistoryChain() throws Exception {
        ExecutorService workers = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Integer> first = concurrentMove(firstTargetId, ready, start);
            Callable<Integer> second = concurrentMove(secondTargetId, ready, start);
            Future<Integer> firstResult = workers.submit(first);
            Future<Integer> secondResult = workers.submit(second);
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            assertEquals(200, firstResult.get(10, TimeUnit.SECONDS));
            assertEquals(200, secondResult.get(10, TimeUnit.SECONDS));
        } finally {
            start.countDown();
            workers.shutdownNow();
        }
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            var history = movements.findByItemIdOrderByMovedAtDescIdDesc(itemId);
            assertEquals(2, history.size());
            assertEquals(sourceId, history.get(1).getFromLocation().getId());
            assertEquals(history.get(1).getToLocation().getId(), history.get(0).getFromLocation().getId());
            assertEquals(history.get(0).getToLocation().getId(), items.findById(itemId).orElseThrow().getLocation().getId());
            assertTrue(history.stream().allMatch(movement -> movement.getQuantity() == 3));
        });
    }

    private Callable<Integer> concurrentMove(Long targetId, CountDownLatch ready, CountDownLatch start) {
        return () -> {
            ready.countDown();
            if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("No se iniciaron ambos movimientos");
            return mvc.perform(patch("/api/items/{id}/move", itemId).header("Authorization", token)
                            .param("toLocationId", targetId.toString()).param("quantity", "3"))
                    .andReturn().getResponse().getStatus();
        };
    }
}
