FROM openjdk:16-alpine AS gradle_build
RUN apk add --no-cache sqlite
COPY . /
RUN ["./gradlew", "-si", "build", "installDist"]

FROM openjdk:16-alpine
RUN apk add --no-cache sqlite
RUN addgroup -g 1001 -S cassette_deck && adduser -u 1001 -S cassette_deck -G cassette_deck
RUN mkdir /cassette_deck && chown -R cassette_deck:cassette_deck /cassette_deck
WORKDIR /cassette_deck
USER cassette_deck
RUN mkdir /cassette_deck/storage
COPY --from=gradle_build /app/build/install/app .
COPY --from=gradle_build /app/src/main/sql/init.sql .
RUN ["sqlite3", "storage/database.sqlite", "-bail", "-init", "init.sql"]
VOLUME /cassette_deck/storage
ENTRYPOINT ["./bin/app"]
EXPOSE 8080/tcp
