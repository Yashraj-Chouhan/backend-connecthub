-- ============================================================
-- ConnectHub Admin Setup Script
-- Run this once in your MySQL/MariaDB to promote a specific user
-- to ADMIN so they can access the admin panel.
-- ============================================================

-- Step 1: Check what users exist
SELECT user_id, username, email, role FROM users;

-- Step 2: Promote the user you want to ADMIN.
--         Replace the email below with the admin account's email.
UPDATE users
SET role = 'ADMIN'
WHERE email = 'your-admin@email.com';

-- Step 3: Verify the change
SELECT user_id, username, email, role, is_blocked FROM users WHERE role = 'ADMIN';
