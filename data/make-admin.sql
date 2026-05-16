-- ============================================================
-- ConnectHub — Promote a user to ADMIN
-- ============================================================
-- 1. Register normally via the app at http://localhost:5173/
-- 2. Find your user ID (or use the email lookup below)
-- 3. Run ONE of these queries against the connecthub_auth DB
-- ============================================================

-- Option A — promote by email address (most convenient)
UPDATE users
SET role = 'ADMIN'
WHERE email = 'your-admin@example.com';

-- Option B — promote by username
UPDATE users
SET role = 'ADMIN'
WHERE username = 'your_username';

-- Verify the change
SELECT user_id, username, email, role, is_blocked
FROM users
WHERE role = 'ADMIN';
