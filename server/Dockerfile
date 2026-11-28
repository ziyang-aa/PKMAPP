FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-Xms128m -Xmx384m -XX:+UseG1GC -Duser.timezone=Asia/Shanghai"

ARG JAR_FILE=target/songguo-server-4.0.0.jar
COPY ${JAR_FILE} /app/app.jar

RUN addgroup -S songguo && adduser -S songguo -G songguo && chown -R songguo:songguo /app
USER songguo

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
  CMD wget -qO- http://127.0.0.1:8080/api/v1/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
