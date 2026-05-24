-- Legacy OAuth client registry. Kept verbatim from the deprecated Spring
-- Security OAuth2 schema so the modern Authorization Server can read it
-- through a custom RegisteredClientRepository.
CREATE TABLE oauth_client_details (
    client_id               VARCHAR(255) PRIMARY KEY,
    resource_ids            VARCHAR(255),
    client_secret           VARCHAR(255),
    scope                   VARCHAR(255),
    authorized_grant_types  VARCHAR(255),
    web_server_redirect_uri VARCHAR(255),
    authorities             VARCHAR(255),
    access_token_validity   INTEGER,
    refresh_token_validity  INTEGER,
    additional_information  VARCHAR(4096),
    autoapprove             VARCHAR(255)
);

-- Application users for form login.
CREATE TABLE users (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username      VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    email         VARCHAR(255) NOT NULL UNIQUE,
    display_name  VARCHAR(150),
    enabled       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE user_authorities (
    user_id   BIGINT NOT NULL REFERENCES users (id),
    authority VARCHAR(50) NOT NULL,
    PRIMARY KEY (user_id, authority)
);
