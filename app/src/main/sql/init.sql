CREATE TABLE minecraft_version
(
    version      TEXT PRIMARY KEY,
    data_version INTEGER,
    release_date TEXT,
    url TEXT
);

CREATE INDEX minecraft_version_data_version_index
ON minecraft_version (data_version);

CREATE TABLE authorized_token
(
    token TEXT PRIMARY KEY
);
