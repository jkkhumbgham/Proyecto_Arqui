-- RF-28: Add name fields to analytics.user_records for certificate generation
ALTER TABLE analytics.user_records ADD COLUMN IF NOT EXISTS first_name VARCHAR(100) NOT NULL DEFAULT '';
ALTER TABLE analytics.user_records ADD COLUMN IF NOT EXISTS last_name  VARCHAR(100) NOT NULL DEFAULT '';
