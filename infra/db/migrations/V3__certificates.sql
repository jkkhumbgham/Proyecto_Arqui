-- RF-28: Certificados (schema analytics)
CREATE TABLE IF NOT EXISTS analytics.certificates (
  id                UUID      PRIMARY KEY DEFAULT gen_random_uuid(),
  student_id        UUID      NOT NULL,
  course_id         UUID      NOT NULL,
  course_title      VARCHAR(200) NOT NULL,
  student_name      VARCHAR(200) NOT NULL,
  instructor_name   VARCHAR(200) NOT NULL,
  verification_code UUID      NOT NULL UNIQUE DEFAULT gen_random_uuid(),
  pdf_url           VARCHAR(500) NOT NULL,
  issued_at         TIMESTAMP NOT NULL DEFAULT NOW(),
  UNIQUE(student_id, course_id)
);

CREATE INDEX IF NOT EXISTS idx_certificates_student ON analytics.certificates(student_id);
CREATE INDEX IF NOT EXISTS idx_certificates_course  ON analytics.certificates(course_id);
