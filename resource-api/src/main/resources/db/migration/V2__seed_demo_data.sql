-- Demo profiles and devices, matching the demo users seeded by auth-server.
-- These make a fresh database immediately usable end to end.
INSERT INTO user_info (username, full_name, email, phone, department, job_title) VALUES
    ('alice', 'Alice Anderson', 'alice@example.com', '+1-555-0101', 'Platform Engineering', 'Staff Engineer'),
    ('bob',   'Bob Brown',      'bob@example.com',   '+1-555-0102', 'Security',             'Security Analyst');

INSERT INTO device_info (user_info_id, device_name, device_type, os, browser, last_seen_at) VALUES
    ((SELECT id FROM user_info WHERE username = 'alice'), 'Alice MacBook Pro', 'laptop', 'macOS 15',     'Chrome',  now()),
    ((SELECT id FROM user_info WHERE username = 'alice'), 'Alice iPhone',      'phone',  'iOS 19',       'Safari',  now() - INTERVAL '2 days'),
    ((SELECT id FROM user_info WHERE username = 'bob'),   'Bob ThinkPad',      'laptop', 'Ubuntu 24.04', 'Firefox', now());
