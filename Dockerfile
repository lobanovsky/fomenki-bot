FROM gradle:8.13-jdk21 AS builder

WORKDIR /app
COPY . .
RUN gradle shadowJar --no-daemon


FROM markhobson/maven-chrome:jdk-21

WORKDIR /app

COPY --from=builder /app/build/libs/fomenki-bot-all.jar app.jar
COPY chromedriver-linux64/chromedriver /app/chromedriver
RUN chmod +x /app/chromedriver

ENV CHROMEDRIVER_PATH=/app/chromedriver

VOLUME ["/app/data"]

CMD ["java", "-jar", "app.jar"]
