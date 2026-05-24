-- Profile information shown on the dashboard. Correlated to auth.users by
-- username (the JWT subject) — no cross-schema foreign key, so the services
-- stay independently deployable.
CREATE TABLE user_info (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username   VARCHAR(100) NOT NULL UNIQUE,
    full_name  VARCHAR(150),
    email      VARCHAR(255),
    phone      VARCHAR(40),
    department VARCHAR(100),
    job_title  VARCHAR(120),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Devices a user has signed in from. Recorded by the SPA after login.
CREATE TABLE device_info (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_info_id  BIGINT NOT NULL REFERENCES user_info (id),
    device_name   VARCHAR(150) NOT NULL,
    device_type   VARCHAR(50),
    os            VARCHAR(80),
    browser       VARCHAR(80),
    last_seen_at  TIMESTAMPTZ,
    registered_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_info_id, device_name)
);
