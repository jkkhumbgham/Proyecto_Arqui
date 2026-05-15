-- Schemas lógicos por servicio (cada servicio es dueño exclusivo de su schema)
CREATE SCHEMA IF NOT EXISTS users;
CREATE SCHEMA IF NOT EXISTS courses;
CREATE SCHEMA IF NOT EXISTS assessments;
CREATE SCHEMA IF NOT EXISTS collaboration;
CREATE SCHEMA IF NOT EXISTS analytics;

-- El usuario de aplicación tiene acceso completo a todos los schemas
GRANT ALL PRIVILEGES ON SCHEMA users        TO puj_admin;
GRANT ALL PRIVILEGES ON SCHEMA courses      TO puj_admin;
GRANT ALL PRIVILEGES ON SCHEMA assessments  TO puj_admin;
GRANT ALL PRIVILEGES ON SCHEMA collaboration TO puj_admin;
GRANT ALL PRIVILEGES ON SCHEMA analytics    TO puj_admin;
