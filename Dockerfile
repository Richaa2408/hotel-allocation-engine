# StayFlow: one image with the Spring Boot API and the built React frontend.
# Built and started by `docker compose --profile app up --build` (see compose.yaml and the README).

# 1. Frontend: TypeScript check and Vite build. Vite writes into ../target/classes/static.
FROM node:24-alpine AS web
WORKDIR /app/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

# 2. Backend: Maven package with the frontend already built (the Maven frontend plugin is skipped).
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline -Dskip.npm -Dskip.installnodenpm
COPY src src
COPY --from=web /app/target/classes/static target/classes/static
# Tests run separately with `mvnw test`; they use an in-memory database and need no Docker.
RUN ./mvnw -q -B package -DskipTests -Dskip.npm -Dskip.installnodenpm \
    && cp target/hotel-allocation-engine-*.jar target/app.jar

# 3. Runtime: JRE only, unprivileged user.
FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 stayflow
WORKDIR /app
COPY --from=build /app/target/app.jar app.jar
USER stayflow
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
