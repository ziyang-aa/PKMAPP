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
        ensureRecurringRulesTable();
    }

    private void ensureRecurringRulesTable() {
        jdbcTemplate.execute(
                """
                CREATE TABLE IF NOT EXISTS biz_recurring_rules (
                  user_id                 VARCHAR(36)  NOT NULL,
                  id                      VARCHAR(64)  NOT NULL,
                  ledger_id               VARCHAR(64)  NOT NULL,
                  title                   VARCHAR(64)  NOT NULL,
                  type                    VARCHAR(16)  NOT NULL,
                  category                VARCHAR(64)  NOT NULL,
                  amount_cents            BIGINT       NOT NULL,
                  frequency               VARCHAR(16)  NOT NULL,
                  day_of_month            INT          NOT NULL DEFAULT 1,
                  day_of_week             INT          NOT NULL DEFAULT 1,
                  month_of_year           INT          NOT NULL DEFAULT 1,
                  start_date_key          VARCHAR(16)  NOT NULL,
                  last_generated_date_key VARCHAR(16)  NOT NULL DEFAULT '',
                  auto_post               TINYINT(1)   NOT NULL DEFAULT 1,
                  enabled                 TINYINT(1)   NOT NULL DEFAULT 1,
                  note                    VARCHAR(255) NOT NULL DEFAULT '',
                  sort_order              INT          NOT NULL DEFAULT 0,
                  client_request_id       VARCHAR(64)  NOT NULL,
                  updated_at              BIGINT       NOT NULL,
                  deleted_at              BIGINT       NOT NULL DEFAULT 0,
                  server_seq              BIGINT       NOT NULL,
                  PRIMARY KEY (user_id, id),
                  UNIQUE KEY uk_rec_req (user_id, client_request_id),
                  INDEX idx_rec_seq (user_id, server_seq)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """
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
