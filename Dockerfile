FROM maven:3-eclipse-temurin-21 AS builder

WORKDIR /usr/src/app

COPY . /usr/src/app
RUN mvn package -DskipTests

FROM eclipse-temurin:21-jre

COPY --from=builder /usr/src/app/target/*.jar /app.jar

EXPOSE 8080

ENTRYPOINT ["java"]
CMD ["-jar", "/app.jar"]
