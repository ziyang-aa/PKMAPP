CREATE DATABASE IF NOT EXISTS pkmapp
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE pkmapp;

-- 1. 用户主表（支持手机号/邮箱密码登录，预留微信小程序 openid 绑定）
CREATE TABLE IF NOT EXISTS sys_users (
  id                 VARCHAR(36)  PRIMARY KEY,
  phone              VARCHAR(24)  NULL UNIQUE,
  email              VARCHAR(128) NULL UNIQUE,
  wx_openid          VARCHAR(64)  NULL UNIQUE,
  password_hash      VARCHAR(128) NULL,
  nickname           VARCHAR(32)  NOT NULL DEFAULT '森林守护员',
  avatar_url         VARCHAR(512) NOT NULL DEFAULT '',
  checked_dates_json JSON         NOT NULL,
  status             TINYINT      NOT NULL DEFAULT 1 COMMENT '1=正常 0=已注销/禁用',
  created_at         BIGINT       NOT NULL,
  updated_at         BIGINT       NOT NULL,
  INDEX idx_users_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. 刷新令牌表（替代 Redis 存储可吊销会话）
CREATE TABLE IF NOT EXISTS sys_user_tokens (
  id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id            VARCHAR(36)  NOT NULL,
  refresh_token_hash CHAR(64)     NOT NULL UNIQUE,
  device_info        VARCHAR(128) NOT NULL DEFAULT '',
  expires_at         BIGINT       NOT NULL,
  revoked            TINYINT(1)   NOT NULL DEFAULT 0,
  created_at         BIGINT       NOT NULL,
  INDEX idx_tokens_user (user_id, revoked)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. 全局同步序列发号器（保证增量 pull 无时钟漂移遗漏）
CREATE TABLE IF NOT EXISTS sys_sync_seq (
  seq BIGINT AUTO_INCREMENT PRIMARY KEY
) ENGINE=InnoDB;

-- 4. 账本表
CREATE TABLE IF NOT EXISTS biz_ledgers (
  user_id              VARCHAR(36) NOT NULL,
  id                   VARCHAR(64) NOT NULL,
  name                 VARCHAR(64) NOT NULL,
  initial_assets_cents BIGINT      NOT NULL DEFAULT 0,
  liabilities_cents    BIGINT      NOT NULL DEFAULT 0,
  monthly_budget_cents BIGINT      NOT NULL DEFAULT 0,
  is_default           TINYINT(1)  NOT NULL DEFAULT 0,
  is_current           TINYINT(1)  NOT NULL DEFAULT 0,
  sort_order           INT         NOT NULL DEFAULT 0,
  client_request_id    VARCHAR(64) NOT NULL,
  updated_at           BIGINT      NOT NULL,
  deleted_at           BIGINT      NOT NULL DEFAULT 0,
  server_seq           BIGINT      NOT NULL,
  PRIMARY KEY (user_id, id),
  UNIQUE KEY uk_ledger_req (user_id, client_request_id),
  INDEX idx_ledger_seq (user_id, server_seq)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. 收支流水表
CREATE TABLE IF NOT EXISTS biz_transactions (
  user_id           VARCHAR(36)  NOT NULL,
  id                VARCHAR(64)  NOT NULL,
  ledger_id         VARCHAR(64)  NOT NULL,
  type              VARCHAR(16)  NOT NULL COMMENT 'EXPENSE / INCOME',
  category          VARCHAR(64)  NOT NULL,
  amount_cents      BIGINT       NOT NULL,
  occurred_at       BIGINT       NOT NULL,
  note              VARCHAR(255) NOT NULL DEFAULT '',
  sort_order        INT          NOT NULL DEFAULT 0,
  client_request_id VARCHAR(64)  NOT NULL,
  updated_at        BIGINT       NOT NULL,
  deleted_at        BIGINT       NOT NULL DEFAULT 0,
  server_seq        BIGINT       NOT NULL,
  PRIMARY KEY (user_id, id),
  UNIQUE KEY uk_tx_req (user_id, client_request_id),
  INDEX idx_tx_ledger_time (user_id, ledger_id, occurred_at),
  INDEX idx_tx_seq (user_id, server_seq)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. 借还往来表
CREATE TABLE IF NOT EXISTS biz_borrowing_records (
  user_id               VARCHAR(36)  NOT NULL,
  id                    VARCHAR(64)  NOT NULL,
  ledger_id             VARCHAR(64)  NOT NULL,
  direction             VARCHAR(16)  NOT NULL COMMENT 'LEND / BORROW',
  counterparty          VARCHAR(64)  NOT NULL,
  amount_cents          BIGINT       NOT NULL,
  occurred_at           BIGINT       NOT NULL,
  note                  VARCHAR(255) NOT NULL DEFAULT '',
  source_transaction_id VARCHAR(64)  NULL,
  settled               TINYINT(1)   NOT NULL DEFAULT 0,
  client_request_id     VARCHAR(64)  NOT NULL,
  updated_at            BIGINT       NOT NULL,
  deleted_at            BIGINT       NOT NULL DEFAULT 0,
  server_seq            BIGINT       NOT NULL,
  PRIMARY KEY (user_id, id),
  UNIQUE KEY uk_borrow_req (user_id, client_request_id),
  INDEX idx_borrow_seq (user_id, server_seq)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 7. 攒钱目标表
CREATE TABLE IF NOT EXISTS biz_savings_goals (
  user_id           VARCHAR(36) NOT NULL,
  id                VARCHAR(64) NOT NULL,
  title             VARCHAR(64) NOT NULL,
  target_cents      BIGINT      NOT NULL,
  saved_cents       BIGINT      NOT NULL DEFAULT 0,
  deadline          VARCHAR(32) NOT NULL DEFAULT '',
  created_at_millis BIGINT      NOT NULL,
  sort_order        INT         NOT NULL DEFAULT 0,
  client_request_id VARCHAR(64) NOT NULL,
  updated_at        BIGINT      NOT NULL,
  deleted_at        BIGINT      NOT NULL DEFAULT 0,
  server_seq        BIGINT      NOT NULL,
  PRIMARY KEY (user_id, id),
  UNIQUE KEY uk_goal_req (user_id, client_request_id),
  INDEX idx_goal_seq (user_id, server_seq)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 8. 自定义分类表
CREATE TABLE IF NOT EXISTS biz_custom_categories (
  user_id            VARCHAR(36) NOT NULL,
  type               VARCHAR(16) NOT NULL,
  name               VARCHAR(64) NOT NULL,
  is_deleted_default TINYINT(1)  NOT NULL DEFAULT 0,
  sort_order         INT         NOT NULL DEFAULT 0,
  client_request_id  VARCHAR(64) NOT NULL,
  updated_at         BIGINT      NOT NULL,
  deleted_at         BIGINT      NOT NULL DEFAULT 0,
  server_seq         BIGINT      NOT NULL,
  PRIMARY KEY (user_id, type, name),
  UNIQUE KEY uk_cat_req (user_id, client_request_id),
  INDEX idx_cat_seq (user_id, server_seq)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 9. 学习计划 / 科目 / 记录 / 任务 / 复盘 / 林地状态 / 待办
CREATE TABLE IF NOT EXISTS biz_study_plans (
  user_id             VARCHAR(36) NOT NULL,
  id                  VARCHAR(64) NOT NULL,
  title               VARCHAR(64) NOT NULL,
  kind                VARCHAR(24) NOT NULL,
  due                 VARCHAR(32) NOT NULL DEFAULT '',
  partner             VARCHAR(24) NOT NULL DEFAULT 'SQUIRREL',
  previous_plan_id    VARCHAR(64) NOT NULL DEFAULT '',
  created_at          BIGINT      NOT NULL,
  archived_at         BIGINT      NOT NULL DEFAULT 0,
  selected_subject_id VARCHAR(64) NOT NULL DEFAULT '',
  session_goal        INT         NOT NULL DEFAULT 45,
  daily_goal          INT         NOT NULL DEFAULT 180,
  remind_deadlines    TINYINT(1)  NOT NULL DEFAULT 0,
  remind_time         VARCHAR(16) NOT NULL DEFAULT '18:00',
  is_current          TINYINT(1)  NOT NULL DEFAULT 0,
  sort_order          INT         NOT NULL DEFAULT 0,
  client_request_id   VARCHAR(64) NOT NULL,
  updated_at          BIGINT      NOT NULL,
  deleted_at          BIGINT      NOT NULL DEFAULT 0,
  server_seq          BIGINT      NOT NULL,
  PRIMARY KEY (user_id, id),
  INDEX idx_plan_seq (user_id, server_seq)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS biz_study_subjects (
  user_id           VARCHAR(36)  NOT NULL,
  plan_id           VARCHAR(64)  NOT NULL,
  id                VARCHAR(64)  NOT NULL,
  title             VARCHAR(64)  NOT NULL,
  hint              VARCHAR(128) NOT NULL DEFAULT '',
  due               VARCHAR(32)  NOT NULL DEFAULT '',
  assessment        VARCHAR(64)  NOT NULL DEFAULT '',
  color             INT          NOT NULL,
  sort_order        INT          NOT NULL DEFAULT 0,
  client_request_id VARCHAR(64)  NOT NULL,
  updated_at        BIGINT       NOT NULL,
  deleted_at        BIGINT       NOT NULL DEFAULT 0,
  server_seq        BIGINT       NOT NULL,
  PRIMARY KEY (user_id, plan_id, id),
  INDEX idx_sub_seq (user_id, server_seq)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS biz_study_records (
  user_id                     VARCHAR(36)  NOT NULL,
  id                          VARCHAR(64)  NOT NULL,
  plan_id                     VARCHAR(64)  NOT NULL,
  subject_id                  VARCHAR(64)  NOT NULL,
  subject_title_snapshot      VARCHAR(64)  NOT NULL DEFAULT '',
  subject_hint_snapshot       VARCHAR(128) NOT NULL DEFAULT '',
  subject_color_snapshot      INT          NOT NULL DEFAULT 0,
  subject_due_snapshot        VARCHAR(32)  NOT NULL DEFAULT '',
  subject_assessment_snapshot VARCHAR(64)  NOT NULL DEFAULT '',
  duration_millis             BIGINT       NOT NULL,
  completed_at                BIGINT       NOT NULL,
  note                        VARCHAR(500) NOT NULL DEFAULT '',
  `manual`                    TINYINT(1)   NOT NULL DEFAULT 0,
  reward_millis               BIGINT       NOT NULL DEFAULT 0,
  edited_at                   BIGINT       NOT NULL DEFAULT 0,
  adjusted                    TINYINT(1)   NOT NULL DEFAULT 0,
  task_id                     VARCHAR(64)  NOT NULL DEFAULT '',
  sort_order                  INT          NOT NULL DEFAULT 0,
  client_request_id           VARCHAR(64)  NOT NULL,
  updated_at                  BIGINT       NOT NULL,
  deleted_at                  BIGINT       NOT NULL DEFAULT 0,
  server_seq                  BIGINT       NOT NULL,
  PRIMARY KEY (user_id, id),
  UNIQUE KEY uk_sr_req (user_id, client_request_id),
  INDEX idx_sr_seq (user_id, server_seq)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS biz_study_tasks (
  user_id             VARCHAR(36)  NOT NULL,
  id                  VARCHAR(64)  NOT NULL,
  plan_id             VARCHAR(64)  NOT NULL,
  subject_id          VARCHAR(64)  NOT NULL,
  title               VARCHAR(160) NOT NULL,
  due                 VARCHAR(32)  NOT NULL DEFAULT '',
  due_time            VARCHAR(16)  NOT NULL DEFAULT '',
  remind              TINYINT(1)   NOT NULL DEFAULT 0,
  remind_lead_minutes INT          NOT NULL DEFAULT 30,
  created_at          BIGINT       NOT NULL,
  done_at             BIGINT       NOT NULL DEFAULT 0,
  is_next             TINYINT(1)   NOT NULL DEFAULT 0,
  priority            BIGINT       NOT NULL DEFAULT 0,
  client_request_id   VARCHAR(64)  NOT NULL,
  updated_at          BIGINT       NOT NULL,
  deleted_at          BIGINT       NOT NULL DEFAULT 0,
  server_seq          BIGINT       NOT NULL,
  PRIMARY KEY (user_id, id),
  INDEX idx_st_seq (user_id, server_seq)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS biz_study_reviews (
  user_id           VARCHAR(36)  NOT NULL,
  record_id         VARCHAR(64)  NOT NULL,
  prompt            VARCHAR(255) NOT NULL,
  day               VARCHAR(32)  NOT NULL,
  completed_at      BIGINT       NOT NULL,
  client_request_id VARCHAR(64)  NOT NULL,
  updated_at        BIGINT       NOT NULL,
  deleted_at        BIGINT       NOT NULL DEFAULT 0,
  server_seq        BIGINT       NOT NULL,
  PRIMARY KEY (user_id, record_id),
  INDEX idx_srv_seq (user_id, server_seq)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS biz_study_garden_state (
  user_id            VARCHAR(36) PRIMARY KEY,
  current_plan_id    VARCHAR(64) NOT NULL DEFAULT '',
  water              INT         NOT NULL DEFAULT 0,
  carry_millis       BIGINT      NOT NULL DEFAULT 0,
  seeds              INT         NOT NULL DEFAULT 1,
  planted            TINYINT(1)  NOT NULL DEFAULT 0,
  growth             INT         NOT NULL DEFAULT 0,
  trees              INT         NOT NULL DEFAULT 0,
  rewarded_days_json JSON        NOT NULL,
  updated_at         BIGINT      NOT NULL,
  server_seq         BIGINT      NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS biz_todo_items (
  user_id               VARCHAR(36)  NOT NULL,
  id                    VARCHAR(64)  NOT NULL,
  title                 VARCHAR(160) NOT NULL,
  note                  VARCHAR(255) NOT NULL DEFAULT '',
  due_date              VARCHAR(32)  NOT NULL DEFAULT '',
  due_time              VARCHAR(16)  NOT NULL DEFAULT '',
  remind                TINYINT(1)   NOT NULL DEFAULT 0,
  remind_before_minutes INT          NOT NULL DEFAULT 30,
  created_at            BIGINT       NOT NULL,
  completed_at          BIGINT       NOT NULL DEFAULT 0,
  sort_order            INT          NOT NULL DEFAULT 0,
  client_request_id     VARCHAR(64)  NOT NULL,
  updated_at            BIGINT       NOT NULL,
  deleted_at            BIGINT       NOT NULL DEFAULT 0,
  server_seq            BIGINT       NOT NULL,
  PRIMARY KEY (user_id, id),
  UNIQUE KEY uk_todo_req (user_id, client_request_id),
  INDEX idx_todo_seq (user_id, server_seq)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 9b. 周期性固定账单 / 订阅规则表
CREATE TABLE IF NOT EXISTS biz_recurring_rules (
  user_id                 VARCHAR(36)  NOT NULL,
  id                      VARCHAR(64)  NOT NULL,
  ledger_id               VARCHAR(64)  NOT NULL,
  title                   VARCHAR(64)  NOT NULL,
  type                    VARCHAR(16)  NOT NULL COMMENT 'EXPENSE / INCOME',
  category                VARCHAR(64)  NOT NULL,
  amount_cents            BIGINT       NOT NULL,
  frequency               VARCHAR(16)  NOT NULL COMMENT 'MONTHLY / WEEKLY / YEARLY',
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 10. 意见反馈与开发者回复表
CREATE TABLE IF NOT EXISTS ops_feedbacks (
  id                VARCHAR(64)  PRIMARY KEY,
  user_id           VARCHAR(36)  NOT NULL,
  platform          VARCHAR(16)  NOT NULL DEFAULT 'android' COMMENT 'android / wx',
  category          VARCHAR(24)  NOT NULL,
  content           TEXT         NOT NULL,
  contact           VARCHAR(64)  NOT NULL DEFAULT '',
  app_version       VARCHAR(24)  NOT NULL DEFAULT '',
  status            VARCHAR(24)  NOT NULL DEFAULT 'pending' COMMENT 'pending / reviewing / resolved',
  reply             TEXT         NULL,
  replied_by        VARCHAR(64)  NULL,
  replied_at        BIGINT       NULL,
  client_request_id VARCHAR(64)  NOT NULL,
  created_at        BIGINT       NOT NULL,
  updated_at        BIGINT       NOT NULL,
  UNIQUE KEY uk_fb_req (user_id, client_request_id),
  INDEX idx_fb_user_time (user_id, created_at),
  INDEX idx_fb_status (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 11. 客户端崩溃与同步异常日志表
CREATE TABLE IF NOT EXISTS ops_client_error_logs (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id     VARCHAR(36)  NULL,
  platform    VARCHAR(16)  NOT NULL,
  app_version VARCHAR(24)  NOT NULL,
  device_info VARCHAR(128) NOT NULL DEFAULT '',
  error_code  VARCHAR(64)  NOT NULL DEFAULT '',
  stack_trace TEXT         NOT NULL,
  created_at  BIGINT       NOT NULL,
  INDEX idx_err_time (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
