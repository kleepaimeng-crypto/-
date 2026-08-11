FROM eclipse-temurin:21-jdk-jammy AS build

WORKDIR /workspace

COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw \
    && ./mvnw -B -ntp dependency:go-offline

COPY src src
RUN ./mvnw -B -ntp clean package -DskipTests

FROM eclipse-temurin:21-jre-jammy

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system cabin \
    && useradd --system --gid cabin --home-dir /app --shell /usr/sbin/nologin cabin

WORKDIR /app

COPY --from=build --chown=cabin:cabin /workspace/target/*.jar /app/app.jar

USER cabin

EXPOSE 8080
EXPOSE 8090-8096/udp

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
