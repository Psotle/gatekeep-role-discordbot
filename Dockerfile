FROM gradle:7.6-jdk17-alpine AS build
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
RUN gradle build --no-daemon 

FROM eclipse-temurin:17-jre-alpine
ARG JAR_FILE=build/libs/gatekeep-role-discordbot-0.2.0.jar

RUN mkdir /app
COPY ${JAR_FILE} /app/gatekeep-role-discordbot-0.2.0.jar

ENTRYPOINT ["java","-jar","/app/gatekeep-role-discordbot-0.2.0.jar"]
