FROM eclipse-temurin:11-jre-alpine
RUN mkdir /opt/app
COPY build/lib/gatekeep-role-discordbot.jar /opt/app
CMD ["java", "-jar", "/opt/app/gatekeep-role-discordbot.jar"]
