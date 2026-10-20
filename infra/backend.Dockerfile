FROM eclipse-temurin:21.0.8_9-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
ARG MODULE
WORKDIR /app
COPY backend/${MODULE}/target/${MODULE}-1.0.0.jar app.jar
USER 10001
ENTRYPOINT ["java","-jar","/app/app.jar"]
