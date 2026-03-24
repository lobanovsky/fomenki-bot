FROM gradle:8.13-jdk21 AS builder
WORKDIR /app
COPY . .
RUN gradle shadowJar --no-daemon


FROM eclipse-temurin:21-jdk-jammy

# Install Chrome dependencies + Chrome stable
RUN apt-get update && apt-get install -y --no-install-recommends \
        wget gnupg unzip ca-certificates fonts-liberation \
        libasound2 libatk-bridge2.0-0 libatk1.0-0 libcups2 \
        libdbus-1-3 libgdk-pixbuf2.0-0 libnspr4 libnss3 \
        libx11-6 libxcomposite1 libxdamage1 libxext6 \
        libxfixes3 libxrandr2 libxrender1 xdg-utils \
    && wget -qO /etc/apt/trusted.gpg.d/google-chrome.asc \
        https://dl.google.com/linux/linux_signing_key.pub \
    && echo "deb [arch=amd64] http://dl.google.com/linux/chrome/deb/ stable main" \
        > /etc/apt/sources.list.d/google-chrome.list \
    && apt-get update && apt-get install -y --no-install-recommends google-chrome-stable \
    && rm -rf /var/lib/apt/lists/*

# Download ChromeDriver matching installed Chrome major version
RUN CHROME_MAJOR=$(google-chrome --version | grep -oP '\d+' | head -1) \
    && DRIVER_VER=$(wget -qO- "https://googlechromelabs.github.io/chrome-for-testing/LATEST_RELEASE_${CHROME_MAJOR}") \
    && wget -qO /tmp/chromedriver.zip \
        "https://storage.googleapis.com/chrome-for-testing-public/${DRIVER_VER}/linux64/chromedriver-linux64.zip" \
    && unzip /tmp/chromedriver.zip -d /tmp \
    && mv /tmp/chromedriver-linux64/chromedriver /usr/local/bin/chromedriver \
    && chmod +x /usr/local/bin/chromedriver \
    && rm -rf /tmp/chromedriver.zip /tmp/chromedriver-linux64

ENV CHROMEDRIVER_PATH=/usr/local/bin/chromedriver

WORKDIR /app
COPY --from=builder /app/build/libs/fomenki-bot-all.jar app.jar

VOLUME ["/app/data"]

CMD ["java", "-jar", "app.jar"]
