#!/usr/bin/env sh
set -ex
if ! [ -d storage ]; then
  mkdir storage
fi
if ! [ -f storage/database.sqlite ]; then
  sqlite3 storage/database.sqlite -bail -init init.sql
fi
./bin/app "$@"
