FROM node:20-alpine AS frontend-build

WORKDIR /app/frontend

COPY frontend/package*.json ./
RUN npm ci

COPY frontend/ ./
RUN mkdir -p /app/src/main/resources/static
RUN npm run build

FROM maven:3.9.9-eclipse-temurin-17 AS backend-build

WORKDIR /app

COPY pom.xml ./
RUN mvn -q -DskipTests dependency:go-offline

COPY src ./src
COPY --from=frontend-build /app/src/main/resources/static/storefront ./src/main/resources/static/storefront
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

RUN addgroup -S app && adduser -S app -G app

COPY --from=backend-build /app/target/whatsapp-checkout-nuvemshop-0.0.1-SNAPSHOT.jar /app/app.jar
COPY deploy/render-entrypoint.sh /app/render-entrypoint.sh
RUN chmod +x /app/render-entrypoint.sh && chown -R app:app /app

USER app

EXPOSE 8080

ENTRYPOINT ["/app/render-entrypoint.sh"]
