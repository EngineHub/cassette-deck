FROM eclipse-temurin:21 AS gradle_build
RUN apt-get -y update && apt-get install -y sqlite3
COPY . /
RUN ["./gradlew", "-si", "build", "installDist"]

FROM eclipse-temurin:21-alpine
RUN apk add --no-cache sqlite
RUN addgroup -g 1001 -S cassette_deck && adduser -u 1001 -S cassette_deck -G cassette_deck
RUN mkdir /cassette_deck && chown -R cassette_deck:cassette_deck /cassette_deck
WORKDIR /cassette_deck
USER cassette_deck
COPY docker/start.sh .
COPY --from=gradle_build /app/build/install/app .
COPY --from=gradle_build /app/src/main/sql/init.sql .
ENTRYPOINT ["./start.sh"]
EXPOSE 8080/tcp
