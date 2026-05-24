-- RF-33: Perfil de usuario
ALTER TABLE users.users ADD COLUMN IF NOT EXISTS avatar_url    VARCHAR(500);
ALTER TABLE users.users ADD COLUMN IF NOT EXISTS bio           VARCHAR(300);
ALTER TABLE users.users ADD COLUMN IF NOT EXISTS display_name  VARCHAR(100);

-- RF-29/30: Gamificación
CREATE TABLE IF NOT EXISTS users.user_points (
  id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id      UUID NOT NULL UNIQUE,
  total_points INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS users.point_events (
  id           UUID      PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id      UUID      NOT NULL,
  action_type  VARCHAR(50) NOT NULL,
  points       INTEGER   NOT NULL,
  reference_id VARCHAR(100),
  created_at   TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS users.user_badges (
  id         UUID      PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id    UUID      NOT NULL,
  badge_code VARCHAR(50) NOT NULL,
  earned_at  TIMESTAMP NOT NULL DEFAULT NOW(),
  UNIQUE(user_id, badge_code)
);

-- Reglas de gamificación configurables (RF-29: no hardcodeadas)
CREATE TABLE IF NOT EXISTS users.gamification_rules (
  action_type  VARCHAR(50) PRIMARY KEY,
  points       INTEGER     NOT NULL,
  description  VARCHAR(200),
  active       BOOLEAN     NOT NULL DEFAULT TRUE
);

-- Datos iniciales de reglas
INSERT INTO users.gamification_rules (action_type, points, description) VALUES
  ('LESSON_COMPLETED',        10,  'Completar una lección'),
  ('ASSESSMENT_PASSED_FIRST', 50,  'Aprobar evaluación en primer intento'),
  ('ASSESSMENT_PASSED_RETRY', 25,  'Aprobar evaluación en intento posterior'),
  ('FORUM_THREAD_CREATED',     5,  'Publicar hilo en foro'),
  ('FORUM_POST_CREATED',       3,  'Responder en foro'),
  ('COURSE_COMPLETED',        100, 'Completar un curso (100%)'),
  ('PATH_COMPLETED',          200, 'Completar una ruta de aprendizaje')
ON CONFLICT (action_type) DO NOTHING;

CREATE INDEX IF NOT EXISTS idx_point_events_user ON users.point_events(user_id);
CREATE INDEX IF NOT EXISTS idx_user_badges_user  ON users.user_badges(user_id);
