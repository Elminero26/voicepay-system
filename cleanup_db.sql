-- Cleanup script to remove unencrypted data
-- Run this in your PostgreSQL database if you want to start fresh with encrypted data

-- In voicepay_user database:
TRUNCATE TABLE users RESTART IDENTITY CASCADE;

-- In voicepay_payment database:
TRUNCATE TABLE payments RESTART IDENTITY CASCADE;
