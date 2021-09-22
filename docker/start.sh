#!/usr/bin/env sh
set -ex
if ! [ -d storage ]; then
  mkdir storage
fi
if ! [ -f storage/database.sqlite ]; then
  sqlite3 storage/database.sqlite -bail -init init.sql
  if [ -d storage/init.d ]; then
    for sql in storage/init.d/*.sql; do
      sqlite3 storage/database.sqlite -bail -init "$sql"
    done
  fi
fi
./bin/app "$@"
