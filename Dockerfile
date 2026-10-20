FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -DskipTests dependency:go-offline
COPY src src
ARG LICENSE_ENFORCE=true
RUN mkdir -p src/main/resources/license \
  && printf '%s\n' "$LICENSE_ENFORCE" > src/main/resources/license/enforce.txt
RUN ./mvnw -q -DskipTests package

FROM eclipse-temurin:25-jre
WORKDIR /app
RUN apt-get update \
  && apt-get install -y --no-install-recommends curl \
  && rm -rf /var/lib/apt/lists/*
COPY --from=build /app/target/*.jar app.jar
ARG LICENSE_ENFORCE=true
ENV ARAFA_LICENSE_ENFORCE=$LICENSE_ENFORCE
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
