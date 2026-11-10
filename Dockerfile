FROM maven:3.9-eclipse-temurin-25 AS build

WORKDIR /app

COPY mvnw .
COPY .mvn/ .mvn/
COPY pom.xml .

RUN chmod +x mvnw

COPY src/ src/

RUN ./mvnw clean package -DskipTests

FROM eclipse-temurin:25-jre

# non-privileged user
RUN adduser --system spring-boot && addgroup --system spring-boot && adduser spring-boot spring-boot
USER spring-boot

WORKDIR /app

COPY --from=build /app/target/api-gateway-adv-0.0.1-SNAPSHOT.jar /app/application.jar

ENTRYPOINT ["java", "-jar", "./application.jar"]