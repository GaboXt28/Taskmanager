-- ============================================================
-- TaskManager - PrymEvolution
-- Script de datos iniciales (seed)
-- ============================================================
-- Uso (después de ejecutar schema.sql):
--   mysql -u root -p < database/data.sql
--
-- Crea las dos cuentas de prueba exigidas por la rúbrica:
--   admin   / admin123  -> rol ADMIN
--   usuario / user123   -> rol USER
--
-- Las contraseñas se guardan como hash BCrypt (nunca en texto plano), generado con el
-- mismo algoritmo que usa BCryptPasswordEncoder en la aplicación (factor de coste 10).
-- Este script es idempotente: usa INSERT IGNORE, por lo que no falla si ya existen
-- los usuarios (por ejemplo, si DataInitializer ya los sembró en un arranque previo).

USE taskmanager_db;

INSERT IGNORE INTO users (username, password, email, role, enabled) VALUES
    ('admin',   '$2b$10$g3IfVhQwWTDTafKG5TLWEuHlzlrMQ2Sciflt4xBdigx9.okUEV9/u', 'admin@taskmanager.com',   'ADMIN', TRUE),
    ('usuario', '$2b$10$WKkbDcGK70hn.XTKwotVR.5XTKfLVS2HdRDhs9KxMoafel/fRvNJa', 'usuario@taskmanager.com', 'USER',  TRUE);
