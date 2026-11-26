package com.example.pkmapp.server.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Idempotent, lossless schema migration checks for existing MySQL databases.
 */
@Component
public class SchemaMigrationRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(SchemaMigrationRunner.class);
    private final JdbcTemplate jdbcTemplate;

    public SchemaMigrationRunner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureColumn(
                "biz_ledgers",
                "monthly_budget_cents",
                "ALTER TABLE biz_ledgers ADD COLUMN monthly_budget_cents BIGINT NOT NULL DEFAULT 0 AFTER liabilities_cents"
        );
    }

    private void ensureColumn(String tableName, String columnName, String alterSql) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND COLUMN_NAME = ?
                """,
                Integer.class,
                tableName,
                columnName
        );
        if (count != null && count == 0) {
            log.info("Applying lossless schema migration: {}", alterSql);
            jdbcTemplate.execute(alterSql);
        }
    }
}
