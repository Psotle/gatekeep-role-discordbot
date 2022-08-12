FROM gradle:7.5-jdk17-alpine AS build
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
RUN gradle build --no-daemon 

FROM eclipse-temurin:17-jre-alpine
ARG JAR_FILE=build/libs/gatekeep-role-discordbot.jar

RUN mkdir /app
COPY ${JAR_FILE} /app/gatekeep-role-discordbot.jar

ENTRYPOINT ["java","-jar","/app/gatekeep-role-discordbot.jar"]
