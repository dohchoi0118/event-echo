CREATE TABLE pages (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    slug TEXT NOT NULL UNIQUE,
    nickname TEXT NOT NULL,
    actual_gender TEXT NOT NULL CHECK (actual_gender IN ('boy', 'girl')),
    reveal_at TEXT NOT NULL,
    due_date TEXT,
    message TEXT,
    theme TEXT NOT NULL CHECK (theme IN ('box', 'cake', 'balloon')),
    bgm_enabled INTEGER NOT NULL DEFAULT 0,
    owner_email TEXT NOT NULL,
    created_at TEXT NOT NULL,
    expires_at TEXT NOT NULL,
    extended INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE guesses (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    page_id INTEGER NOT NULL REFERENCES pages(id),
    guest_cookie_id TEXT NOT NULL,
    guessed_gender TEXT NOT NULL CHECK (guessed_gender IN ('boy', 'girl')),
    created_at TEXT NOT NULL,
    UNIQUE (page_id, guest_cookie_id)
);

CREATE TABLE guestbook_entries (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    page_id INTEGER NOT NULL REFERENCES pages(id),
    nickname TEXT NOT NULL,
    message TEXT NOT NULL,
    hidden INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL
);

CREATE TABLE magic_link_tokens (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    owner_email TEXT NOT NULL,
    page_id INTEGER REFERENCES pages(id),
    token_hash TEXT NOT NULL,
    expires_at TEXT NOT NULL,
    used INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL
);

CREATE TABLE owner_sessions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    page_id INTEGER NOT NULL REFERENCES pages(id),
    session_token_hash TEXT NOT NULL UNIQUE,
    expires_at TEXT NOT NULL,
    created_at TEXT NOT NULL
);

CREATE INDEX idx_guestbook_entries_page_id ON guestbook_entries(page_id);
CREATE INDEX idx_magic_link_tokens_token_hash ON magic_link_tokens(token_hash);
CREATE INDEX idx_owner_sessions_token_hash ON owner_sessions(session_token_hash);
