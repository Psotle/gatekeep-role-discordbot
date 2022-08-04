FROM eclipse-temurin:11-jre-alpine
RUN mkdir /opt/app
COPY /home/runner/work/gatekeep-role-discordbot/gatekeep-role-discordbot/build/libs/gatekeep-role-discordbot.jar /opt/app
CMD ["java", "-jar", "/opt/app/gatekeep-role-discordbot.jar"]
