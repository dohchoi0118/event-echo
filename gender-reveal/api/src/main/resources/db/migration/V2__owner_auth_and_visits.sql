DROP TABLE owner_sessions;
CREATE TABLE owner_sessions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    owner_email TEXT NOT NULL,
    session_token_hash TEXT NOT NULL UNIQUE,
    expires_at TEXT NOT NULL,
    created_at TEXT NOT NULL
);
CREATE INDEX idx_owner_sessions_owner_email ON owner_sessions(owner_email);

DROP TABLE magic_link_tokens;
CREATE TABLE magic_link_tokens (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    owner_email TEXT NOT NULL,
    token_hash TEXT NOT NULL UNIQUE,
    expires_at TEXT NOT NULL,
    used INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL
);
CREATE INDEX idx_magic_link_tokens_owner_email ON magic_link_tokens(owner_email, created_at);

CREATE TABLE page_visits (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    page_id INTEGER NOT NULL REFERENCES pages(id),
    guest_cookie_id TEXT NOT NULL,
    created_at TEXT NOT NULL,
    UNIQUE (page_id, guest_cookie_id)
);

ALTER TABLE guestbook_entries ADD COLUMN guest_cookie_id TEXT;
