FROM gradle:9.7.1-jdk17 AS build
WORKDIR /app
COPY . /app
RUN gradle installDist --no-daemon -q

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/build/install/guardianlink-server /app
ENV DATA_DIR=/data
RUN groupadd --system guardian && useradd --system --gid guardian --home-dir /nonexistent --shell /usr/sbin/nologin guardian \
    && mkdir -p /data \
    && chown -R guardian:guardian /app /data
USER guardian:guardian
EXPOSE 8080
CMD ["/app/bin/guardianlink-server"]
