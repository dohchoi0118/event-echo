-- actual_gender is no longer set at page creation: a separate owner-authenticated flow lets
-- a third party (e.g. the doctor) set it later, so it must be nullable until then. SQLite has
-- no ALTER COLUMN, so the table is recreated. The new table is built under a temporary name and
-- the old "pages" is dropped and renamed into place last, so no other table's REFERENCES pages(id)
-- clause is rewritten mid-migration (SQLite auto-rewrites FK clauses in OTHER tables when the
-- REFERENCED table itself is renamed, which recreating "pages" directly would trigger).
--
-- foreign_keys must be off for this: with real data present, guesses/guestbook_entries/etc. hold
-- rows referencing pages(id), and SQLite enforces that FK on DROP TABLE pages, not just on row
-- writes. This is SQLite's own documented procedure for a constraint change via table recreation.
PRAGMA foreign_keys = OFF;

CREATE TABLE pages_new (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    slug TEXT NOT NULL UNIQUE,
    nickname TEXT NOT NULL,
    actual_gender TEXT CHECK (actual_gender IN ('boy', 'girl')),
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

INSERT INTO pages_new SELECT * FROM pages;

DROP TABLE pages;

ALTER TABLE pages_new RENAME TO pages;

PRAGMA foreign_keys = ON;
