-- ============================================================
-- TaskManager - PrymEvolution
-- Script de creación de base de datos y tablas
-- ============================================================
-- Uso:
--   mysql -u root -p < database/schema.sql
--
-- Este script es idempotente: puede ejecutarse varias veces sin duplicar tablas.
-- Refleja exactamente el esquema que generan las entidades JPA (User y Task).

CREATE DATABASE IF NOT EXISTS taskmanager_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE taskmanager_db;

-- ------------------------------------------------------------
-- Tabla: users
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50)  NOT NULL,
    password VARCHAR(255) NOT NULL,
    email    VARCHAR(255) NOT NULL,
    role     VARCHAR(20)  NOT NULL,
    enabled  BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT uq_users_email    UNIQUE (email)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ------------------------------------------------------------
-- Tabla: tasks
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tasks (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    title       VARCHAR(255) NOT NULL,
    description VARCHAR(500),
    due_date    DATE         NOT NULL,
    priority    VARCHAR(20),
    status      VARCHAR(20)  NOT NULL DEFAULT 'PENDIENTE',
    user_id     BIGINT       NOT NULL,
    created_at  DATETIME,
    CONSTRAINT fk_tasks_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
