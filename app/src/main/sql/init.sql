CREATE TABLE minecraft_version
(
    version      TEXT PRIMARY KEY,
    data_version INTEGER UNIQUE,
    release_date TEXT
);

CREATE TABLE authorized_token
(
    token TEXT PRIMARY KEY
);
