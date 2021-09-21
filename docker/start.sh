#!/usr/bin/env sh
set -ex
mkdir -p storage
if ! [ -f storage/database.sqlite ]; then
  sqlite3 storage/database.sqlite -bail -init init.sql
fi
./bin/app "$@"
