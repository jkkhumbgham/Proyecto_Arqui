-- Schemas lógicos por servicio en learning_platform
CREATE SCHEMA IF NOT EXISTS users;
CREATE SCHEMA IF NOT EXISTS courses;
CREATE SCHEMA IF NOT EXISTS assessments;
CREATE SCHEMA IF NOT EXISTS collaboration;

-- El usuario de aplicación tiene acceso completo a todos los schemas
GRANT ALL PRIVILEGES ON SCHEMA users         TO puj_admin;
GRANT ALL PRIVILEGES ON SCHEMA courses       TO puj_admin;
GRANT ALL PRIVILEGES ON SCHEMA assessments   TO puj_admin;
GRANT ALL PRIVILEGES ON SCHEMA collaboration TO puj_admin;

-- Base de datos dedicada para analytics-service (.NET)
-- Se crea aquí para que el init.sql la provisione en el mismo servidor PostgreSQL
SELECT 'CREATE DATABASE analytics_db OWNER puj_admin ENCODING ''UTF8'' TEMPLATE template0'
    WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'analytics_db')\gexec

\c analytics_db
CREATE SCHEMA IF NOT EXISTS analytics;
GRANT ALL PRIVILEGES ON SCHEMA analytics TO puj_admin;
GRANT ALL PRIVILEGES ON DATABASE analytics_db TO puj_admin;
