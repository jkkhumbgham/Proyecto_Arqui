-- RF-32: Notificaciones in-app (schema collaboration)
CREATE TABLE IF NOT EXISTS collaboration.notifications (
  id           UUID      PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id      UUID      NOT NULL,
  type         VARCHAR(50)  NOT NULL,
  title        VARCHAR(200) NOT NULL,
  body         VARCHAR(500),
  reference_id VARCHAR(100),
  is_read      BOOLEAN   NOT NULL DEFAULT FALSE,
  created_at   TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_notifications_user     ON collaboration.notifications(user_id);
CREATE INDEX IF NOT EXISTS idx_notifications_unread   ON collaboration.notifications(user_id, is_read) WHERE is_read = FALSE;
