package com.brahyam.storagemanager.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Keeps existing PostgreSQL constraints compatible with inventory without a location. */
@Component
@RequiredArgsConstructor
public class UnassignedInventoryMigration implements ApplicationRunner {
    private final JdbcTemplate jdbc;

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        jdbc.execute("ALTER TABLE items ALTER COLUMN location_id DROP NOT NULL");
        jdbc.execute("ALTER TABLE item_movements ALTER COLUMN from_location_id DROP NOT NULL");
        jdbc.execute("ALTER TABLE item_consumptions ALTER COLUMN location_id DROP NOT NULL");
        jdbc.execute("ALTER TABLE item_restocks ALTER COLUMN location_id DROP NOT NULL");
        jdbc.execute("ALTER TABLE item_uses ALTER COLUMN location_id DROP NOT NULL");
        jdbc.update("UPDATE items SET house_id = (SELECT house_id FROM locations WHERE locations.id = items.location_id) "
                + "WHERE house_id IS NULL AND location_id IS NOT NULL");
    }
}
