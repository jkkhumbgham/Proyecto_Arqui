-- RF-26/27: Rutas de Aprendizaje (schema courses)

CREATE TABLE IF NOT EXISTS courses.learning_paths (
  id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  title        VARCHAR(150)  NOT NULL,
  description  VARCHAR(1000),
  cover_url    VARCHAR(500),
  status       VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
  instructor_id UUID         NOT NULL,
  created_at   TIMESTAMP     NOT NULL DEFAULT NOW(),
  updated_at   TIMESTAMP     NOT NULL DEFAULT NOW(),
  deleted_at   TIMESTAMP
);

CREATE TABLE IF NOT EXISTS courses.learning_path_courses (
  learning_path_id UUID    REFERENCES courses.learning_paths(id) ON DELETE CASCADE,
  course_id        UUID    NOT NULL,
  position         INTEGER NOT NULL,
  PRIMARY KEY (learning_path_id, course_id)
);

CREATE TABLE IF NOT EXISTS courses.learning_path_enrollments (
  id               UUID      PRIMARY KEY DEFAULT gen_random_uuid(),
  student_id       UUID      NOT NULL,
  learning_path_id UUID      REFERENCES courses.learning_paths(id),
  enrolled_at      TIMESTAMP NOT NULL DEFAULT NOW(),
  UNIQUE(student_id, learning_path_id)
);

-- RF-31: Búsqueda — campo category en courses
ALTER TABLE courses.courses ADD COLUMN IF NOT EXISTS category VARCHAR(100);

CREATE INDEX IF NOT EXISTS idx_courses_title_desc ON courses.courses USING gin(to_tsvector('spanish', coalesce(title,'') || ' ' || coalesce(description,'')));
CREATE INDEX IF NOT EXISTS idx_courses_category ON courses.courses(category);
CREATE INDEX IF NOT EXISTS idx_learning_paths_status ON courses.learning_paths(status);
