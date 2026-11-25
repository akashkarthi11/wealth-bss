FROM maven:3.9-eclipse-temurin-21 AS build
ARG COMMON_CACHE_BUST
RUN apt-get update && apt-get install -y --no-install-recommends git && rm -rf /var/lib/apt/lists/*
RUN git clone --depth 1 --branch development https://github.com/akashkarthi11/wealth-common.git /wealth-common && mvn -B -f /wealth-common/pom.xml install -DskipTests
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 appuser
COPY --from=build /workspace/target/wealth-bss-0.1.0-SNAPSHOT.jar app.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=65.0","-jar","/app/app.jar"]
