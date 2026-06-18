-- Migration script to add retry count, max retries, status list and last attempt date to subscriptions table
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS retry_count INTEGER DEFAULT 0;
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS max_retries INTEGER DEFAULT 3;
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS last_attempt_date TIMESTAMP WITHOUT TIME ZONE;

-- Drop check constraint and recreate it to allow PAST_DUE and PAUSED statuses
ALTER TABLE subscriptions DROP CONSTRAINT IF EXISTS subscriptions_status_check;
ALTER TABLE subscriptions ADD CONSTRAINT subscriptions_status_check CHECK (status::text = ANY (ARRAY['ACTIVE'::character varying, 'PAST_DUE'::character varying, 'PAUSED'::character varying, 'CANCELLED'::character varying]::text[]));
