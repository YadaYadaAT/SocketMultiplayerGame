FROM eclipse-temurin:21-jre

WORKDIR /app

COPY target/gomoku-server-1.0-SNAPSHOT.jar app.jar
COPY ../gomoku-protocol/target/gomoku-protocol-1.0-SNAPSHOT.jar protocol.jar

EXPOSE 999

ENTRYPOINT ["java", "-cp", "app.jar:protocol.jar", "com.athtech.gomoku.server.ServerLauncher"]
